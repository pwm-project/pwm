/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.ws.server.rest;

import lombok.Builder;
import lombok.Data;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.svc.stats.AvgStatistic;
import password.pwm.svc.stats.DailyKey;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticType;
import password.pwm.svc.stats.StatisticsBundle;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestWebServer;

import javax.servlet.annotation.WebServlet;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/statistics"
        }
)
@RestWebServer( webService = WebServiceUsage.Statistics )
public class RestStatisticsServer extends RestServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestStatisticsServer.class );

    private static final String FIELD_VERSION = "version";
    private static final String FIELD_DAYS = "days";
    private static final int MAX_DAYS = 365 * 5;

    @Value
    @Builder
    public static class JsonOutput implements Serializable
    {
        public List<StatLabelData> labels;
        public List<StatValue> eventRates;
        public List<StatValue> current;
        public List<StatValue> cumulative;
        public List<HistoryData> history;
    }

    @Builder
    @Value static class HistoryData implements Serializable
    {
        private String name;
        private String date;
        private int year;
        private int month;
        private int day;
        private int daysAgo;
        private List<StatValue> data;
    }

    @Value
    public static class StatValue implements Serializable
    {
        private String name;
        private String value;
    }

    @Value
    public static class StatLabelData implements Serializable
    {
        final String name;
        final String label;
        final String type;
        final String description;
    }

    @Override
    public void preCheckRequest( final RestRequest restRequest ) throws PwmUnrecoverableException
    {
    }

    @RestMethodHandler( method = HttpMethod.GET, consumes = HttpContentType.form, produces = HttpContentType.json )
    public RestResultBean doPwmStatisticJsonGet( final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        final int defaultVersion = Integer.parseInt( restRequest.getPwmApplication().getConfig().readAppProperty( AppProperty.WS_REST_SERVER_STATISTICS_DEFAULT_VERSION ) );
        final int version = restRequest.readParameterAsInt( FIELD_VERSION, defaultVersion );

        switch ( version )
        {

            case 1:
                return OutputVersion1.dataOutput( restRequest );

            case 2:
                return OutputVersion2.makeData( restRequest );

            default:
                JavaHelper.unhandledSwitchStatement( version );
        }

        // unreachable
        return null;
    }

    private static class OutputVersion2
    {
        private static RestResultBean makeData( final RestRequest restRequest )
                throws PwmUnrecoverableException
        {
            final Locale locale = restRequest.getLocale();
            final int defaultDays = Integer.parseInt( restRequest.getPwmApplication().getConfig().readAppProperty( AppProperty.WS_REST_SERVER_STATISTICS_DEFAULT_HISTORY ) );
            final int days = JavaHelper.rangeCheck(
                    0,
                    restRequest.readParameterAsInt( FIELD_DAYS, defaultDays ),
                    MAX_DAYS
            );

            final StatisticsManager statisticsManager = restRequest.getPwmApplication().getStatisticsManager();
            final JsonOutput jsonOutput = RestStatisticsServer.JsonOutput.builder()
                    .cumulative( makeStatInfos( statisticsManager, StatisticsManager.KEY_CUMULATIVE ) )
                    .current( makeStatInfos( statisticsManager, StatisticsManager.KEY_CURRENT ) )
                    .eventRates( makeEpsStatInfos( statisticsManager ) )
                    .history( makeHistoryStatInfos( statisticsManager, days ) )
                    .labels( makeLabels( locale ) )
                    .build();
            return RestResultBean.withData( jsonOutput );
        }

        private static List<StatValue> makeStatInfos( final StatisticsManager statisticsManager, final String key )
        {
            final Map<String, StatValue> output = new TreeMap<>();
            for ( final Statistic statistic : Statistic.values() )
            {
                final StatisticsBundle bundle = statisticsManager.getStatBundleForKey( key );
                final String value = bundle.getStatistic( statistic );
                final StatValue statValue = new StatValue( statistic.name(), value );
                output.put( statistic.name(), statValue );
            }

            return Collections.unmodifiableList( new ArrayList<>( output.values() ) );
        }

        private static List<HistoryData> makeHistoryStatInfos(
                final StatisticsManager statisticsManager,
                final int days
        )
        {
            final List<HistoryData> outerOutput = new ArrayList<>();

            DailyKey dailyKey = DailyKey.forToday();

            for ( int daysAgo = 0; daysAgo < days; daysAgo++ )
            {
                final Map<String, StatValue> output = new TreeMap<>();
                for ( final Statistic statistic : Statistic.values() )
                {
                    final StatisticsBundle bundle = statisticsManager.getStatBundleForKey( dailyKey.toString() );
                    final String value = bundle.getStatistic( statistic );
                    final StatValue statValue = new StatValue( statistic.name(), value );
                    output.put( statistic.name(), statValue );
                }
                final List<StatValue> statValues = Collections.unmodifiableList( new ArrayList<>( output.values() ) );
                final HistoryData historyData = HistoryData.builder()
                        .name( dailyKey.toString() )
                        .date( DateTimeFormatter.ofPattern( "yyyy-MM-dd" ).withZone( ZoneOffset.UTC )
                                .format( dailyKey.localDate() ) )
                        .year( dailyKey.localDate().getYear() )
                        .month( dailyKey.localDate().getMonthValue() )
                        .day( dailyKey.localDate().getDayOfMonth() )
                        .daysAgo( daysAgo )
                        .data( statValues )
                        .build();
                outerOutput.add( historyData );
                dailyKey = dailyKey.previous();
            }

            return Collections.unmodifiableList( outerOutput );
        }

        private static List<StatValue> makeEpsStatInfos( final StatisticsManager statisticsManager )
        {
            final Map<String, StatValue> output = new TreeMap<>();
            for ( final EpsStatistic loopEps : EpsStatistic.values() )
            {
                for ( final Statistic.EpsDuration loopDuration : Statistic.EpsDuration.values() )
                {
                    final BigDecimal loopValue = statisticsManager.readEps( loopEps, loopDuration );
                    final BigDecimal outputValue = loopValue.setScale( 3, RoundingMode.UP );
                    final String name = loopEps.toString() + "_" + loopDuration.toString();
                    final StatValue statValue = new StatValue( name, outputValue.toString() );
                    output.put( name, statValue );
                }
            }

            return Collections.unmodifiableList( new ArrayList<>( output.values() ) );
        }

        private static List<StatLabelData> makeLabels( final Locale locale )
        {
            final Map<String, StatLabelData> output = new TreeMap<>();
            for ( final Statistic statistic : Statistic.values() )
            {
                final StatLabelData statLabelData = new StatLabelData(
                        statistic.name(),
                        statistic.getLabel( locale ),
                        StatisticType.INCREMENTER.name(),
                        statistic.getDescription( locale ) );
                output.put( statistic.name(), statLabelData );
            }
            for ( final AvgStatistic statistic : AvgStatistic.values() )
            {
                final StatLabelData statLabelData = new StatLabelData(
                        statistic.name(),
                        statistic.getLabel( locale ),
                        StatisticType.AVERAGE.name(),
                        statistic.getDescription( locale ) );
                output.put( statistic.name(), statLabelData );
            }
            for ( final EpsStatistic loopEps : EpsStatistic.values() )
            {
                for ( final Statistic.EpsDuration loopDuration : Statistic.EpsDuration.values() )
                {
                    final String name = loopEps.toString() + "_" + loopDuration.toString();
                    final StatLabelData statLabelData = new StatLabelData(
                            name,
                            loopEps.getLabel( locale ),
                            StatisticType.EPS.name(),
                            null );
                    output.put( name, statLabelData );
                }
            }

            return Collections.unmodifiableList( new ArrayList<>( output.values() ) );
        }
    }


    public static class OutputVersion1
    {
        @Data
        public static class JsonOutput implements Serializable
        {
            @SuppressWarnings( "checkstyle:MemberName" )
            public Map<String, String> EPS;
            public Map<String, Object> nameData;
            public Map<String, Object> keyData;
        }

        private static RestResultBean dataOutput( final RestRequest restRequest )
                throws PwmUnrecoverableException
        {
            final String statKey = restRequest.readParameterAsString( "statKey", PwmHttpRequestWrapper.Flag.BypassValidation );
            final String statName = restRequest.readParameterAsString( "statName", PwmHttpRequestWrapper.Flag.BypassValidation );
            final String days = restRequest.readParameterAsString( "days", PwmHttpRequestWrapper.Flag.BypassValidation );

            try
            {
                final StatisticsManager statisticsManager = restRequest.getPwmApplication().getStatisticsManager();
                final JsonOutput jsonOutput = new JsonOutput();
                jsonOutput.EPS = addEpsStats( statisticsManager );

                if ( statName != null && statName.length() > 0 )
                {
                    jsonOutput.nameData = doNameStat( statisticsManager, statName, days );
                }
                else
                {
                    jsonOutput.keyData = doKeyStat( statisticsManager, statKey );
                }

                StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_STATISTICS );

                final RestResultBean resultBean = RestResultBean.withData( jsonOutput );
                return resultBean;
            }
            catch ( final Exception e )
            {
                final String errorMsg = "unexpected error building json response: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                return RestResultBean.fromError( restRequest, errorInformation );
            }
        }

        public static Map<String, Object> doNameStat( final StatisticsManager statisticsManager, final String statName, final String days )
        {
            final Statistic statistic = Statistic.valueOf( statName );
            final int historyDays = StringUtil.convertStrToInt( days, 30 );

            final Map<String, Object> results = new HashMap<>( statisticsManager.getStatHistory( statistic, historyDays ) );
            return results;
        }

        public static Map<String, Object> doKeyStat( final StatisticsManager statisticsManager, final String statKey )
        {
            final String key = ( statKey == null )
                    ? StatisticsManager.KEY_CUMULATIVE
                    : statKey;

            final StatisticsBundle statisticsBundle = statisticsManager.getStatBundleForKey( key );
            final Map<String, Object> outputValueMap = new TreeMap<>();
            for ( final Statistic stat : Statistic.values() )
            {
                outputValueMap.put( stat.name(), statisticsBundle.getStatistic( stat ) );
            }

            return outputValueMap;
        }

        public static Map<String, String> addEpsStats( final StatisticsManager statisticsManager )
        {
            final Map<String, String> outputMap = new TreeMap<>();
            for ( final EpsStatistic loopEps : EpsStatistic.values() )
            {
                for ( final Statistic.EpsDuration loopDuration : Statistic.EpsDuration.values() )
                {
                    final BigDecimal loopValue = statisticsManager.readEps( loopEps, loopDuration );
                    final BigDecimal outputValue = loopValue.setScale( 3, RoundingMode.UP );
                    outputMap.put( loopEps.toString() + "_" + loopDuration.toString(), outputValue.toString() );
                }
            }

            return outputMap;
        }
    }
}

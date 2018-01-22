/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.ws.server.rest;

import lombok.Data;
import password.pwm.PwmConstants;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsBundle;
import password.pwm.svc.stats.StatisticsManager;
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
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/statistics"
        }
)
@RestWebServer( webService = WebServiceUsage.Statistics, requireAuthentication = true )
public class RestStatisticsServer extends RestServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestStatisticsServer.class );

    @Data
    public static class JsonOutput implements Serializable
    {
        @SuppressWarnings( "checkstyle:MemberName" )
        public Map<String, String> EPS;
        public Map<String, Object> nameData;
        public Map<String, Object> keyData;
    }

    @Override
    public void preCheckRequest( final RestRequest restRequest ) throws PwmUnrecoverableException
    {
        if ( !restRequest.getRestAuthentication().getUsages().contains( WebServiceUsage.Health ) )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "public statistics service is not enabled" );
        }
    }

    @RestMethodHandler( method = HttpMethod.GET, consumes = HttpContentType.form, produces = HttpContentType.json )
    public RestResultBean doPwmStatisticJsonGet( final RestRequest restRequest )
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
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMsg );
            return RestResultBean.fromError( restRequest, errorInformation );

        }
    }


    public static Map<String, Object> doNameStat( final StatisticsManager statisticsManager, final String statName, final String days )
    {
        final Statistic statistic = Statistic.valueOf( statName );
        final int historyDays = StringUtil.convertStrToInt( days, 30 );

        final Map<String, Object> results = new HashMap<>();
        results.putAll( statisticsManager.getStatHistory( statistic, historyDays ) );
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
            outputValueMap.put( stat.getKey(), statisticsBundle.getStatistic( stat ) );
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

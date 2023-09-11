/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.http.servlet.admin.domain;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.DisplayElement;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.admin.SystemAdminServlet;
import password.pwm.svc.stats.StatisticsBundle;
import password.pwm.svc.stats.StatisticsBundleKey;
import password.pwm.svc.stats.StatisticsService;
import password.pwm.svc.stats.StatisticsUtils;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.CollectorUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/statistics",
        }
)
public class DomainAdminStatisticsServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DomainAdminStatisticsServlet.class );
    private static final String DISPLAY_KEY_PREFIX = "Statistic_Key_";

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    public enum DomainAdminStatisticsAction implements ProcessAction
    {
        downloadStatisticsLogCsv( HttpMethod.POST ),
        readKeys( HttpMethod.POST ),
        readStatistics( HttpMethod.POST ),;

        private final Collection<HttpMethod> method;

        DomainAdminStatisticsAction( final HttpMethod... method )
        {
            this.method = List.of( method );
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return method;
        }
    }

    @Override
    protected PwmServletDefinition getServletDefinition()
    {
        return PwmServletDefinition.DomainAdminStatistics;
    }

    @Override
    public Optional<Class<? extends ProcessAction>> getProcessActionsClass()
    {
        return Optional.of( DomainAdminStatisticsAction.class );
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        pwmRequest.forwardToJsp( JspUrl.ADMIN_STATISTICS );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        return SystemAdminServlet.preProcessAdminCheck( pwmRequest );

    }

    @ActionHandler( action = "readKeys" )
    public ProcessStatus restReadKeys( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final StatisticsService statisticsService = pwmRequest.getPwmDomain().getStatisticsService();
        final Locale locale = pwmRequest.getLocale();

        final Map<String, String> keys = statisticsService.allKeys().stream()
                .collect( CollectorUtil.toUnmodifiableLinkedMap(
                        Object::toString,
                        entry -> entry.getLabel( locale ) ) );

        final RestResultBean<Map> results = RestResultBean.withData( keys, Map.class );
        pwmRequest.outputJsonResult( results );
        return ProcessStatus.Halt;
    }


    @ActionHandler( action = "readStatistics" )
    public ProcessStatus restReadStatistics( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final String selectedKey = pwmRequest.readParameterAsString( "statKey" );
        final StatisticsBundleKey statKey = StatisticsBundleKey.fromStringOrDefaultCumulative( selectedKey );

        final StatisticsService statisticsService = pwmRequest.getPwmDomain().getStatisticsService();
        final StatisticsBundle statisticsBundle = statisticsService.getStatBundleForKey( statKey )
                .orElseThrow();

        final List<DisplayElement> displayStatistics = StatisticsUtils.statsDisplayElementsForBundle(
                DISPLAY_KEY_PREFIX, pwmRequest.getLocale(), statisticsBundle );

        final List<DisplayElement> averageDisplayStatistics = StatisticsUtils.avgStatsDisplayElementsForBundle(
                DISPLAY_KEY_PREFIX, pwmRequest.getLocale(), statisticsBundle );

        final StatisticsData statisticsData = new StatisticsData( displayStatistics, averageDisplayStatistics );

        final RestResultBean<StatisticsData> results = RestResultBean.withData( statisticsData, StatisticsData.class );
        pwmRequest.outputJsonResult( results );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "downloadStatisticsLogCsv" )
    public ProcessStatus downloadStatisticsLogCsv( final PwmRequest pwmRequest )
            throws IOException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        pwmRequest.getPwmResponse().markAsDownload(
                HttpContentType.csv,
                pwmRequest.getPwmDomain().getConfig().readAppProperty( AppProperty.DOWNLOAD_FILENAME_STATISTICS_CSV )
        );

        try ( OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream() )
        {
           StatisticsUtils.outputStatsToCsv(
                    pwmRequest.getLabel(),
                    pwmDomain.getStatisticsService(),
                    outputStream,
                    pwmRequest.getLocale(),
                    StatisticsUtils.CsvOutputFlag.includeHeader );
        }

        return ProcessStatus.Halt;
    }

    private record StatisticsData(
            List<DisplayElement> statistics,
            List<DisplayElement> averageStatistics
    )
    {
        private StatisticsData(
                final List<DisplayElement> statistics,
                final List<DisplayElement> averageStatistics
        )
        {
            this.statistics = CollectionUtil.stripNulls( statistics );
            this.averageStatistics = CollectionUtil.stripNulls( averageStatistics );
        }
    }


}

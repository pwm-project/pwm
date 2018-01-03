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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestWebServer;
import password.pwm.ws.server.rest.bean.HealthData;
import password.pwm.ws.server.rest.bean.HealthRecord;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/health",
        }
)
@RestWebServer( webService = WebServiceUsage.Health, requireAuthentication = false )
public class RestHealthServer extends RestServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestHealthServer.class );

    private static final String PARAM_IMMEDIATE_REFRESH = "refreshImmediate";

    @Override
    public void preCheckRequest( final RestRequest restRequest ) throws PwmUnrecoverableException
    {
        if ( !restRequest.getRestAuthentication().getUsages().contains( WebServiceUsage.Health ) )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "public health service is not enabled" );
        }
    }

    @RestMethodHandler( method = HttpMethod.GET, produces = HttpContentType.plain )
    private RestResultBean doPwmHealthPlainGet( final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        final boolean requestImmediateParam = restRequest.readParameterAsBoolean( PARAM_IMMEDIATE_REFRESH );

        try
        {
            final HealthMonitor.CheckTimeliness timeliness = determineDataTimeliness( requestImmediateParam );
            final String resultString = restRequest.getPwmApplication().getHealthMonitor().getMostSevereHealthStatus( timeliness ).toString() + "\n";
            StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_HEALTH );
            return RestResultBean.withData( resultString );
        }
        catch ( Exception e )
        {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMessage );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }

    @RestMethodHandler( method = HttpMethod.GET, consumes = HttpContentType.json, produces = HttpContentType.json )
    private RestResultBean doPwmHealthJsonGet( final RestRequest restRequest )
            throws PwmUnrecoverableException, IOException
    {
        final boolean requestImmediateParam = restRequest.readParameterAsBoolean( PARAM_IMMEDIATE_REFRESH );

        final HealthData jsonOutput = processGetHealthCheckData( restRequest.getPwmApplication(), restRequest.getLocale(), requestImmediateParam );
        StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_HEALTH );
        return RestResultBean.withData( jsonOutput );
    }

    private static HealthMonitor.CheckTimeliness determineDataTimeliness(
            final boolean refreshImmediate
    )
            throws PwmUnrecoverableException
    {
        return refreshImmediate
                ? HealthMonitor.CheckTimeliness.Immediate
                : HealthMonitor.CheckTimeliness.CurrentButNotAncient;
    }

    public static HealthData processGetHealthCheckData(
            final PwmApplication pwmApplication,
            final Locale locale,
            final boolean refreshImmediate
    )
            throws IOException, PwmUnrecoverableException
    {
        final HealthMonitor healthMonitor = pwmApplication.getHealthMonitor();
        final HealthMonitor.CheckTimeliness timeliness = determineDataTimeliness( refreshImmediate );
        final List<password.pwm.health.HealthRecord> healthRecords = new ArrayList<>( healthMonitor.getHealthRecords( timeliness ) );
        final List<HealthRecord> healthRecordBeans = HealthRecord.fromHealthRecords( healthRecords, locale,
                pwmApplication.getConfig() );
        final HealthData healthData = new HealthData();
        healthData.timestamp = healthMonitor.getLastHealthCheckTime();
        healthData.overall = healthMonitor.getMostSevereHealthStatus( timeliness ).toString();
        healthData.records = healthRecordBeans;
        return healthData;
    }
}

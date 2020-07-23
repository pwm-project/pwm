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
@RestWebServer( webService = WebServiceUsage.Health )
public class RestHealthServer extends RestServlet
{
    private static final String PARAM_IMMEDIATE_REFRESH = "refreshImmediate";

    @Override
    public void preCheckRequest( final RestRequest restRequest ) throws PwmUnrecoverableException
    {
    }

    @RestMethodHandler( method = HttpMethod.GET, produces = HttpContentType.plain )
    private RestResultBean doPwmHealthPlainGet( final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        try
        {
            final String resultString = restRequest.getPwmApplication().getHealthMonitor().getMostSevereHealthStatus().toString() + "\n";
            StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_HEALTH );
            return RestResultBean.withData( resultString );
        }
        catch ( final Exception e )
        {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMessage );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }

    @RestMethodHandler( method = HttpMethod.GET, consumes = HttpContentType.json, produces = HttpContentType.json )
    private RestResultBean doPwmHealthJsonGet( final RestRequest restRequest )
            throws PwmUnrecoverableException, IOException
    {
        final HealthData jsonOutput = processGetHealthCheckData( restRequest.getPwmApplication(), restRequest.getLocale() );
        StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_HEALTH );
        return RestResultBean.withData( jsonOutput );
    }

    public static HealthData processGetHealthCheckData(
            final PwmApplication pwmApplication,
            final Locale locale
    )
    {
        final HealthMonitor healthMonitor = pwmApplication.getHealthMonitor();
        final List<password.pwm.health.HealthRecord> healthRecords = new ArrayList<>( healthMonitor.getHealthRecords() );
        final List<HealthRecord> healthRecordBeans = HealthRecord.fromHealthRecords( healthRecords, locale,
                pwmApplication.getConfig() );
        return HealthData.builder()
                .timestamp( healthMonitor.getLastHealthCheckTime() )
                .overall( healthMonitor.getMostSevereHealthStatus().toString() )
                .records( healthRecordBeans )
                .build();

    }
}

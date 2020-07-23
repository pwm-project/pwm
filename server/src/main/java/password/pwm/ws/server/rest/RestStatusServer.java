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

import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmConstants;
import password.pwm.bean.pub.PublicUserInfoBean;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestUtility;
import password.pwm.ws.server.RestWebServer;

import javax.servlet.annotation.WebServlet;
import java.time.Instant;


@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/status",
        }
)
@RestWebServer( webService = WebServiceUsage.Status )
public class RestStatusServer extends RestServlet
{
    public static final PwmLogger LOGGER = PwmLogger.forClass( RestStatusServer.class );

    @Override
    public void preCheckRequest( final RestRequest restRequest ) throws PwmUnrecoverableException
    {
    }

    @RestMethodHandler( method = HttpMethod.GET, produces = HttpContentType.json, consumes = HttpContentType.json )
    public RestResultBean doGetStatusData( final RestRequest restRequest )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final String username = restRequest.readParameterAsString( "username" );
        final TargetUserIdentity targetUserIdentity = RestUtility.resolveRequestedUsername( restRequest, username );

        try
        {
            final ChaiProvider chaiProvider = targetUserIdentity.getChaiProvider();
            final UserInfo userInfo = UserInfoFactory.newUserInfo(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    restRequest.getLocale(),
                    targetUserIdentity.getUserIdentity(),
                    chaiProvider
            );
            final MacroMachine macroMachine = MacroMachine.forUser(
                    restRequest.getPwmApplication(),
                    restRequest.getLocale(),
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getUserIdentity()
            );

            final PublicUserInfoBean publicUserInfoBean = PublicUserInfoBean.fromUserInfoBean(
                    userInfo,
                    restRequest.getPwmApplication().getConfig(),
                    restRequest.getLocale(),
                    macroMachine
            );

            StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_STATUS );

            final RestResultBean restResultBean = RestResultBean.withData( publicUserInfoBean );
            LOGGER.debug( restRequest.getSessionLabel(), () -> "completed REST status request in "
                    + TimeDuration.compactFromCurrent( startTime ) + ", result=" + JsonUtil.serialize( restResultBean ) );
            return restResultBean;
        }
        catch ( final PwmException e )
        {
            return RestResultBean.fromError( e.getErrorInformation() );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }
}

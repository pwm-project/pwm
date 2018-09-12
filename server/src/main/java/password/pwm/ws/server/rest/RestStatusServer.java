/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
@RestWebServer( webService = WebServiceUsage.Status, requireAuthentication = true )
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
            LOGGER.debug( restRequest.getSessionLabel(), "completed REST status request in "
                    + TimeDuration.compactFromCurrent( startTime ) + ", result=" + JsonUtil.serialize( restResultBean ) );
            return restResultBean;
        }
        catch ( PwmException e )
        {
            return RestResultBean.fromError( e.getErrorInformation() );
        }
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMsg );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }
}

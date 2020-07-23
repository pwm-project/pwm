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

package password.pwm.http.servlet.accountinfo;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

@WebServlet(
        name = "UserInformationServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/account",
                PwmConstants.URL_PREFIX_PRIVATE + "/userinfo",
                PwmConstants.URL_PREFIX_PRIVATE + "/accountinformation.jsp"
        }
)
public class AccountInformationServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AccountInformationServlet.class );

    public enum AccountInformationAction implements AbstractPwmServlet.ProcessAction
    {
        read( HttpMethod.GET ),;

        private final HttpMethod method;

        AccountInformationAction( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return AccountInformationAction.class;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        try
        {
            final AccountInformationBean accountInformationBean = AccountInformationBean.makeUserAccountInfoBean(
                    pwmRequest,
                    pwmRequest.getPwmSession().getUserInfo(),
                    pwmRequest.getLocale()
            );
            pwmRequest.setAttribute( PwmRequestAttribute.AccountInfo, accountInformationBean );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( pwmRequest, () -> "error reading user form data: " + e.getMessage() );
        }

        pwmRequest.forwardToJsp( JspUrl.ACCOUNT_INFORMATION );
    }

    @ActionHandler( action = "read" )
    public ProcessStatus handleReadRequest( final PwmRequest pwmRequest ) throws IOException, PwmUnrecoverableException
    {
        final AccountInformationBean accountInformationBean = AccountInformationBean.makeUserAccountInfoBean(
                pwmRequest,
                pwmRequest.getPwmSession().getUserInfo(),
                pwmRequest.getLocale()
        );
        pwmRequest.outputJsonResult( RestResultBean.withData( accountInformationBean ) );
        return ProcessStatus.Halt;
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        if ( !pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.ACCOUNT_INFORMATION_ENABLED ) )
        {
            pwmRequest.respondWithError( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE ) );
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }
}

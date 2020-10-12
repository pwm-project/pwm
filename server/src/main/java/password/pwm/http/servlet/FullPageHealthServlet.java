/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

@WebServlet(
        name = "FullPageHealthServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/health",
        }
)
public class FullPageHealthServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( FullPageHealthServlet.class );

    public enum FullPageHealthAction implements AbstractPwmServlet.ProcessAction
    {
        value( HttpMethod.GET ),;

        private final HttpMethod method;

        FullPageHealthAction( final HttpMethod method )
        {
            this.method = method;
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return FullPageHealthAction.class;
    }

    @Override
    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        super.processAction( pwmRequest );
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        forwardToJSP( pwmRequest );
        pwmRequest.getPwmSession().unauthenticateUser( pwmRequest );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        if ( !pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES ) )
        {
            final Locale locale = pwmRequest.getLocale();
            final String errorMsg = "configuration setting "
                    + PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES.toMenuLocationDebug( null, locale )
                    + " must be enabled for this page to function.";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        return ProcessStatus.Continue;
    }

    private void forwardToJSP(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        pwmRequest.forwardToJsp( JspUrl.FULL_PAGE_HEALTH );
    }

    @ActionHandler( action = "value" )
    private ProcessStatus bogusValueHandler( final PwmRequest pwmRequest )
    {
        // bogus method to satisfy test case
        return ProcessStatus.Continue;
    }
}

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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.LoginServletBean;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User interaction servlet for form-based authentication.   Depending on how PWM is deployed,
 * users may or may not ever visit this servlet.   If using some type of SSO method
 * the login form will not be invoked.
 *
 * @author Jason D. Rivard
 */
@WebServlet(
        name = "LoginServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/login",
                PwmConstants.URL_PREFIX_PRIVATE + "/Login"
        }
)
public class LoginServlet extends ControlledPwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.getLogger( LoginServlet.class.getName() );

    public enum LoginServletAction implements ProcessAction
    {
        login( HttpMethod.POST ),
        restLogin( HttpMethod.POST ),
        receiveUrl( HttpMethod.GET ),;

        private final HttpMethod method;

        LoginServletAction( final HttpMethod method )
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
        return LoginServletAction.class;
    }

    private boolean passwordOnly( final PwmRequest pwmRequest )
    {
        return pwmRequest.isAuthenticated()
                && pwmRequest.getPwmSession().getLoginInfoBean().getType() == AuthenticationType.AUTH_WITHOUT_PASSWORD;

    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        final boolean passwordOnly = passwordOnly( pwmRequest );
        forwardToJSP( pwmRequest, passwordOnly );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException
    {
        if ( pwmRequest.isAuthenticated() && !passwordOnly( pwmRequest ) )
        {
            final String redirectURL = pwmRequest.getContextPath()
                    + pwmRequest.getConfig().readSettingAsString( PwmSetting.URL_INTRO );
            LOGGER.debug( pwmRequest, () -> "user is already authenticated, so redirecting user to intro url: " + redirectURL );
            pwmRequest.sendRedirect( redirectURL );
            return ProcessStatus.Halt;
        }
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "login" )
    private ProcessStatus processLogin( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final boolean passwordOnly = passwordOnly( pwmRequest );
        final Map<String, String> valueMap = pwmRequest.readParametersAsMap();
        try
        {
            handleLoginRequest( pwmRequest, valueMap, passwordOnly );
        }
        catch ( final PwmOperationalException e )
        {
            setLastError( pwmRequest, e.getErrorInformation() );
            forwardToJSP( pwmRequest, passwordOnly );
            return ProcessStatus.Halt;
        }

        // login has succeeded
        pwmRequest.sendRedirect( determinePostLoginUrl( pwmRequest ) );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "restLogin" )
    private ProcessStatus processRestLogin( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ServletException, IOException, ChaiUnavailableException
    {
        final boolean passwordOnly = passwordOnly( pwmRequest );
        final Map<String, String> valueMap = pwmRequest.readBodyAsJsonStringMap();

        if ( valueMap == null || valueMap.isEmpty() )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, "missing json request body" );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            return ProcessStatus.Halt;
        }

        try
        {
            handleLoginRequest( pwmRequest, valueMap, passwordOnly );
        }
        catch ( final PwmOperationalException e )
        {
            final ErrorInformation errorInformation = e.getErrorInformation();
            LOGGER.trace( pwmRequest, () -> "returning rest login error to client: " + errorInformation.toDebugStr() );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            return ProcessStatus.Halt;
        }

        pwmRequest.readParametersAsMap();

        // login has succeeded
        final String nextLoginUrl = determinePostLoginUrl( pwmRequest );
        final HashMap<String, String> resultMap = new HashMap<>( Collections.singletonMap( "nextURL", nextLoginUrl ) );
        final RestResultBean restResultBean = RestResultBean.withData( resultMap );
        LOGGER.debug( pwmRequest, () -> "rest login succeeded" );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "receiveUrl" )
    private ProcessStatus processReceiveUrl( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final String encryptedNextUrl = pwmRequest.readParameterAsString( PwmConstants.PARAM_POST_LOGIN_URL );
        if ( !StringUtil.isEmpty( encryptedNextUrl ) )
        {
            final String nextUrl = pwmRequest.getPwmApplication().getSecureService().decryptStringValue( encryptedNextUrl );
            if ( !StringUtil.isEmpty( nextUrl ) )
            {
                final LoginServletBean loginServletBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, LoginServletBean.class );
                LOGGER.trace( pwmRequest, () -> "received nextUrl and storing in module bean, value: " + nextUrl );
                loginServletBean.setNextUrl( nextUrl );
            }
        }

        pwmRequest.sendRedirect( PwmServletDefinition.Login );
        return ProcessStatus.Halt;
    }

    private void handleLoginRequest(
            final PwmRequest pwmRequest,
            final Map<String, String> valueMap,
            final boolean passwordOnly
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final String username = valueMap.get( PwmConstants.PARAM_USERNAME );
        final String passwordStr = valueMap.get( PwmConstants.PARAM_PASSWORD );
        final PasswordData password = passwordStr != null && passwordStr.length() > 0
                ? new PasswordData( passwordStr )
                : null;
        final String context = valueMap.get( PwmConstants.PARAM_CONTEXT );
        final String ldapProfile = valueMap.get( PwmConstants.PARAM_LDAP_PROFILE );
        final String recaptchaResponse = valueMap.get( CaptchaUtility.PARAM_RECAPTCHA_FORM_NAME );


        if ( !passwordOnly && ( username == null || username.isEmpty() ) )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, "missing username parameter" ) );
        }

        if ( password == null )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, "missing password parameter" ) );
        }

        if ( CaptchaUtility.captchaEnabledForRequest( pwmRequest ) )
        {
            if ( !CaptchaUtility.verifyReCaptcha( pwmRequest, recaptchaResponse ) )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_BAD_CAPTCHA_RESPONSE, "captcha incorrect" ) );
            }
        }

        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                pwmRequest.getPwmApplication(),
                pwmRequest,
                PwmAuthenticationSource.LOGIN_FORM
        );

        if ( passwordOnly )
        {
            final UserIdentity userIdentity = pwmRequest.getPwmSession().getUserInfo().getUserIdentity();
            sessionAuthenticator.authenticateUser( userIdentity, password );
        }
        else
        {
            sessionAuthenticator.searchAndAuthenticateUser( username, password, context, ldapProfile );
        }

        // if here then login was successful

        // recycle the session to prevent session fixation attack.
        pwmRequest.getPwmSession().getSessionStateBean().setSessionIdRecycleNeeded( true );
    }

    private void forwardToJSP(
            final PwmRequest pwmRequest,
            final boolean passwordOnly
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final JspUrl url = passwordOnly ? JspUrl.LOGIN_PW_ONLY : JspUrl.LOGIN;
        pwmRequest.forwardToJsp( url );
    }

    private static String determinePostLoginUrl( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final LoginServletBean loginServletBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, LoginServletBean.class );
        final String decryptedValue = loginServletBean.getNextUrl();

        if ( !StringUtil.isEmpty( decryptedValue ) )
        {
            final PwmURL originalPwmURL = new PwmURL( URI.create( decryptedValue ), pwmRequest.getContextPath() );
            if ( !originalPwmURL.isLoginServlet() )
            {
                loginServletBean.setNextUrl( null );
                return decryptedValue;
            }
        }
        return pwmRequest.getContextPath();
    }

    public static void redirectToLoginServlet( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        //store the original requested url
        final String originalRequestedUrl = pwmRequest.getURLwithQueryString();

        final String encryptedRedirUrl = pwmRequest.getPwmApplication().getSecureService().encryptToString( originalRequestedUrl );

        final Map<String, String> paramMap = new HashMap<>();
        paramMap.put( PwmConstants.PARAM_POST_LOGIN_URL, encryptedRedirUrl );
        paramMap.put( PwmConstants.PARAM_ACTION_REQUEST, LoginServletAction.receiveUrl.toString() );

        final String redirectUrl = PwmURL.appendAndEncodeUrlParameters(
                pwmRequest.getContextPath() + PwmServletDefinition.Login.servletUrl(),
                paramMap
        );

        LOGGER.trace( pwmRequest, () -> "redirecting to self to set nextUrl to: " + originalRequestedUrl );

        pwmRequest.sendRedirect( redirectUrl );
    }
}


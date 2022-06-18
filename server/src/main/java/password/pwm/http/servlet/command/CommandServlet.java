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

package password.pwm.http.servlet.command;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ChangePasswordProfile;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.filter.AuthenticationFilter;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;

public abstract class CommandServlet extends ControlledPwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( CommandServlet.class );

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return CommandAction.class;
    }

    public enum CommandAction implements ProcessAction
    {
        idleUpdate,
        checkResponses,

        //deprecated
        checkIfResponseConfigNeeded,
        checkExpire,
        checkProfile,

        // deprecated
        checkAttributes,
        checkAll,
        pageLeaveNotice,
        cspReport,
        next,;

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return Arrays.asList( HttpMethod.GET, HttpMethod.POST );
        }
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        // no mvc pattern in this servlet
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "cspReport" )
    public ProcessStatus processCspReport(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final String body = pwmRequest.readRequestBodyAsString();
        try
        {
            LOGGER.trace( pwmRequest, () -> "CSP Report: " + body );
        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "error processing csp report: " + e.getMessage() + ", body=" + body );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "idleUpdate" )
    public ProcessStatus processIdleUpdate(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        pwmRequest.validatePwmFormID();
        if ( !pwmRequest.getPwmResponse().isCommitted() )
        {
            pwmRequest.getPwmResponse().setHeader( HttpHeader.CacheControl, "no-cache, no-store, must-revalidate" );
            pwmRequest.getPwmResponse().setContentType( HttpContentType.plain );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "next" )
    public ProcessStatus processNext(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if ( pwmRequest.isAuthenticated() )
        {
            if ( AuthenticationFilter.forceRequiredRedirects( pwmRequest ) == ProcessStatus.Halt )
            {
                return ProcessStatus.Halt;
            }

            // log the user out if our finish action is currently set to log out.
            final ChangePasswordProfile changePasswordProfile = pwmRequest.getChangePasswordProfile();

            final boolean forceLogoutOnChange = changePasswordProfile.readSettingAsBoolean( PwmSetting.LOGOUT_AFTER_PASSWORD_CHANGE );
            if ( forceLogoutOnChange && pwmSession.getSessionStateBean().isPasswordModified() )
            {
                LOGGER.trace( pwmRequest, () -> "logging out user; password has been modified" );
                pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.Logout );
                return ProcessStatus.Halt;
            }
        }

        redirectToForwardURL( pwmRequest );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "pageLeaveNotice" )
    public ProcessStatus processPageLeaveNotice( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final String referrer = pwmRequest.getHttpServletRequest().getHeader( "Referer" );
        final Instant pageLeaveNoticeTime = Instant.now();
        pwmSession.getSessionStateBean().setPageLeaveNoticeTime( pageLeaveNoticeTime );
        LOGGER.debug( () -> "pageLeaveNotice indicated at " + pageLeaveNoticeTime.toString() + ", referer=" + referrer );
        if ( !pwmRequest.getPwmResponse().isCommitted() )
        {
            pwmRequest.getPwmResponse().setHeader( HttpHeader.CacheControl, "no-cache, no-store, must-revalidate" );
            pwmRequest.getPwmResponse().setContentType( HttpContentType.plain );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "checkAttributes" )
    public ProcessStatus processCheckAttributes(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        return processCheckProfile( pwmRequest );
    }

    @ActionHandler( action = "checkProfile" )
    public ProcessStatus processCheckProfile(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        if ( !checkIfUserAuthenticated( pwmRequest ) )
        {
            return ProcessStatus.Halt;
        }

        if ( pwmRequest.getPwmSession().getUserInfo().isRequiresUpdateProfile() )
        {
            pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.UpdateProfile );
        }
        else
        {
            redirectToForwardURL( pwmRequest );
        }

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "checkAll" )
    public ProcessStatus processCheckAll(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        if ( !checkIfUserAuthenticated( pwmRequest ) )
        {
            return ProcessStatus.Halt;
        }

        if ( AuthenticationFilter.forceRequiredRedirects( pwmRequest ) == ProcessStatus.Continue )
        {
            redirectToForwardURL( pwmRequest );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "checkIfResponseConfigNeeded" )
    public ProcessStatus processCheckIfResponseConfigNeeded(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        return processCheckResponses( pwmRequest );
    }

    @ActionHandler( action = "checkResponses" )
    public ProcessStatus processCheckResponses(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        if ( !checkIfUserAuthenticated( pwmRequest ) )
        {
            return ProcessStatus.Halt;
        }

        if ( pwmRequest.getPwmSession().getUserInfo().isRequiresResponseConfig() )
        {
            pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.SetupResponses );
        }
        else
        {
            redirectToForwardURL( pwmRequest );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "checkExpire" )
    public ProcessStatus processCheckExpire(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        if ( !checkIfUserAuthenticated( pwmRequest ) )
        {
            return ProcessStatus.Halt;
        }

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        if ( pwmSession.getUserInfo().isRequiresNewPassword() && !pwmSession.getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.skipNewPw ) )
        {
            pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.PrivateChangePassword.servletUrlName() );
        }
        else
        {
            redirectToForwardURL( pwmRequest );
        }
        return ProcessStatus.Halt;
    }

    private static void redirectToForwardURL( final PwmRequest pwmRequest )
            throws IOException
    {
        final LocalSessionStateBean sessionStateBean = pwmRequest.getPwmSession().getSessionStateBean();

        final String redirectURL = pwmRequest.getForwardUrl();
        LOGGER.trace( pwmRequest, () -> "redirecting user to forward url: " + redirectURL );

        // after redirecting we need to clear the session forward url
        if ( sessionStateBean.getForwardURL() != null )
        {
            LOGGER.trace( pwmRequest, () -> "clearing session forward url: " + sessionStateBean.getForwardURL() );
            sessionStateBean.setForwardURL( null );
        }

        pwmRequest.getPwmResponse().sendRedirect( redirectURL );
    }

    private static boolean checkIfUserAuthenticated(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        if ( !pwmRequest.isAuthenticated() )
        {
            final String action = pwmRequest.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST );
            LOGGER.info( pwmRequest, () -> "authentication required for " + action );
            pwmRequest.respondWithError( PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo() );
            return false;
        }
        return true;
    }
}


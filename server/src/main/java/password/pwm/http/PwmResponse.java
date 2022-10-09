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

package password.pwm.http;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.exception.ExceptionUtils;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.command.CommandServlet;
import password.pwm.i18n.Message;
import password.pwm.util.Validator;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class PwmResponse extends PwmHttpResponseWrapper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmResponse.class );

    private final PwmRequest pwmRequest;

    public enum Flag
    {
        AlwaysShowMessage,
        ForceLogout,
    }

    public enum RedirectType
    {
        Permanent_301( HttpServletResponse.SC_MOVED_PERMANENTLY ),
        Found_302( HttpServletResponse.SC_FOUND ),
        Other_303( 303 ),;

        private final int code;

        RedirectType( final int code )
        {
            this.code = code;
        }

        public int getCode( )
        {
            return code;
        }
    }

    public PwmResponse(
            final HttpServletResponse response,
            final PwmRequest pwmRequest,
            final AppConfig appConfig
    )
    {
        super( pwmRequest.getHttpServletRequest(), response, appConfig );
        this.pwmRequest = pwmRequest;
    }

    // its okay to disappear the exception during logging
    @SuppressFBWarnings( "DE_MIGHT_IGNORE" )
    public void forwardToJsp(
            final JspUrl jspURL
    )
            throws ServletException, IOException, PwmUnrecoverableException
    {
        incrementRequestCounterKey( pwmRequest );

        preCommitActions();

        final HttpServletRequest httpServletRequest = pwmRequest.getHttpServletRequest();
        final ServletContext servletContext = httpServletRequest.getSession().getServletContext();
        final String url = jspURL.getPath();
        try
        {
            LOGGER.trace( pwmRequest, () -> "forwarding to " + url );
        }
        catch ( final Exception e )
        {
            /* noop, server may not be up enough to do the log output */
        }
        servletContext.getRequestDispatcher( url ).forward( httpServletRequest, this.getHttpServletResponse() );
    }

    private static void incrementRequestCounterKey( final PwmRequest pwmRequest )
    {
        if ( pwmRequest.isFlag( PwmRequestFlag.NO_REQ_COUNTER ) )
        {
            return;
        }

        final int nextCounter = pwmRequest.getPwmSession().getLoginInfoBean().getReqCounter() + 1;
        pwmRequest.getPwmSession().getLoginInfoBean().setReqCounter( nextCounter );

        LOGGER.trace( pwmRequest, () -> "incremented request counter to " + nextCounter );
    }

    public void forwardToSuccessPage( final Message message, final String... field )
            throws ServletException, PwmUnrecoverableException, IOException

    {
        final String messageStr = Message.getLocalizedMessage( pwmRequest.getLocale(), message, pwmRequest.getDomainConfig(), field );
        forwardToSuccessPage( messageStr );
    }

    public void forwardToSuccessPage( final String message, final Flag... flags )
            throws ServletException, IOException

    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        this.pwmRequest.setAttribute( PwmRequestAttribute.SuccessMessage, message );

        final boolean showMessage = !pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.DISPLAY_SUCCESS_PAGES )
                && !Arrays.asList( flags ).contains( Flag.AlwaysShowMessage );

        if ( showMessage )
        {
            LOGGER.trace( pwmRequest, () -> "skipping success page due to configuration setting" );
            final String redirectUrl = pwmRequest.getBasePath()
                    + PwmServletDefinition.PublicCommand.servletUrl()
                    + "?processAction=next";
            sendRedirect( redirectUrl );
            return;
        }

        try
        {
            forwardToJsp( JspUrl.SUCCESS );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( () -> "unexpected error sending user to success page: " + e );
        }
    }

    public void respondWithError(
            final ErrorInformation errorInformation,
            final Flag... flags
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        LOGGER.error( pwmRequest.getLabel(), errorInformation );

        pwmRequest.setAttribute( PwmRequestAttribute.PwmErrorInfo, errorInformation );

        if ( EnumUtil.enumArrayContainsValue( flags, Flag.ForceLogout ) )
        {
            LOGGER.debug( pwmRequest, () -> "forcing logout due to error " + errorInformation.toDebugStr() );
            pwmRequest.getPwmSession().unAuthenticateUser( pwmRequest );
        }

        if ( getResponseFlags().contains( PwmResponseFlag.ERROR_RESPONSE_SENT ) )
        {
            LOGGER.debug( pwmRequest, () -> "response error has been previously set, disregarding new error: " + errorInformation.toDebugStr() );
            return;
        }

        if ( isCommitted() )
        {
            final String msg = "cannot respond with error '" + errorInformation.toDebugStr() + "', response is already committed";
            LOGGER.warn( pwmRequest.getLabel(), () -> ExceptionUtils.getStackTrace( new Throwable( msg ) ) );
            return;
        }

        if ( pwmRequest.isJsonRequest() )
        {
            outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
        }
        else if ( pwmRequest.isHtmlRequest() )
        {
            try
            {
                forwardToJsp( JspUrl.ERROR );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "unexpected error sending user to error page: " + e );
            }
        }
        else
        {
            final boolean showDetail = pwmRequest.getPwmDomain().determineIfDetailErrorMsgShown();
            final String errorStatusText = showDetail
                    ? errorInformation.toDebugStr()
                    : errorInformation.toUserStr( pwmRequest.getPwmSession(), pwmRequest.getDomainConfig() );
            getHttpServletResponse().sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorStatusText );
        }

        setResponseFlag( PwmResponseFlag.ERROR_RESPONSE_SENT );
    }


    public void outputJsonResult(
            final RestResultBean restResultBean
    )
            throws IOException
    {
        preCommitActions();
        final HttpServletResponse resp = this.getHttpServletResponse();
        final String outputString = restResultBean.toJson( pwmRequest.isPrettyPrintJsonParameterTrue() );
        resp.setContentType( HttpContentType.json.getHeaderValueWithEncoding() );
        resp.getWriter().print( outputString );
        resp.getWriter().close();
    }


    public void writeEncryptedCookie( final String cookieName, final Serializable cookieValue, final PwmCookiePath path )
            throws PwmUnrecoverableException
    {
        writeEncryptedCookie( cookieName, cookieValue, -1, path );
    }

    public void writeEncryptedCookie( final String cookieName, final Serializable cookieValue, final int seconds, final PwmCookiePath path )
            throws PwmUnrecoverableException
    {
        final String encryptedValue = pwmRequest.encryptObjectToString( cookieValue );
        writeCookie( cookieName, encryptedValue, seconds, path, PwmHttpResponseWrapper.Flag.BypassSanitation );
    }

    public void markAsDownload( final HttpContentType contentType, final String filename )
    {
        this.setHeader( HttpHeader.ContentDisposition, "attachment; fileName=" + filename );
        this.setHeader( HttpHeader.ContentTransferEncoding, "binary" );
        this.setHeader( HttpHeader.Expires, "0" );
        this.setContentType( contentType );
    }

    public void sendRedirect( final String url )
            throws IOException
    {
        sendRedirect( url, RedirectType.Found_302 );
    }

    public void sendRedirectToIntoPage() throws IOException
    {
        final String redirectURL = pwmRequest.getPwmDomain().getConfig().readSettingAsString( PwmSetting.URL_INTRO );
        sendRedirect( redirectURL );
    }

    public void sendRedirect( final PwmServletDefinition pwmServletDefinition )
            throws PwmUnrecoverableException, IOException
    {
        sendRedirect( pwmRequest.getBasePath() + pwmServletDefinition.servletUrl() );
    }

    public void sendRedirectToContinue( )
            throws PwmUnrecoverableException, IOException
    {
        String redirectURL = pwmRequest.getBasePath() + PwmServletDefinition.PublicCommand.servletUrl();
        redirectURL = PwmURL.appendAndEncodeUrlParameters(
                redirectURL,
                Collections.singletonMap( PwmConstants.PARAM_ACTION_REQUEST, CommandServlet.CommandAction.next.toString() )
        );
        sendRedirect( redirectURL );
    }

    public void sendRedirect( final String url, final RedirectType redirectType )
            throws IOException
    {
        Objects.requireNonNull ( url );
        preCommitActions();

        final String basePath = pwmRequest.getBasePath();
        final String effectiveUrl = url.startsWith( basePath )
                ? url
                : basePath + url;

        // http "other" redirect
        final HttpServletResponse resp = pwmRequest.getPwmResponse().getHttpServletResponse();
        resp.setStatus( redirectType.getCode() );
        resp.setHeader( HttpHeader.Location.getHttpName(), effectiveUrl );
        LOGGER.trace( pwmRequest, () -> "sending " + redirectType.getCode() + " redirect to " + effectiveUrl );
    }

    private void preCommitActions( )
    {
        if ( pwmRequest.getPwmResponse().isCommitted() )
        {
            return;
        }

        pwmRequest.getPwmDomain().getSessionStateService().saveLoginSessionState( pwmRequest );
        pwmRequest.getPwmDomain().getSessionStateService().saveSessionBeans( pwmRequest );
    }

    private final Set<PwmResponseFlag> pwmResponseFlags = EnumSet.noneOf( PwmResponseFlag.class );

    private Collection<PwmResponseFlag> getResponseFlags( )
    {
        return Collections.unmodifiableSet( pwmResponseFlags );
    }

    private void setResponseFlag( final PwmResponseFlag flag )
    {
        pwmResponseFlags.add( flag );
    }

    public void writeCookie(
            final String cookieName,
            final String cookieValue,
            final int seconds,
            final PwmCookiePath path,
            final PwmHttpResponseWrapper.Flag... flags
    )
            throws PwmUnrecoverableException
    {
        if ( this.getHttpServletResponse().isCommitted() )
        {
            LOGGER.warn( () -> "attempt to write cookie '" + cookieName + "' after response is committed" );
        }

        final AppConfig appConfig = pwmRequest.getAppConfig();

        final boolean secureFlag;
        {
            final String configValue = appConfig.readAppProperty( AppProperty.HTTP_COOKIE_DEFAULT_SECURE_FLAG );
            if ( configValue == null || "auto".equalsIgnoreCase( configValue ) )
            {
                secureFlag = pwmRequest.getHttpServletRequest().isSecure();
            }
            else
            {
                secureFlag = Boolean.parseBoolean( configValue );
            }
        }

        final boolean httpOnlyEnabled = Boolean.parseBoolean( appConfig.readAppProperty( AppProperty.HTTP_COOKIE_HTTPONLY_ENABLE ) );
        final boolean httpOnly = httpOnlyEnabled && !EnumUtil.enumArrayContainsValue( flags, PwmHttpResponseWrapper.Flag.NonHttpOnly );

        final String value;
        {
            if ( cookieValue == null )
            {
                value = null;
            }
            else
            {
                if ( EnumUtil.enumArrayContainsValue( flags, PwmHttpResponseWrapper.Flag.BypassSanitation ) )
                {
                    value = StringUtil.urlEncode( cookieValue );
                }
                else
                {
                    value = StringUtil.urlEncode(
                            Validator.sanitizeHeaderValue( appConfig, cookieValue )
                    );
                }
            }
        }

        final Cookie theCookie = new Cookie( cookieName, value );
        theCookie.setMaxAge( JavaHelper.rangeCheck( -1, Integer.MAX_VALUE, seconds ) );
        theCookie.setHttpOnly( httpOnly );
        theCookie.setSecure( secureFlag );

        theCookie.setPath( path == null
                ? PwmCookiePath.CurrentURL.toStringPath( pwmRequest )
                : path.toStringPath( pwmRequest ) );
        if ( value != null && value.length() > 2000 )
        {
            LOGGER.warn( () -> "writing large cookie to response: cookieName=" + cookieName + ", length=" + value.length() );
        }
        this.getHttpServletResponse().addCookie( theCookie );
        addSameSiteCookieAttribute();
    }

    public void removeCookie( final String cookieName, final PwmCookiePath path )
            throws PwmUnrecoverableException
    {
        writeCookie( cookieName, null, 0, path );
    }

}

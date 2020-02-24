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

package password.pwm.http;

import lombok.Value;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.ImmutableByteArray;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.PwmRequestID;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.http.servlet.command.CommandServlet;
import password.pwm.ldap.UserInfo;
import password.pwm.util.Validator;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class PwmRequest extends PwmHttpRequestWrapper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmRequest.class );

    private final PwmResponse pwmResponse;
    private final PwmURL pwmURL;
    private final PwmRequestID pwmRequestID;

    private final transient PwmApplication pwmApplication;
    private final transient PwmSession pwmSession;
    private final transient Supplier<SessionLabel> sessionLabelLazySupplier = new LazySupplier<>( this::makeSessionLabel );

    private final Set<PwmRequestFlag> flags = new HashSet<>();

    public static PwmRequest forRequest(
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws PwmUnrecoverableException
    {
        PwmRequest pwmRequest = ( PwmRequest ) request.getAttribute( PwmRequestAttribute.PwmRequest.toString() );
        if ( pwmRequest == null )
        {
            final PwmSession pwmSession = PwmSessionWrapper.readPwmSession( request );
            final PwmApplication pwmApplication = ContextManager.getPwmApplication( request );
            pwmRequest = new PwmRequest( request, response, pwmApplication, pwmSession );
            request.setAttribute( PwmRequestAttribute.PwmRequest.toString(), pwmRequest );
        }
        return pwmRequest;
    }

    private PwmRequest(
            final HttpServletRequest httpServletRequest,
            final HttpServletResponse httpServletResponse,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException
    {
        super( httpServletRequest, pwmApplication.getConfig() );
        this.pwmRequestID = PwmRequestID.next();
        this.pwmResponse = new PwmResponse( httpServletResponse, this, pwmApplication.getConfig() );
        this.pwmSession = pwmSession;
        this.pwmApplication = pwmApplication;
        this.pwmURL = new PwmURL( this.getHttpServletRequest() );
    }

    public PwmApplication getPwmApplication( )
    {
        return pwmApplication;
    }

    public PwmSession getPwmSession( )
    {
        return pwmSession;
    }

    public SessionLabel getLabel( )
    {
        return sessionLabelLazySupplier.get();
    }

    private SessionLabel makeSessionLabel( )
    {
        return getPwmSession().getLabel().toBuilder()
                .requestID( pwmRequestID.toString() )
                .build();
    }

    public PwmResponse getPwmResponse( )
    {
        return pwmResponse;
    }

    public Locale getLocale( )
    {
        if ( isFlag( PwmRequestFlag.INCLUDE_CONFIG_CSS ) )
        {
            return PwmConstants.DEFAULT_LOCALE;
        }
        if ( !getURL().isLocalizable() )
        {
            return PwmConstants.DEFAULT_LOCALE;
        }
        return pwmSession.getSessionStateBean().getLocale();
    }

    public void forwardToJsp( final JspUrl jspURL )
            throws ServletException, IOException, PwmUnrecoverableException
    {
        this.getPwmResponse().forwardToJsp( jspURL );
    }

    public void respondWithError( final ErrorInformation errorInformation )
            throws IOException, ServletException
    {
        respondWithError( errorInformation, true );
    }

    public void respondWithError(
            final ErrorInformation errorInformation,
            final boolean forceLogout
    )
            throws IOException, ServletException
    {
        if ( forceLogout )
        {
            getPwmResponse().respondWithError( errorInformation, PwmResponse.Flag.ForceLogout );
        }
        else
        {
            getPwmResponse().respondWithError( errorInformation );
        }
    }

    public void sendRedirect( final String redirectURL )
            throws PwmUnrecoverableException, IOException
    {
        getPwmResponse().sendRedirect( redirectURL );
    }

    public void sendRedirect( final PwmServletDefinition pwmServletDefinition )
            throws PwmUnrecoverableException, IOException
    {
        getPwmResponse().sendRedirect( this.getContextPath() + pwmServletDefinition.servletUrl() );
    }

    public void sendRedirectToContinue( )
            throws PwmUnrecoverableException, IOException
    {
        String redirectURL = this.getContextPath() + PwmServletDefinition.PublicCommand.servletUrl();
        redirectURL = PwmURL.appendAndEncodeUrlParameters(
                redirectURL,
                Collections.singletonMap( PwmConstants.PARAM_ACTION_REQUEST, CommandServlet.CommandAction.next.toString() )
        );
        sendRedirect( redirectURL );
    }


    public void outputJsonResult( final RestResultBean restResultBean )
            throws IOException
    {
        this.getPwmResponse().outputJsonResult( restResultBean );
    }

    public ContextManager getContextManager( )
            throws PwmUnrecoverableException
    {
        return ContextManager.getContextManager( this );
    }

    public InputStream readFileUploadStream( final String filePartName )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        try
        {
            if ( ServletFileUpload.isMultipartContent( this.getHttpServletRequest() ) )
            {

                // Create a new file upload handler
                final ServletFileUpload upload = new ServletFileUpload();

                // Parse the request
                for ( final FileItemIterator iter = upload.getItemIterator( this.getHttpServletRequest() ); iter.hasNext(); )
                {
                    final FileItemStream item = iter.next();

                    if ( filePartName.equals( item.getFieldName() ) )
                    {
                        return item.openStream();
                    }
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error reading file upload: " + e.getMessage() );
        }
        return null;
    }

    public Map<String, FileUploadItem> readFileUploads(
            final int maxFileSize,
            final int maxItems
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final Map<String, FileUploadItem> returnObj = new LinkedHashMap<>();
        try
        {
            if ( ServletFileUpload.isMultipartContent( this.getHttpServletRequest() ) )
            {
                final ServletFileUpload upload = new ServletFileUpload();
                final FileItemIterator iter = upload.getItemIterator( this.getHttpServletRequest() );
                while ( iter.hasNext() && returnObj.size() < maxItems )
                {
                    final FileItemStream item = iter.next();
                    final InputStream inputStream = item.openStream();
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final long length = IOUtils.copyLarge( inputStream, baos, 0, maxFileSize + 1 );
                    if ( length > maxFileSize )
                    {
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "upload file size limit exceeded" );
                        LOGGER.error( this, errorInformation );
                        respondWithError( errorInformation );
                        return Collections.emptyMap();
                    }
                    final byte[] outputFile = baos.toByteArray();
                    final FileUploadItem fileUploadItem = new FileUploadItem(
                            item.getName(),
                            item.getContentType(),
                            ImmutableByteArray.of( outputFile )
                    );
                    returnObj.put( item.getFieldName(), fileUploadItem );
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error reading file upload: " + e.getMessage() );
        }
        return Collections.unmodifiableMap( returnObj );
    }

    @Value
    public static class FileUploadItem
    {
        private final String name;
        private final String type;
        private final ImmutableByteArray content;
    }

    public UserIdentity getUserInfoIfLoggedIn( )
    {
        return this.getPwmSession().isAuthenticated()
                ? this.getPwmSession().getUserInfo().getUserIdentity()
                : null;
    }


    public void validatePwmFormID( )
            throws PwmUnrecoverableException
    {
        Validator.validatePwmFormID( this );
    }

    public boolean convertURLtokenCommand(
            final PwmServletDefinition pwmServletDefinition,
            final AbstractPwmServlet.ProcessAction processAction
    )
            throws IOException, PwmUnrecoverableException
    {
        final String uri = getURLwithoutQueryString();
        if ( uri == null || uri.length() < 1 )
        {
            return false;
        }
        final String servletPath = this.getHttpServletRequest().getServletPath();
        if ( !uri.contains( servletPath ) )
        {
            LOGGER.error( () -> "unexpected uri handler, uri '" + uri + "' does not contain servlet path '" + servletPath + "'" );
            return false;
        }

        String aftPath = uri.substring( uri.indexOf( servletPath ) + servletPath.length() );
        if ( aftPath.startsWith( "/" ) )
        {
            aftPath = aftPath.substring( 1 );
        }

        if ( aftPath.contains( "?" ) )
        {
            aftPath = aftPath.substring( 0, aftPath.indexOf( "?" ) );
        }

        if ( aftPath.contains( "&" ) )
        {
            aftPath = aftPath.substring( 0, aftPath.indexOf( "?" ) );
        }

        if ( aftPath.length() <= 1 )
        {
            return false;
        }

        // note this value is still urlencoded - the servlet container does not decode path values.
        final String tokenValue = aftPath;

        final StringBuilder redirectURL = new StringBuilder();
        redirectURL.append( this.getHttpServletRequest().getContextPath() );
        redirectURL.append( pwmServletDefinition.servletUrl() );
        redirectURL.append( "?" );
        redirectURL.append( PwmConstants.PARAM_ACTION_REQUEST ).append( "=" ).append( processAction.toString() );
        redirectURL.append( "&" );
        redirectURL.append( PwmConstants.PARAM_TOKEN ).append( "=" ).append( tokenValue );

        LOGGER.debug( this, () -> "detected long servlet url, redirecting user to " + redirectURL );
        sendRedirect( redirectURL.toString() );
        return true;
    }

    public void setAttribute( final PwmRequestAttribute name, final Serializable value )
    {
        this.getHttpServletRequest().setAttribute( name.toString(), value );
    }

    public Serializable getAttribute( final PwmRequestAttribute name )
    {
        return ( Serializable ) this.getHttpServletRequest().getAttribute( name.toString() );
    }

    public PwmURL getURL( )
    {
        return pwmURL;
    }

    public void debugHttpRequestToLog( final String extraText )
            throws PwmUnrecoverableException
    {
        if ( LOGGER.isEnabled( PwmLogLevel.TRACE ) )
        {
            final String debugTxt = debugHttpRequestToString( extraText, false );
            LOGGER.trace( this.getLabel(), () -> debugTxt );
        }
    }

    public boolean isAuthenticated( )
    {
        return pwmSession.isAuthenticated();
    }

    public boolean isForcedPageView( ) throws PwmUnrecoverableException
    {
        if ( !isAuthenticated() )
        {
            return false;
        }

        final PwmURL pwmURL = getURL();
        final UserInfo userInfoBean = pwmSession.getUserInfo();


        if ( pwmSession.getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.forcePwChange ) && pwmURL.isChangePasswordURL() )
        {
            return true;
        }

        if ( userInfoBean.isRequiresNewPassword() && pwmURL.isChangePasswordURL() )
        {
            return true;
        }

        if ( userInfoBean.isRequiresResponseConfig() && pwmURL.isSetupResponsesURL() )
        {
            return true;
        }

        if ( userInfoBean.isRequiresOtpConfig() && pwmURL.isSetupOtpSecretURL() )
        {
            return true;
        }

        if ( userInfoBean.isRequiresUpdateProfile() && pwmURL.isProfileUpdateURL() )
        {
            return true;
        }

        return false;
    }

    public void setFlag( final PwmRequestFlag flag, final boolean status )
    {
        if ( status )
        {
            flags.add( flag );
        }
        else
        {
            flags.remove( flag );
        }
    }

    public boolean isFlag( final PwmRequestFlag flag )
    {
        return flags.contains( flag );
    }

    public boolean hasForwardUrl( )
    {
        final LocalSessionStateBean ssBean = this.getPwmSession().getSessionStateBean();
        final String redirectURL = ssBean.getForwardURL();
        return !( ( redirectURL == null || redirectURL.isEmpty() ) && this.getConfig().isDefaultValue( PwmSetting.URL_FORWARD ) );
    }

    public String getForwardUrl( )
    {
        final LocalSessionStateBean ssBean = this.getPwmSession().getSessionStateBean();
        String redirectURL = ssBean.getForwardURL();
        if ( StringUtil.isEmpty( redirectURL ) )
        {
            redirectURL = this.getConfig().readSettingAsString( PwmSetting.URL_FORWARD );
        }

        if ( StringUtil.isEmpty( redirectURL ) )
        {
            redirectURL = this.getContextPath();
        }

        if ( StringUtil.isEmpty( redirectURL ) )
        {
            redirectURL = "/";
        }

        return redirectURL;
    }

    public String getLogoutURL(
    )
    {
        final LocalSessionStateBean ssBean = this.getPwmSession().getSessionStateBean();
        return ssBean.getLogoutURL() == null ? pwmApplication.getConfig().readSettingAsString( PwmSetting.URL_LOGOUT ) : ssBean.getLogoutURL();
    }

    public synchronized String getCspNonce( )
    {
        if ( getAttribute( PwmRequestAttribute.CspNonce ) == null )
        {
            final int nonceLength = Integer.parseInt( getConfig().readAppProperty( AppProperty.HTTP_HEADER_CSP_NONCE_BYTES ) );
            final byte[] cspNonce = pwmApplication.getSecureService().pwmRandom().newBytes( nonceLength );
            final String cspString = StringUtil.base64Encode( cspNonce );
            setAttribute( PwmRequestAttribute.CspNonce, cspString );
        }
        return ( String ) getAttribute( PwmRequestAttribute.CspNonce );
    }

    public <T extends Serializable> T readEncryptedCookie( final String cookieName, final Class<T> returnClass )
            throws PwmUnrecoverableException
    {
        final String strValue = this.readCookie( cookieName );

        if ( strValue != null && !strValue.isEmpty() )
        {
            final PwmSecurityKey pwmSecurityKey = pwmSession.getSecurityKey( this );
            return pwmApplication.getSecureService().decryptObject( strValue, pwmSecurityKey, returnClass );
        }

        return null;
    }

    public String toString( )
    {
        return this.getClass().getSimpleName() + " "
                + ( this.getLabel() == null ? "" : getLabel().toString() )
                + " " + getURLwithoutQueryString();

    }

    public void addFormInfoToRequestAttr(
            final PwmSetting formSetting,
            final boolean readOnly,
            final boolean showPasswordFields
    )
    {
        final ArrayList<FormConfiguration> formConfiguration = new ArrayList<>( this.getConfig().readSettingAsForm( formSetting ) );
        addFormInfoToRequestAttr( formConfiguration, null, readOnly, showPasswordFields );

    }

    public void addFormInfoToRequestAttr(
            final List<FormConfiguration> formConfiguration,
            final Map<FormConfiguration, String> formDataMap,
            final boolean readOnly,
            final boolean showPasswordFields
    )
    {
        final LinkedHashMap<FormConfiguration, String> formDataMapValue = formDataMap == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>( formDataMap );

        this.setAttribute( PwmRequestAttribute.FormConfiguration, new ArrayList<>( formConfiguration ) );
        this.setAttribute( PwmRequestAttribute.FormData, formDataMapValue );
        this.setAttribute( PwmRequestAttribute.FormReadOnly, readOnly );
        this.setAttribute( PwmRequestAttribute.FormShowPasswordFields, showPasswordFields );
    }

    public void invalidateSession( )
    {
        this.getPwmSession().unauthenticateUser( this );
        this.getHttpServletRequest().getSession().invalidate();
    }

    public String getURLwithQueryString( ) throws PwmUnrecoverableException
    {
        return PwmURL.appendAndEncodeUrlParameters( getURLwithoutQueryString(), readParametersAsMap() );
    }

    public boolean endUserFunctionalityAvailable( )
    {
        final PwmApplicationMode mode = pwmApplication.getApplicationMode();
        if ( mode == PwmApplicationMode.NEW )
        {
            return false;
        }
        if ( PwmConstants.TRIAL_MODE )
        {
            return true;
        }
        if ( mode == PwmApplicationMode.RUNNING )
        {
            return true;
        }
        return false;
    }

    public CommonValues commonValues()
    {
        return new CommonValues( pwmApplication, this.getLabel(), this.getLocale(), pwmRequestID );
    }

    public String getPwmRequestID()
    {
        return pwmRequestID.toString();
    }
}

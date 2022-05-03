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

import lombok.Value;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.AccountInformationProfile;
import password.pwm.config.profile.ChangePasswordProfile;
import password.pwm.config.profile.DeleteAccountProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.PeopleSearchProfile;
import password.pwm.config.profile.Profile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.SetupOtpProfile;
import password.pwm.config.profile.SetupResponsesProfile;
import password.pwm.config.profile.UpdateProfileProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.PwmRequestID;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.ldap.UserInfo;
import password.pwm.util.Validator;
import password.pwm.util.java.ImmutableByteArray;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class PwmRequest extends PwmHttpRequestWrapper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmRequest.class );

    private final PwmResponse pwmResponse;
    private final PwmURL pwmURL;
    private final PwmRequestID pwmRequestID;

    private final transient PwmApplication pwmApplication;
    private final transient Supplier<SessionLabel> sessionLabelLazySupplier = new LazySupplier<>( this::makeSessionLabel );

    private final Set<PwmRequestFlag> flags = EnumSet.noneOf( PwmRequestFlag.class );
    private final Instant requestStartTime = Instant.now();
    private final DomainID domainID;
    private final Lock cspCreationLock = new ReentrantLock();

    private static final Lock CREATE_LOCK = new ReentrantLock();

    public static PwmRequest forRequest(
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws PwmUnrecoverableException
    {
        CREATE_LOCK.lock();
        try
        {
            PwmRequest pwmRequest = ( PwmRequest ) request.getAttribute( PwmRequestAttribute.PwmRequest.toString() );
            if ( pwmRequest == null )
            {
                final PwmApplication pwmApplication = ContextManager.getPwmApplication( request );
                pwmRequest = new PwmRequest( request, response, pwmApplication );
                request.setAttribute( PwmRequestAttribute.PwmRequest.toString(), pwmRequest );
            }
            return pwmRequest;
        }
        finally
        {
            CREATE_LOCK.unlock();
        }
    }

    private PwmRequest(
            final HttpServletRequest httpServletRequest,
            final HttpServletResponse httpServletResponse,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException
    {
        super( httpServletRequest, pwmApplication.getConfig() );
        this.pwmRequestID = PwmRequestID.next();
        this.pwmResponse = new PwmResponse( httpServletResponse, this, pwmApplication.getConfig() );
        this.pwmApplication = pwmApplication;
        this.pwmURL = PwmURL.create( this.getHttpServletRequest() );
        this.domainID = PwmHttpRequestWrapper.readDomainIdFromRequest( httpServletRequest );
    }

    public PwmDomain getPwmDomain( )
    {
        return pwmApplication.domains().get( getDomainID() );
    }

    public PwmSession getPwmSession( )
    {
        return PwmSessionFactory.readPwmSession( this.getHttpServletRequest().getSession(), getPwmDomain() );
    }

    public SessionLabel getLabel( )
    {
        return sessionLabelLazySupplier.get();
    }

    private SessionLabel makeSessionLabel( )
    {
        if ( getHttpServletRequest().getSession( false ) == null )
        {
            // in case session does not exist, invoked for some non-servlet requests such as logging
            return SessionLabel.builder()
                    .domain( domainID.stringValue() )
                    .build();
        }

        // nominal case
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

        final Locale userLocale = getPwmSession().getSessionStateBean().getLocale();
        return userLocale != null ? userLocale : PwmConstants.DEFAULT_LOCALE;
    }

    public void forwardToJsp( final JspUrl jspURL )
            throws ServletException, IOException, PwmUnrecoverableException
    {
        this.getPwmResponse().forwardToJsp( jspURL );
    }

    public void respondWithError( final ErrorInformation errorInformation )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        respondWithError( errorInformation, true );
    }

    public void respondWithError(
            final ErrorInformation errorInformation,
            final boolean forceLogout
    )
            throws IOException, ServletException, PwmUnrecoverableException
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

    public Optional<InputStream> readFileUploadStream( final String filePartName )
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
                        return Optional.of( item.openStream() );
                    }
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error reading file upload: " + e.getMessage() );
        }
        return Optional.empty();
    }

    public Map<String, FileUploadItem> readFileUploads(
            final int maxFileSize,
            final int maxItems
    )
            throws PwmUnrecoverableException
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
                    final ImmutableByteArray fileContents = JavaHelper.copyToBytes( inputStream, maxFileSize + 1 );
                    if ( fileContents.size() > maxFileSize )
                    {
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "upload file size limit exceeded" );
                        LOGGER.error( this, errorInformation );
                        respondWithError( errorInformation );
                        return Collections.emptyMap();
                    }
                    final FileUploadItem fileUploadItem = new FileUploadItem(
                            item.getName(),
                            item.getContentType(),
                            fileContents
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
        final PwmSession pwmSession = getPwmSession();
        return pwmSession.isAuthenticated()
                ? pwmSession.getUserInfo().getUserIdentity()
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
            aftPath = aftPath.substring( 0, aftPath.indexOf( '?' ) );
        }

        if ( aftPath.contains( "&" ) )
        {
            aftPath = aftPath.substring( 0, aftPath.indexOf( '?' ) );
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
        redirectURL.append( '?' );
        redirectURL.append( PwmConstants.PARAM_ACTION_REQUEST ).append( '=' ).append( processAction );
        redirectURL.append( '&' );
        redirectURL.append( PwmConstants.PARAM_TOKEN ).append( '=' ).append( tokenValue );

        LOGGER.debug( this, () -> "detected long servlet url, redirecting user to " + redirectURL );
        getPwmResponse().sendRedirect( redirectURL.toString() );
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

    public void debugHttpRequestToLog( final String extraText, final Supplier<TimeDuration> timeDuration )
            throws PwmUnrecoverableException
    {
        if ( LOGGER.isEnabled( PwmLogLevel.TRACE ) )
        {
            final String moreExtraText = ( StringUtil.isEmpty( extraText ) ? "" : extraText + " " )
                    + "request=" + this.getPwmRequestID() + ", domain=" + this.getDomainID().stringValue();
            final String debugTxt = debugHttpRequestToString( moreExtraText, false );
            LOGGER.trace( this.getLabel(), () -> debugTxt, timeDuration );
        }
    }

    public boolean isAuthenticated( )
    {
        return getPwmSession().isAuthenticated();
    }

    public boolean isForcedPageView( ) throws PwmUnrecoverableException
    {
        if ( !isAuthenticated() )
        {
            return false;
        }

        final PwmURL pwmURL = getURL();
        final UserInfo userInfoBean = getPwmSession().getUserInfo();

        if ( getPwmSession().getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.forcePwChange ) && pwmURL.isChangePasswordURL() )
        {
            return true;
        }

        if ( userInfoBean.isRequiresNewPassword() && pwmURL.isChangePasswordURL() )
        {
            return true;
        }

        if ( userInfoBean.isRequiresResponseConfig() && pwmURL.matches( PwmServletDefinition.SetupResponses ) )
        {
            return true;
        }

        if ( userInfoBean.isRequiresOtpConfig() && pwmURL.matches( PwmServletDefinition.SetupOtp ) )
        {
            return true;
        }

        if ( userInfoBean.isRequiresUpdateProfile() && pwmURL.matches( PwmServletDefinition.UpdateProfile ) )
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
        return StringUtil.notEmpty( redirectURL );
    }

    public String getForwardUrl( )
    {
        final LocalSessionStateBean ssBean = this.getPwmSession().getSessionStateBean();
        String redirectURL = ssBean.getForwardURL();
        if ( StringUtil.isEmpty( redirectURL ) )
        {
            redirectURL = this.getDomainConfig().readSettingAsString( PwmSetting.URL_FORWARD );
        }

        if ( StringUtil.isEmpty( redirectURL ) )
        {
            redirectURL = this.getBasePath();
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

    public String getCspNonce( )
            throws PwmUnrecoverableException
    {
        cspCreationLock.lock();
        try
        {
            if ( getAttribute( PwmRequestAttribute.CspNonce ) == null )
            {
                final int nonceLength = Integer.parseInt( getDomainConfig().readAppProperty( AppProperty.HTTP_HEADER_CSP_NONCE_BYTES ) );
                final byte[] cspNonce = getPwmDomain().getSecureService().pwmRandom().newBytes( nonceLength );
                final String cspString = StringUtil.base64Encode( cspNonce );
                setAttribute( PwmRequestAttribute.CspNonce, cspString );
            }
            return ( String ) getAttribute( PwmRequestAttribute.CspNonce );
        }
        finally
        {
            cspCreationLock.unlock();
        }
    }

    public <T extends Serializable> Optional<T> readEncryptedCookie( final String cookieName, final Class<T> returnClass )
            throws PwmUnrecoverableException
    {
        final Optional<String> strValue = this.readCookie( cookieName );

        if ( strValue.isEmpty() )
        {
            return Optional.empty();
        }

        final PwmSecurityKey pwmSecurityKey = getPwmSession().getSecurityKey( this );
        final T t = getPwmDomain().getSecureService().decryptObject( strValue.get(), pwmSecurityKey, returnClass );
        return Optional.of( t );
    }

    @Override
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
        final ArrayList<FormConfiguration> formConfiguration = new ArrayList<>( this.getDomainConfig().readSettingAsForm( formSetting ) );
        addFormInfoToRequestAttr( formConfiguration, null, readOnly, showPasswordFields );

    }

    public void addFormInfoToRequestAttr(
            final List<FormConfiguration> formConfiguration,
            final Map<FormConfiguration, String> formDataMap,
            final boolean readOnly,
            final boolean showPasswordFields
    )
    {
        final Map<FormConfiguration, String> formDataMapValue = formDataMap == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>( formDataMap );

        this.setAttribute( PwmRequestAttribute.FormConfiguration, new ArrayList<>( formConfiguration ) );
        this.setAttribute( PwmRequestAttribute.FormData, ( Serializable ) formDataMapValue );
        this.setAttribute( PwmRequestAttribute.FormReadOnly, readOnly );
        this.setAttribute( PwmRequestAttribute.FormShowPasswordFields, showPasswordFields );
    }

    public void invalidateSession( )
            throws PwmUnrecoverableException
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

    public String getContextPath( )
    {
        return this.getHttpServletRequest().getContextPath();
    }

    public String getBasePath( )
    {
        final String rawContextPath = this.getHttpServletRequest().getContextPath();

        final AppConfig appConfig = getAppConfig();
        if ( appConfig.isMultiDomain() && appConfig.readSettingAsBoolean( PwmSetting.DOMAIN_DOMAIN_PATHS ) )
        {
            return rawContextPath + "/" + StringUtil.urlPathEncode( this.getDomainID().stringValue() );
        }

        return rawContextPath;
    }

    public PwmRequestContext getPwmRequestContext()
    {
        return new PwmRequestContext( pwmApplication, this.getDomainID(), this.getLabel(), this.getLocale(), pwmRequestID );
    }

    public String getPwmRequestID()
    {
        return pwmRequestID.toString();
    }

    public Instant getRequestStartTime()
    {
        return requestStartTime;
    }

    public DomainID getDomainID()
    {
        return domainID;
    }

    public DomainConfig getDomainConfig()
    {
        return getPwmDomain().getConfig();
    }

    public PwmApplication getPwmApplication()
    {
        return pwmApplication;
    }

    private Profile getProfile( final PwmDomain pwmDomain, final ProfileDefinition profileDefinition ) throws PwmUnrecoverableException
    {
        if ( profileDefinition.isAuthenticated() && !getPwmSession().isAuthenticated() )
        {
            throw new IllegalStateException( "can not read authenticated profile while session is unauthenticated" );
        }

        final String profileID = getPwmSession().getUserInfo().getProfileIDs().get( profileDefinition );
        if ( profileID != null )
        {
            return pwmDomain.getConfig().getProfileMap( profileDefinition ).get( profileID );
        }
        throw new PwmUnrecoverableException( PwmError.ERROR_NO_PROFILE_ASSIGNED );
    }

    public HelpdeskProfile getHelpdeskProfile() throws PwmUnrecoverableException
    {
        return ( HelpdeskProfile ) getProfile( getPwmDomain(), ProfileDefinition.Helpdesk );
    }

    public SetupOtpProfile getSetupOTPProfile() throws PwmUnrecoverableException
    {
        return ( SetupOtpProfile ) getProfile( getPwmDomain(), ProfileDefinition.SetupOTPProfile );
    }

    public SetupResponsesProfile getSetupResponsesProfile() throws PwmUnrecoverableException
    {
        return ( SetupResponsesProfile ) getProfile( getPwmDomain(), ProfileDefinition.SetupResponsesProfile );
    }

    public UpdateProfileProfile getUpdateAttributeProfile() throws PwmUnrecoverableException
    {
        return ( UpdateProfileProfile ) getProfile( getPwmDomain(), ProfileDefinition.UpdateAttributes );
    }

    public PeopleSearchProfile getPeopleSearchProfile() throws PwmUnrecoverableException
    {
        return ( PeopleSearchProfile ) getProfile( getPwmDomain(), ProfileDefinition.PeopleSearch );
    }

    public DeleteAccountProfile getSelfDeleteProfile() throws PwmUnrecoverableException
    {
        return ( DeleteAccountProfile ) getProfile( getPwmDomain(), ProfileDefinition.DeleteAccount );
    }

    public ChangePasswordProfile getChangePasswordProfile() throws PwmUnrecoverableException
    {
        return ( ChangePasswordProfile ) getProfile( getPwmDomain(), ProfileDefinition.ChangePassword );
    }

    public AccountInformationProfile getAccountInfoProfile() throws PwmUnrecoverableException
    {
        return ( AccountInformationProfile ) getProfile( getPwmDomain(), ProfileDefinition.AccountInformation );
    }

}

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

package password.pwm.http.servlet.peoplesearch;

import com.novell.ldapchai.provider.ChaiProvider;
import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.ImmutableByteArray;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.ldap.PhotoDataBean;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

public class PhotoDataReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PhotoDataReader.class );

    private final Settings settings;
    private final PwmRequest pwmRequest;
    private final UserIdentity userIdentity;

    @Value
    @Builder
    public static class Settings
    {
        private boolean enabled;
        private List<UserPermission> photoPermissions;
        private ChaiProvider chaiProvider;
    }

    public enum PhotoReaderMethod
    {
        Ldap,
        ServerHttp,
        ClientHttp,
    }

    public PhotoDataReader( final PwmRequest pwmRequest, final Settings settings, final UserIdentity userIdentity )
    {
        this.pwmRequest = pwmRequest;
        this.settings = settings;
        this.userIdentity = userIdentity;
    }

    private PhotoReaderMethod figurePhotoDataReaderMethod()
            throws PwmUnrecoverableException
    {
        final Optional<String> photoUrlOverride = getPhotoUrlOverride( userIdentity );
        if ( !photoUrlOverride.isPresent() )
        {
            return PhotoReaderMethod.Ldap;
        }

        final boolean enableInternalHttpProxy = Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.PHOTO_INTERNAL_HTTP_PROXY_ENABLE ) );
        if ( enableInternalHttpProxy )
        {
            return PhotoReaderMethod.ServerHttp;
        }

        return PhotoReaderMethod.ClientHttp;
    }

    private boolean verifyViewPhotoPermission()
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        if ( !settings.isEnabled() )
        {
            return false;
        }

        final List<UserPermission> permissions = settings.getPhotoPermissions();
        if ( JavaHelper.isEmpty( permissions ) )
        {
            return true;
        }

        final boolean hasPermission = LdapPermissionTester.testUserPermissions( pwmRequest.getPwmApplication(), pwmRequest.getLabel(), userIdentity, permissions );
        if ( !hasPermission )
        {
            LOGGER.debug( pwmRequest, () -> "user " + userIdentity + " failed photo query filter, denying photo view ("
                    + TimeDuration.compactFromCurrent( startTime ) + ")" );
        }

        return hasPermission;
    }

    public String figurePhotoURL()
            throws PwmUnrecoverableException
    {
        if ( !verifyViewPhotoPermission() )
        {
            return null;
        }

        final PhotoReaderMethod method = figurePhotoDataReaderMethod( );

        switch ( method )
        {
            case ClientHttp:
                return getPhotoUrlOverride( userIdentity ).orElse( null );

            case Ldap:
            case ServerHttp:
                String returnUrl = pwmRequest.getURLwithoutQueryString();
                returnUrl = PwmURL.appendAndEncodeUrlParameters( returnUrl, PwmConstants.PARAM_ACTION_REQUEST, PeopleSearchServlet.PeopleSearchActions.photo.name() );
                returnUrl = PwmURL.appendAndEncodeUrlParameters( returnUrl, PwmConstants.PARAM_USERKEY,  userIdentity.toObfuscatedKey( pwmRequest.getPwmApplication() ) );
                return returnUrl;

            default:
                JavaHelper.unhandledSwitchStatement( method );

        }

        return null;
    }

    public Optional<PhotoDataBean> readPhotoData( )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Instant startTime = Instant.now();

        if ( !verifyViewPhotoPermission() )
        {
            return Optional.empty();
        }

        final PhotoReaderMethod method = figurePhotoDataReaderMethod( );

        Optional<PhotoDataBean> photoDataBean = Optional.empty();
        try
        {
            switch ( method )
            {
                case Ldap:
                    photoDataBean = readPhotoDataFromLdap();
                    break;

                case ServerHttp:
                    photoDataBean = readPhotoDataFromHTTP();
                    break;

                default:
                    JavaHelper.unhandledSwitchStatement( method );
            }
        }
        finally
        {
            final Optional<PhotoDataBean> finalData = photoDataBean;
            if ( finalData.isPresent() )
            {
                LOGGER.trace( pwmRequest, () -> "user photo data received for " + userIdentity.toDisplayString()
                        + " " + finalData.get().toString()
                        + " (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
            }
            else
            {
                LOGGER.trace( pwmRequest, () -> "no user photo data received for " + userIdentity.toDisplayString()
                        + " (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
            }
        }

        return photoDataBean;
    }

    private Optional<PhotoDataBean> readPhotoDataFromLdap()
            throws PwmUnrecoverableException, PwmOperationalException
    {
        return LdapOperationsHelper.readPhotoDataFromLdap(
                pwmRequest.getConfig(),
                pwmRequest.getPwmApplication().getProxiedChaiUser( userIdentity ).getChaiProvider(),
                userIdentity
        );
    }

    private Optional<PhotoDataBean> readPhotoDataFromHTTP()
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Optional<String> overrideURL = getPhotoUrlOverride( userIdentity );
        if ( !overrideURL.isPresent() )
        {
            return Optional.empty();
        }

        try
        {
            final PwmHttpClientConfiguration configuration = PwmHttpClientConfiguration.builder()
                    .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.promiscuous )
                    .build();
            final PwmHttpClient pwmHttpClient = pwmRequest.getPwmApplication().getHttpClientService().getPwmHttpClient( configuration );
            final PwmHttpClientRequest clientRequest = PwmHttpClientRequest.builder()
                    .method( HttpMethod.GET )
                    .url( overrideURL.get() )
                    .build();
            final PwmHttpClientResponse response = pwmHttpClient.makeRequest( clientRequest, pwmRequest.getLabel() );
            if ( response != null )
            {
                final ImmutableByteArray bodyContents = response.getBinaryBody();
                if ( bodyContents != null && !bodyContents.isEmpty() )
                {
                    final String mimeType = response.getContentType().getMimeType();
                    return Optional.of( new PhotoDataBean( mimeType, bodyContents ) );
                }
            }
            return Optional.empty();
        }
        catch ( final Exception e )
        {
            final String msg = "error reading remote http photo data: " + JavaHelper.readHostileExceptionMessage( e );
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_SERVICE_UNREACHABLE, msg ) );
        }
    }

    private Optional<String> getPhotoUrlOverride( final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmRequest.getConfig() );
        final String configuredUrl = ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_PHOTO_URL_OVERRIDE );

        if ( !StringUtil.isEmpty( configuredUrl ) )
        {
            final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest.commonValues(), userIdentity );
            return Optional.of( macroMachine.expandMacros( configuredUrl ) );

        }

        return Optional.empty();
    }

    public static void servletRespondWithPhoto(
            final PwmRequest pwmRequest,
            final Callable<Optional<PhotoDataBean>> photoReader
    )
    {
        final long cacheSeconds = JavaHelper.silentParseLong( pwmRequest.getConfig().readAppProperty( AppProperty.PHOTO_CLIENT_CACHE_SECONDS ), 3600 );
        final TimeDuration maxCacheTime = TimeDuration.of( cacheSeconds, TimeDuration.Unit.SECONDS );
        pwmRequest.getPwmResponse().getHttpServletResponse().setDateHeader( HttpHeader.Expires.getHttpName(), System.currentTimeMillis() + ( maxCacheTime.asMillis() ) );
        pwmRequest.getPwmResponse().setHeader( HttpHeader.CacheControl,  "private, max-age=" + maxCacheTime.as( TimeDuration.Unit.SECONDS ) );

        try ( OutputStream outputStream = pwmRequest.getPwmResponse().getOutputStream() )
        {
            final Optional<PhotoDataBean> optionalPhotoDataBean = photoReader.call();
            if ( optionalPhotoDataBean.isPresent() )
            {
                final PhotoDataBean photoDataBean = optionalPhotoDataBean.get();
                final HttpServletResponse resp = pwmRequest.getPwmResponse().getHttpServletResponse();
                resp.setContentType( photoDataBean.getMimeType() );

                if ( photoDataBean.getContents() != null && !photoDataBean.getContents().isEmpty() )
                {
                    outputStream.write( photoDataBean.getContents().copyOf() );
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.debug( pwmRequest, () -> "error reading user photo data: " + e.getMessage() );
            if ( !pwmRequest.getPwmResponse().isCommitted() )
            {
                pwmRequest.getPwmResponse().setStatus( 500 );
            }
        }
    }
}

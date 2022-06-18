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

package password.pwm.http.servlet.configmanager;

import com.google.gson.annotations.SerializedName;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Value;
import password.pwm.AppAttribute;
import password.pwm.AppProperty;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.UserIdentity;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationFileManager;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmCookiePath;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.svc.intruder.IntruderRecordType;
import password.pwm.svc.intruder.IntruderServiceClient;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.java.PwmTimeUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@WebServlet(
        name = "ConfigManagerLogin",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/login",
        }
)
public class ConfigManagerLoginServlet extends AbstractPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigManagerLoginServlet.class );

    private static final String COOKIE_NAME = PwmConstants.COOKIE_PERSISTENT_CONFIG_LOGIN;
    private static final PwmCookiePath COOKIE_PATH = PwmCookiePath.CurrentURL;

    public enum ConfigManagerLoginAction implements ProcessAction
    {
        login( HttpMethod.POST ),;

        private final HttpMethod method;

        ConfigManagerLoginAction( final HttpMethod method )
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
    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        checkPersistentLoginCookie( pwmRequest );

        final Optional<ConfigManagerLoginAction> processAction = readProcessAction( pwmRequest );
        if ( processAction.isPresent() )
        {
            switch ( processAction.get() )
            {
                case login:
                    processLoginRequest( pwmRequest );
                    break;

                default:
                    MiscUtil.unhandledSwitchStatement( processAction.get() );

            }
            return;
        }


        final ConfigManagerBean configManagerBean = pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, ConfigManagerBean.class );
        if ( configManagerBean.isPasswordVerified() )
        {
            forwardToNextUrl( pwmRequest );
            return;

        }
        forwardToJsp( pwmRequest );
    }

    protected void processLoginRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final ConfigurationFileManager runningConfigReader = ContextManager.getContextManager( pwmRequest.getHttpServletRequest().getSession() ).getConfigReader();
        final StoredConfiguration storedConfig = runningConfigReader.getStoredConfiguration();

        final String password = pwmRequest.readParameterAsString( "password" );
        if ( StringUtil.notEmpty( password ) )
        {
            if ( StoredConfigurationUtil.verifyPassword( storedConfig, password ) )
            {
                LOGGER.trace( pwmRequest, () -> "valid configuration password accepted" );
                updateLoginHistory( pwmRequest, pwmRequest.getUserInfoIfLoggedIn(), true );
                processLoginSuccess( pwmRequest, true );
            }
            else
            {
                LOGGER.trace( pwmRequest, () -> "configuration password is not correct" );
                IntruderServiceClient.markAddressAndSession( pwmDomain, pwmRequest.getPwmSession() );
                pwmDomain.getIntruderService().mark( IntruderRecordType.USERNAME, PwmConstants.CONFIGMANAGER_INTRUDER_USERNAME, pwmRequest.getLabel() );
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_PASSWORD_ONLY_BAD );
                updateLoginHistory( pwmRequest, pwmRequest.getUserInfoIfLoggedIn(), false );
                setLastError( pwmRequest, errorInformation );
                forwardToJsp( pwmRequest );
            }
        }
    }


    @Override
    protected Optional<ConfigManagerLoginAction> readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        return JavaHelper.readEnumFromString( ConfigManagerLoginAction.class, request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
    }


    private static void forwardToJsp( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final int persistentSeconds = figureMaxLoginSeconds( pwmRequest );
        final String time = PwmTimeUtil.asLongString( TimeDuration.of( persistentSeconds, TimeDuration.Unit.SECONDS ), pwmRequest.getLocale() );

        final ConfigLoginHistory configLoginHistory = readConfigLoginHistory( pwmRequest );
        final boolean persistentLoginEnabled = persistentLoginEnabled( pwmRequest );

        pwmRequest.setAttribute( PwmRequestAttribute.ConfigEnablePersistentLogin, persistentLoginEnabled );
        pwmRequest.setAttribute( PwmRequestAttribute.ConfigLoginHistory, configLoginHistory );
        pwmRequest.setAttribute( PwmRequestAttribute.ConfigPasswordRememberTime, time );
        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_LOGIN );
    }


    private static ConfigLoginHistory readConfigLoginHistory( final PwmRequest pwmRequest )
    {
       return pwmRequest.getPwmApplication().readAppAttribute( AppAttribute.CONFIG_LOGIN_HISTORY, ConfigLoginHistory.class )
               .orElseGet( ConfigLoginHistory::new );
    }

    private static void updateLoginHistory( final PwmRequest pwmRequest, final UserIdentity userIdentity, final boolean successful )
    {
        if ( userIdentity == null )
        {
            return;
        }

        final ConfigLoginHistory configLoginHistory = readConfigLoginHistory( pwmRequest );
        final ConfigLoginEvent event = new ConfigLoginEvent(
                userIdentity.toDisplayString(),
                Instant.now(),
                pwmRequest.getPwmSession().getSessionStateBean().getSrcAddress() );

        final int maxEvents = Integer.parseInt( pwmRequest.getPwmDomain().getConfig().readAppProperty( AppProperty.CONFIG_HISTORY_MAX_ITEMS ) );
        configLoginHistory.addEvent( event, maxEvents, successful );
        pwmRequest.getPwmApplication().writeAppAttribute( AppAttribute.CONFIG_LOGIN_HISTORY, configLoginHistory );
    }

    @Value
    public static class ConfigLoginHistory implements Serializable
    {
        private List<ConfigLoginEvent> successEvents = new ArrayList<>();
        private List<ConfigLoginEvent> failedEvents = new ArrayList<>();

        void addEvent( final ConfigLoginEvent event, final int maxEvents, final boolean successful )
        {
            final List<ConfigLoginEvent> events = successful ? successEvents : failedEvents;
            events.add( event );
            if ( maxEvents > 0 )
            {
                while ( events.size() > maxEvents )
                {
                    events.remove( 0 );
                }
            }
        }

        public List<ConfigLoginEvent> successEvents( )
        {
            return Collections.unmodifiableList( successEvents );
        }

        public List<ConfigLoginEvent> failedEvents( )
        {
            return Collections.unmodifiableList( failedEvents );
        }
    }

    @Value
    public static class ConfigLoginEvent implements Serializable
    {
        private final String userIdentity;
        private final Instant date;
        private final String networkAddress;
    }

    private static ProcessStatus processLoginSuccess( final PwmRequest pwmRequest, final boolean persistentLoginEnabled )
            throws PwmUnrecoverableException, IOException
    {
        final ConfigManagerBean configManagerBean = pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, ConfigManagerBean.class );
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        configManagerBean.setPasswordVerified( true );
        IntruderServiceClient.clearAddressAndSession( pwmDomain, pwmSession );
        pwmDomain.getIntruderService().clear( IntruderRecordType.USERNAME, PwmConstants.CONFIGMANAGER_INTRUDER_USERNAME );
        pwmRequest.getPwmSession().getSessionStateBean().setSessionIdRecycleNeeded( true );
        if ( persistentLoginEnabled && "on".equals( pwmRequest.readParameterAsString( "remember" ) ) )
        {
            writePersistentLoginCookie( pwmRequest );
        }

        if ( configManagerBean.getPrePasswordEntryUrl() != null )
        {
            final String originalUrl = configManagerBean.getPrePasswordEntryUrl();
            configManagerBean.setPrePasswordEntryUrl( null );
            pwmRequest.getPwmResponse().sendRedirect( originalUrl );
            return ProcessStatus.Halt;
        }

        pwmRequest.getPwmResponse().sendRedirect( pwmRequest.getURLwithQueryString() );
        return ProcessStatus.Continue;
    }

    private static void forwardToNextUrl( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final ConfigManagerBean configManagerBean = pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, ConfigManagerBean.class );

        if ( configManagerBean.getPrePasswordEntryUrl() != null )
        {
            final String originalUrl = configManagerBean.getPrePasswordEntryUrl();
            configManagerBean.setPrePasswordEntryUrl( null );
            pwmRequest.getPwmResponse().sendRedirect( originalUrl );
            return;
        }

        pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.ConfigManager );
    }


    public static void writePersistentLoginCookie( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final int persistentSeconds = figureMaxLoginSeconds( pwmRequest );

        if ( persistentSeconds > 0 )
        {
            final StoredConfiguration storedConfig = pwmRequest.getDomainConfig().getStoredConfiguration();
            final String persistentLoginValue = makePersistentLoginPassword( pwmRequest, storedConfig );
            final PersistentLoginInfo persistentLoginInfo = new PersistentLoginInfo( Instant.now(), persistentLoginValue );
            final String cookieValue = pwmRequest.getPwmDomain().getSecureService().encryptObjectToString( persistentLoginInfo );
            pwmRequest.getPwmResponse().writeCookie(
                    COOKIE_NAME,
                    cookieValue,
                    persistentSeconds,
                    COOKIE_PATH
            );
            LOGGER.debug( pwmRequest, () -> "issued persistent config login cookie" );
        }
    }

    private static void checkPersistentLoginCookie(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        if ( !persistentLoginEnabled( pwmRequest ) )
        {
            return;
        }

        final ConfigurationFileManager runningConfigReader = ContextManager.getContextManager( pwmRequest.getHttpServletRequest().getSession() ).getConfigReader();
        final StoredConfiguration storedConfig = runningConfigReader.getStoredConfiguration();

        try
        {
            final Optional<String> cookieValue = pwmRequest.readCookie( COOKIE_NAME );
            if ( cookieValue.isPresent() )
            {
                final PersistentLoginInfo persistentLoginInfo = pwmRequest.getPwmDomain().getSecureService().decryptObject( cookieValue.get(), PersistentLoginInfo.class );
                if ( persistentLoginInfo != null && persistentLoginInfo.getIssueTimestamp() != null )
                {
                    final int maxLoginSeconds = figureMaxLoginSeconds( pwmRequest );
                    final TimeDuration cookieAge = TimeDuration.fromCurrent( persistentLoginInfo.getIssueTimestamp() );

                    if ( cookieAge.isShorterThan( TimeDuration.of( maxLoginSeconds, TimeDuration.Unit.SECONDS ) ) )
                    {
                        final String persistentLoginPassword = makePersistentLoginPassword( pwmRequest, storedConfig );
                        if ( StringUtil.nullSafeEquals( persistentLoginPassword, persistentLoginInfo.getPassword() ) )
                        {
                            final Instant expireTime = Instant.now().plus( maxLoginSeconds, ChronoUnit.SECONDS );
                            LOGGER.debug( pwmRequest, () -> "accepting persistent config login from cookie (expires at "
                                    + expireTime.toString()
                                    + ")"
                            );

                            final ConfigManagerBean configManagerBean = pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, ConfigManagerBean.class );
                            configManagerBean.setPasswordVerified( true );
                        }
                        else
                        {
                            LOGGER.debug( pwmRequest, () -> "discarding persistent login cookie with incorrect password value" );
                            pwmRequest.getPwmResponse().removeCookie( COOKIE_NAME, COOKIE_PATH );
                        }
                    }
                    else
                    {
                        LOGGER.debug( pwmRequest, () -> "removing expired (" + cookieAge.asCompactString() + ") persistent config login cookie" );
                        pwmRequest.getPwmResponse().removeCookie( COOKIE_NAME, COOKIE_PATH );
                    }
                }
                else
                {
                    LOGGER.debug( pwmRequest, () -> "discarding invalid persistent login cookie " );
                    pwmRequest.getPwmResponse().removeCookie( COOKIE_NAME, COOKIE_PATH );
                }
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "error examining persistent config login cookie: " + e.getMessage() );
        }
    }


    @Value
    private static class PersistentLoginInfo implements Serializable
    {
        @SerializedName( "i" )
        private Instant issueTimestamp;

        @SerializedName( "p" )
        private String password;
    }

    public static int figureMaxLoginSeconds( final PwmRequest pwmRequest )
    {
        return JavaHelper.silentParseInt(
                pwmRequest.getDomainConfig().readAppProperty( AppProperty.CONFIG_MAX_PERSISTENT_LOGIN_SECONDS ),
                (int) TimeDuration.HOUR.as( TimeDuration.Unit.SECONDS )
        );
    }


    private static String makePersistentLoginPassword(
            final PwmRequest pwmRequest,
            final StoredConfiguration storedConfig
    )
            throws PwmUnrecoverableException
    {
        final int hashChars = 32;

        if ( !persistentLoginEnabled( pwmRequest ) )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "persistent login not enabled" );
        }

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final String configPasswordHash = storedConfig.readConfigProperty( ConfigurationProperty.PASSWORD_HASH )
                .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "missing config password" ) );

        final String hashValue = configPasswordHash + pwmSession.getUserInfo().getUserIdentity().toDelimitedKey();

        return StringUtil.truncate( SecureEngine.hash( hashValue, PwmHashAlgorithm.SHA512 ), hashChars );
    }


    private static boolean persistentLoginEnabled(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        if ( PwmApplicationMode.RUNNING != pwmRequest.getPwmDomain().getApplicationMode() )
        {
            LOGGER.debug( pwmRequest, () -> "app not in running mode, persistent login not possible." );
            return false;
        }

        if ( pwmRequest.getAppConfig().getSecurityKey() == null )
        {
            LOGGER.debug( pwmRequest, () -> "security key not available, persistent login not possible." );
            return false;
        }

        final Optional<String> configPasswordHash = pwmRequest.getDomainConfig().getStoredConfiguration().readConfigProperty( ConfigurationProperty.PASSWORD_HASH );
        if ( configPasswordHash.isEmpty() )
        {
            LOGGER.debug( pwmRequest, () -> "config password is not present, persistent login not possible." );
            return false;
        }

        return true;
    }
}

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

package password.pwm.health;

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiErrors;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.provider.DirectoryVendor;
import com.novell.ldapchai.util.ChaiUtility;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.PwmConstants;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.password.RandomPasswordGenerator;
import password.pwm.ws.server.rest.bean.HealthData;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LDAPHealthChecker implements HealthChecker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LDAPHealthChecker.class );

    @Override
    public List<HealthRecord> doHealthCheck( final PwmDomain pwmDomain )
    {
        final DomainConfig config = pwmDomain.getConfig();
        final List<HealthRecord> returnRecords = new ArrayList<>();
        final Map<String, LdapProfile> ldapProfiles = pwmDomain.getConfig().getLdapProfiles();

        for ( final Map.Entry<String, LdapProfile> entry : ldapProfiles.entrySet() )
        {
            final String profileID = entry.getKey();
            final List<HealthRecord> profileRecords = new ArrayList<>(
                    checkBasicLdapConnectivity( pwmDomain, config, entry.getValue(), true )
            );

            if ( profileRecords.isEmpty() )
            {
                profileRecords.addAll( checkLdapServerUrls( pwmDomain, config, ldapProfiles.get( profileID ) ) );
            }

            if ( profileRecords.isEmpty() )
            {
                profileRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_OK ) );
                profileRecords.addAll( doLdapTestUserCheck( config, ldapProfiles.get( profileID ), pwmDomain ) );
            }
            returnRecords.addAll( profileRecords );
        }

        for ( final Map.Entry<String, ErrorInformation> entry : pwmDomain.getLdapConnectionService().getLastLdapFailure().entrySet() )
        {
            final ErrorInformation errorInfo = entry.getValue();
            final LdapProfile ldapProfile = pwmDomain.getConfig().getLdapProfiles().get( entry.getKey() );
            if ( errorInfo != null )
            {
                final TimeDuration errorAge = TimeDuration.fromCurrent( errorInfo.getDate() );

                final long cautionDurationMS = Long.parseLong( pwmDomain.getConfig().readAppProperty( AppProperty.HEALTH_LDAP_CAUTION_DURATION_MS ) );
                if ( errorAge.isShorterThan( cautionDurationMS ) )
                {
                    final String ageString = errorAge.asLongString();
                    final String errorDate = JavaHelper.toIsoDate( errorInfo.getDate() );
                    final String errorMsg = errorInfo.toDebugStr();
                    returnRecords.add( HealthRecord.forMessage(
                            HealthMessage.LDAP_RecentlyUnreachable,
                            ldapProfile.getDisplayName( PwmConstants.DEFAULT_LOCALE ),
                            ageString,
                            errorDate,
                            errorMsg
                    ) );
                }
            }
        }

        if ( config.getLdapProfiles() != null && !config.getLdapProfiles().isEmpty() )
        {
            final List<String> urls = config.getLdapProfiles().values().iterator().next().readSettingAsStringArray( PwmSetting.LDAP_SERVER_URLS );
            if ( urls != null && !urls.isEmpty() && !StringUtil.isEmpty( urls.iterator().next() ) )
            {
                returnRecords.addAll( checkVendorSameness( pwmDomain ) );

                returnRecords.addAll( checkUserPermissionValues( pwmDomain ) );

                returnRecords.addAll( checkLdapDNSyntaxValues( pwmDomain ) );

                returnRecords.addAll( checkNewUserPasswordTemplateSetting( pwmDomain, config ) );

     //           returnRecords.addAll( checkUserSearching( pwmApplication ) );
            }
        }

        return returnRecords;
    }

    @SuppressWarnings( "checkstyle:MethodLength" )
    public List<HealthRecord> doLdapTestUserCheck(
            final DomainConfig config,
            final LdapProfile ldapProfile,
            final PwmDomain pwmDomain
    )
    {
        String testUserDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_TEST_USER_DN );
        String proxyUserDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_PROXY_USER_DN );
        final PasswordData proxyUserPW = ldapProfile.readSettingAsPassword( PwmSetting.LDAP_PROXY_USER_PASSWORD );

        final List<HealthRecord> returnRecords = new ArrayList<>();

        if ( testUserDN == null || testUserDN.length() < 1 )
        {
            return returnRecords;
        }

        try
        {
            testUserDN = ldapProfile.readCanonicalDN( pwmDomain, testUserDN );
            proxyUserDN = ldapProfile.readCanonicalDN( pwmDomain, proxyUserDN );
        }
        catch ( final PwmUnrecoverableException e )
        {
            final String msgString = e.getMessage();
            LOGGER.trace( SessionLabel.HEALTH_SESSION_LABEL, () -> "unexpected error while testing test user (during object creation): message="
                    + msgString + " debug info: " + JavaHelper.readHostileExceptionMessage( e ) );
            returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserUnexpected,
                    PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                    msgString
            ) );
            return returnRecords;
        }

        if ( proxyUserDN.equalsIgnoreCase( testUserDN ) )
        {
            returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_ProxyTestSameUser,
                    PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                    PwmSetting.LDAP_PROXY_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
            ) );
            return returnRecords;
        }

        ChaiUser theUser = null;
        ChaiProvider chaiProvider = null;

        try
        {
            try
            {

                chaiProvider = LdapOperationsHelper.createChaiProvider(
                        pwmDomain,
                        SessionLabel.HEALTH_SESSION_LABEL,
                        ldapProfile,
                        config,
                        proxyUserDN,
                        proxyUserPW
                );

                theUser = chaiProvider.getEntryFactory().newChaiUser( testUserDN );

            }
            catch ( final ChaiUnavailableException e )
            {
                returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserUnavailable,
                        PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                        e.getMessage()
                ) );
                return returnRecords;
            }
            catch ( final Throwable e )
            {
                final String msgString = e.getMessage();
                LOGGER.trace(
                        SessionLabel.HEALTH_SESSION_LABEL,
                        () -> "unexpected error while testing test user (during object creation): message="
                                + msgString + " debug info: " + JavaHelper.readHostileExceptionMessage( e )
                );
                returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserUnexpected,
                        PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                        msgString
                ) );
                return returnRecords;
            }

            try
            {
                theUser.readObjectClass();
            }
            catch ( final ChaiException e )
            {
                returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserError,
                        PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                        e.getMessage()
                ) );
                return returnRecords;
            }

            LOGGER.trace(
                    SessionLabel.HEALTH_SESSION_LABEL,
                    () -> "beginning process to check ldap test user password read/write operations for profile "
                            + ldapProfile.getIdentifier()
            );
            try
            {
                final boolean readPwdEnabled = pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.EDIRECTORY_READ_USER_PWD )
                        && theUser.getChaiProvider().getDirectoryVendor() == DirectoryVendor.EDIRECTORY;

                if ( readPwdEnabled )
                {
                    try
                    {
                        theUser.readPassword();
                    }
                    catch ( final Exception e )
                    {
                        LOGGER.debug( SessionLabel.HEALTH_SESSION_LABEL, () -> "error reading user password from directory " + e.getMessage() );
                        returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserReadPwError,
                                PwmSetting.EDIRECTORY_READ_USER_PWD.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ),
                                PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                                e.getMessage()
                        ) );
                        return returnRecords;
                    }
                }
                else
                {
                    final Locale locale = PwmConstants.DEFAULT_LOCALE;
                    final UserIdentity userIdentity = UserIdentity.createUserIdentity( testUserDN, ldapProfile.getIdentifier() );

                    final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                            pwmDomain, null, userIdentity, theUser, locale );

                    boolean doPasswordChange = true;
                    final int minLifetimeSeconds = passwordPolicy.getRuleHelper().readIntValue( PwmPasswordRule.MinimumLifetime );
                    if ( minLifetimeSeconds > 0 )
                    {
                        final Instant pwdLastModified = PasswordUtility.determinePwdLastModified(
                                pwmDomain,
                                SessionLabel.HEALTH_SESSION_LABEL,
                                userIdentity
                        );


                        final PasswordStatus passwordStatus;
                        {
                            final UserInfo userInfo = UserInfoFactory.newUserInfo(
                                    pwmDomain,
                                    SessionLabel.HEALTH_SESSION_LABEL,
                                    locale,
                                    userIdentity,
                                    chaiProvider
                            );
                            passwordStatus = userInfo.getPasswordStatus();
                        }

                        {
                            final boolean withinMinLifetime = PasswordUtility.isPasswordWithinMinimumLifetimeImpl(
                                    theUser,
                                    SessionLabel.HEALTH_SESSION_LABEL,
                                    passwordPolicy,
                                    pwdLastModified,
                                    passwordStatus
                            );
                            if ( withinMinLifetime )
                            {
                                LOGGER.trace( SessionLabel.HEALTH_SESSION_LABEL, () -> "skipping test user password set due to password being within minimum lifetime" );
                                doPasswordChange = false;
                            }
                        }
                    }
                    if ( doPasswordChange )
                    {
                        final PasswordData newPassword = RandomPasswordGenerator.createRandomPassword( null, passwordPolicy, pwmDomain );
                        try
                        {
                            theUser.setPassword( newPassword.getStringValue() );
                            LOGGER.debug( SessionLabel.HEALTH_SESSION_LABEL, () -> "set random password on test user " + userIdentity.toDisplayString() );
                        }
                        catch ( final ChaiException e )
                        {
                            returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserWritePwError,
                                    PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                                    e.getMessage()
                            ) );
                            return returnRecords;
                        }

                    }
                }
            }
            catch ( final Exception e )
            {
                final String msg = "error setting test user password: " + JavaHelper.readHostileExceptionMessage( e );
                LOGGER.error( SessionLabel.HEALTH_SESSION_LABEL, () -> msg, e );
                returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserUnexpected,
                        PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                        msg
                ) );
                return returnRecords;
            }

            try
            {
                final UserIdentity userIdentity = UserIdentity.createUserIdentity( theUser.getEntryDN(), ldapProfile.getIdentifier() );
                final UserInfo userInfo = UserInfoFactory.newUserInfo(
                        pwmDomain,
                        SessionLabel.HEALTH_SESSION_LABEL,
                        PwmConstants.DEFAULT_LOCALE,
                        userIdentity,
                        chaiProvider
                );
                userInfo.getPasswordStatus();
                userInfo.getAccountExpirationTime();
                userInfo.getResponseInfoBean();
                userInfo.getPasswordPolicy();
                userInfo.getChallengeProfile();
                userInfo.getProfileIDs();
                userInfo.getOtpUserRecord();
                userInfo.getUserGuid();
                userInfo.getUsername();
                userInfo.getUserEmailAddress();
                userInfo.getUserSmsNumber();
            }
            catch ( final PwmUnrecoverableException e )
            {
                returnRecords.add( HealthRecord.forMessage(
                        HealthMessage.LDAP_TestUserError,
                        PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                        "unable to read test user data: " + e.getMessage() ) );
                return returnRecords;
            }

        }
        finally
        {
            if ( chaiProvider != null )
            {
                try
                {
                    chaiProvider.close();
                }
                catch ( final Exception e )
                {
                    // ignore
                }
            }
        }

        returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserOK, ldapProfile.getDisplayName( PwmConstants.DEFAULT_LOCALE ) ) );
        return returnRecords;
    }


    public List<HealthRecord> checkLdapServerUrls(
            final PwmDomain pwmDomain,
            final DomainConfig config,
            final LdapProfile ldapProfile
    )
    {
        final List<HealthRecord> returnRecords = new ArrayList<>();
        final List<String> serverURLs = ldapProfile.readSettingAsStringArray( PwmSetting.LDAP_SERVER_URLS );
        for ( final String loopURL : serverURLs )
        {
            final String proxyDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_PROXY_USER_DN );
            ChaiProvider chaiProvider = null;
            try
            {
                chaiProvider = LdapOperationsHelper.createChaiProvider(
                        pwmDomain,
                        SessionLabel.HEALTH_SESSION_LABEL,
                        config,
                        ldapProfile,
                        Collections.singletonList( loopURL ),
                        proxyDN,
                        ldapProfile.readSettingAsPassword( PwmSetting.LDAP_PROXY_USER_PASSWORD )
                );
                final ChaiUser proxyUser = chaiProvider.getEntryFactory().newChaiUser( proxyDN );
                proxyUser.exists();
            }
            catch ( final Exception e )
            {
                final String errorString = "error connecting to ldap server '" + loopURL + "': " + e.getMessage();
                returnRecords.add( HealthRecord.forMessage(
                        HealthMessage.LDAP_No_Connection,
                        loopURL,
                        errorString ) );
            }
            finally
            {
                if ( chaiProvider != null )
                {
                    try
                    {
                        chaiProvider.close();
                    }
                    catch ( final Exception e )
                    {
                        /* ignore */
                    }
                }
            }
        }
        return returnRecords;
    }

    public List<HealthRecord> checkBasicLdapConnectivity(
            final PwmDomain pwmDomain,
            final DomainConfig config,
            final LdapProfile ldapProfile,
            final boolean testContextlessRoot
    )
    {

        final List<HealthRecord> returnRecords = new ArrayList<>();
        ChaiProvider chaiProvider = null;
        try
        {
            final DirectoryVendor directoryVendor;
            try
            {
                final String proxyDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_PROXY_USER_DN );
                final PasswordData proxyPW = ldapProfile.readSettingAsPassword( PwmSetting.LDAP_PROXY_USER_PASSWORD );
                if ( proxyDN == null || proxyDN.length() < 1 )
                {
                    final String menuLocationStr = PwmSetting.LDAP_PROXY_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE );
                    return Collections.singletonList( HealthRecord.forMessage(
                            HealthMessage.LDAP_No_Connection,
                             ldapProfile.getIdentifier(),
                             "Missing Proxy User DN: " + menuLocationStr ) );
                }
                if ( proxyPW == null )
                {
                    final String menuLocationStr = PwmSetting.LDAP_PROXY_USER_PASSWORD.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE );
                    return Collections.singletonList( HealthRecord.forMessage(
                            HealthMessage.LDAP_No_Connection,
                            ldapProfile.getIdentifier(),
                            "Missing Proxy User Password: " + menuLocationStr ) );
                }
                chaiProvider = LdapOperationsHelper.createChaiProvider( pwmDomain, SessionLabel.HEALTH_SESSION_LABEL, ldapProfile, config, proxyDN, proxyPW );
                final ChaiUser adminEntry = chaiProvider.getEntryFactory().newChaiUser( proxyDN );
                adminEntry.exists();
                directoryVendor = chaiProvider.getDirectoryVendor();

                if ( adminEntry.isPasswordExpired() )
                {
                    final Instant passwordExpireDate = adminEntry.readPasswordExpirationDate();
                    final TimeDuration maxPwExpireTime = TimeDuration.of(
                            Integer.parseInt( config.readAppProperty( AppProperty.HEALTH_LDAP_PROXY_WARN_PW_EXPIRE_SECONDS ) ),
                            TimeDuration.Unit.SECONDS );
                    final TimeDuration expirationDuration = TimeDuration.fromCurrent( passwordExpireDate  );
                    if ( maxPwExpireTime.isLongerThan( expirationDuration ) )
                    {
                        return Collections.singletonList( HealthRecord.forMessage(
                                HealthMessage.LDAP_ProxyUserPwExpired,
                                adminEntry.getEntryDN(),
                                expirationDuration.asLongString( PwmConstants.DEFAULT_LOCALE )
                        ) );
                    }
                }

            }
            catch ( final ChaiException e )
            {
                final ChaiError chaiError = ChaiErrors.getErrorForMessage( e.getMessage() );
                final PwmError pwmError = PwmError.forChaiError( chaiError );
                final StringBuilder errorString = new StringBuilder();
                final String profileName = ldapProfile.getIdentifier();
                errorString.append( "error connecting to ldap directory (" ).append( profileName ).append( "), error: " ).append( e.getMessage() );
                if ( chaiError != null && chaiError != ChaiError.UNKNOWN )
                {
                    errorString.append( " (" );
                    errorString.append( chaiError.toString() );
                    if ( pwmError != null && pwmError != PwmError.ERROR_INTERNAL )
                    {
                        errorString.append( " - " );
                        errorString.append( pwmError.getLocalizedMessage( PwmConstants.DEFAULT_LOCALE, pwmDomain.getConfig() ) );
                    }
                    errorString.append( ")" );
                }
                returnRecords.add( HealthRecord.forMessage(
                        HealthMessage.LDAP_No_Connection,
                        ldapProfile.getIdentifier(),
                        errorString.toString() ) );

                pwmDomain.getLdapConnectionService().setLastLdapFailure( ldapProfile,
                        new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, errorString.toString() ) );
                return returnRecords;
            }
            catch ( final Exception e )
            {
                final HealthRecord record = HealthRecord.forMessage( HealthMessage.LDAP_No_Connection, e.getMessage() );
                returnRecords.add( record );
                pwmDomain.getLdapConnectionService().setLastLdapFailure( ldapProfile,
                        new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, record.getDetail( PwmConstants.DEFAULT_LOCALE, pwmDomain.getConfig() ) ) );
                return returnRecords;
            }

            if ( directoryVendor != null && directoryVendor == DirectoryVendor.ACTIVE_DIRECTORY )
            {
                returnRecords.addAll( checkAd( pwmDomain, config, ldapProfile ) );
            }

            if ( testContextlessRoot )
            {
                for ( final String loopContext : ldapProfile.readSettingAsStringArray( PwmSetting.LDAP_CONTEXTLESS_ROOT ) )
                {
                    try
                    {
                        final ChaiEntry contextEntry = chaiProvider.getEntryFactory().newChaiEntry( loopContext );
                        final Set<String> objectClasses = contextEntry.readObjectClass();

                        if ( objectClasses == null || objectClasses.isEmpty() )
                        {
                            final String errorString = "ldap context setting '"
                                    + PwmSetting.LDAP_CONTEXTLESS_ROOT.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
                                    + "' value '" + loopContext + "' is not valid";
                            returnRecords.add( HealthRecord.forMessage(
                                    HealthMessage.LDAP_No_Connection,
                                    ldapProfile.getIdentifier(),
                                    errorString ) );
                        }
                    }
                    catch ( final Exception e )
                    {
                        final String errorString = "ldap context setting '"
                                + PwmSetting.LDAP_CONTEXTLESS_ROOT.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
                                + "' value '" + loopContext + "' is not valid: " + e.getMessage();
                        returnRecords.add( HealthRecord.forMessage(
                                HealthMessage.LDAP_No_Connection,
                                ldapProfile.getIdentifier(),
                                errorString ) );
                    }
                }
            }
        }
        finally
        {
            if ( chaiProvider != null )
            {
                try
                {
                    chaiProvider.close();
                }
                catch ( final Exception e )
                {
                    /* ignore */
                }
            }
        }

        return returnRecords;
    }

    private static List<HealthRecord> checkAd( final PwmDomain pwmDomain, final DomainConfig config, final LdapProfile ldapProfile )
    {
        final List<HealthRecord> returnList = new ArrayList<>();
        final List<String> serverURLs = ldapProfile.readSettingAsStringArray( PwmSetting.LDAP_SERVER_URLS );
        for ( final String loopURL : serverURLs )
        {
            try
            {
                if ( !urlUsingHostname( loopURL ) )
                {
                    returnList.add( HealthRecord.forMessage(
                            HealthMessage.LDAP_AD_StaticIP,
                            loopURL
                    ) );
                }

                final URI uri = URI.create( loopURL );
                final String scheme = uri.getScheme();
                if ( "ldap".equalsIgnoreCase( scheme ) )
                {
                    returnList.add( HealthRecord.forMessage(
                            HealthMessage.LDAP_AD_Unsecure,
                            loopURL
                    ) );
                }
            }
            catch ( final MalformedURLException | UnknownHostException e )
            {
                returnList.add( HealthRecord.forMessage(
                        HealthMessage.Config_ParseError,
                        e.getMessage(),
                        PwmSetting.LDAP_SERVER_URLS.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                        loopURL
                ) );
            }
        }

        returnList.addAll( checkAdPasswordPolicyApi( pwmDomain ) );

        return returnList;
    }

    private static boolean urlUsingHostname( final String inputURL ) throws MalformedURLException, UnknownHostException
    {
        final URI uri = URI.create( inputURL );
        final String host = uri.getHost();
        final InetAddress inetAddress = InetAddress.getByName( host );
        if ( inetAddress != null && inetAddress.getHostName() != null && inetAddress.getHostName().equalsIgnoreCase( host ) )
        {
            return true;
        }
        return false;
    }

    private List<HealthRecord> checkVendorSameness( final PwmDomain pwmDomain )
    {
        final Map<HealthMonitor.HealthMonitorFlag, Serializable> healthProperties = pwmDomain.getHealthMonitor().getHealthProperties();
        if ( healthProperties.containsKey( HealthMonitor.HealthMonitorFlag.LdapVendorSameCheck ) )
        {
            return ( List<HealthRecord> ) healthProperties.get( HealthMonitor.HealthMonitorFlag.LdapVendorSameCheck );
        }

        LOGGER.trace( SessionLabel.HEALTH_SESSION_LABEL, () -> "beginning check for replica vendor sameness" );
        boolean errorReachingServer = false;
        final Map<String, DirectoryVendor> replicaVendorMap = new HashMap<>();

        try
        {
            for ( final LdapProfile ldapProfile : pwmDomain.getConfig().getLdapProfiles().values() )
            {
                final ChaiConfiguration profileChaiConfiguration = LdapOperationsHelper.createChaiConfiguration(
                        pwmDomain.getConfig(),
                        ldapProfile
                );
                final Collection<ChaiConfiguration> replicaConfigs = ChaiUtility.splitConfigurationPerReplica( profileChaiConfiguration, Collections.emptyMap() );
                for ( final ChaiConfiguration chaiConfiguration : replicaConfigs )
                {
                    final ChaiProvider loopProvider = pwmDomain.getLdapConnectionService().getChaiProviderFactory().newProvider( chaiConfiguration );
                    replicaVendorMap.put( chaiConfiguration.getSetting( ChaiSetting.BIND_URLS ), loopProvider.getDirectoryVendor() );
                }
            }
        }
        catch ( final Exception e )
        {
            errorReachingServer = true;
            LOGGER.error( SessionLabel.HEALTH_SESSION_LABEL, () -> "error during replica vendor sameness check: " + e.getMessage() );
        }

        final ArrayList<HealthRecord> healthRecords = new ArrayList<>();
        final Set<DirectoryVendor> discoveredVendors = JavaHelper.copiedEnumSet( replicaVendorMap.values(), DirectoryVendor.class );

        if ( discoveredVendors.size() >= 2 )
        {
            final StringBuilder vendorMsg = new StringBuilder();
            for ( final Iterator<Map.Entry<String, DirectoryVendor>> iterator = replicaVendorMap.entrySet().iterator(); iterator.hasNext(); )
            {
                final Map.Entry<String, DirectoryVendor> entry = iterator.next();
                final String key = entry.getKey();
                vendorMsg.append( key ).append( "=" ).append( entry.getValue().toString() );
                if ( iterator.hasNext() )
                {
                    vendorMsg.append( ", " );
                }
            }
            healthRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_VendorsNotSame, vendorMsg.toString() ) );
            // cache the error
            healthProperties.put( HealthMonitor.HealthMonitorFlag.LdapVendorSameCheck, healthRecords );

            LOGGER.warn( SessionLabel.HEALTH_SESSION_LABEL, () -> "multiple ldap vendors found: " + vendorMsg.toString() );
        }
        else if ( discoveredVendors.size() == 1 )
        {
            if ( !errorReachingServer )
            {
                // cache the no errors
                healthProperties.put( HealthMonitor.HealthMonitorFlag.LdapVendorSameCheck, healthRecords );
            }
        }

        return healthRecords;
    }

    private static List<HealthRecord> checkAdPasswordPolicyApi( final PwmDomain pwmDomain )
    {


        final boolean passwordPolicyApiEnabled = pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.AD_ENFORCE_PW_HISTORY_ON_SET );
        if ( !passwordPolicyApiEnabled )
        {
            return Collections.emptyList();
        }

        if ( pwmDomain.getHealthMonitor() != null )
        {
            final Map<HealthMonitor.HealthMonitorFlag, Serializable> healthProperties = pwmDomain.getHealthMonitor().getHealthProperties();
            if ( healthProperties.containsKey( HealthMonitor.HealthMonitorFlag.AdPasswordPolicyApiCheck ) )
            {
                final List<HealthRecord> healthRecords = ( List<HealthRecord> ) healthProperties.get( HealthMonitor.HealthMonitorFlag.AdPasswordPolicyApiCheck );
                return healthRecords;
            }
        }

        LOGGER.trace( SessionLabel.HEALTH_SESSION_LABEL, () -> "beginning check for ad api password policy (asn "
                + PwmConstants.LDAP_AD_PASSWORD_POLICY_CONTROL_ASN + ") support" );
        boolean errorReachingServer = false;
        final ArrayList<HealthRecord> healthRecords = new ArrayList<>();

        try
        {
            for ( final LdapProfile ldapProfile : pwmDomain.getConfig().getLdapProfiles().values() )
            {
                final ChaiConfiguration profileChaiConfiguration = LdapOperationsHelper.createChaiConfiguration(
                        pwmDomain.getConfig(),
                        ldapProfile
                );
                final Collection<ChaiConfiguration> replicaConfigs = ChaiUtility.splitConfigurationPerReplica(
                        profileChaiConfiguration,
                        Collections.emptyMap()
                );

                for ( final ChaiConfiguration chaiConfiguration : replicaConfigs )
                {
                    final ChaiProvider loopProvider = pwmDomain.getLdapConnectionService().getChaiProviderFactory().newProvider( chaiConfiguration );
                    final ChaiEntry rootDSE = ChaiUtility.getRootDSE( loopProvider );
                    final Set<String> controls = rootDSE.readMultiStringAttribute( "supportedControl" );
                    final boolean asnSupported = controls.contains( PwmConstants.LDAP_AD_PASSWORD_POLICY_CONTROL_ASN );
                    if ( !asnSupported )
                    {
                        final String url = chaiConfiguration.getSetting( ChaiSetting.BIND_URLS );
                        final HealthRecord record = HealthRecord.forMessage(
                                HealthMessage.LDAP_Ad_History_Asn_Missing,
                                PwmSetting.AD_ENFORCE_PW_HISTORY_ON_SET.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ),
                                url
                        );
                        healthRecords.add( record );
                        LOGGER.warn( () -> record.toDebugString( PwmConstants.DEFAULT_LOCALE, pwmDomain.getConfig() ) );
                    }
                }
            }
        }
        catch ( final Exception e )
        {
            errorReachingServer = true;
            LOGGER.error( SessionLabel.HEALTH_SESSION_LABEL,
                    () ->  "error during ad api password policy (asn " + PwmConstants.LDAP_AD_PASSWORD_POLICY_CONTROL_ASN + ") check: " + e.getMessage() );
        }

        if ( !errorReachingServer && pwmDomain.getHealthMonitor() != null )
        {
            final Map<HealthMonitor.HealthMonitorFlag, Serializable> healthProperties = pwmDomain.getHealthMonitor().getHealthProperties();
            healthProperties.put( HealthMonitor.HealthMonitorFlag.AdPasswordPolicyApiCheck, healthRecords );
        }

        return healthRecords;
    }

    private static List<HealthRecord> checkUserPermissionValues( final PwmDomain pwmDomain )
    {
        final List<HealthRecord> returnList = new ArrayList<>();
        final DomainConfig config = pwmDomain.getConfig();
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            if ( !pwmSetting.isHidden() && pwmSetting.getSyntax() == PwmSettingSyntax.USER_PERMISSION )
            {
                if ( !pwmSetting.getCategory().hasProfiles() )
                {
                    final List<UserPermission> userPermissions = config.readSettingAsUserPermission( pwmSetting );
                    for ( final UserPermission userPermission : userPermissions )
                    {
                        try
                        {
                            returnList.addAll( checkUserPermission( pwmDomain, userPermission, pwmSetting ) );
                        }
                        catch ( final PwmUnrecoverableException e )
                        {
                            LOGGER.error( () -> "error checking configured permission settings:" + e.getMessage() );
                        }
                    }
                }
            }
        }
        return returnList;
    }

    private static List<HealthRecord> checkLdapDNSyntaxValues( final PwmDomain pwmDomain )
    {
        final List<HealthRecord> returnList = new ArrayList<>();
        final DomainConfig config = pwmDomain.getConfig();

        try
        {
            for ( final PwmSetting pwmSetting : PwmSetting.values() )
            {
                if ( !pwmSetting.isHidden()
                        && pwmSetting.getCategory() == PwmSettingCategory.LDAP_PROFILE
                        && pwmSetting.getFlags().contains( PwmSettingFlag.ldapDNsyntax )
                )
                {
                    for ( final String profile : config.getLdapProfiles().keySet() )
                    {
                        if ( pwmSetting.getSyntax() == PwmSettingSyntax.STRING )
                        {
                            final String value = config.getLdapProfiles().get( profile ).readSettingAsString( pwmSetting );
                            if ( value != null && !value.isEmpty() )
                            {
                                final Optional<String> errorMsg = validateDN( pwmDomain, value, profile );
                                errorMsg.ifPresent( s -> returnList.add( HealthRecord.forMessage(
                                        HealthMessage.Config_DNValueValidity,
                                        pwmSetting.toMenuLocationDebug( profile, PwmConstants.DEFAULT_LOCALE ), s )
                                ) );
                            }
                        }
                        else if ( pwmSetting.getSyntax() == PwmSettingSyntax.STRING_ARRAY )
                        {
                            final List<String> values = config.getLdapProfiles().get( profile ).readSettingAsStringArray( pwmSetting );
                            if ( values != null )
                            {
                                for ( final String value : values )
                                {
                                    final Optional<String> errorMsg = validateDN( pwmDomain, value, profile );
                                    errorMsg.ifPresent( s -> returnList.add( HealthRecord.forMessage(
                                            HealthMessage.Config_DNValueValidity,
                                            pwmSetting.toMenuLocationDebug( profile, PwmConstants.DEFAULT_LOCALE ), s )
                                    ) );
                                }
                            }
                        }
                    }
                }
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.warn( () -> "error while checking DN ldap syntax values: " + e.getMessage() );
        }

        return returnList;
    }

    private static List<HealthRecord> checkNewUserPasswordTemplateSetting(
            final PwmDomain pwmDomain,
            final DomainConfig domainConfig
    )
    {
        final Locale locale = PwmConstants.DEFAULT_LOCALE;
        if ( !domainConfig.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
        {
            return Collections.emptyList();
        }

        for ( final NewUserProfile newUserProfile : domainConfig.getNewUserProfiles().values() )
        {
            final String policyUserStr = newUserProfile.readSettingAsString( PwmSetting.NEWUSER_PASSWORD_POLICY_USER );

            if ( StringUtil.isEmpty( policyUserStr ) )
            {
                return Collections.singletonList(
                        HealthRecord.forMessage(
                                HealthMessage.NewUser_PwTemplateBad,
                                PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug( newUserProfile.getIdentifier(), locale ),
                                "Value missing"
                        )
                );
            }

            try
            {
                final LdapProfile ldapProfile = newUserProfile.getLdapProfile();
                if ( NewUserProfile.TEST_USER_CONFIG_VALUE.equals( policyUserStr ) )
                {
                    final UserIdentity testUser = ldapProfile.getTestUser( pwmDomain );
                    if ( testUser != null )
                    {
                        return Collections.emptyList();
                    }
                }

                final UserIdentity newUserTemplateIdentity = UserIdentity.createUserIdentity( policyUserStr, ldapProfile.getIdentifier() );

                final ChaiUser chaiUser = pwmDomain.getProxiedChaiUser( newUserTemplateIdentity );

                try
                {
                    if ( !chaiUser.exists() )
                    {
                        return Collections.singletonList(
                                HealthRecord.forMessage(
                                        HealthMessage.NewUser_PwTemplateBad,
                                        PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug( newUserProfile.getIdentifier(), locale ),
                                        "userDN value is not valid"
                                )
                        );
                    }
                }
                catch ( final ChaiUnavailableException e )
                {
                    throw PwmUnrecoverableException.fromChaiException( e );
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error checking new user password policy user settings:" + e.getMessage() );
            }
        }

        return Collections.emptyList();
    }

    private static List<HealthRecord> checkUserSearching(
            final PwmDomain pwmDomain
    )
    {
        final TimeDuration warnDuration = TimeDuration.of(
                JavaHelper.silentParseLong( pwmDomain.getConfig().readAppProperty( AppProperty.HEALTH_LDAP_USER_SEARCH_WARN_MS ), 10_1000 ),
                TimeDuration.Unit.MILLISECONDS );

        final Instant startTime = Instant.now();


        try
        {
            final String healthUsername = MacroRequest.forStatic().expandMacros( pwmDomain.getConfig().readAppProperty( AppProperty.HEALTH_LDAP_USER_SEARCH_TERM ) );

            final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                    .enableValueEscaping( false )
                    .searchTimeout( warnDuration )
                    .username( healthUsername )
                    .build();

            pwmDomain.getUserSearchEngine().performMultiUserSearch( searchConfiguration, 1, Collections.singletonList( "cn" ), SessionLabel.HEALTH_SESSION_LABEL );
        }
        catch ( final Exception e )
        {
            return Collections.singletonList(
                    HealthRecord.forMessage( HealthMessage.LDAP_SearchFailure,
                            e.getMessage()
                    ) );
        }

        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );

        if ( timeDuration.isLongerThan( warnDuration ) )
        {
            return Collections.singletonList(
                    HealthRecord.forMessage( HealthMessage.LDAP_SearchFailure,
                            "user search time of " + timeDuration.asLongString() + " exceeded ideal of " + warnDuration.asLongString(  )
                    ) );
        }

        return Collections.emptyList();
    }

    private static List<HealthRecord> checkUserPermission(
            final PwmDomain pwmDomain,
            final UserPermission userPermission,
            final PwmSetting pwmSetting
    )
            throws PwmUnrecoverableException
    {
        final String settingDebugName = pwmSetting.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
        final List<HealthRecord> returnList = new ArrayList<>();
        final DomainConfig config = pwmDomain.getConfig();
        final List<String> ldapProfilesToCheck = new ArrayList<>();
        {
            final String configuredLdapProfileID = userPermission.getLdapProfileID();
            if ( configuredLdapProfileID == null || configuredLdapProfileID.isEmpty() || configuredLdapProfileID.equals( PwmConstants.PROFILE_ID_ALL ) )
            {
                ldapProfilesToCheck.addAll( config.getLdapProfiles().keySet() );
            }
            else
            {
                if ( config.getLdapProfiles().containsKey( configuredLdapProfileID ) )
                {
                    ldapProfilesToCheck.add( configuredLdapProfileID );
                }
                else
                {
                    return Collections.singletonList(
                            HealthRecord.forMessage( HealthMessage.Config_UserPermissionValidity,
                                    settingDebugName,
                                    "specified ldap profile ID invalid: " + configuredLdapProfileID
                            ) );
                }
            }
        }

        for ( final String ldapProfileID : ldapProfilesToCheck )
        {
            switch ( userPermission.getType() )
            {
                case ldapAllUsers:
                    break;

                case ldapUser:
                {
                    final String userDN = userPermission.getLdapBase();
                    if ( userDN != null && !isExampleDN( userDN ) )
                    {
                        final Optional<String> errorMsg = validateDN( pwmDomain, userDN, ldapProfileID );
                        errorMsg.ifPresent( s -> returnList.add( HealthRecord.forMessage(
                                HealthMessage.Config_UserPermissionValidity,
                                settingDebugName, "userDN: " + s ) ) );
                    }
                }
                break;

                case ldapGroup:
                {
                    final String groupDN = userPermission.getLdapBase();
                    if ( groupDN != null && !isExampleDN( groupDN ) )
                    {
                        final Optional<String> errorMsg = validateDN( pwmDomain, groupDN, ldapProfileID );
                        errorMsg.ifPresent( s -> returnList.add( HealthRecord.forMessage(
                                HealthMessage.Config_UserPermissionValidity,
                                settingDebugName, "groupDN: " + s ) ) );
                    }
                }
                break;

                case ldapQuery:
                {
                    final String baseDN = userPermission.getLdapBase();
                    if ( baseDN != null && !isExampleDN( baseDN ) )
                    {
                        final Optional<String> errorMsg = validateDN( pwmDomain, baseDN, ldapProfileID );
                        errorMsg.ifPresent( s -> returnList.add( HealthRecord.forMessage(
                                HealthMessage.Config_UserPermissionValidity,
                                settingDebugName, "baseDN: " + s ) ) );
                    }
                }
                break;

                default:
                    JavaHelper.unhandledSwitchStatement( userPermission.getType() );
            }
        }
        return returnList;
    }

    private static Optional<String> validateDN(
            final PwmDomain pwmDomain,
            final String dnValue,
            final String ldapProfileID
    )
            throws PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( dnValue ) )
        {
            return Optional.empty();
        }

        final ChaiProvider chaiProvider = pwmDomain.getProxyChaiProvider( ldapProfileID );
        try
        {
            if ( !isExampleDN( dnValue ) )
            {
                final ChaiEntry baseDNEntry = chaiProvider.getEntryFactory().newChaiEntry( dnValue );
                if ( !baseDNEntry.exists() )
                {
                    return Optional.of( "DN '" + dnValue + "' is invalid" );
                }
                else
                {
                    final String canonicalDN = baseDNEntry.readCanonicalDN();
                    if ( !dnValue.equals( canonicalDN ) )
                    {
                        return Optional.of( "DN '" + dnValue + "' is not the correct canonical value, the server reports the canonical value as '"
                                + canonicalDN + "'" );
                    }
                }
            }
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( final ChaiException e )
        {
            LOGGER.error( () -> "error while evaluating ldap DN '" + dnValue + "', error: " + e.getMessage() );
        }
        return Optional.empty();
    }

    private static boolean isExampleDN( final String dnValue )
    {
        if ( StringUtil.isEmpty( dnValue ) )
        {
            return false;
        }

        final String[] exampleSuffixes = new String[] {
                "DC=site,DC=example,DC=net",
                "ou=groups,o=example",
        };

        for ( final String suffix : exampleSuffixes )
        {
            if ( dnValue.endsWith( suffix ) )
            {
                return true;
            }
        }
        return false;
    }

    public static HealthData healthForNewConfiguration(
            final PwmDomain pwmDomain,
            final DomainConfig config,
            final Locale locale,
            final String profileID,
            final boolean testContextless,
            final boolean fullTest

    )
            throws PwmUnrecoverableException
    {
        final PwmApplication tempApplication = PwmApplication.createPwmApplication( pwmDomain.getPwmEnvironment().makeRuntimeInstance( config.getAppConfig() ) );
        final LDAPHealthChecker ldapHealthChecker = new LDAPHealthChecker();
        final List<HealthRecord> profileRecords = new ArrayList<>();

        final LdapProfile ldapProfile = config.getLdapProfiles().get( profileID );
        profileRecords.addAll( ldapHealthChecker.checkBasicLdapConnectivity( tempApplication.getDefaultDomain(), config, ldapProfile,
                testContextless ) );
        if ( fullTest )
        {
            profileRecords.addAll( ldapHealthChecker.checkLdapServerUrls( pwmDomain, config, ldapProfile ) );
        }

        if ( profileRecords.isEmpty() )
        {
            profileRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_OK ) );
        }

        if ( fullTest )
        {
            profileRecords.addAll( ldapHealthChecker.doLdapTestUserCheck( config, ldapProfile, tempApplication.getDefaultDomain() ) );
        }

        return HealthRecord.asHealthDataBean( config, locale, profileRecords );
    }
}

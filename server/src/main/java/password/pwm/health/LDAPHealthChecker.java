/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
import password.pwm.PwmConstants;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
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
import password.pwm.util.PasswordData;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.PasswordUtility;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LDAPHealthChecker implements HealthChecker
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( LDAPHealthChecker.class );
    private static final String TOPIC = "LDAP";

    public List<HealthRecord> doHealthCheck( final PwmApplication pwmApplication )
    {
        final Configuration config = pwmApplication.getConfig();
        final List<HealthRecord> returnRecords = new ArrayList<>();
        final Map<String, LdapProfile> ldapProfiles = pwmApplication.getConfig().getLdapProfiles();

        for ( final Map.Entry<String, LdapProfile> entry : ldapProfiles.entrySet() )
        {
            final String profileID = entry.getKey();
            final List<HealthRecord> profileRecords = new ArrayList<>(
                    checkBasicLdapConnectivity( pwmApplication, config, entry.getValue(), true )
            );

            if ( profileRecords.isEmpty() )
            {
                profileRecords.addAll( checkLdapServerUrls( pwmApplication, config, ldapProfiles.get( profileID ) ) );
            }

            if ( profileRecords.isEmpty() )
            {
                profileRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_OK ) );
                profileRecords.addAll( doLdapTestUserCheck( config, ldapProfiles.get( profileID ), pwmApplication ) );
            }
            returnRecords.addAll( profileRecords );
        }

        for ( final LdapProfile ldapProfile : pwmApplication.getLdapConnectionService().getLastLdapFailure().keySet() )
        {
            final ErrorInformation errorInfo = pwmApplication.getLdapConnectionService().getLastLdapFailure().get( ldapProfile );
            if ( errorInfo != null )
            {
                final TimeDuration errorAge = TimeDuration.fromCurrent( errorInfo.getDate() );

                final long cautionDurationMS = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.HEALTH_LDAP_CAUTION_DURATION_MS ) );
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
                returnRecords.addAll( checkVendorSameness( pwmApplication ) );

                returnRecords.addAll( checkUserPermissionValues( pwmApplication ) );

                returnRecords.addAll( checkLdapDNSyntaxValues( pwmApplication ) );

                returnRecords.addAll( checkNewUserPasswordTemplateSetting( pwmApplication, config ) );
            }
        }

        return returnRecords;
    }

    @SuppressWarnings( "checkstyle:MethodLength" )
    public List<HealthRecord> doLdapTestUserCheck(
            final Configuration config,
            final LdapProfile ldapProfile,
            final PwmApplication pwmApplication
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
            testUserDN = ldapProfile.readCanonicalDN( pwmApplication, testUserDN );
            proxyUserDN = ldapProfile.readCanonicalDN( pwmApplication, proxyUserDN );
        }
        catch ( PwmUnrecoverableException e )
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
                        pwmApplication,
                        SessionLabel.HEALTH_SESSION_LABEL,
                        ldapProfile,
                        config,
                        proxyUserDN,
                        proxyUserPW
                );

                theUser = chaiProvider.getEntryFactory().newChaiUser( testUserDN );

            }
            catch ( ChaiUnavailableException e )
            {
                returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserUnavailable,
                        PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                        e.getMessage()
                ) );
                return returnRecords;
            }
            catch ( Throwable e )
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
            catch ( ChaiException e )
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
                final boolean readPwdEnabled = pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.EDIRECTORY_READ_USER_PWD )
                        && theUser.getChaiProvider().getDirectoryVendor() == DirectoryVendor.EDIRECTORY;

                if ( readPwdEnabled )
                {
                    try
                    {
                        theUser.readPassword();
                    }
                    catch ( Exception e )
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
                    final UserIdentity userIdentity = new UserIdentity( testUserDN, ldapProfile.getIdentifier() );

                    final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                            pwmApplication, null, userIdentity, theUser, locale );

                    boolean doPasswordChange = true;
                    final int minLifetimeSeconds = passwordPolicy.getRuleHelper().readIntValue( PwmPasswordRule.MinimumLifetime );
                    if ( minLifetimeSeconds > 0 )
                    {
                        final Instant pwdLastModified = PasswordUtility.determinePwdLastModified(
                                pwmApplication,
                                SessionLabel.HEALTH_SESSION_LABEL,
                                userIdentity
                        );


                        final PasswordStatus passwordStatus;
                        {
                            final UserInfo userInfo = UserInfoFactory.newUserInfo(
                                    pwmApplication,
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
                        final PasswordData newPassword = RandomPasswordGenerator.createRandomPassword( null, passwordPolicy, pwmApplication );
                        try
                        {
                            theUser.setPassword( newPassword.getStringValue() );
                            LOGGER.debug( SessionLabel.HEALTH_SESSION_LABEL, () -> "set random password on test user " + userIdentity.toDisplayString() );
                        }
                        catch ( ChaiException e )
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
            catch ( Exception e )
            {
                final String msg = "error setting test user password: " + JavaHelper.readHostileExceptionMessage( e );
                LOGGER.error( SessionLabel.HEALTH_SESSION_LABEL, msg, e );
                returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserUnexpected,
                        PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                        msg
                ) );
                return returnRecords;
            }

            try
            {
                final UserIdentity userIdentity = new UserIdentity( theUser.getEntryDN(), ldapProfile.getIdentifier() );
                final UserInfo userInfo = UserInfoFactory.newUserInfo(
                        pwmApplication,
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
            catch ( PwmUnrecoverableException e )
            {
                returnRecords.add( new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic( ldapProfile, config ),
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
                catch ( Exception e )
                {
                    // ignore
                }
            }
        }

        returnRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_TestUserOK, ldapProfile.getDisplayName( PwmConstants.DEFAULT_LOCALE ) ) );
        return returnRecords;
    }


    public List<HealthRecord> checkLdapServerUrls(
            final PwmApplication pwmApplication,
            final Configuration config,
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
                        pwmApplication,
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
            catch ( Exception e )
            {
                final String errorString = "error connecting to ldap server '" + loopURL + "': " + e.getMessage();
                returnRecords.add( new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic( ldapProfile, config ),
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
                    catch ( Exception e )
                    { /* ignore */ }
                }
            }
        }
        return returnRecords;
    }

    public List<HealthRecord> checkBasicLdapConnectivity(
            final PwmApplication pwmApplication,
            final Configuration config,
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
                    return Collections.singletonList( new HealthRecord( HealthStatus.WARN, HealthTopic.LDAP, "Missing Proxy User DN" ) );
                }
                if ( proxyPW == null )
                {
                    return Collections.singletonList( new HealthRecord( HealthStatus.WARN, HealthTopic.LDAP, "Missing Proxy User Password" ) );
                }
                chaiProvider = LdapOperationsHelper.createChaiProvider( pwmApplication, SessionLabel.HEALTH_SESSION_LABEL, ldapProfile, config, proxyDN, proxyPW );
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
            catch ( ChaiException e )
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
                        errorString.append( pwmError.getLocalizedMessage( PwmConstants.DEFAULT_LOCALE, pwmApplication.getConfig() ) );
                    }
                    errorString.append( ")" );
                }
                returnRecords.add( new HealthRecord(
                        HealthStatus.WARN,
                        makeLdapTopic( ldapProfile, config ),
                        errorString.toString() ) );
                pwmApplication.getLdapConnectionService().setLastLdapFailure( ldapProfile,
                        new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, errorString.toString() ) );
                return returnRecords;
            }
            catch ( Exception e )
            {
                final HealthRecord record = HealthRecord.forMessage( HealthMessage.LDAP_No_Connection, e.getMessage() );
                returnRecords.add( record );
                pwmApplication.getLdapConnectionService().setLastLdapFailure( ldapProfile,
                        new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, record.getDetail( PwmConstants.DEFAULT_LOCALE, pwmApplication.getConfig() ) ) );
                return returnRecords;
            }

            if ( directoryVendor != null && directoryVendor == DirectoryVendor.ACTIVE_DIRECTORY )
            {
                returnRecords.addAll( checkAd( pwmApplication, config, ldapProfile ) );
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
                            final String errorString = "ldap context setting '" + loopContext + "' is not valid";
                            returnRecords.add( new HealthRecord( HealthStatus.WARN, makeLdapTopic( ldapProfile, config ), errorString ) );
                        }
                    }
                    catch ( Exception e )
                    {
                        final String errorString = "ldap root context '" + loopContext + "' is not valid: " + e.getMessage();
                        returnRecords.add( new HealthRecord( HealthStatus.WARN, makeLdapTopic( ldapProfile, config ), errorString ) );
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
                catch ( Exception e )
                { /* ignore */ }
            }
        }

        return returnRecords;
    }

    private static List<HealthRecord> checkAd( final PwmApplication pwmApplication, final Configuration config, final LdapProfile ldapProfile )
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
            catch ( MalformedURLException | UnknownHostException e )
            {
                returnList.add( HealthRecord.forMessage(
                        HealthMessage.Config_ParseError,
                        e.getMessage(),
                        PwmSetting.LDAP_SERVER_URLS.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                        loopURL
                ) );
            }
        }

        returnList.addAll( checkAdPasswordPolicyApi( pwmApplication ) );

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

    private static String makeLdapTopic(
            final LdapProfile ldapProfile,
            final Configuration configuration
    )
    {
        return makeLdapTopic( ldapProfile.getIdentifier(), configuration );
    }

    private static String makeLdapTopic(
            final String profileID,
            final Configuration configuration
    )
    {
        if ( configuration.getLdapProfiles().isEmpty() || configuration.getLdapProfiles().size() < 2 )
        {
            return TOPIC;
        }
        return TOPIC + "-" + profileID;
    }

    private List<HealthRecord> checkVendorSameness( final PwmApplication pwmApplication )
    {
        final Map<HealthMonitor.HealthMonitorFlag, Serializable> healthProperties = pwmApplication.getHealthMonitor().getHealthProperties();
        if ( healthProperties.containsKey( HealthMonitor.HealthMonitorFlag.LdapVendorSameCheck ) )
        {
            return ( List<HealthRecord> ) healthProperties.get( HealthMonitor.HealthMonitorFlag.LdapVendorSameCheck );
        }

        LOGGER.trace( SessionLabel.HEALTH_SESSION_LABEL, () -> "beginning check for replica vendor sameness" );
        boolean errorReachingServer = false;
        final Map<String, DirectoryVendor> replicaVendorMap = new HashMap<>();

        try
        {
            for ( final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values() )
            {
                final ChaiConfiguration profileChaiConfiguration = LdapOperationsHelper.createChaiConfiguration(
                        pwmApplication.getConfig(),
                        ldapProfile
                );
                final Collection<ChaiConfiguration> replicaConfigs = ChaiUtility.splitConfigurationPerReplica( profileChaiConfiguration, Collections.emptyMap() );
                for ( final ChaiConfiguration chaiConfiguration : replicaConfigs )
                {
                    final ChaiProvider loopProvider = pwmApplication.getLdapConnectionService().getChaiProviderFactory().newProvider( chaiConfiguration );
                    replicaVendorMap.put( chaiConfiguration.getSetting( ChaiSetting.BIND_URLS ), loopProvider.getDirectoryVendor() );
                }
            }
        }
        catch ( Exception e )
        {
            errorReachingServer = true;
            LOGGER.error( SessionLabel.HEALTH_SESSION_LABEL, "error during replica vendor sameness check: " + e.getMessage() );
        }

        final ArrayList<HealthRecord> healthRecords = new ArrayList<>();
        final Set<DirectoryVendor> discoveredVendors = new HashSet<>( replicaVendorMap.values() );

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

            LOGGER.warn( SessionLabel.HEALTH_SESSION_LABEL, "multiple ldap vendors found: " + vendorMsg.toString() );
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

    private static List<HealthRecord> checkAdPasswordPolicyApi( final PwmApplication pwmApplication )
    {


        final boolean passwordPolicyApiEnabled = pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.AD_ENFORCE_PW_HISTORY_ON_SET );
        if ( !passwordPolicyApiEnabled )
        {
            return Collections.emptyList();
        }

        if ( pwmApplication.getHealthMonitor() != null )
        {
            final Map<HealthMonitor.HealthMonitorFlag, Serializable> healthProperties = pwmApplication.getHealthMonitor().getHealthProperties();
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
            for ( final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values() )
            {
                final ChaiConfiguration profileChaiConfiguration = LdapOperationsHelper.createChaiConfiguration(
                        pwmApplication.getConfig(),
                        ldapProfile
                );
                final Collection<ChaiConfiguration> replicaConfigs = ChaiUtility.splitConfigurationPerReplica(
                        profileChaiConfiguration,
                        Collections.emptyMap()
                );

                for ( final ChaiConfiguration chaiConfiguration : replicaConfigs )
                {
                    final ChaiProvider loopProvider = pwmApplication.getLdapConnectionService().getChaiProviderFactory().newProvider( chaiConfiguration );
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
                        LOGGER.warn( record.toDebugString( PwmConstants.DEFAULT_LOCALE, pwmApplication.getConfig() ) );
                    }
                }
            }
        }
        catch ( Exception e )
        {
            errorReachingServer = true;
            LOGGER.error( SessionLabel.HEALTH_SESSION_LABEL,
                    "error during ad api password policy (asn " + PwmConstants.LDAP_AD_PASSWORD_POLICY_CONTROL_ASN + ") check: " + e.getMessage() );
        }

        if ( !errorReachingServer && pwmApplication.getHealthMonitor() != null )
        {
            final Map<HealthMonitor.HealthMonitorFlag, Serializable> healthProperties = pwmApplication.getHealthMonitor().getHealthProperties();
            healthProperties.put( HealthMonitor.HealthMonitorFlag.AdPasswordPolicyApiCheck, healthRecords );
        }

        return healthRecords;
    }

    private static List<HealthRecord> checkUserPermissionValues( final PwmApplication pwmApplication )
    {
        final List<HealthRecord> returnList = new ArrayList<>();
        final Configuration config = pwmApplication.getConfig();
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
                            returnList.addAll( checkUserPermission( pwmApplication, userPermission, pwmSetting ) );
                        }
                        catch ( PwmUnrecoverableException e )
                        {
                            LOGGER.error( "error checking configured permission settings:" + e.getMessage() );
                        }
                    }
                }
            }
        }
        return returnList;
    }

    private static List<HealthRecord> checkLdapDNSyntaxValues( final PwmApplication pwmApplication )
    {
        final List<HealthRecord> returnList = new ArrayList<>();
        final Configuration config = pwmApplication.getConfig();

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
                                final String errorMsg = validateDN( pwmApplication, value, profile );
                                if ( errorMsg != null )
                                {
                                    returnList.add( HealthRecord.forMessage(
                                            HealthMessage.Config_DNValueValidity,
                                            pwmSetting.toMenuLocationDebug( profile, PwmConstants.DEFAULT_LOCALE ), errorMsg )
                                    );
                                }
                            }
                        }
                        else if ( pwmSetting.getSyntax() == PwmSettingSyntax.STRING_ARRAY )
                        {
                            final List<String> values = config.getLdapProfiles().get( profile ).readSettingAsStringArray( pwmSetting );
                            if ( values != null )
                            {
                                for ( final String value : values )
                                {
                                    final String errorMsg = validateDN( pwmApplication, value, profile );
                                    if ( errorMsg != null )
                                    {
                                        returnList.add( HealthRecord.forMessage(
                                                HealthMessage.Config_DNValueValidity,
                                                pwmSetting.toMenuLocationDebug( profile, PwmConstants.DEFAULT_LOCALE ), errorMsg )
                                        );
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.warn( "error while checking DN ldap syntax values: " + e.getMessage() );
        }

        return returnList;
    }

    private static List<HealthRecord> checkNewUserPasswordTemplateSetting(
            final PwmApplication pwmApplication,
            final Configuration configuration
    )
    {
        final Locale locale = PwmConstants.DEFAULT_LOCALE;
        if ( !configuration.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
        {
            return Collections.emptyList();
        }

        for ( final NewUserProfile newUserProfile : configuration.getNewUserProfiles().values() )
        {
            final String policyUserStr = newUserProfile.readSettingAsString( PwmSetting.NEWUSER_PASSWORD_POLICY_USER );

            if ( StringUtil.isEmpty( policyUserStr ) )
            {
                return Collections.singletonList(
                        HealthRecord.forMessage(
                                HealthMessage.NewUser_PwTemplateBad,
                                PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug( newUserProfile.getIdentifier(), locale ),
                                LocaleHelper.valueNotApplicable( locale )
                        )
                );
            }

            try
            {
                final LdapProfile ldapProfile = configuration.getDefaultLdapProfile();
                if ( NewUserProfile.TEST_USER_CONFIG_VALUE.equals( policyUserStr ) )
                {
                    final UserIdentity testUser = ldapProfile.getTestUser( pwmApplication );
                    if ( testUser != null )
                    {
                        return Collections.emptyList();
                    }
                }

                final UserIdentity newUserTemplateIdentity = new UserIdentity( policyUserStr, ldapProfile.getIdentifier() );

                final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser( newUserTemplateIdentity );

                try
                {
                    if ( !chaiUser.exists() )
                    {
                        return Collections.singletonList(
                                HealthRecord.forMessage(
                                        HealthMessage.NewUser_PwTemplateBad,
                                        PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug( newUserProfile.getIdentifier(), locale )
                                )
                        );
                    }
                }
                catch ( ChaiUnavailableException e )
                {
                    throw PwmUnrecoverableException.fromChaiException( e );
                }
            }
            catch ( PwmUnrecoverableException e )
            {
                LOGGER.error( "error checking new user password policy user settings:" + e.getMessage() );
            }
        }

        return Collections.emptyList();
    }


    private static List<HealthRecord> checkUserPermission(
            final PwmApplication pwmApplication,
            final UserPermission userPermission,
            final PwmSetting pwmSetting
    )
            throws PwmUnrecoverableException
    {
        final String settingDebugName = pwmSetting.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
        final List<HealthRecord> returnList = new ArrayList<>();
        final Configuration config = pwmApplication.getConfig();
        final List<String> ldapProfilesToCheck = new ArrayList<>();
        {
            final String configuredLdapProfileID = userPermission.getLdapProfileID();
            if ( configuredLdapProfileID == null || configuredLdapProfileID.isEmpty() || configuredLdapProfileID.equals( PwmConstants.PROFILE_ID_ALL ) )
            {
                ldapProfilesToCheck.addAll( config.getLdapProfiles().keySet() );
            }
            else
            {
                if ( config.getLdapProfiles().keySet().contains( configuredLdapProfileID ) )
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
                case ldapGroup:
                {
                    final String groupDN = userPermission.getLdapBase();
                    if ( groupDN != null && !isExampleDN( groupDN ) )
                    {
                        final String errorMsg = validateDN( pwmApplication, groupDN, ldapProfileID );
                        if ( errorMsg != null )
                        {
                            returnList.add( HealthRecord.forMessage( HealthMessage.Config_UserPermissionValidity, settingDebugName, "groupDN: " + errorMsg ) );
                        }
                    }
                }
                break;

                case ldapQuery:
                {
                    final String baseDN = userPermission.getLdapBase();
                    if ( baseDN != null && !isExampleDN( baseDN ) )
                    {
                        final String errorMsg = validateDN( pwmApplication, baseDN, ldapProfileID );
                        if ( errorMsg != null )
                        {
                            returnList.add( HealthRecord.forMessage( HealthMessage.Config_UserPermissionValidity, settingDebugName, "baseDN: " + errorMsg ) );
                        }
                    }
                }
                break;

                default:
                    JavaHelper.unhandledSwitchStatement( userPermission.getType() );
            }
        }
        return returnList;
    }

    private static String validateDN( final PwmApplication pwmApplication, final String dnValue, final String ldapProfileID )
            throws PwmUnrecoverableException
    {
        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider( ldapProfileID );
        try
        {
            if ( !isExampleDN( dnValue ) )
            {
                final ChaiEntry baseDNEntry = chaiProvider.getEntryFactory().newChaiEntry( dnValue );
                if ( !baseDNEntry.exists() )
                {
                    return "DN '" + dnValue + "' is invalid";
                }
                else
                {
                    final String canonicalDN = baseDNEntry.readCanonicalDN();
                    if ( !dnValue.equals( canonicalDN ) )
                    {
                        return "DN '" + dnValue + "' is not the correct canonical value, the server reports the canonical value as '"
                                + canonicalDN + "'";
                    }
                }
            }
        }
        catch ( ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( ChaiException e )
        {
            LOGGER.error( "error while evaluating ldap DN '" + dnValue + "', error: " + e.getMessage() );
        }
        return null;
    }

    private static boolean isExampleDN( final String dnValue )
    {
        if ( dnValue == null )
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
            final PwmApplication pwmApplication,
            final Configuration config,
            final Locale locale,
            final String profileID,
            final boolean testContextless,
            final boolean fullTest

    )
            throws PwmUnrecoverableException
    {
        final PwmApplication tempApplication = new PwmApplication( pwmApplication.getPwmEnvironment().makeRuntimeInstance( config ) );
        final LDAPHealthChecker ldapHealthChecker = new LDAPHealthChecker();
        final List<HealthRecord> profileRecords = new ArrayList<>();

        final LdapProfile ldapProfile = config.getLdapProfiles().get( profileID );
        profileRecords.addAll( ldapHealthChecker.checkBasicLdapConnectivity( tempApplication, config, ldapProfile,
                testContextless ) );
        if ( fullTest )
        {
            profileRecords.addAll( ldapHealthChecker.checkLdapServerUrls( pwmApplication, config, ldapProfile ) );
        }

        if ( profileRecords.isEmpty() )
        {
            profileRecords.add( HealthRecord.forMessage( HealthMessage.LDAP_OK ) );
        }

        if ( fullTest )
        {
            profileRecords.addAll( ldapHealthChecker.doLdapTestUserCheck( config, ldapProfile, tempApplication ) );
        }

        return HealthRecord.asHealthDataBean( config, locale, profileRecords );
    }
}

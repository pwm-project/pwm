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

import password.pwm.AppProperty;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.ActivateUserProfile;
import password.pwm.config.profile.ChangePasswordProfile;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Config;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.util.PasswordData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordUtility;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigurationChecker implements HealthChecker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigurationChecker.class );

    private static final List<Class<? extends ConfigHealthCheck>> ALL_CHECKS = List.of(
            VerifyBasicConfigs.class,
            VerifyPasswordStrengthLevels.class,
            VerifyPasswordPolicyConfigs.class,
            VerifyResponseLdapAttribute.class,
            VerifyDbConfiguredIfNeeded.class,
            VerifyIfDeprecatedSendMethodValuesUsed.class,
            VerifyIfDeprecatedJsFormOptionUsed.class,
            VerifyNewUserLdapProfile.class,
            VerifyPasswordWaitTimes.class,
            VerifyUserPermissionSettings.class );

    @Override
    public List<HealthRecord> doHealthCheck( final PwmDomain pwmDomain )
    {
        if ( pwmDomain.getConfig() == null )
        {
            return Collections.emptyList();
        }

        final DomainConfig config = pwmDomain.getConfig();

        final List<HealthRecord> records = new ArrayList<>();

        if ( pwmDomain.getApplicationMode() == PwmApplicationMode.CONFIGURATION )
        {
            records.add( HealthRecord.forMessage(
                    config.getDomainID(),
                    HealthMessage.Config_ConfigMode ) );
        }

        if ( config.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
        {
            for ( final NewUserProfile newUserProfile : config.getNewUserProfiles().values() )
            {
                try
                {
                    newUserProfile.getNewUserPasswordPolicy( pwmDomain, PwmConstants.DEFAULT_LOCALE );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    records.add( HealthRecord.forMessage(
                            config.getDomainID(),
                            HealthMessage.NewUser_PwTemplateBad,
                            PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug( newUserProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE ),
                            e.getMessage() ) );
                }
            }
        }

        records.addAll( doHealthCheck( config, PwmConstants.DEFAULT_LOCALE ) );

        return Collections.unmodifiableList( records );
    }

    public List<HealthRecord> doHealthCheck( final DomainConfig config, final Locale locale )
    {
        if ( config.readSettingAsBoolean( PwmSetting.HIDE_CONFIGURATION_HEALTH_WARNINGS ) )
        {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList( allChecks( config, locale ) );
    }


    private List<HealthRecord> allChecks(
            final DomainConfig config,
            final Locale locale
    )
    {
        final List<HealthRecord> records = new ArrayList<>();
        for ( final Class<? extends ConfigHealthCheck> clazz : ALL_CHECKS )
        {
            final ConfigHealthCheck healthCheckClass;
            try
            {
                healthCheckClass = clazz.getDeclaredConstructor().newInstance();
                records.addAll( healthCheckClass.healthCheck( config, locale ) );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unexpected error during health check operation for class " + clazz.toString() + ", error:" + e.getMessage(), e );
            }
        }
        return records;
    }



    static class VerifyBasicConfigs implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainConfig config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();
            final String siteUrl = config.readSettingAsString( PwmSetting.PWM_SITE_URL );
            final String separator = LocaleHelper.getLocalizedMessage( locale, Config.Display_SettingNavigationSeparator, null );

            if ( siteUrl == null || siteUrl.isEmpty() || siteUrl.equals(
                    PwmSetting.PWM_SITE_URL.getDefaultValue( config.getTemplate() ).toNativeObject() ) )
            {
                records.add( HealthRecord.forMessage(
                        config.getDomainID(),
                        HealthMessage.Config_NoSiteURL,
                        PwmSetting.PWM_SITE_URL.toMenuLocationDebug( null, locale ) ) );
            }

            if ( config.readSettingAsBoolean( PwmSetting.LDAP_ENABLE_WIRE_TRACE ) )
            {
                records.add( HealthRecord.forMessage(
                        config.getDomainID(),
                        HealthMessage.Config_LDAPWireTrace,
                        PwmSetting.LDAP_ENABLE_WIRE_TRACE.toMenuLocationDebug( null, locale ) ) );
            }

            if ( Boolean.parseBoolean( config.readAppProperty( AppProperty.LDAP_PROMISCUOUS_ENABLE ) ) )
            {
                final String appPropertyKey = "AppProperty" + separator + AppProperty.LDAP_PROMISCUOUS_ENABLE.getKey();
                records.add( HealthRecord.forMessage(
                        config.getDomainID(),
                        HealthMessage.Config_PromiscuousLDAP,
                        appPropertyKey ) );
            }

            if ( config.getAppConfig().readSettingAsBoolean( PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS ) )
            {
                records.add( HealthRecord.forMessage(
                        config.getDomainID(),
                        HealthMessage.Config_ShowDetailedErrors,
                        PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS.toMenuLocationDebug( null, locale ) ) );
            }

            if ( config.getLdapProfiles().isEmpty() )
            {
                records.add( HealthRecord.forMessage(
                        config.getDomainID(),
                        HealthMessage.Config_NoLdapProfiles ) );
            }

            for ( final LdapProfile ldapProfile : config.getLdapProfiles().values() )
            {
                final String testUserDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_TEST_USER_DN );
                if ( testUserDN == null || testUserDN.length() < 1 )
                {
                    records.add( HealthRecord.forMessage(
                            config.getDomainID(),
                            HealthMessage.Config_AddTestUser,
                            PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), locale )
                    ) );
                }
            }

            for ( final LdapProfile ldapProfile : config.getLdapProfiles().values() )
            {
                final List<String> ldapServerURLs = ldapProfile.getLdapUrls();
                if ( ldapServerURLs != null && !ldapServerURLs.isEmpty() )
                {
                    for ( final String urlStringValue : ldapServerURLs )
                    {
                        try
                        {
                            final URI url = new URI( urlStringValue );
                            final boolean secure = "ldaps".equalsIgnoreCase( url.getScheme() );
                            if ( !secure )
                            {
                                records.add( HealthRecord.forMessage(
                                        config.getDomainID(),
                                        HealthMessage.Config_LDAPUnsecure,
                                        PwmSetting.LDAP_SERVER_URLS.toMenuLocationDebug( ldapProfile.getIdentifier(), locale )
                                ) );
                            }
                        }
                        catch ( final URISyntaxException e )
                        {
                            records.add( HealthRecord.forMessage(
                                    config.getDomainID(),
                                    HealthMessage.Config_ParseError,
                                    e.getMessage(),
                                    PwmSetting.LDAP_SERVER_URLS.toMenuLocationDebug( ldapProfile.getIdentifier(), locale ),
                                    urlStringValue
                            ) );
                        }
                    }
                }
            }

            return records;
        }
    }

    static class VerifyPasswordStrengthLevels implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainConfig config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();

            final Stream<StoredConfigKey> interestedKeys = StoredConfigKey.filterBySettingSyntax(
                    PwmSettingSyntax.PASSWORD,
                    StoredConfigKey.filterByType(
                            StoredConfigKey.RecordType.SETTING,
                            config.getStoredConfiguration().keys() ) );

            try
            {
                for ( final StoredConfigKey key : interestedKeys.collect( Collectors.toList() ) )
                {
                    final PwmSetting pwmSetting = key.toPwmSetting();
                    final StoredValue storedValue = config.getStoredConfiguration().readStoredValue( key ).orElseThrow();
                    final PasswordData passwordValue = ValueTypeConverter.valueToPassword( storedValue );
                    if ( passwordValue != null )
                    {
                        final String stringValue = passwordValue.getStringValue();

                        if ( !StringUtil.isEmpty( stringValue ) )
                        {
                            final int strength = PasswordUtility.judgePasswordStrength( config, stringValue );
                            if ( strength < 50 )
                            {
                                records.add( HealthRecord.forMessage(
                                        config.getDomainID(),
                                        HealthMessage.Config_WeakPassword,
                                        pwmSetting.toMenuLocationDebug( key.getProfileID(), locale ), String.valueOf( strength ) ) );
                            }
                        }
                    }
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "unexpected error examining password strength of configuration: " );
            }

            return Collections.unmodifiableList( records );
        }
    }

    static class VerifyResponseLdapAttribute implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck(
                final DomainConfig config,
                final Locale locale
        )
        {
            final List<HealthRecord> records = new ArrayList<>();
            final PwmSetting[] interestedSettings = new PwmSetting[]
                    {
                            PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE,
                            PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE,
                    };
            for ( final PwmSetting loopSetting : interestedSettings )
            {
                if ( config.getResponseStorageLocations( loopSetting ).contains( DataStorageMethod.LDAP ) )
                {
                    for ( final LdapProfile ldapProfile : config.getLdapProfiles().values() )
                    {
                        final String responseAttr = ldapProfile.readSettingAsString( PwmSetting.CHALLENGE_USER_ATTRIBUTE );
                        final boolean hasResponseAttribute = responseAttr != null && !responseAttr.isEmpty();
                        if ( !hasResponseAttribute )
                        {
                            records.add( HealthRecord.forMessage( HealthMessage.Config_MissingLDAPResponseAttr,
                                    loopSetting.toMenuLocationDebug( null, locale ),
                                    PwmSetting.CHALLENGE_USER_ATTRIBUTE.toMenuLocationDebug( ldapProfile.getIdentifier(), locale )
                            ) );
                        }
                    }
                }
            }
            return records;
        }
    }

    static class VerifyDbConfiguredIfNeeded implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainConfig config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();
            if ( !config.getAppConfig().hasDbConfigured() )
            {
                final Set<PwmSetting> causalSettings = new LinkedHashSet<>();
                {
                    final List<PwmSetting> settingsToCheck = List.of(
                            PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE,
                            PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE,
                            PwmSetting.INTRUDER_STORAGE_METHOD,
                            PwmSetting.EVENTS_USER_STORAGE_METHOD );

                    for ( final PwmSetting loopSetting : settingsToCheck )
                    {
                        if ( config.getResponseStorageLocations( loopSetting ).contains( DataStorageMethod.DB ) )
                        {
                            causalSettings.add( loopSetting );
                        }
                    }
                }

                if ( config.readSettingAsEnum( PwmSetting.PW_EXPY_NOTIFY_STORAGE_MODE, DataStorageMethod.class ) == DataStorageMethod.DB )
                {
                    causalSettings.add( PwmSetting.PW_EXPY_NOTIFY_STORAGE_MODE );
                }

                for ( final PwmSetting setting : causalSettings )
                {
                    records.add( HealthRecord.forMessage(
                            config.getDomainID(),
                            HealthMessage.Config_MissingDB,
                            setting.toMenuLocationDebug( null, locale ) ) );
                }
            }

            if ( config.getResponseStorageLocations( PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE ).contains( DataStorageMethod.LOCALDB ) )
            {
                records.add( HealthRecord.forMessage(
                        config.getDomainID(),
                        HealthMessage.Config_UsingLocalDBResponseStorage,
                        PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE.toMenuLocationDebug( null, locale ) ) );
            }

            if ( config.getOtpSecretStorageLocations( PwmSetting.OTP_SECRET_WRITE_PREFERENCE ).contains( DataStorageMethod.LOCALDB ) )
            {
                records.add( HealthRecord.forMessage(
                        config.getDomainID(),
                        HealthMessage.Config_UsingLocalDBResponseStorage,
                        PwmSetting.OTP_SECRET_WRITE_PREFERENCE.toMenuLocationDebug( null, locale ) ) );
            }

            return records;
        }
    }

    static class VerifyPasswordPolicyConfigs implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainConfig config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();
            for ( final String profileID : config.getPasswordProfileIDs() )
            {
                try
                {
                    final PwmPasswordPolicy pwmPasswordPolicy = config.getPasswordPolicy( profileID );
                    records.addAll( pwmPasswordPolicy.health( locale ) );
                }
                catch ( final Exception e )
                {
                    LOGGER.error( () -> "unexpected error during password policy health check: " + e.getMessage(), e );
                }
            }
            return records;
        }
    }

    static class VerifyNewUserLdapProfile implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainConfig config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();
            for ( final NewUserProfile newUserProfile : config.getNewUserProfiles().values() )
            {
                final String configuredProfile = newUserProfile.readSettingAsString( PwmSetting.NEWUSER_LDAP_PROFILE );
                if ( !StringUtil.isEmpty( configuredProfile ) )
                {
                    final LdapProfile ldapProfile = config.getLdapProfiles().get( configuredProfile );

                    if ( ldapProfile == null )
                    {
                        records.add( HealthRecord.forMessage(
                                config.getDomainID(),
                                HealthMessage.Config_InvalidLdapProfile,
                                PwmSetting.NEWUSER_LDAP_PROFILE.toMenuLocationDebug( newUserProfile.getIdentifier(), locale ) ) );
                    }
                }
            }
            return Collections.unmodifiableList( records );
        }
    }

    static class VerifyIfDeprecatedJsFormOptionUsed implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainConfig config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();

            final Stream<StoredConfigKey> interestedKeys = StoredConfigKey.filterBySettingSyntax(
                    PwmSettingSyntax.FORM, config.getStoredConfiguration().keys() );

            for ( final StoredConfigKey key : interestedKeys.collect( Collectors.toList() ) )
            {
                final PwmSetting loopSetting = key.toPwmSetting();
                final StoredValue storedValue = config.getStoredConfiguration().readStoredValue( key ).orElseThrow();
                final List<FormConfiguration> forms = ValueTypeConverter.valueToForm( storedValue );
                for ( final FormConfiguration form : forms )
                {
                    if ( !StringUtil.isEmpty( form.getJavascript() ) )
                    {
                        records.add( HealthRecord.forMessage(
                                config.getDomainID(),
                                HealthMessage.Config_DeprecatedJSForm,
                                loopSetting.toMenuLocationDebug( key.getProfileID(), locale ),
                                PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT.toMenuLocationDebug( null, locale )
                        ) );
                    }
                }

            }
            return records;
        }
    }

    static class VerifyIfDeprecatedSendMethodValuesUsed implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainConfig config, final Locale locale )
        {
            final Set<MessageSendMethod> deprecatedMethods = Arrays
                    .stream( MessageSendMethod.values() )
                    .filter( MessageSendMethod::isDeprecated )
                    .collect( Collectors.toSet() );

            final List<HealthRecord> records = new ArrayList<>();

            for ( final ActivateUserProfile activationProfile : config.getUserActivationProfiles().values() )
            {
                final MessageSendMethod method = activationProfile.readSettingAsEnum( PwmSetting.ACTIVATE_TOKEN_SEND_METHOD, MessageSendMethod.class );
                if ( deprecatedMethods.contains( method ) )
                {
                    records.add( HealthRecord.forMessage(
                            config.getDomainID(),
                            HealthMessage.Config_InvalidSendMethod,
                            method.toString(),
                            PwmSetting.ACTIVATE_TOKEN_SEND_METHOD.toMenuLocationDebug( activationProfile.getIdentifier(), locale )
                    ) );
                }
            }

            {
                final MessageSendMethod method = config.readSettingAsEnum( PwmSetting.FORGOTTEN_USERNAME_SEND_USERNAME_METHOD, MessageSendMethod.class );
                if ( deprecatedMethods.contains( method ) )
                {
                    records.add( HealthRecord.forMessage(
                            config.getDomainID(),
                            HealthMessage.Config_InvalidSendMethod,
                            method.toString(),
                            PwmSetting.FORGOTTEN_USERNAME_SEND_USERNAME_METHOD.toMenuLocationDebug( null, locale )
                    ) );
                }
            }

            for ( final HelpdeskProfile helpdeskProfile : config.getHelpdeskProfiles().values() )
            {
                final MessageSendMethod method = helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_TOKEN_SEND_METHOD, MessageSendMethod.class );

                if ( deprecatedMethods.contains( method ) )
                {
                    records.add( HealthRecord.forMessage(
                            config.getDomainID(),
                            HealthMessage.Config_InvalidSendMethod,
                            method.toString(),
                            PwmSetting.HELPDESK_TOKEN_SEND_METHOD.toMenuLocationDebug( helpdeskProfile.getIdentifier(), locale )
                    ) );
                }
            }

            for ( final ForgottenPasswordProfile forgottenPasswordProfile : config.getForgottenPasswordProfiles().values() )
            {
                {
                    final MessageSendMethod method = forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_SENDNEWPW_METHOD, MessageSendMethod.class );

                    if ( deprecatedMethods.contains( method ) )
                    {
                        records.add( HealthRecord.forMessage(
                                config.getDomainID(),
                                HealthMessage.Config_InvalidSendMethod,
                                method.toString(),
                                PwmSetting.RECOVERY_SENDNEWPW_METHOD.toMenuLocationDebug( forgottenPasswordProfile.getIdentifier(), locale )
                        ) );
                    }
                }
                {
                    final MessageSendMethod method = forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_TOKEN_SEND_METHOD, MessageSendMethod.class );

                    if ( deprecatedMethods.contains( method ) )
                    {
                        records.add( HealthRecord.forMessage(
                                config.getDomainID(),
                                HealthMessage.Config_InvalidSendMethod,
                                method.toString(),
                                PwmSetting.RECOVERY_TOKEN_SEND_METHOD.toMenuLocationDebug( forgottenPasswordProfile.getIdentifier(), locale )
                        ) );
                    }
                }
            }

            return records;
        }
    }

    static class VerifyPasswordWaitTimes implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainConfig config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();

            for ( final ChangePasswordProfile changePasswordProfile : config.getChangePasswordProfile().values() )
            {
                final long minValue = changePasswordProfile.readSettingAsLong( PwmSetting.PASSWORD_SYNC_MIN_WAIT_TIME );
                final long maxValue = changePasswordProfile.readSettingAsLong( PwmSetting.PASSWORD_SYNC_MAX_WAIT_TIME );
                if ( maxValue > 0 && minValue > maxValue )
                {
                    final String profileID = changePasswordProfile.getIdentifier();
                    final String detailMsg = " (" + minValue + ")"
                            + " > "
                            + " (" + maxValue + ")";
                    records.add( HealthRecord.forMessage(
                            config.getDomainID(),
                            HealthMessage.Config_ValueConflict,
                            PwmSetting.PASSWORD_SYNC_MAX_WAIT_TIME.toMenuLocationDebug( profileID, locale ),
                            PwmSetting.PASSWORD_SYNC_MIN_WAIT_TIME.toMenuLocationDebug( profileID, locale ),
                            detailMsg
                    ) );
                }
            }
            return records;
        }
    }



    static class VerifyUserPermissionSettings implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainConfig config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();

            final StoredConfiguration storedConfiguration = config.getStoredConfiguration();
            for ( final StoredConfigKey configItemKey : StoredConfigKey.filterBySettingSyntax(
                    PwmSettingSyntax.USER_PERMISSION,
                    storedConfiguration.keys() ).collect( Collectors.toList() ) )
            {
                final StoredValue storedValue = storedConfiguration.readStoredValue( configItemKey ).orElseThrow( NoSuchElementException::new );
                final List<UserPermission> permissions = ValueTypeConverter.valueToUserPermissions( storedValue );
                for ( final UserPermission permission : permissions )
                {
                    try
                    {
                        UserPermissionUtility.validatePermissionSyntax( permission );
                    }
                    catch ( final PwmUnrecoverableException e )
                    {
                        final PwmSetting pwmSetting = configItemKey.toPwmSetting();
                        records.add( HealthRecord.forMessage(
                                config.getDomainID(),
                                HealthMessage.Config_SettingIssue,
                                pwmSetting.toMenuLocationDebug( configItemKey.getProfileID(), locale ),
                                e.getMessage() ) );
                    }

                    records.addAll( checkLdapProfile( config, configItemKey, locale, permission ) );
                }
            }

            return Collections.unmodifiableList( records );
        }

        private static List<HealthRecord> checkLdapProfile(
                final DomainConfig domainConfig,
                final StoredConfigKey storedConfigKey,
                final Locale locale,
                final UserPermission permission
        )
        {
            final List<LdapProfile> ldapProfiles = ldapProfilesForLdapProfileSetting( domainConfig, permission.getLdapProfileID() );
            if ( ldapProfiles.isEmpty()  )
            {
                final PwmSetting pwmSetting = storedConfigKey.toPwmSetting();
                return Collections.singletonList( HealthRecord.forMessage(
                        domainConfig.getDomainID(),
                        HealthMessage.Config_ProfileValueValidity,
                        pwmSetting.toMenuLocationDebug( storedConfigKey.getProfileID(), locale ),
                        permission.getLdapProfileID() ) );
            }

            return Collections.emptyList();
        }

        public static List<LdapProfile> ldapProfilesForLdapProfileSetting( final DomainConfig domainConfig, final String profileID )
        {
            if ( UserPermissionUtility.isAllProfiles( profileID ) )
            {
                return List.copyOf( domainConfig.getLdapProfiles().values() );
            }

            if ( domainConfig.getLdapProfiles().containsKey( profileID ) )
            {
                return Collections.singletonList( domainConfig.getLdapProfiles().get( profileID ) );
            }

            return Collections.emptyList();
        }
    }

    interface ConfigHealthCheck
    {
        List<HealthRecord> healthCheck(
                DomainConfig domainConfig,
                Locale locale );
    }
}

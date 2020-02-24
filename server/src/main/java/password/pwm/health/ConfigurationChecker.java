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

package password.pwm.health;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.ActivateUserProfile;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.stored.StoredConfigItemKey;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Config;
import password.pwm.util.PasswordData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordUtility;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigurationChecker implements HealthChecker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigurationChecker.class );

    public List<HealthRecord> doHealthCheck( final PwmApplication pwmApplication )
    {
        if ( pwmApplication.getConfig() == null )
        {
            return Collections.emptyList();
        }

        final Configuration config = pwmApplication.getConfig();

        final List<HealthRecord> records = new ArrayList<>();

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.CONFIGURATION )
        {
            records.add( HealthRecord.forMessage( HealthMessage.Config_ConfigMode ) );
        }

        if ( config.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
        {
            for ( final NewUserProfile newUserProfile : config.getNewUserProfiles().values() )
            {
                try
                {
                    newUserProfile.getNewUserPasswordPolicy( pwmApplication, PwmConstants.DEFAULT_LOCALE );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    records.add( new HealthRecord( HealthStatus.WARN, HealthTopic.Configuration, e.getMessage() ) );
                }
            }
        }

        records.addAll( doHealthCheck( config, PwmConstants.DEFAULT_LOCALE ) );

        return records;
    }

    public List<HealthRecord> doHealthCheck( final Configuration config, final Locale locale )
    {
        if ( config.readSettingAsBoolean( PwmSetting.HIDE_CONFIGURATION_HEALTH_WARNINGS ) )
        {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList( allChecks( config, locale ) );
    }


    private List<HealthRecord> allChecks(
            final Configuration config,
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

    private static final List<Class<? extends ConfigHealthCheck>> ALL_CHECKS = Collections.unmodifiableList( Arrays.asList(
            VerifyBasicConfigs.class,
            VerifyPasswordStrengthLevels.class,
            VerifyPasswordPolicyConfigs.class,
            VerifyResponseLdapAttribute.class,
            VerifyDbConfiguredIfNeeded.class,
            VerifyIfDeprecatedSendMethodValuesUsed.class,
            VerifyIfDeprecatedJsFormOptionUsed.class
    ) );

    static class VerifyBasicConfigs implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final Configuration config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();
            final String siteUrl = config.readSettingAsString( PwmSetting.PWM_SITE_URL );
            final String separator = LocaleHelper.getLocalizedMessage( locale, Config.Display_SettingNavigationSeparator, null );

            if ( siteUrl == null || siteUrl.isEmpty() || siteUrl.equals(
                    PwmSetting.PWM_SITE_URL.getDefaultValue( config.getTemplate() ).toNativeObject() ) )
            {
                records.add(
                        HealthRecord.forMessage( HealthMessage.Config_NoSiteURL, PwmSetting.PWM_SITE_URL.toMenuLocationDebug( null, locale ) ) );
            }

            if ( config.readSettingAsBoolean( PwmSetting.LDAP_ENABLE_WIRE_TRACE ) )
            {
                records.add(
                        HealthRecord.forMessage( HealthMessage.Config_LDAPWireTrace, PwmSetting.LDAP_ENABLE_WIRE_TRACE.toMenuLocationDebug( null, locale ) ) );
            }

            if ( Boolean.parseBoolean( config.readAppProperty( AppProperty.LDAP_PROMISCUOUS_ENABLE ) ) )
            {
                final String appPropertyKey = "AppProperty" + separator + AppProperty.LDAP_PROMISCUOUS_ENABLE.getKey();
                records.add( HealthRecord.forMessage( HealthMessage.Config_PromiscuousLDAP, appPropertyKey ) );
            }

            if ( config.readSettingAsBoolean( PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS ) )
            {
                records.add( HealthRecord.forMessage( HealthMessage.Config_ShowDetailedErrors, PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS.toMenuLocationDebug( null, locale ) ) );
            }

            for ( final LdapProfile ldapProfile : config.getLdapProfiles().values() )
            {
                final String testUserDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_TEST_USER_DN );
                if ( testUserDN == null || testUserDN.length() < 1 )
                {
                    records.add( HealthRecord.forMessage(
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
                                        HealthMessage.Config_LDAPUnsecure,
                                        PwmSetting.LDAP_SERVER_URLS.toMenuLocationDebug( ldapProfile.getIdentifier(), locale )
                                ) );
                            }
                        }
                        catch ( final URISyntaxException e )
                        {
                            records.add( HealthRecord.forMessage( HealthMessage.Config_ParseError,
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
        public List<HealthRecord> healthCheck( final Configuration config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();

            try
            {
                for ( final StoredConfigItemKey key : config.getStoredConfiguration().modifiedItems() )
                {
                    final Instant startTime = Instant.now();
                    if ( key.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
                    {
                        final PwmSetting pwmSetting = key.toPwmSetting();
                        if ( pwmSetting.getSyntax() == PwmSettingSyntax.PASSWORD )
                        {
                            final StoredValue storedValue = config.getStoredConfiguration().readSetting( pwmSetting, key.getProfileID() );
                            final PasswordData passwordValue = ( PasswordData ) storedValue.toNativeObject();
                            if ( passwordValue != null )
                            {
                                final String stringValue = passwordValue.getStringValue();

                                if ( !StringUtil.isEmpty( stringValue ) )
                                {
                                    final int strength = PasswordUtility.judgePasswordStrength( config, stringValue );
                                    if ( strength < 50 )
                                    {
                                        records.add( HealthRecord.forMessage( HealthMessage.Config_WeakPassword,
                                                pwmSetting.toMenuLocationDebug( key.getProfileID(), locale ), String.valueOf( strength ) ) );
                                    }
                                }
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
                final Configuration config,
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
        public List<HealthRecord> healthCheck( final Configuration config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();
            if ( !config.hasDbConfigured() )
            {
                final Set<PwmSetting> causalSettings = new LinkedHashSet<>();
                {
                    final PwmSetting[] settingsToCheck = new PwmSetting[] {
                            PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE,
                            PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE,
                            PwmSetting.INTRUDER_STORAGE_METHOD,
                            PwmSetting.EVENTS_USER_STORAGE_METHOD,
                    };

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
                    records.add( HealthRecord.forMessage( HealthMessage.Config_MissingDB, setting.toMenuLocationDebug( null, locale ) ) );
                }
            }

            if ( config.getResponseStorageLocations( PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE ).contains( DataStorageMethod.LOCALDB ) )
            {
                records.add( HealthRecord.forMessage(
                        HealthMessage.Config_UsingLocalDBResponseStorage,
                        PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE.toMenuLocationDebug( null, locale ) ) );
            }

            if ( config.getOtpSecretStorageLocations( PwmSetting.OTP_SECRET_WRITE_PREFERENCE ).contains( DataStorageMethod.LOCALDB ) )
            {
                records.add( HealthRecord.forMessage(
                        HealthMessage.Config_UsingLocalDBResponseStorage,
                        PwmSetting.OTP_SECRET_WRITE_PREFERENCE.toMenuLocationDebug( null, locale ) ) );
            }

            return records;
        }
    }

    static class VerifyPasswordPolicyConfigs implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final Configuration config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();
            for ( final String profileID : config.getPasswordProfileIDs() )
            {
                try
                {
                    final PwmPasswordPolicy pwmPasswordPolicy = config.getPasswordPolicy( profileID, locale );
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

    static class VerifyIfDeprecatedJsFormOptionUsed implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final Configuration config, final Locale locale )
        {
            final List<HealthRecord> records = new ArrayList<>();

            for ( final PwmSetting loopSetting : PwmSetting.values() )
            {
                if ( loopSetting.getSyntax() == PwmSettingSyntax.FORM )
                {
                    if ( loopSetting.getCategory().hasProfiles() )
                    {

                        final List<String> profiles = config.getStoredConfiguration().profilesForSetting( loopSetting );
                        for ( final String profile : profiles )
                        {
                            final StoredValue storedValue = config.getStoredConfiguration().readSetting( loopSetting, profile );
                            final List<FormConfiguration> forms = (List<FormConfiguration>) storedValue.toNativeObject();
                            for ( final FormConfiguration form : forms )
                            {
                                if ( !StringUtil.isEmpty( form.getJavascript() ) )
                                {
                                    records.add( HealthRecord.forMessage(
                                            HealthMessage.Config_DeprecatedJSForm,
                                            loopSetting.toMenuLocationDebug( profile, locale ),
                                            PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT.toMenuLocationDebug( null, locale )
                                    ) );
                                }
                            }
                        }

                    }
                    else
                    {
                        final List<FormConfiguration> forms = config.readSettingAsForm( loopSetting );
                        for ( final FormConfiguration form : forms )
                        {
                            if ( !StringUtil.isEmpty( form.getJavascript() ) )
                            {
                                records.add( HealthRecord.forMessage(
                                        HealthMessage.Config_DeprecatedJSForm,
                                        loopSetting.toMenuLocationDebug( null, locale ),
                                        PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT.toMenuLocationDebug( null, locale )
                                ) );
                            }
                        }
                    }
                }
            }
            return records;
        }
    }

    static class VerifyIfDeprecatedSendMethodValuesUsed implements ConfigHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final Configuration config, final Locale locale )
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
                    records.add( HealthRecord.forMessage( HealthMessage.Config_InvalidSendMethod,
                            method.toString(),
                            PwmSetting.ACTIVATE_TOKEN_SEND_METHOD.toMenuLocationDebug( null, locale )
                    ) );
                }
            }

            {
                final MessageSendMethod method = config.readSettingAsEnum( PwmSetting.FORGOTTEN_USERNAME_SEND_USERNAME_METHOD, MessageSendMethod.class );
                if ( deprecatedMethods.contains( method ) )
                {
                    records.add( HealthRecord.forMessage( HealthMessage.Config_InvalidSendMethod,
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
                    records.add( HealthRecord.forMessage( HealthMessage.Config_InvalidSendMethod,
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
                        records.add( HealthRecord.forMessage( HealthMessage.Config_InvalidSendMethod,
                                method.toString(),
                                PwmSetting.RECOVERY_SENDNEWPW_METHOD.toMenuLocationDebug( forgottenPasswordProfile.getIdentifier(), locale )
                        ) );
                    }
                }
                {
                    final MessageSendMethod method = forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_TOKEN_SEND_METHOD, MessageSendMethod.class );

                    if ( deprecatedMethods.contains( method ) )
                    {
                        records.add( HealthRecord.forMessage( HealthMessage.Config_InvalidSendMethod,
                                method.toString(),
                                PwmSetting.RECOVERY_TOKEN_SEND_METHOD.toMenuLocationDebug( forgottenPasswordProfile.getIdentifier(), locale )
                        ) );
                    }
                }
            }

            return records;
        }
    }

    interface ConfigHealthCheck
    {
        List<HealthRecord> healthCheck(
                Configuration configuration,
                Locale locale );
    }
}

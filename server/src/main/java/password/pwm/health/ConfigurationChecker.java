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

package password.pwm.health;

import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.PwmEnvironment;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
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
import password.pwm.http.servlet.configguide.ConfigGuideForm;
import password.pwm.i18n.Config;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.util.PasswordData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordUtility;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ConfigurationChecker implements HealthSupplier
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigurationChecker.class );

    private static final List<Class<? extends ConfigDomainHealthCheck>> DOMAIN_CHECKS = List.of(
            VerifyNewUserPasswordPolicy.class,
            VerifyBasicDomainConfigs.class,
            VerifyPasswordStrengthLevels.class,
            VerifyPasswordPolicyConfigs.class,
            VerifyResponseLdapAttribute.class,
            VerifyDbConfiguredIfNeeded.class,
            VerifyIfDeprecatedSendMethodValuesUsed.class,
            VerifyIfDeprecatedJsFormOptionUsed.class,
            VerifyNewUserLdapProfile.class,
            VerifyPasswordWaitTimes.class,
            VerifyUserPermissionSettings.class );

    private static final List<Class<? extends ConfigSystemHealthCheck>> SYSTEM_CHECKS = List.of(
            VerifyBasicSystemConfigs.class,
            VerifyDbConfiguredIfNeededSystem.class );

    @Override
    public List<Supplier<List<HealthRecord>>> jobs( final HealthSupplier.HealthSupplierRequest request )
    {
        final PwmApplication pwmApplication = request.getPwmApplication();
        final SessionLabel sessionLabel = request.getSessionLabel();

        if ( pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.HIDE_CONFIGURATION_HEALTH_WARNINGS ) )
        {
            return Collections.emptyList();
        }

        final List<Supplier<List<HealthRecord>>> suppliers = new ArrayList<>( pwmApplication.domains().size() + 1 );
        suppliers.add( () -> checkAppMode( pwmApplication ) );
        for ( final PwmDomain domain : pwmApplication.domains().values() )
        {
            suppliers.add( () -> allDomainChecks( sessionLabel, domain.getConfig(), PwmConstants.DEFAULT_LOCALE ) );
        }
        suppliers.add( () -> allSystemChecks( sessionLabel, pwmApplication.getConfig(), PwmConstants.DEFAULT_LOCALE ) );
        return suppliers;
    }

    public List<HealthRecord> doHealthCheck( final PwmApplication tempApp, final SessionLabel sessionLabel )
    {
        final HealthSupplier.HealthSupplierRequest request = new HealthSupplierRequest( tempApp, sessionLabel );
        return jobs( request ).stream().map( Supplier::get ).flatMap( Collection::stream ).collect( Collectors.toList() );
    }

    private List<HealthRecord> allDomainChecks( final SessionLabel sessionLabel, final DomainConfig config, final Locale locale )
    {
        final List<HealthRecord> records = new ArrayList<>();
        for ( final Class<? extends ConfigDomainHealthCheck> clazz : DOMAIN_CHECKS )
        {
            final ConfigDomainHealthCheck healthCheckClass;
            try
            {
                healthCheckClass = clazz.getDeclaredConstructor().newInstance();
                records.addAll( healthCheckClass.healthCheck( new DomainHealthCheckRequest( config, locale, sessionLabel ) ) );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unexpected error during health check operation for class " + clazz.toString() + ", error:" + e.getMessage(), e );
            }
        }
        return Collections.unmodifiableList( records );
    }

    private List<HealthRecord> allSystemChecks(
            final SessionLabel sessionLabel,
            final AppConfig config,
            final Locale locale
    )
    {
        final List<HealthRecord> records = new ArrayList<>();
        for ( final Class<? extends ConfigSystemHealthCheck> clazz : SYSTEM_CHECKS )
        {
            final ConfigSystemHealthCheck healthCheckClass;
            try
            {
                healthCheckClass = clazz.getDeclaredConstructor().newInstance();
                records.addAll( healthCheckClass.healthCheck( new SystemHealthCheckRequest( config, locale, sessionLabel ) ) );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unexpected error during health check operation for class " + clazz.toString() + ", error:" + e.getMessage(), e );
            }
        }
        return Collections.unmodifiableList( records );
    }

    private List<HealthRecord> checkAppMode( final PwmApplication pwmApplication )
    {
        final List<HealthRecord> records = new ArrayList<>();

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.CONFIGURATION )
        {
            records.add( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.Config_ConfigMode ) );
        }
        return Collections.unmodifiableList( records );
    }

    static class VerifyNewUserPasswordPolicy implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final List<HealthRecord> records = new ArrayList<>();

            if ( config.readSettingAsBoolean( PwmSetting.NEWUSER_ENABLE ) )
            {
                for ( final NewUserProfile newUserProfile : config.getNewUserProfiles().values() )
                {
                    try
                    {
                        final PwmApplication tempApplication = PwmApplication.createPwmApplication( PwmEnvironment.builder().build()
                                .makeRuntimeInstance( config.getAppConfig() ) );
                        final PwmDomain tempDomain = tempApplication.domains().get( ConfigGuideForm.DOMAIN_ID );

                        newUserProfile.getNewUserPasswordPolicy( domainHealthCheckRequest.getSessionLabel(), tempDomain, PwmConstants.DEFAULT_LOCALE );
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

            return Collections.unmodifiableList( records );
        }
    }

    static class VerifyBasicSystemConfigs implements ConfigSystemHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final SystemHealthCheckRequest systemHealthCheckRequest )
        {
            final AppConfig config = systemHealthCheckRequest.getDomainConfig();
            final Locale locale = systemHealthCheckRequest.getLocale();

            final String separator = LocaleHelper.getLocalizedMessage( locale, Config.Display_SettingNavigationSeparator, null );
            final List<HealthRecord> records = new ArrayList<>();

            if ( Boolean.parseBoolean( config.readAppProperty( AppProperty.LDAP_PROMISCUOUS_ENABLE ) ) )
            {
                final String appPropertyKey = "AppProperty" + separator + AppProperty.LDAP_PROMISCUOUS_ENABLE.getKey();
                records.add( HealthRecord.forMessage(
                        DomainID.systemId(),
                        HealthMessage.Config_PromiscuousLDAP,
                        appPropertyKey ) );
            }

            if ( config.readSettingAsBoolean( PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS ) )
            {
                records.add( HealthRecord.forMessage(
                        DomainID.systemId(),
                        HealthMessage.Config_ShowDetailedErrors,
                        PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS.toMenuLocationDebug( null, locale ) ) );
            }
            return Collections.unmodifiableList( records );
        }
    }

    static class VerifyBasicDomainConfigs implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final Locale locale = domainHealthCheckRequest.getLocale();

            final List<HealthRecord> records = new ArrayList<>();
            final String siteUrl = config.getAppConfig().readSettingAsString( PwmSetting.PWM_SITE_URL );

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

            return Collections.unmodifiableList( records );
        }
    }

    static class VerifyPasswordStrengthLevels implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final List<HealthRecord> records = new ArrayList<>();

            final List<StoredConfigKey> interestedKeys = CollectionUtil.iteratorToStream( config.getStoredConfiguration().keys() )
                    .filter( key -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                    .filter( key -> key.toPwmSetting().getSyntax() == PwmSettingSyntax.PASSWORD )
                    .collect( Collectors.toList() );

            for ( final StoredConfigKey key : interestedKeys )
            {
                try
                {
                    checkKey( domainHealthCheckRequest, key ).ifPresent( records::add );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    LOGGER.error( () -> "unexpected error examining password strength of configuration: " );
                }
            }

            return Collections.unmodifiableList( records );
        }

        private Optional<HealthRecord> checkKey( final DomainHealthCheckRequest domainHealthCheckRequest, final StoredConfigKey key )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration config = domainHealthCheckRequest.getDomainConfig().getStoredConfiguration();
            final PwmSetting pwmSetting = key.toPwmSetting();
            final StoredValue storedValue = config.readStoredValue( key ).orElseThrow();
            final PasswordData passwordValue = ValueTypeConverter.valueToPassword( storedValue );

            if ( passwordValue != null )
            {
                final String stringValue = passwordValue.getStringValue();

                if ( StringUtil.notEmpty( stringValue ) )
                {
                    final int strength = PasswordUtility.judgePasswordStrength( domainHealthCheckRequest.getDomainConfig(), stringValue );
                    if ( strength < 50 )
                    {
                        return Optional.of( HealthRecord.forMessage(
                                domainHealthCheckRequest.getDomainConfig().getDomainID(),
                                HealthMessage.Config_WeakPassword,
                                pwmSetting.toMenuLocationDebug( key.getProfileID(), domainHealthCheckRequest.getLocale() ), String.valueOf( strength ) ) );
                    }
                }
            }

            return Optional.empty();
        }
    }

    static class VerifyResponseLdapAttribute implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final Locale locale = domainHealthCheckRequest.getLocale();

            final List<HealthRecord> records = new ArrayList<>();
            final List<PwmSetting> interestedSettings = List.of(
                            PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE,
                            PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE );

            for ( final PwmSetting loopSetting : interestedSettings )
            {
                if ( config.readGenericStorageLocations( loopSetting ).contains( DataStorageMethod.LDAP ) )
                {
                    for ( final LdapProfile ldapProfile : config.getLdapProfiles().values() )
                    {
                        final String responseAttr = ldapProfile.readSettingAsString( PwmSetting.CHALLENGE_USER_ATTRIBUTE );
                        final boolean hasResponseAttribute = StringUtil.notEmpty( responseAttr );
                        if ( !hasResponseAttribute )
                        {
                            records.add( HealthRecord.forMessage(
                                    config.getDomainID(),
                                    HealthMessage.Config_MissingLDAPResponseAttr,
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

    static class VerifyDbConfiguredIfNeeded implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final Locale locale = domainHealthCheckRequest.getLocale();

            final List<HealthRecord> records = new ArrayList<>();
            if ( !config.getAppConfig().hasDbConfigured() )
            {
                final Set<PwmSetting> causalSettings = new LinkedHashSet<>();
                {
                    final List<PwmSetting> settingsToCheck = List.of(
                            PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE,
                            PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE,
                            PwmSetting.EVENTS_USER_STORAGE_METHOD );

                    for ( final PwmSetting loopSetting : settingsToCheck )
                    {
                        if ( config.readGenericStorageLocations( loopSetting ).contains( DataStorageMethod.DB ) )
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

            if ( config.readGenericStorageLocations( PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE ).contains( DataStorageMethod.LOCALDB ) )
            {
                records.add( HealthRecord.forMessage(
                        config.getDomainID(),
                        HealthMessage.Config_UsingLocalDBResponseStorage,
                        PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE.toMenuLocationDebug( null, locale ) ) );
            }

            if ( config.readGenericStorageLocations( PwmSetting.OTP_SECRET_WRITE_PREFERENCE ).contains( DataStorageMethod.LOCALDB ) )
            {
                records.add( HealthRecord.forMessage(
                        config.getDomainID(),
                        HealthMessage.Config_UsingLocalDBResponseStorage,
                        PwmSetting.OTP_SECRET_WRITE_PREFERENCE.toMenuLocationDebug( null, locale ) ) );
            }

            return records;
        }
    }

    static class VerifyDbConfiguredIfNeededSystem implements ConfigSystemHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final SystemHealthCheckRequest systemHealthCheckRequest )
        {
            final AppConfig config = systemHealthCheckRequest.getDomainConfig();
            final Locale locale = systemHealthCheckRequest.getLocale();
            final List<HealthRecord> records = new ArrayList<>();
            if ( !config.hasDbConfigured() )
            {
                final Set<PwmSetting> causalSettings = new LinkedHashSet<>();
                {
                    final List<PwmSetting> settingsToCheck = List.of(
                            PwmSetting.INTRUDER_STORAGE_METHOD  );

                    for ( final PwmSetting loopSetting : settingsToCheck )
                    {
                        if ( config.readGenericStorageLocations( loopSetting ).contains( DataStorageMethod.DB ) )
                        {
                            causalSettings.add( loopSetting );
                        }
                    }
                }


                for ( final PwmSetting setting : causalSettings )
                {
                    records.add( HealthRecord.forMessage(
                            DomainID.systemId(),
                            HealthMessage.Config_MissingDB,
                            setting.toMenuLocationDebug( null, locale ) ) );
                }
            }

            return records;
        }
    }

    static class VerifyPasswordPolicyConfigs implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final Locale locale = domainHealthCheckRequest.getLocale();

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

    static class VerifyNewUserLdapProfile implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final Locale locale = domainHealthCheckRequest.getLocale();

            final List<HealthRecord> records = new ArrayList<>();
            for ( final NewUserProfile newUserProfile : config.getNewUserProfiles().values() )
            {
                final String configuredProfile = newUserProfile.readSettingAsString( PwmSetting.NEWUSER_LDAP_PROFILE );
                if ( StringUtil.notEmpty( configuredProfile ) )
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

    static class VerifyIfDeprecatedJsFormOptionUsed implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final Locale locale = domainHealthCheckRequest.getLocale();

            final List<HealthRecord> records = new ArrayList<>();

            final List<StoredConfigKey> interestedKeys = CollectionUtil.iteratorToStream( config.getStoredConfiguration().keys() )
                    .filter( key -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                    .filter( key -> key.toPwmSetting().getSyntax() == PwmSettingSyntax.FORM )
                    .collect( Collectors.toList() );

            for ( final StoredConfigKey key : interestedKeys )
            {
                final PwmSetting loopSetting = key.toPwmSetting();
                final StoredValue storedValue = config.getStoredConfiguration().readStoredValue( key ).orElseThrow();
                final List<FormConfiguration> forms = ValueTypeConverter.valueToForm( storedValue );
                for ( final FormConfiguration form : forms )
                {
                    if ( StringUtil.notEmpty( form.getJavascript() ) )
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

    static class VerifyIfDeprecatedSendMethodValuesUsed implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final Locale locale = domainHealthCheckRequest.getLocale();

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

    static class VerifyPasswordWaitTimes implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final Locale locale = domainHealthCheckRequest.getLocale();

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



    static class VerifyUserPermissionSettings implements ConfigDomainHealthCheck
    {
        @Override
        public List<HealthRecord> healthCheck( final DomainHealthCheckRequest domainHealthCheckRequest )
        {
            final DomainConfig config = domainHealthCheckRequest.getDomainConfig();
            final Locale locale = domainHealthCheckRequest.getLocale();

            final List<HealthRecord> records = new ArrayList<>();
            final StoredConfiguration storedConfiguration = config.getStoredConfiguration();
            final List<StoredConfigKey> interestedKeys = CollectionUtil.iteratorToStream( config.getStoredConfiguration().keys() )
                    .filter( key -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                    .filter( key -> key.toPwmSetting().getSyntax() == PwmSettingSyntax.USER_PERMISSION )
                    .collect( Collectors.toList() );

            for ( final StoredConfigKey configItemKey : interestedKeys )
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

    @Value
    static class DomainHealthCheckRequest
    {
        private final DomainConfig domainConfig;
        private final Locale locale;
        private final SessionLabel sessionLabel;
    }

    interface ConfigDomainHealthCheck
    {
        List<HealthRecord> healthCheck( DomainHealthCheckRequest domainHealthCheckRequest );
    }

    @Value
    static class SystemHealthCheckRequest
    {
        private final AppConfig domainConfig;
        private final Locale locale;
        private final SessionLabel sessionLabel;
    }

    interface ConfigSystemHealthCheck
    {
        List<HealthRecord> healthCheck( SystemHealthCheckRequest systemHealthCheckRequest );
    }
}

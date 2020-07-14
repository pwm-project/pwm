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

package password.pwm.config;

import com.novell.ldapchai.util.StringHelper;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.option.CertificateMatchingMode;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.config.profile.ActivateUserProfile;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.ChangePasswordProfile;
import password.pwm.config.profile.EmailServerProfile;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.PeopleSearchProfile;
import password.pwm.config.profile.Profile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.profile.SetupOtpProfile;
import password.pwm.config.profile.UpdateProfileProfile;
import password.pwm.config.stored.StoredConfigItemKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.NamedSecretData;
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.PasswordData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureService;

import java.lang.reflect.InvocationTargetException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * @author Jason D. Rivard
 */
public class Configuration implements SettingReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( Configuration.class );

    private final StoredConfiguration storedConfiguration;

    private final ConfigurationSuppliers configurationSuppliers = new ConfigurationSuppliers();

    private DataCache dataCache = new DataCache();

    public Configuration( final StoredConfiguration storedConfiguration )
    {
        this.storedConfiguration = storedConfiguration;
    }

    public static void deprecatedSettingException( final PwmSetting pwmSetting, final String profile, final MessageSendMethod value )
    {
        if ( value != null && value.isDeprecated() )
        {
            final String msg = pwmSetting.toMenuLocationDebug( profile, PwmConstants.DEFAULT_LOCALE )
                    + " setting is using a no longer functional setting value: " + value;
            throw new IllegalStateException( msg );
        }
    }

    public List<FormConfiguration> readSettingAsForm( final PwmSetting setting )
    {
        final StoredValue value = readStoredValue( setting );
        return ValueTypeConverter.valueToForm( value );
    }

    public List<UserPermission> readSettingAsUserPermission( final PwmSetting setting )
    {
        final StoredValue value = readStoredValue( setting );
        return ValueTypeConverter.valueToUserPermissions( value );
    }

    public Map<String, LdapProfile> getLdapProfiles( )
    {
        return configurationSuppliers.ldapProfilesSupplier.get();
    }

    public EmailItemBean readSettingAsEmail( final PwmSetting setting, final Locale locale )
    {
        if ( PwmSettingSyntax.EMAIL != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read EMAIL value for setting: " + setting.toString() );
        }

        final Map<String, EmailItemBean> storedValues = ( Map<String, EmailItemBean> ) readStoredValue( setting ).toNativeObject();
        final Map<Locale, EmailItemBean> availableLocaleMap = new LinkedHashMap<>();
        for ( final Map.Entry<String, EmailItemBean> entry : storedValues.entrySet() )
        {
            final String localeStr = entry.getKey();
            availableLocaleMap.put( LocaleHelper.parseLocaleString( localeStr ), entry.getValue() );
        }
        final Locale matchedLocale = LocaleHelper.localeResolver( locale, availableLocaleMap.keySet() );

        return availableLocaleMap.get( matchedLocale );
    }

    public <E extends Enum<E>> E readSettingAsEnum( final PwmSetting setting, final Class<E> enumClass )
    {
        final StoredValue value = readStoredValue( setting );
        final E returnValue = ValueTypeConverter.valueToEnum( setting, value, enumClass );
        if ( MessageSendMethod.class.equals( enumClass ) )
        {
            deprecatedSettingException( setting, null, ( MessageSendMethod ) returnValue );
        }

        return returnValue;
    }

    public <E extends Enum<E>> Set<E> readSettingAsOptionList( final PwmSetting setting, final Class<E> enumClass )
    {
        return ValueTypeConverter.valueToOptionList( setting, readStoredValue( setting ), enumClass );
    }

    public MessageSendMethod readSettingAsTokenSendMethod( final PwmSetting setting )
    {
        return readSettingAsEnum( setting, MessageSendMethod.class );
    }

    public List<ActionConfiguration> readSettingAsAction( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToAction( setting, readStoredValue( setting ) );
    }

    public List<String> readSettingAsLocalizedStringArray( final PwmSetting setting, final Locale locale )
    {
        if ( PwmSettingSyntax.LOCALIZED_STRING_ARRAY != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read LOCALIZED_STRING_ARRAY value for setting: " + setting.toString() );
        }

        final StoredValue value = readStoredValue( setting );
        return ValueTypeConverter.valueToLocalizedStringArray( value, locale );
    }

    public String readSettingAsString( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToString( readStoredValue( setting ) );
    }

    public List<RemoteWebServiceConfiguration> readSettingAsRemoteWebService( final PwmSetting pwmSetting )
    {
        return ValueTypeConverter.valueToRemoteWebServiceConfiguration( readStoredValue( pwmSetting ) );
    }

    public PasswordData readSettingAsPassword( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToPassword( readStoredValue( setting ) );
    }

    public Map<String, NamedSecretData> readSettingAsNamedPasswords( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToNamedPassword( readStoredValue( setting ) );
    }

    public Map<Locale, String> readLocalizedBundle( final PwmLocaleBundle className, final String keyName )
    {
        final String key = className + "-" + keyName;
        if ( dataCache.customText.containsKey( key ) )
        {
            return dataCache.customText.get( key );
        }


        final Map<String, String> storedValue = storedConfiguration.readLocaleBundleMap( className, keyName );
        if ( storedValue == null || storedValue.isEmpty() )
        {
            dataCache.customText.put( key, null );
            return null;
        }

        final Map<Locale, String> localizedMap = new LinkedHashMap<>();
        for ( final Map.Entry<String, String> entry : storedValue.entrySet() )
        {
            final String localeKey = entry.getKey();
            localizedMap.put( LocaleHelper.parseLocaleString( localeKey ), entry.getValue() );
        }

        dataCache.customText.put( key, localizedMap );
        return localizedMap;
    }

    public PwmLogLevel getEventLogLocalDBLevel( )
    {
        return readSettingAsEnum( PwmSetting.EVENTS_LOCALDB_LOG_LEVEL, PwmLogLevel.class );
    }

    public List<String> getChallengeProfileIDs( )
    {
        return StoredConfigurationUtil.profilesForSetting( PwmSetting.CHALLENGE_PROFILE_LIST, storedConfiguration );
    }

    public ChallengeProfile getChallengeProfile( final String profile, final Locale locale )
    {
        if ( !"".equals( profile ) && !getChallengeProfileIDs().contains( profile ) )
        {
            throw new IllegalArgumentException( "unknown challenge profileID specified: " + profile );
        }

        // challengeProfile challengeSet's are mutable (question text) and can not be cached.
        final ChallengeProfile challengeProfile = ChallengeProfile.readChallengeProfileFromConfig( profile, locale, storedConfiguration );
        return challengeProfile;
    }

    public List<Long> readSettingAsLongArray( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToLongArray( readStoredValue( setting ) );
    }

    public long readSettingAsLong( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToLong( readStoredValue( setting ) );
    }

    public PwmPasswordPolicy getPasswordPolicy( final String profile, final Locale locale )
    {
        if ( dataCache.cachedPasswordPolicy.containsKey( profile ) && dataCache.cachedPasswordPolicy.get( profile ).containsKey(
                locale ) )
        {
            return dataCache.cachedPasswordPolicy.get( profile ).get( locale );
        }

        final PwmPasswordPolicy policy = initPasswordPolicy( profile, locale );
        if ( !dataCache.cachedPasswordPolicy.containsKey( profile ) )
        {
            dataCache.cachedPasswordPolicy.put( profile, new LinkedHashMap<>() );
        }
        dataCache.cachedPasswordPolicy.get( profile ).put( locale, policy );
        return policy;
    }

    public List<String> getPasswordProfileIDs( )
    {
        return StoredConfigurationUtil.profilesForSetting( PwmSetting.PASSWORD_PROFILE_LIST, storedConfiguration );
    }

    protected PwmPasswordPolicy initPasswordPolicy( final String profile, final Locale locale )
    {
        final Map<String, String> passwordPolicySettings = new LinkedHashMap<>();
        for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
        {
            if ( rule.getPwmSetting() != null || rule.getAppProperty() != null )
            {
                final String value;
                final PwmSetting pwmSetting = rule.getPwmSetting();
                switch ( rule )
                {
                    case DisallowedAttributes:
                    case DisallowedValues:
                    case CharGroupsValues:
                        value = StringHelper.stringCollectionToString(
                                ValueTypeConverter.valueToStringArray( storedConfiguration.readSetting( pwmSetting, profile ) ), "\n" );
                        break;
                    case RegExMatch:
                    case RegExNoMatch:
                        value = StringHelper.stringCollectionToString(
                                ValueTypeConverter.valueToStringArray( storedConfiguration.readSetting( pwmSetting,
                                        profile ) ), ";;;" );
                        break;
                    case ChangeMessage:
                        value = ValueTypeConverter.valueToLocalizedString(
                                storedConfiguration.readSetting( pwmSetting, profile ), locale );
                        break;
                    case ADComplexityLevel:
                        value = ValueTypeConverter.valueToEnum(
                                pwmSetting, storedConfiguration.readSetting( pwmSetting, profile ),
                                ADPolicyComplexity.class
                        ).toString();
                        break;
                    case AllowMacroInRegExSetting:
                        value = readAppProperty( AppProperty.ALLOW_MACRO_IN_REGEX_SETTING );
                        break;
                    default:
                        value = String.valueOf(
                                storedConfiguration.readSetting( pwmSetting, profile ).toNativeObject() );
                }
                passwordPolicySettings.put( rule.getKey(), value );
            }
        }

        // set case sensitivity
        final String caseSensitivitySetting = ValueTypeConverter.valueToString( storedConfiguration.readSetting(
                PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY, null ) );
        if ( !"read".equals( caseSensitivitySetting ) )
        {
            passwordPolicySettings.put( PwmPasswordRule.CaseSensitive.getKey(), caseSensitivitySetting );
        }

        // set pwm-specific values
        final List<UserPermission> queryMatch = ( List<UserPermission> ) storedConfiguration.readSetting( PwmSetting.PASSWORD_POLICY_QUERY_MATCH, profile ).toNativeObject();
        final String ruleText = ValueTypeConverter.valueToLocalizedString( storedConfiguration.readSetting( PwmSetting.PASSWORD_POLICY_RULE_TEXT, profile ), locale );

        final PwmPasswordPolicy.PolicyMetaData policyMetaData = PwmPasswordPolicy.PolicyMetaData.builder()
                .profileID( profile )
                .userPermissions( queryMatch )
                .ruleText( ruleText )
                .build();

        return  PwmPasswordPolicy.createPwmPasswordPolicy( passwordPolicySettings, null, policyMetaData );
    }

    public List<String> readSettingAsStringArray( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToStringArray( readStoredValue( setting ) );
    }

    public String readSettingAsLocalizedString( final PwmSetting setting, final Locale locale )
    {
        return ValueTypeConverter.valueToLocalizedString( readStoredValue( setting ), locale );
    }

    public boolean isDefaultValue( final PwmSetting pwmSetting )
    {
        return storedConfiguration.isDefaultValue( pwmSetting, null );
    }

    public Collection<Locale> localesForSetting( final PwmSetting setting )
    {
        final Collection<Locale> returnCollection = new ArrayList<>();
        switch ( setting.getSyntax() )
        {
            case LOCALIZED_TEXT_AREA:
            case LOCALIZED_STRING:
                for ( final String localeStr : ( ( Map<String, String> ) readStoredValue( setting ).toNativeObject() ).keySet() )
                {
                    returnCollection.add( LocaleHelper.parseLocaleString( localeStr ) );
                }
                break;

            case LOCALIZED_STRING_ARRAY:
                for ( final String localeStr : ( ( Map<String, List<String>> ) readStoredValue( setting ).toNativeObject() ).keySet() )
                {
                    returnCollection.add( LocaleHelper.parseLocaleString( localeStr ) );
                }
                break;

            default:
                // ignore other types
                break;
        }

        return returnCollection;
    }

    public boolean readSettingAsBoolean( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToBoolean( readStoredValue( setting ) );
    }

    public Map<FileValue.FileInformation, FileValue.FileContent> readSettingAsFile( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToFile( setting, readStoredValue( setting ) );
    }

    public List<X509Certificate> readSettingAsCertificate( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToX509Certificates( setting, readStoredValue( setting ) );
    }

    public PrivateKeyCertificate readSettingAsPrivateKey( final PwmSetting setting )
    {
        if ( PwmSettingSyntax.PRIVATE_KEY != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read PRIVATE_KEY value for setting: " + setting.toString() );
        }
        if ( readStoredValue( setting ) == null )
        {
            return null;
        }
        return ( PrivateKeyCertificate ) readStoredValue( setting ).toNativeObject();
    }

    private PwmSecurityKey tempInstanceKey = null;

    public PwmSecurityKey getSecurityKey( ) throws PwmUnrecoverableException
    {

        return configurationSuppliers.pwmSecurityKey.call();
    }

    public List<DataStorageMethod> getResponseStorageLocations( final PwmSetting setting )
    {

        return getGenericStorageLocations( setting );
    }

    public List<DataStorageMethod> getOtpSecretStorageLocations( final PwmSetting setting )
    {
        return getGenericStorageLocations( setting );
    }

    private List<DataStorageMethod> getGenericStorageLocations( final PwmSetting setting )
    {
        final String input = readSettingAsString( setting );
        final List<DataStorageMethod> storageMethods = new ArrayList<>();
        for ( final String rawValue : input.split( "-" ) )
        {
            try
            {
                storageMethods.add( DataStorageMethod.valueOf( rawValue ) );
            }
            catch ( final IllegalArgumentException e )
            {
                LOGGER.error( () -> "unknown STORAGE_METHOD found: " + rawValue );
            }
        }
        return storageMethods;
    }

    public LdapProfile getDefaultLdapProfile( ) throws PwmUnrecoverableException
    {
        return getLdapProfiles().values().iterator().next();
    }


    public List<Locale> getKnownLocales( )
    {
        return Collections.unmodifiableList( new ArrayList<>( configurationSuppliers.localeFlagMap.get().keySet() ) );
    }

    public Map<Locale, String> getKnownLocaleFlagMap( )
    {
        return Collections.unmodifiableMap( configurationSuppliers.localeFlagMap.get() );
    }


    public TokenStorageMethod getTokenStorageMethod( )
    {
        try
        {
            return TokenStorageMethod.valueOf( readSettingAsString( PwmSetting.TOKEN_STORAGEMETHOD ) );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unknown storage method specified: " + readSettingAsString( PwmSetting.TOKEN_STORAGEMETHOD );
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
            LOGGER.warn( () -> errorInformation.toDebugStr() );
            return null;
        }
    }

    public PwmSettingTemplateSet getTemplate( )
    {
        return storedConfiguration.getTemplateSet();
    }

    public boolean hasDbConfigured( )
    {
        if ( StringUtil.isEmpty( readSettingAsString( PwmSetting.DATABASE_CLASS ) ) )
        {
            return false;
        }
        if ( StringUtil.isEmpty( readSettingAsString( PwmSetting.DATABASE_URL ) ) )
        {
            return false;
        }
        if ( StringUtil.isEmpty( readSettingAsString( PwmSetting.DATABASE_USERNAME ) ) )
        {
            return false;
        }
        if ( readSettingAsPassword( PwmSetting.DATABASE_PASSWORD ) == null )
        {
            return false;
        }

        return true;
    }

    public String readAppProperty( final AppProperty property )
    {
        return configurationSuppliers.appPropertyOverrides.get().getOrDefault( property.getKey(), property.getDefaultValue() );
    }

    private StoredValue readStoredValue( final PwmSetting setting )
    {
        if ( setting.getCategory().hasProfiles() )
        {
            throw new IllegalStateException( "attempt to read setting value for setting '"
                    + setting.getKey() + "' as non-profiled setting " );
        }

        return storedConfiguration.readSetting( setting, null );
    }

    private class ConfigurationSuppliers
    {
        private final Supplier<Map<String, LdapProfile>> ldapProfilesSupplier = new LazySupplier<>( () ->
        {
            final Map<String, LdapProfile> map = new LinkedHashMap<>();
            for ( final Map.Entry<String, LdapProfile> entry : getProfileMap( ProfileDefinition.LdapProfile, LdapProfile.class ).entrySet() )
            {
                if ( entry.getValue().isEnabled() )
                {
                    map.put( entry.getKey(), entry.getValue() );
                }
            }
            return Collections.unmodifiableMap( map );
        } );

        private final Supplier<Map<String, String>> appPropertyOverrides = new LazySupplier<>( () ->
                StringUtil.convertStringListToNameValuePair(
                        readSettingAsStringArray( PwmSetting.APP_PROPERTY_OVERRIDES ), "=" ) );

        private final Supplier<Map<Locale, String>> localeFlagMap = new LazySupplier<>( () ->
        {
            final String defaultLocaleAsString = PwmConstants.DEFAULT_LOCALE.toString();

            final List<String> inputList = readSettingAsStringArray( PwmSetting.KNOWN_LOCALES );
            final Map<String, String> inputMap = StringUtil.convertStringListToNameValuePair( inputList, "::" );

            // Sort the map by display name
            final Map<String, String> sortedMap = new TreeMap<>();
            for ( final String localeString : inputMap.keySet() )
            {
                final Locale theLocale = LocaleHelper.parseLocaleString( localeString );
                if ( theLocale != null )
                {
                    sortedMap.put( theLocale.getDisplayName(), localeString );
                }
            }

            final List<String> returnList = new ArrayList<>();

            //ensure default is first.
            returnList.add( defaultLocaleAsString );
            for ( final String localeString : sortedMap.values() )
            {
                if ( !defaultLocaleAsString.equals( localeString ) )
                {
                    returnList.add( localeString );
                }
            }

            final Map<Locale, String> localeFlagMap = new LinkedHashMap<>();
            for ( final String localeString : returnList )
            {
                final Locale loopLocale = LocaleHelper.parseLocaleString( localeString );
                if ( loopLocale != null )
                {
                    final String flagCode = inputMap.containsKey( localeString ) ? inputMap.get( localeString ) : loopLocale.getCountry();
                    localeFlagMap.put( loopLocale, flagCode );
                }
            }
            return Collections.unmodifiableMap( localeFlagMap );
        } );

        private final LazySupplier.CheckedSupplier<PwmSecurityKey, PwmUnrecoverableException> pwmSecurityKey
                = LazySupplier.checked( () ->
        {
            final PasswordData configValue = readSettingAsPassword( PwmSetting.PWM_SECURITY_KEY );

            if ( configValue == null || configValue.getStringValue().isEmpty() )
            {
                final String errorMsg = "Security Key value is not configured, will generate temp value for use by runtime instance";
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg );
                LOGGER.warn( errorInfo::toDebugStr );
                if ( tempInstanceKey == null )
                {
                    tempInstanceKey = new PwmSecurityKey( PwmRandom.getInstance().alphaNumericString( 1024 ) );
                }
                return tempInstanceKey;
            }
            else
            {
                final int minSecurityKeyLength = Integer.parseInt( readAppProperty( AppProperty.SECURITY_CONFIG_MIN_SECURITY_KEY_LENGTH ) );
                if ( configValue.getStringValue().length() < minSecurityKeyLength )
                {
                    final String errorMsg = "Security Key must be greater than 32 characters in length";
                    final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg );
                    throw new PwmUnrecoverableException( errorInfo );
                }

                try
                {
                    return new PwmSecurityKey( configValue.getStringValue() );
                }
                catch ( final Exception e )
                {
                    final String errorMsg = "unexpected error generating Security Key crypto: " + e.getMessage();
                    final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg );
                    LOGGER.error( errorInfo::toDebugStr, e );
                    throw new PwmUnrecoverableException( errorInfo );
                }
            }
        } );
    }

    private static class DataCache
    {
        private final Map<String, Map<Locale, PwmPasswordPolicy>> cachedPasswordPolicy = new LinkedHashMap<>();
        private final Map<String, Map<Locale, String>> customText = new LinkedHashMap<>();
        private final Map<ProfileDefinition, Map> profileCache = new LinkedHashMap<>();
    }

    public Map<AppProperty, String> readAllNonDefaultAppProperties( )
    {
        final LinkedHashMap<AppProperty, String> nonDefaultProperties = new LinkedHashMap<>();
        for ( final AppProperty loopProperty : AppProperty.values() )
        {
            final String configuredValue = readAppProperty( loopProperty );
            final String defaultValue = loopProperty.getDefaultValue();
            if ( configuredValue != null && !configuredValue.equals( defaultValue ) )
            {
                nonDefaultProperties.put( loopProperty, configuredValue );
            }
        }
        return nonDefaultProperties;
    }

    /* generic profile stuff */
    public Map<String, NewUserProfile> getNewUserProfiles( )
    {
        return getProfileMap( ProfileDefinition.NewUser, NewUserProfile.class );
    }

    public Map<String, ActivateUserProfile> getUserActivationProfiles( )
    {
        return getProfileMap( ProfileDefinition.ActivateUser, ActivateUserProfile.class );
    }

    public Map<String, HelpdeskProfile> getHelpdeskProfiles( )
    {
        return getProfileMap( ProfileDefinition.Helpdesk, HelpdeskProfile.class );
    }

    public Map<String, EmailServerProfile> getEmailServerProfiles( )
    {
        return getProfileMap( ProfileDefinition.EmailServers, EmailServerProfile.class );
    }

    public Map<String, PeopleSearchProfile> getPeopleSearchProfiles( )
    {
        return getProfileMap( ProfileDefinition.PeopleSearch, PeopleSearchProfile.class );
    }

    public Map<String, SetupOtpProfile> getSetupOTPProfiles( )
    {
        return getProfileMap( ProfileDefinition.SetupOTPProfile, SetupOtpProfile.class );
    }

    public Map<String, UpdateProfileProfile> getUpdateAttributesProfile( )
    {
        return getProfileMap( ProfileDefinition.UpdateAttributes, UpdateProfileProfile.class );
    }

    public Map<String, ChangePasswordProfile> getChangePasswordProfile( )
    {
        return getProfileMap( ProfileDefinition.ChangePassword, ChangePasswordProfile.class );
    }

    public Map<String, ForgottenPasswordProfile> getForgottenPasswordProfiles( )
    {
        return getProfileMap( ProfileDefinition.ForgottenPassword, ForgottenPasswordProfile.class );
    }

    private <T extends Profile> Map<String, T> getProfileMap( final ProfileDefinition profileDefinition, final Class<T> classOfT  )
    {
        if ( !dataCache.profileCache.containsKey( profileDefinition ) )
        {
            final Map<String, T> returnMap = new LinkedHashMap<>();
            final Map<String, Profile> profileMap = profileMap( profileDefinition );
            for ( final Map.Entry<String, Profile> entry : profileMap.entrySet() )
            {
                returnMap.put( entry.getKey(), ( T ) entry.getValue() );
            }
            dataCache.profileCache.put( profileDefinition, Collections.unmodifiableMap( returnMap ) );
        }
        return dataCache.profileCache.get( profileDefinition );
    }

    public Map<String, Profile> profileMap( final ProfileDefinition profileDefinition )
    {
        final Map<String, Profile> returnMap = new LinkedHashMap<>();
        for ( final String profileID : ProfileUtility.profileIDsForCategory( this, profileDefinition.getCategory() ) )
        {
            if ( profileDefinition.getProfileFactoryClass().isPresent() )
            {
                final Profile newProfile = newProfileForID( profileDefinition, profileID );
                returnMap.put( profileID, newProfile );
            }
        }
        return Collections.unmodifiableMap( returnMap );
    }

    private Profile newProfileForID( final ProfileDefinition profileDefinition, final String profileID )
    {
        Objects.requireNonNull( profileDefinition );
        Objects.requireNonNull( profileID );

        final Optional<Class<? extends Profile.ProfileFactory>> optionalProfileFactoryClass = profileDefinition.getProfileFactoryClass();

        if ( optionalProfileFactoryClass.isPresent() )
        {
            final Profile.ProfileFactory profileFactory;
            try
            {
                profileFactory = optionalProfileFactoryClass.get().getDeclaredConstructor().newInstance();
                return profileFactory.makeFromStoredConfiguration( storedConfiguration, profileID );
            }
            catch ( final InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e )
            {
                throw new IllegalStateException( "unable to create profile instance for " + profileDefinition );
            }
        }

        throw new IllegalStateException( "unable to create profile instance for " + profileDefinition + " ( profile factory class not defined )" );
    }

    public StoredConfiguration getStoredConfiguration( )
    {
        return this.storedConfiguration;
    }

    public boolean isDevDebugMode( )
    {
        return Boolean.parseBoolean( readAppProperty( AppProperty.LOGGING_DEV_OUTPUT ) );
    }

    public String configurationHash( final SecureService secureService )
            throws PwmUnrecoverableException
    {
        return storedConfiguration.valueHash();
    }

    public Set<PwmSetting> nonDefaultSettings( )
    {
        final Set<PwmSetting> returnSet = new LinkedHashSet<>();
        for ( final StoredConfigItemKey key : this.storedConfiguration.modifiedItems() )
        {
            if ( key.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
            {
                returnSet.add( key.toPwmSetting() );
            }
        }
        return returnSet;
    }

    public CertificateMatchingMode readCertificateMatchingMode()
    {
        final CertificateMatchingMode mode = readSettingAsEnum( PwmSetting.CERTIFICATE_VALIDATION_MODE, CertificateMatchingMode.class );
        return mode == null
                ? CertificateMatchingMode.CA_ONLY
                : mode;
    }

    public Optional<PeopleSearchProfile> getPublicPeopleSearchProfile()
    {
        if ( readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_PUBLIC ) )
        {
            final String profileID = readSettingAsString( PwmSetting.PEOPLE_SEARCH_PUBLIC_PROFILE );
            final Map<String, PeopleSearchProfile> profiles = this.getProfileMap( ProfileDefinition.PeopleSearchPublic, PeopleSearchProfile.class );
            return Optional.ofNullable( profiles.get( profileID ) );
        }
        return Optional.empty();
    }
}

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
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.DeleteAccountProfile;
import password.pwm.config.profile.EmailServerProfile;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.Profile;
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.profile.SetupOtpProfile;
import password.pwm.config.profile.UpdateProfileProfile;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.BooleanValue;
import password.pwm.config.value.CustomLinkValue;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.FormValue;
import password.pwm.config.value.LocalizedStringArrayValue;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.NamedSecretValue;
import password.pwm.config.value.NumericArrayValue;
import password.pwm.config.value.NumericValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.RemoteWebServiceValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.UserPermissionValue;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.NamedSecretData;
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * @author Jason D. Rivard
 */
public class Configuration implements SettingReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( Configuration.class );

    private final StoredConfigurationImpl storedConfiguration;

    private DataCache dataCache = new DataCache();

    private String cashedConfigurationHash;

    public Configuration( final StoredConfigurationImpl storedConfiguration )
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

    public void outputToLog( )
    {
        if ( !LOGGER.isEnabled( PwmLogLevel.TRACE ) )
        {
            return;
        }

        final Map<String, String> debugStrings = storedConfiguration.getModifiedSettingDebugValues( PwmConstants.DEFAULT_LOCALE, true );
        final List<Supplier<CharSequence>> outputStrings = new ArrayList<>();

        for ( final Map.Entry<String, String> entry : debugStrings.entrySet() )
        {
            final String spacedValue = entry.getValue().replace( "\n", "\n   " );
            final String output = " " + entry.getKey() + "\n   " + spacedValue + "\n";
            outputStrings.add( () -> output );
        }

        LOGGER.trace( () -> "--begin current configuration output--" );
        outputStrings.forEach( LOGGER::trace );
        LOGGER.trace( () -> "--end current configuration output--" );
    }

    public List<FormConfiguration> readSettingAsForm( final PwmSetting setting )
    {
        final StoredValue value = readStoredValue( setting );
        return JavaTypeConverter.valueToForm( value );
    }

    public List<UserPermission> readSettingAsUserPermission( final PwmSetting setting )
    {
        final StoredValue value = readStoredValue( setting );
        return JavaTypeConverter.valueToUserPermissions( value );
    }

    public Map<String, LdapProfile> getLdapProfiles( )
    {
        if ( dataCache.ldapProfiles != null )
        {
            return dataCache.ldapProfiles;
        }

        final List<String> profiles = StoredConfigurationUtil.profilesForSetting( PwmSetting.LDAP_PROFILE_LIST, storedConfiguration );
        final LinkedHashMap<String, LdapProfile> returnList = new LinkedHashMap<>();
        for ( final String profileID : profiles )
        {
            final LdapProfile ldapProfile = LdapProfile.makeFromStoredConfiguration( this.storedConfiguration, profileID );
            if ( ldapProfile.readSettingAsBoolean( PwmSetting.LDAP_PROFILE_ENABLED ) )
            {
                returnList.put( profileID, ldapProfile );
            }
        }

        dataCache.ldapProfiles = Collections.unmodifiableMap( returnList );
        return dataCache.ldapProfiles;
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
        final E returnValue = JavaTypeConverter.valueToEnum( setting, value, enumClass );
        if ( MessageSendMethod.class.equals( enumClass ) )
        {
            deprecatedSettingException( setting, null, ( MessageSendMethod ) returnValue );
        }

        return returnValue;
    }

    public <E extends Enum<E>> Set<E> readSettingAsOptionList( final PwmSetting setting, final Class<E> enumClass )
    {
        return JavaTypeConverter.valueToOptionList( setting, readStoredValue( setting ), enumClass );
    }

    public MessageSendMethod readSettingAsTokenSendMethod( final PwmSetting setting )
    {
        return readSettingAsEnum( setting, MessageSendMethod.class );
    }

    public List<ActionConfiguration> readSettingAsAction( final PwmSetting setting )
    {
        return JavaTypeConverter.valueToAction( setting, readStoredValue( setting ) );
    }

    public List<String> readSettingAsLocalizedStringArray( final PwmSetting setting, final Locale locale )
    {
        if ( PwmSettingSyntax.LOCALIZED_STRING_ARRAY != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read LOCALIZED_STRING_ARRAY value for setting: " + setting.toString() );
        }

        final StoredValue value = readStoredValue( setting );
        return JavaTypeConverter.valueToLocalizedStringArray( value, locale );
    }

    public String readSettingAsString( final PwmSetting setting )
    {
        return JavaTypeConverter.valueToString( readStoredValue( setting ) );
    }

    public List<RemoteWebServiceConfiguration> readSettingAsRemoteWebService( final PwmSetting pwmSetting )
    {
        return JavaTypeConverter.valueToRemoteWebServiceConfiguration( readStoredValue( pwmSetting ) );
    }

    public PasswordData readSettingAsPassword( final PwmSetting setting )
    {
        return JavaTypeConverter.valueToPassword( readStoredValue( setting ) );
    }

    public Map<String, NamedSecretData> readSettingAsNamedPasswords( final PwmSetting setting )
    {
        return JavaTypeConverter.valueToNamedPassword( readStoredValue( setting ) );
    }

    public abstract static class JavaTypeConverter
    {
        public static long valueToLong( final StoredValue value )
        {
            if ( !( value instanceof NumericValue ) )
            {
                throw new IllegalArgumentException( "setting value is not readable as number" );
            }
            return ( long ) value.toNativeObject();
        }

        public static List<Long> valueToLongArray( final StoredValue value )
        {
            if ( !( value instanceof NumericArrayValue ) )
            {
                throw new IllegalArgumentException( "setting value is not readable as number array" );
            }
            return ( List<Long> ) value.toNativeObject();
        }

        public static String valueToString( final StoredValue value )
        {
            if ( value == null )
            {
                return null;
            }
            if ( ( !( value instanceof StringValue ) ) && ( !( value instanceof BooleanValue ) ) )
            {
                throw new IllegalArgumentException( "setting value is not readable as string" );
            }
            final Object nativeObject = value.toNativeObject();
            if ( nativeObject == null )
            {
                return null;
            }
            return nativeObject.toString();
        }

        public static PasswordData valueToPassword( final StoredValue value )
        {
            if ( value == null )
            {
                return null;
            }
            if ( ( !( value instanceof PasswordValue ) ) )
            {
                throw new IllegalArgumentException( "setting value is not readable as password" );
            }
            final Object nativeObject = value.toNativeObject();
            if ( nativeObject == null )
            {
                return null;
            }
            return ( PasswordData ) nativeObject;
        }

        public static Map<String, NamedSecretData> valueToNamedPassword( final StoredValue value )
        {
            if ( value == null )
            {
                return null;
            }
            if ( ( !( value instanceof NamedSecretValue ) ) )
            {
                throw new IllegalArgumentException( "setting value is not readable as named password" );
            }
            final Object nativeObject = value.toNativeObject();
            if ( nativeObject == null )
            {
                return null;
            }
            return ( Map<String, NamedSecretData> ) nativeObject;
        }

        public static List<RemoteWebServiceConfiguration> valueToRemoteWebServiceConfiguration( final StoredValue value )
        {
            if ( value == null )
            {
                return null;
            }
            if ( ( !( value instanceof RemoteWebServiceValue ) ) )
            {
                throw new IllegalArgumentException( "setting value is not readable as named password" );
            }
            final Object nativeObject = value.toNativeObject();
            if ( nativeObject == null )
            {
                return null;
            }
            return ( List<RemoteWebServiceConfiguration> ) nativeObject;
        }

        public static List<ActionConfiguration> valueToAction( final PwmSetting setting, final StoredValue storedValue )
        {
            if ( PwmSettingSyntax.ACTION != setting.getSyntax() )
            {
                throw new IllegalArgumentException( "may not read ACTION value for setting: " + setting.toString() );
            }

            return ( List<ActionConfiguration> ) storedValue.toNativeObject();
        }


        public static List<FormConfiguration> valueToForm( final StoredValue value )
        {
            if ( value == null )
            {
                return null;
            }

            if ( value instanceof CustomLinkValue )
            {
                return ( List<FormConfiguration> ) value.toNativeObject();
            }

            if ( ( !( value instanceof FormValue ) ) )
            {
                throw new IllegalArgumentException( "setting value is not readable as form" );
            }

            return ( List<FormConfiguration> ) value.toNativeObject();
        }

        public static List<String> valueToStringArray( final StoredValue value )
        {
            if ( !( value instanceof StringArrayValue ) )
            {
                throw new IllegalArgumentException( "setting value is not readable as string array" );
            }

            final List<String> results = new ArrayList<>( ( List<String> ) value.toNativeObject() );
            for ( final Iterator iter = results.iterator(); iter.hasNext(); )
            {
                final Object loopString = iter.next();
                if ( loopString == null || loopString.toString().length() < 1 )
                {
                    iter.remove();
                }
            }
            return results;
        }

        public static List<UserPermission> valueToUserPermissions( final StoredValue value )
        {
            if ( value == null )
            {
                return Collections.emptyList();
            }

            if ( !( value instanceof UserPermissionValue ) )
            {
                throw new IllegalArgumentException( "setting value is not readable as string array" );
            }

            final List<UserPermission> results = new ArrayList<>( ( List<UserPermission> ) value.toNativeObject() );
            for ( final Iterator iter = results.iterator(); iter.hasNext(); )
            {
                final Object loopString = iter.next();
                if ( loopString == null || loopString.toString().length() < 1 )
                {
                    iter.remove();
                }
            }
            return results;
        }

        public static boolean valueToBoolean( final StoredValue value )
        {
            if ( !( value instanceof BooleanValue ) )
            {
                throw new IllegalArgumentException( "may not read BOOLEAN value for setting" );
            }

            return ( Boolean ) value.toNativeObject();
        }

        public static String valueToLocalizedString( final StoredValue value, final Locale locale )
        {
            if ( !( value instanceof LocalizedStringValue ) )
            {
                throw new IllegalArgumentException( "may not read LOCALIZED_STRING or LOCALIZED_TEXT_AREA values for setting" );
            }

            final Map<String, String> availableValues = ( Map<String, String> ) value.toNativeObject();
            final Map<Locale, String> availableLocaleMap = new LinkedHashMap<>();
            for ( final Map.Entry<String, String> entry : availableValues.entrySet() )
            {
                final String localeStr = entry.getKey();
                availableLocaleMap.put( LocaleHelper.parseLocaleString( localeStr ), entry.getValue() );
            }
            final Locale matchedLocale = LocaleHelper.localeResolver( locale, availableLocaleMap.keySet() );

            return availableLocaleMap.get( matchedLocale );
        }

        public static List<String> valueToLocalizedStringArray( final StoredValue value, final Locale locale )
        {
            if ( !( value instanceof LocalizedStringArrayValue ) )
            {
                throw new IllegalArgumentException( "may not read LOCALIZED_STRING_ARRAY value" );
            }
            final Map<String, List<String>> storedValues = ( Map<String, List<String>> ) value.toNativeObject();
            final Map<Locale, List<String>> availableLocaleMap = new LinkedHashMap<>();
            for ( final Map.Entry<String, List<String>> entry : storedValues.entrySet() )
            {
                final String localeStr = entry.getKey();
                availableLocaleMap.put( LocaleHelper.parseLocaleString( localeStr ), entry.getValue() );
            }
            final Locale matchedLocale = LocaleHelper.localeResolver( locale, availableLocaleMap.keySet() );

            return availableLocaleMap.get( matchedLocale );
        }

        public static <E extends Enum<E>> E valueToEnum( final PwmSetting setting, final StoredValue value, final Class<E> enumClass )
        {
            if ( PwmSettingSyntax.SELECT != setting.getSyntax() )
            {
                throw new IllegalArgumentException( "may not read SELECT enum value for setting: " + setting.toString() );
            }

            if ( value != null )
            {
                final String strValue = ( String ) value.toNativeObject();
                try
                {
                    return ( E ) enumClass.getMethod( "valueOf", String.class ).invoke( null, strValue );
                }
                catch ( InvocationTargetException e1 )
                {
                    if ( e1.getCause() instanceof IllegalArgumentException )
                    {
                        LOGGER.error( "illegal setting value for option '" + strValue + "' for setting key '" + setting.getKey() + "' is not recognized, will use default" );
                    }
                }
                catch ( Exception e1 )
                {
                    LOGGER.error( "unexpected error", e1 );
                }
            }

            return null;
        }

        public static <E extends Enum<E>> Set<E> valueToOptionList( final PwmSetting setting, final StoredValue value, final Class<E> enumClass )
        {
            if ( PwmSettingSyntax.OPTIONLIST != setting.getSyntax() )
            {
                throw new IllegalArgumentException( "may not read optionlist value for setting: " + setting.toString() );
            }

            final Set<E> returnSet = new LinkedHashSet<>();
            final Set<String> strValues = ( Set<String> ) value.toNativeObject();
            for ( final String strValue : strValues )
            {
                try
                {
                    returnSet.add( ( E ) enumClass.getMethod( "valueOf", String.class ).invoke( null, strValue ) );
                }
                catch ( InvocationTargetException e1 )
                {
                    if ( e1.getCause() instanceof IllegalArgumentException )
                    {
                        LOGGER.error( "illegal setting value for option '" + strValue + "' is not recognized, will use default" );
                    }
                }
                catch ( Exception e1 )
                {
                    LOGGER.error( "unexpected error", e1 );
                }
            }

            return Collections.unmodifiableSet( returnSet );
        }
    }

    public Map<Locale, String> readLocalizedBundle( final String className, final String keyName )
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
        return JavaTypeConverter.valueToLongArray( readStoredValue( setting ) );
    }

    public long readSettingAsLong( final PwmSetting setting )
    {
        return JavaTypeConverter.valueToLong( readStoredValue( setting ) );
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
                                JavaTypeConverter.valueToStringArray( storedConfiguration.readSetting( pwmSetting, profile ) ), "\n" );
                        break;
                    case RegExMatch:
                    case RegExNoMatch:
                        value = StringHelper.stringCollectionToString(
                                JavaTypeConverter.valueToStringArray( storedConfiguration.readSetting( pwmSetting,
                                        profile ) ), ";;;" );
                        break;
                    case ChangeMessage:
                        value = JavaTypeConverter.valueToLocalizedString(
                                storedConfiguration.readSetting( pwmSetting, profile ), locale );
                        break;
                    case ADComplexityLevel:
                        value = JavaTypeConverter.valueToEnum(
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
        final String caseSensitivitySetting = JavaTypeConverter.valueToString( storedConfiguration.readSetting(
                PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY ) );
        if ( !"read".equals( caseSensitivitySetting ) )
        {
            passwordPolicySettings.put( PwmPasswordRule.CaseSensitive.getKey(), caseSensitivitySetting );
        }

        // set pwm-specific values
        final List<UserPermission> queryMatch = ( List<UserPermission> ) storedConfiguration.readSetting( PwmSetting.PASSWORD_POLICY_QUERY_MATCH, profile ).toNativeObject();
        final String ruleText = JavaTypeConverter.valueToLocalizedString( storedConfiguration.readSetting( PwmSetting.PASSWORD_POLICY_RULE_TEXT, profile ), locale );

        final PwmPasswordPolicy.PolicyMetaData policyMetaData = PwmPasswordPolicy.PolicyMetaData.builder()
                .profileID( profile )
                .userPermissions( queryMatch )
                .ruleText( ruleText )
                .build();

        return  PwmPasswordPolicy.createPwmPasswordPolicy( passwordPolicySettings, null, policyMetaData );
    }

    public List<String> readSettingAsStringArray( final PwmSetting setting )
    {
        return JavaTypeConverter.valueToStringArray( readStoredValue( setting ) );
    }

    public String readSettingAsLocalizedString( final PwmSetting setting, final Locale locale )
    {
        return JavaTypeConverter.valueToLocalizedString( readStoredValue( setting ), locale );
    }

    public boolean isDefaultValue( final PwmSetting pwmSetting )
    {
        return storedConfiguration.isDefaultValue( pwmSetting );
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

    public String readProperty( final ConfigurationProperty key )
    {
        return storedConfiguration.readConfigProperty( key );
    }

    public boolean readSettingAsBoolean( final PwmSetting setting )
    {
        return JavaTypeConverter.valueToBoolean( readStoredValue( setting ) );
    }

    public Map<FileValue.FileInformation, FileValue.FileContent> readSettingAsFile( final PwmSetting setting )
    {
        final FileValue fileValue = ( FileValue ) storedConfiguration.readSetting( setting );
        return ( Map ) fileValue.toNativeObject();
    }

    public List<X509Certificate> readSettingAsCertificate( final PwmSetting setting )
    {
        if ( PwmSettingSyntax.X509CERT != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read X509CERT value for setting: " + setting.toString() );
        }
        if ( readStoredValue( setting ) == null )
        {
            return Collections.emptyList();
        }
        final X509Certificate[] arrayCerts = ( X509Certificate[] ) readStoredValue( setting ).toNativeObject();
        return arrayCerts == null ? Collections.emptyList() : Arrays.asList( arrayCerts );
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

    public String getNotes( )
    {
        return storedConfiguration.readConfigProperty( ConfigurationProperty.NOTES );
    }

    private PwmSecurityKey tempInstanceKey = null;

    public PwmSecurityKey getSecurityKey( ) throws PwmUnrecoverableException
    {
        final PasswordData configValue = readSettingAsPassword( PwmSetting.PWM_SECURITY_KEY );

        if ( configValue == null || configValue.getStringValue().isEmpty() )
        {
            final String errorMsg = "Security Key value is not configured,will generate temp value for use by runtime instance";
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg );
            LOGGER.warn( errorInfo.toDebugStr() );
            if ( tempInstanceKey == null )
            {
                tempInstanceKey = new PwmSecurityKey( PwmRandom.getInstance().alphaNumericString( 256 ) );
            }
            return tempInstanceKey;
        }

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
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error generating Security Key crypto: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg );
            LOGGER.error( errorInfo.toDebugStr(), e );
            throw new PwmUnrecoverableException( errorInfo );
        }
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
            catch ( IllegalArgumentException e )
            {
                LOGGER.error( "unknown STORAGE_METHOD found: " + rawValue );
            }
        }
        return storageMethods;
    }

    public LdapProfile getDefaultLdapProfile( ) throws PwmUnrecoverableException
    {
        if ( getLdapProfiles().isEmpty() )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            "no ldap profiles are defined",
                    }
            ) );
        }
        return getLdapProfiles().values().iterator().next();
    }


    public List<Locale> getKnownLocales( )
    {
        if ( dataCache.localeFlagMap == null )
        {
            dataCache.localeFlagMap = figureLocaleFlagMap();
        }
        return Collections.unmodifiableList( new ArrayList<>( dataCache.localeFlagMap.keySet() ) );
    }

    public Map<Locale, String> getKnownLocaleFlagMap( )
    {
        if ( dataCache.localeFlagMap == null )
        {
            dataCache.localeFlagMap = figureLocaleFlagMap();
        }
        return dataCache.localeFlagMap;
    }

    private Map<Locale, String> figureLocaleFlagMap( )
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
    }

    public TokenStorageMethod getTokenStorageMethod( )
    {
        try
        {
            return TokenStorageMethod.valueOf( readSettingAsString( PwmSetting.TOKEN_STORAGEMETHOD ) );
        }
        catch ( Exception e )
        {
            final String errorMsg = "unknown storage method specified: " + readSettingAsString( PwmSetting.TOKEN_STORAGEMETHOD );
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg );
            LOGGER.warn( errorInformation.toDebugStr() );
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
        if ( dataCache.appPropertyOverrides == null )
        {
            dataCache.appPropertyOverrides = StringUtil.convertStringListToNameValuePair( this.readSettingAsStringArray( PwmSetting.APP_PROPERTY_OVERRIDES ), "=" );
        }

        return dataCache.appPropertyOverrides.getOrDefault( property.getKey(), property.getDefaultValue() );
    }

    private Convenience helper = new Convenience();

    public Convenience helper( )
    {
        return helper;
    }

    public class Convenience
    {
        public List<DataStorageMethod> getCrReadPreference( )
        {
            final List<DataStorageMethod> readPreferences = getResponseStorageLocations( PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE );
            if ( readPreferences.size() == 1 && readPreferences.get( 0 ) == DataStorageMethod.AUTO )
            {
                readPreferences.clear();
                if ( hasDbConfigured() )
                {
                    readPreferences.add( DataStorageMethod.DB );
                }
                else
                {
                    readPreferences.add( DataStorageMethod.LDAP );
                }
            }


            if ( readSettingAsBoolean( PwmSetting.EDIRECTORY_USE_NMAS_RESPONSES ) )
            {
                readPreferences.add( DataStorageMethod.NMAS );
            }

            return readPreferences;
        }

        public List<DataStorageMethod> getCrWritePreference( )
        {
            final List<DataStorageMethod> writeMethods = getResponseStorageLocations( PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE );
            if ( writeMethods.size() == 1 && writeMethods.get( 0 ) == DataStorageMethod.AUTO )
            {
                writeMethods.clear();
                if ( hasDbConfigured() )
                {
                    writeMethods.add( DataStorageMethod.DB );
                }
                else
                {
                    writeMethods.add( DataStorageMethod.LDAP );
                }
            }
            if ( readSettingAsBoolean( PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES ) )
            {
                writeMethods.add( DataStorageMethod.NMAS );
            }
            return writeMethods;
        }
    }

    private StoredValue readStoredValue( final PwmSetting setting )
    {
        if ( dataCache.settings.containsKey( setting ) )
        {
            return dataCache.settings.get( setting );
        }

        final StoredValue readValue = storedConfiguration.readSetting( setting );
        dataCache.settings.put( setting, readValue );
        return readValue;
    }

    private static class DataCache implements Serializable
    {
        private final Map<String, Map<Locale, PwmPasswordPolicy>> cachedPasswordPolicy = new LinkedHashMap<>();
        private Map<Locale, String> localeFlagMap = null;
        private Map<String, LdapProfile> ldapProfiles;
        private final Map<PwmSetting, StoredValue> settings = new EnumMap<>( PwmSetting.class );
        private final Map<String, Map<Locale, String>> customText = new LinkedHashMap<>();
        private final Map<ProfileType, Map<String, Profile>> profileCache = new LinkedHashMap<>();
        private Map<String, String> appPropertyOverrides = null;
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
        final Map<String, NewUserProfile> returnMap = new LinkedHashMap<>();
        final Map<String, Profile> profileMap = profileMap( ProfileType.NewUser );
        for ( final Map.Entry<String, Profile> entry : profileMap.entrySet() )
        {
            returnMap.put( entry.getKey(), ( NewUserProfile ) entry.getValue() );
        }
        return returnMap;
    }

    public Map<String, HelpdeskProfile> getHelpdeskProfiles( )
    {
        final Map<String, HelpdeskProfile> returnMap = new LinkedHashMap<>();
        final Map<String, Profile> profileMap = profileMap( ProfileType.Helpdesk );
        for ( final Map.Entry<String, Profile> entry : profileMap.entrySet() )
        {
            returnMap.put( entry.getKey(), ( HelpdeskProfile ) entry.getValue() );
        }
        return returnMap;
    }

    public Map<String, EmailServerProfile> getEmailServerProfiles( )
    {
        final Map<String, EmailServerProfile> returnMap = new LinkedHashMap<>();
        final Map<String, Profile> profileMap = profileMap( ProfileType.EmailServers );
        for ( final Map.Entry<String, Profile> entry : profileMap.entrySet() )
        {
            returnMap.put( entry.getKey(), ( EmailServerProfile ) entry.getValue() );
        }
        return returnMap;
    }

    public Map<String, SetupOtpProfile> getSetupOTPProfiles( )
    {
        final Map<String, SetupOtpProfile> returnMap = new LinkedHashMap<>();
        final Map<String, Profile> profileMap = profileMap( ProfileType.SetupOTPProfile );
        for ( final Map.Entry<String, Profile> entry : profileMap.entrySet() )
        {
            returnMap.put( entry.getKey(), ( SetupOtpProfile ) entry.getValue() );
        }
        return returnMap;
    }

    public Map<String, UpdateProfileProfile> getUpdateAttributesProfile( )
    {
        final Map<String, UpdateProfileProfile> returnMap = new LinkedHashMap<>();
        final Map<String, Profile> profileMap = profileMap( ProfileType.UpdateAttributes );
        for ( final Map.Entry<String, Profile> entry : profileMap.entrySet() )
        {
            returnMap.put( entry.getKey(), ( UpdateProfileProfile ) entry.getValue() );
        }
        return returnMap;
    }

    public Map<String, ForgottenPasswordProfile> getForgottenPasswordProfiles( )
    {
        final Map<String, ForgottenPasswordProfile> returnMap = new LinkedHashMap<>();
        final Map<String, Profile> profileMap = profileMap( ProfileType.ForgottenPassword );
        for ( final Map.Entry<String, Profile> entry : profileMap.entrySet() )
        {
            returnMap.put( entry.getKey(), ( ForgottenPasswordProfile ) entry.getValue() );
        }
        return returnMap;
    }

    public Map<String, Profile> profileMap( final ProfileType profileType )
    {
        if ( !dataCache.profileCache.containsKey( profileType ) )
        {
            dataCache.profileCache.put( profileType, new LinkedHashMap<>() );
            for ( final String profileID : ProfileUtility.profileIDsForCategory( this, profileType.getCategory() ) )
            {
                final Profile newProfile = newProfileForID( profileType, profileID );
                dataCache.profileCache.get( profileType ).put( profileID, newProfile );
            }
        }
        return dataCache.profileCache.get( profileType );
    }

    private Profile newProfileForID( final ProfileType profileType, final String profileID )
    {
        final Profile newProfile;
        switch ( profileType )
        {
            case Helpdesk:
                newProfile = HelpdeskProfile.makeFromStoredConfiguration( storedConfiguration, profileID );
                break;

            case ForgottenPassword:
                newProfile = ForgottenPasswordProfile.makeFromStoredConfiguration( storedConfiguration, profileID );
                break;

            case NewUser:
                newProfile = NewUserProfile.makeFromStoredConfiguration( storedConfiguration, profileID );
                break;

            case UpdateAttributes:
                newProfile = UpdateProfileProfile.makeFromStoredConfiguration( storedConfiguration, profileID );
                break;

            case DeleteAccount:
                newProfile = DeleteAccountProfile.makeFromStoredConfiguration( storedConfiguration, profileID );
                break;

            case EmailServers:
                newProfile = EmailServerProfile.makeFromStoredConfiguration( storedConfiguration, profileID );
                break;

            case SetupOTPProfile:
                newProfile = SetupOtpProfile.makeFromStoredConfiguration( storedConfiguration, profileID );
                break;

            default:
                throw new IllegalArgumentException( "unknown profile type: " + profileType.toString() );
        }

        return newProfile;
    }

    public StoredConfigurationImpl getStoredConfiguration( ) throws PwmUnrecoverableException
    {
        final StoredConfigurationImpl copiedStoredConfiguration = StoredConfigurationImpl.copy( storedConfiguration );
        copiedStoredConfiguration.lock();
        return copiedStoredConfiguration;
    }

    public boolean isDevDebugMode( )
    {
        return Boolean.parseBoolean( readAppProperty( AppProperty.LOGGING_DEV_OUTPUT ) );
    }

    public String configurationHash( )
            throws PwmUnrecoverableException
    {
        if ( this.cashedConfigurationHash == null )
        {
            this.cashedConfigurationHash = storedConfiguration.settingChecksum();
        }
        return cashedConfigurationHash;
    }

    public Set<PwmSetting> nonDefaultSettings( )
    {
        final Set returnSet = new LinkedHashSet();
        for ( final StoredConfigurationImpl.SettingValueRecord valueRecord : this.storedConfiguration.modifiedSettings() )
        {
            returnSet.add( valueRecord.getSetting() );
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

}

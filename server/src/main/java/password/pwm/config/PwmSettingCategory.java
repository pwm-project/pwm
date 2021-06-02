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

import password.pwm.PwmConstants;
import password.pwm.i18n.Config;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.macro.MacroRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum PwmSettingCategory
{
    TEMPLATES( null ),
    NOTES( null ),

    LDAP( null ),
    SETTINGS( null ),
    PROFILES( null ),
    MODULES( null ),
    MODULES_PUBLIC( MODULES ),
    MODULES_PRIVATE( MODULES ),

    LDAP_PROFILE( LDAP ),
    LDAP_BASE( LDAP_PROFILE ),
    LDAP_LOGIN( LDAP_PROFILE ),
    LDAP_ATTRIBUTES( LDAP_PROFILE ),


    LDAP_SETTINGS( LDAP ),
    LDAP_GLOBAL( LDAP_SETTINGS ),

    EDIRECTORY( LDAP_SETTINGS ),
    EDIR_SETTINGS( EDIRECTORY ),
    EDIR_CR_SETTINGS( EDIRECTORY ),

    ACTIVE_DIRECTORY( LDAP_SETTINGS ),
    ORACLE_DS( LDAP_SETTINGS ),

    APPLICATION( SETTINGS ),
    GENERAL( APPLICATION ),
    CLUSTERING( APPLICATION ),
    LOCALIZATION( APPLICATION ),
    TELEMETRY( APPLICATION ),

    AUDITING( SETTINGS ),
    AUDIT_CONFIG( AUDITING ),
    AUDIT_FORWARD( AUDITING ),

    USER_HISTORY( SETTINGS ),

    CAPTCHA( SETTINGS ),

    INTRUDER( SETTINGS ),
    INTRUDER_SETTINGS( INTRUDER ),
    INTRUDER_TIMEOUTS( INTRUDER ),

    USER_INTERFACE( SETTINGS ),
    UI_FEATURES( USER_INTERFACE ),
    UI_WEB( USER_INTERFACE ),

    EMAIL                       ( SETTINGS ),
    EMAIL_SETTINGS              ( EMAIL ),
    EMAIL_TEMPLATES             ( EMAIL ),
    EMAIL_SERVERS               ( EMAIL ),

    SMS( SETTINGS ),
    SMS_GATEWAY( SMS ),
    SMS_MESSAGES( SMS ),

    SECURITY( SETTINGS ),
    APP_SECURITY( SECURITY ),
    WEB_SECURITY( SECURITY ),

    WORDLISTS( SETTINGS ),

    PASSWORD_GLOBAL( SETTINGS ),

    TOKEN( SETTINGS ),
    LOGGING( SETTINGS ),

    DATABASE( SETTINGS ),
    DATABASE_SETTINGS( DATABASE ),
    DATABASE_ADV( DATABASE ),

    REPORTING( SETTINGS ),

    SSO( SETTINGS ),
    OAUTH( SSO ),
    HTTP_SSO( SSO ),
    CAS_SSO( SSO ),
    BASIC_SSO( SSO ),

    PW_EXP_NOTIFY( SETTINGS ),

    WEB_SERVICES( SETTINGS ),
    REST_SERVER( WEB_SERVICES ),
    REST_CLIENT( WEB_SERVICES ),

    PASSWORD_POLICY( PROFILES ),
    CHALLENGE_POLICY( PROFILES ),

    HTTPS_SERVER( SETTINGS ),

    ADMINISTRATION( MODULES_PRIVATE ),

    ACCOUNT_INFO( MODULES_PRIVATE ),
    ACCOUNT_INFO_SETTINGS( ACCOUNT_INFO ),
    ACCOUNT_INFO_PROFILE( ACCOUNT_INFO ),


    CHANGE_PASSWORD( MODULES_PRIVATE ),
    CHANGE_PASSWORD_SETTINGS( CHANGE_PASSWORD ),
    CHANGE_PASSWORD_PROFILE( CHANGE_PASSWORD ),

    CHALLENGE( MODULES_PRIVATE ),

    RECOVERY( MODULES_PUBLIC ),
    RECOVERY_SETTINGS( RECOVERY ),
    RECOVERY_PROFILE( RECOVERY ),

    RECOVERY_DEF( RECOVERY_PROFILE ),
    RECOVERY_OPTIONS( RECOVERY_PROFILE ),
    RECOVERY_OAUTH( RECOVERY_PROFILE ),

    FORGOTTEN_USERNAME( MODULES_PUBLIC ),

    ACTIVATION( MODULES_PUBLIC ),
    ACTIVATION_SETTINGS( ACTIVATION ),
    ACTIVATION_PROFILE( ACTIVATION ),

    NEWUSER( MODULES_PUBLIC ),
    NEWUSER_SETTINGS( NEWUSER ),
    NEWUSER_PROFILE( NEWUSER ),

    UPDATE( MODULES_PRIVATE ),
    UPDATE_SETTINGS( UPDATE ),
    UPDATE_PROFILE( UPDATE ),

    GUEST( MODULES_PRIVATE ),
    SHORTCUT( MODULES_PRIVATE ),

    PEOPLE_SEARCH( MODULES_PRIVATE ),
    PEOPLE_SEARCH_SETTINGS( PEOPLE_SEARCH ),
    PEOPLE_SEARCH_PROFILE( PEOPLE_SEARCH ),

    HELPDESK( MODULES_PRIVATE ),
    HELPDESK_PROFILE( HELPDESK ),

    HELPDESK_BASE( HELPDESK_PROFILE ),
    HELPDESK_VERIFICATION( HELPDESK_PROFILE ),
    HELPDESK_OPTIONS( HELPDESK_PROFILE ),

    HELPDESK_SETTINGS( HELPDESK ),

    OTP_SETUP( MODULES_PRIVATE ),
    OTP_PROFILE( OTP_SETUP ),
    OTP_SETTINGS( OTP_SETUP ),

    DELETE_ACCOUNT( MODULES_PRIVATE ),
    DELETE_ACCOUNT_SETTINGS( DELETE_ACCOUNT ),
    DELETE_ACCOUNT_PROFILE( DELETE_ACCOUNT ),

    INTERNAL( SETTINGS ),;

    private static final Comparator<PwmSettingCategory> MENU_LOCATION_COMPARATOR = Comparator.comparing(
            ( pwmSettingCategory ) -> pwmSettingCategory.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ) );

    private static final Supplier<List<PwmSettingCategory>> SORTED_VALUES = new LazySupplier<>( () -> Collections.unmodifiableList( Arrays.stream( values() )
            .sorted( MENU_LOCATION_COMPARATOR )
            .collect( Collectors.toList() ) ) );

    private final PwmSettingCategory parent;

    private final transient Supplier<Optional<PwmSetting>> profileSetting = new LazySupplier<>( () -> DataReader.readProfileSettingFromXml( this, true ) );
    private final transient Supplier<Integer> level = new LazySupplier<>( () -> DataReader.readLevel( this ) );
    private final transient Supplier<Boolean> hidden = new LazySupplier<>( () -> DataReader.readHidden( this ) );
    private final transient Supplier<Boolean> isTopLevelProfile = new LazySupplier<>( () -> DataReader.readIsTopLevelProfile( this ) );
    private final transient Supplier<String> defaultLocaleLabel = new LazySupplier<>( () -> DataReader.readLabel( this, PwmConstants.DEFAULT_LOCALE ) );
    private final transient Supplier<String> defaultLocaleDescription = new LazySupplier<>( () -> DataReader.readDescription( this, PwmConstants.DEFAULT_LOCALE ) );
    private final transient Supplier<PwmSettingScope> scope = new LazySupplier<>( () -> DataReader.readScope( this ) );
    private final transient Supplier<Set<PwmSettingCategory>> children = new LazySupplier<>( () -> DataReader.readChildren( this ) );
    private final transient Supplier<Set<PwmSetting>> settings = new LazySupplier<>( () -> DataReader.readSettings( this ) );

    PwmSettingCategory( final PwmSettingCategory parent )
    {
        this.parent = parent;
    }

    public PwmSettingCategory getParent( )
    {
        return parent;
    }

    public String getKey( )
    {
        return this.toString();
    }

    public Optional<PwmSetting> getProfileSetting( )
    {
        return profileSetting.get();
    }

    public boolean hasProfiles( )
    {
        return getProfileSetting().isPresent();
    }

    public boolean isTopLevelProfile( )
    {
        return isTopLevelProfile.get();
    }

    public String getLabel( final Locale locale )
    {
        if ( PwmConstants.DEFAULT_LOCALE.equals( locale ) )
        {
            return defaultLocaleLabel.get();
        }

        return DataReader.readLabel( this, locale );
    }

    public String getDescription( final Locale locale )
    {
        if ( PwmConstants.DEFAULT_LOCALE.equals( locale ) )
        {
            return defaultLocaleDescription.get();
        }

        return DataReader.readDescription( this, locale );
    }

    public int getLevel( )
    {
        return level.get();
    }

    public boolean isHidden( )
    {
        return hidden.get();
    }

    public boolean isTopCategory( )
    {
        return getParent() == null;
    }

    public PwmSettingScope getScope()
    {
        return scope.get();
    }

    public boolean hasChildren()
    {
        return !getChildren().isEmpty();
    }

    public Set<PwmSettingCategory> getChildren( )
    {
        return children.get();
    }

    public Set<PwmSetting> getSettings( )
    {
        return settings.get();
    }

    public String toMenuLocationDebug(
            final String profileID,
            final Locale locale
    )
    {
        return toMenuLocationDebug( this, profileID, locale );
    }

    private static String toMenuLocationDebug(
            final PwmSettingCategory category,
            final String profileID,
            final Locale locale
    )
    {
        final String parentValue = category.getParent() == null
                ? ""
                : toMenuLocationDebug( category.getParent(), profileID, locale );

        final String separator = LocaleHelper.getLocalizedMessage( locale, Config.Display_SettingNavigationSeparator, null );
        final StringBuilder sb = new StringBuilder();

        if ( !parentValue.isEmpty() )
        {
            sb.append( parentValue );
            sb.append( separator );
        }

        sb.append( category.getLabel( locale ) );

        if ( category.isTopLevelProfile() )
        {
            sb.append( separator );
            if ( profileID != null )
            {
                sb.append( profileID );
            }
            else
            {
                sb.append( LocaleHelper.getLocalizedMessage( locale, Config.Display_SettingNavigationNullProfile, null ) );
            }
        }

        return sb.toString();
    }

    public static List<PwmSettingCategory> sortedValues( )
    {
        return SORTED_VALUES.get();
    }

    public static List<PwmSettingCategory> valuesForReferenceDoc()
    {
        final List<PwmSettingCategory> values = sortedValues().stream()
                .filter( ( category ) -> !category.isHidden() )
                .filter( ( category ) -> !category.getSettings().isEmpty() )
                .collect( Collectors.toList( ) );

        return Collections.unmodifiableList( values );
    }

    public static List<PwmSettingCategory> associatedProfileCategories( final PwmSettingCategory inputCategory )
    {
        if ( inputCategory == null || !inputCategory.hasProfiles() )
        {
            return Collections.emptyList();
        }

        final List<PwmSettingCategory> returnValues = new ArrayList<>();

        PwmSettingCategory topLevelCategory = inputCategory;
        while ( !topLevelCategory.isTopLevelProfile() )
        {
            topLevelCategory = topLevelCategory.getParent();
        }
        returnValues.add( topLevelCategory );
        returnValues.addAll( topLevelCategory.getChildren() );

        return Collections.unmodifiableList( returnValues );
    }

    public static Optional<PwmSettingCategory> forKey( final String key )
    {
        return sortedValues().stream()
                .filter( loopValue -> loopValue.getKey().equals( key ) )
                .findFirst();
    }

    public static Optional<PwmSettingCategory> forProfileSetting( final PwmSetting setting )
    {
        for ( final PwmSettingCategory loopCategory : sortedValues() )
        {
            if ( loopCategory.hasProfiles() )
            {
                final Optional<PwmSetting> profileSetting = loopCategory.getProfileSetting();
                if ( profileSetting.isPresent() && profileSetting.get() == setting )
                {
                    return Optional.of( loopCategory );
                }
            }
        }

        return Optional.empty();
    }

    private static class DataReader
    {
        private static Optional<PwmSetting> readProfileSettingFromXml( final PwmSettingCategory category, final boolean nested )
        {
            PwmSettingCategory nextCategory = category;
            while ( nextCategory != null )
            {
                final XmlElement categoryElement = PwmSettingXml.readCategoryXml( nextCategory );
                final Optional<XmlElement> profileElement = categoryElement.getChild( "profile" );
                if ( profileElement.isPresent() )
                {
                    final String settingKey = profileElement.get().getAttributeValue( "setting" );
                    if ( settingKey != null )
                    {
                        return PwmSetting.forKey( settingKey );
                    }
                }
                if ( nested )
                {
                    nextCategory = nextCategory.getParent();
                }
                else
                {
                    nextCategory = null;
                }
            }

            return Optional.empty();
        }

        private static int readLevel( final PwmSettingCategory category )
        {
            final XmlElement settingElement = PwmSettingXml.readCategoryXml( category );
            final String levelAttribute = settingElement.getAttributeValue( PwmSettingXml.XML_ELEMENT_LEVEL );
            return levelAttribute != null ? Integer.parseInt( levelAttribute ) : 0;
        }

        private static PwmSettingScope readScope( final PwmSettingCategory category )
        {
            final String attributeValue = readAttributeFromCategoryOrParent( category, PwmSettingXml.XML_ELEMENT_SCOPE );
            return JavaHelper.readEnumFromString( PwmSettingScope.class, attributeValue ).orElseThrow( () -> new IllegalStateException(
                    "unable to parse value for PwmSettingCategory '" + category + "' scope attribute" ) );
        }

        private static boolean readHidden( final PwmSettingCategory category )
        {
            final String attributeValue = readAttributeFromCategoryOrParent( category, PwmSettingXml.XML_ELEMENT_HIDDEN );
            return "true".equalsIgnoreCase( attributeValue );
        }

        private static String readAttributeFromCategoryOrParent(
                final PwmSettingCategory category,
                final String attribute
        )
        {
            PwmSettingCategory nextCategory = category;
            while ( nextCategory != null )
            {
                final XmlElement settingElement = PwmSettingXml.readCategoryXml( category );
                final String attributeValue = settingElement.getAttributeValue( attribute );
                if ( !StringUtil.isEmpty( attributeValue ) )
                {
                    return attributeValue;
                }
                nextCategory = nextCategory.getParent();
            }
            throw new IllegalStateException( "can't read attribute '"
                    + attribute + "' from category '" + category + "' or ancestor." );
        }

        private static boolean readIsTopLevelProfile( final PwmSettingCategory category )
        {
            return readProfileSettingFromXml( category, false ).isPresent();
        }

        private static String readLabel( final PwmSettingCategory category, final Locale locale )
        {
            return readStringProperty( password.pwm.i18n.PwmSetting.CATEGORY_LABEL_PREFIX + category.getKey(), locale );
        }

        private static String readDescription( final PwmSettingCategory category, final Locale locale )
        {
            return readStringProperty( password.pwm.i18n.PwmSetting.CATEGORY_DESCRIPTION_PREFIX + category.getKey(), locale );
        }

        private static String readStringProperty( final String key, final Locale locale )
        {
            final String storedText = LocaleHelper.getLocalizedMessage( locale, key, null, password.pwm.i18n.PwmSetting.class );
            final MacroRequest macroRequest = MacroRequest.forStatic();
            return macroRequest.expandMacros( storedText );
        }

        public static Set<PwmSettingCategory> readChildren( final PwmSettingCategory category )
        {
            final Set<PwmSettingCategory> categories = Arrays.stream( PwmSettingCategory.values() )
                    .filter( ( loopCategory ) -> loopCategory.getParent() == category )
                    .collect( Collectors.toSet() );
            return Collections.unmodifiableSet( JavaHelper.copiedEnumSet( categories, PwmSettingCategory.class ) );
        }

        public static Set<PwmSetting> readSettings( final PwmSettingCategory category )
        {
            final Set<PwmSetting> settings = Arrays.stream( PwmSetting.values() )
                    .filter( ( setting ) -> setting.getCategory() == category )
                    .collect( Collectors.toSet() );
            return Collections.unmodifiableSet( JavaHelper.copiedEnumSet( settings, PwmSetting.class ) );
        }
    }
}

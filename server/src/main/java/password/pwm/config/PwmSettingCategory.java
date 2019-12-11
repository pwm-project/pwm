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

import password.pwm.i18n.Config;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.XmlElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

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
    CHANGE_PASSWORD( MODULES_PRIVATE ),
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


    private static List<PwmSettingCategory> cachedSortedSettings;

    private final PwmSettingCategory parent;

    private transient Supplier<PwmSetting> profileSetting;
    private transient Supplier<Integer> level;
    private transient Supplier<Boolean> hidden;
    private transient Supplier<Boolean> isTopLevelProfile;


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

    public password.pwm.config.PwmSetting getProfileSetting( )
    {
        if ( profileSetting == null )
        {
            final PwmSetting setting = readProfileSettingFromXml( true );
            profileSetting = ( ) -> setting;
        }
        return profileSetting.get();
    }

    public boolean hasProfiles( )
    {
        return getProfileSetting() != null;
    }

    public boolean isTopLevelProfile( )
    {
        if ( isTopLevelProfile == null )
        {
            final boolean output = readProfileSettingFromXml( false ) != null;
            isTopLevelProfile = ( ) -> output;
        }
        return isTopLevelProfile.get();
    }

    public String getLabel( final Locale locale )
    {
        final String key = password.pwm.i18n.PwmSetting.CATEGORY_LABEL_PREFIX + this.getKey();
        return LocaleHelper.getLocalizedMessage( locale, key, null, password.pwm.i18n.PwmSetting.class );
    }

    public String getDescription( final Locale locale )
    {
        final String key = password.pwm.i18n.PwmSetting.CATEGORY_DESCRIPTION_PREFIX + this.getKey();
        return LocaleHelper.getLocalizedMessage( locale, key, null, password.pwm.i18n.PwmSetting.class );
    }

    public int getLevel( )
    {
        if ( level == null )
        {
            final XmlElement settingElement = PwmSettingXml.readCategoryXml( this );
            final String levelAttribute = settingElement.getAttributeValue( "level" );
            final int output = levelAttribute != null ? Integer.parseInt( levelAttribute ) : 0;
            level = ( ) -> output;
        }
        return level.get();
    }

    public boolean isHidden( )
    {
        if ( hidden == null )
        {
            final XmlElement settingElement = PwmSettingXml.readCategoryXml( this );
            final String hiddenElement = settingElement.getAttributeValue( "hidden" );
            if ( hiddenElement != null && "true".equalsIgnoreCase( hiddenElement ) )
            {
                hidden = () -> true;
            }
            else
            {
                for ( final PwmSettingCategory parentCategory : getParents() )
                {
                    if ( parentCategory.isHidden() )
                    {
                        hidden = () -> true;
                    }
                }
            }
            if ( hidden == null )
            {
                hidden = () -> false;
            }
        }
        return hidden.get();
    }

    public boolean isTopCategory( )
    {
        return getParent() == null;
    }

    public Collection<PwmSettingCategory> getParents( )
    {
        final ArrayList<PwmSettingCategory> returnObj = new ArrayList<>();
        PwmSettingCategory currentCategory = this.getParent();
        while ( currentCategory != null )
        {
            returnObj.add( 0, currentCategory );
            currentCategory = currentCategory.getParent();
        }
        return returnObj;
    }

    public Collection<PwmSettingCategory> getChildCategories( )
    {
        final ArrayList<PwmSettingCategory> returnObj = new ArrayList<>();
        for ( final PwmSettingCategory category : values() )
        {
            if ( this == category.getParent() )
            {
                returnObj.add( category );
            }
        }
        return returnObj;
    }

    private password.pwm.config.PwmSetting readProfileSettingFromXml( final boolean nested )
    {
        PwmSettingCategory nextCategory = this;
        while ( nextCategory != null )
        {
            final XmlElement categoryElement = PwmSettingXml.readCategoryXml( nextCategory );
            final Optional<XmlElement> profileElement = categoryElement.getChild( "profile" );
            if ( profileElement.isPresent() )
            {
                final String settingKey = profileElement.get().getAttributeValue( "setting" );
                if ( settingKey != null )
                {
                    return password.pwm.config.PwmSetting.forKey( settingKey );
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

        return null;
    }

    public List<PwmSetting> getSettings( )
    {
        final List<password.pwm.config.PwmSetting> returnList = new ArrayList<>();
        for ( final password.pwm.config.PwmSetting setting : password.pwm.config.PwmSetting.values() )
        {
            if ( setting.getCategory() == this )
            {
                returnList.add( setting );
            }
        }
        return Collections.unmodifiableList( returnList );
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

    public static List<PwmSettingCategory> sortedValues( final Locale locale )
    {
        if ( cachedSortedSettings == null )
        {
            // prevents dupes from being eliminated;
            int counter = 0;

            final Map<String, PwmSettingCategory> sortedCategories = new TreeMap<>();
            for ( final PwmSettingCategory category : PwmSettingCategory.values() )
            {
                final String sortValue = category.toMenuLocationDebug( null, locale ) + ( counter++ );
                sortedCategories.put( sortValue, category );
            }
            cachedSortedSettings = Collections.unmodifiableList( new ArrayList<>( sortedCategories.values() ) );
        }
        return cachedSortedSettings;
    }

    public static List<PwmSettingCategory> valuesForReferenceDoc( final Locale locale )
    {
        final List<PwmSettingCategory> values = new ArrayList<>( sortedValues( locale ) );
        for ( final Iterator<PwmSettingCategory> iterator = values.iterator(); iterator.hasNext(); )
        {
            final PwmSettingCategory category = iterator.next();
            if ( category.isHidden() )
            {
                iterator.remove();
            }
            else if ( category.getSettings().isEmpty() )
            {
                iterator.remove();
            }
        }
        return Collections.unmodifiableList( values );
    }

    public static Collection<PwmSettingCategory> associatedProfileCategories( final PwmSettingCategory inputCategory )
    {
        final Collection<PwmSettingCategory> returnValues = new ArrayList<>();
        if ( inputCategory != null && inputCategory.hasProfiles() )
        {
            PwmSettingCategory topLevelCategory = inputCategory;
            while ( !topLevelCategory.isTopLevelProfile() )
            {
                topLevelCategory = topLevelCategory.getParent();
            }
            returnValues.add( topLevelCategory );
            returnValues.addAll( topLevelCategory.getChildCategories() );
        }

        return Collections.unmodifiableCollection( returnValues );
    }

    public static PwmSettingCategory forKey( final String key )
    {
        return Arrays.stream( values() )
                .filter( loopValue -> loopValue.getKey().equals( key ) )
                .findFirst()
                .orElse( null );
    }
}

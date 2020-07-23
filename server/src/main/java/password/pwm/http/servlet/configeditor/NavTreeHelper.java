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

package password.pwm.http.servlet.configeditor;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.i18n.Config;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

class NavTreeHelper
{
    static Set<String> determineModifiedKeysSettings(
            final PwmLocaleBundle bundle,
            final Configuration config,
            final StoredConfiguration storedConfiguration
    )
    {
        final Set<String> modifiedKeys = new TreeSet<>();
        for ( final String key : bundle.getDisplayKeys() )
        {
            final Map<String, String> storedBundle = storedConfiguration.readLocaleBundleMap( bundle, key );
            if ( !storedBundle.isEmpty() )
            {
                for ( final Locale locale : config.getKnownLocales() )
                {
                    final ResourceBundle defaultBundle = ResourceBundle.getBundle( bundle.getTheClass().getName(), locale );
                    final String localeKeyString = PwmConstants.DEFAULT_LOCALE.toString().equals( locale.toString() ) ? "" : locale.toString();
                    if ( storedBundle.containsKey( localeKeyString ) )
                    {
                        final String value = storedBundle.get( localeKeyString );
                        if ( value != null && !value.equals( defaultBundle.getString( key ) ) )
                        {
                            modifiedKeys.add( key );
                        }
                    }
                }
            }
        }
        return modifiedKeys;
    }

    static boolean categoryMatcher(
            final PwmApplication pwmApplication,
            final PwmSettingCategory category,
            final StoredConfiguration storedConfiguration,
            final boolean modifiedOnly,
            final int minLevel,
            final String text
    )
    {
        if ( category.isHidden() )
        {
            return false;
        }

        if ( category == PwmSettingCategory.HTTPS_SERVER )
        {
            if ( !pwmApplication.getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.ManageHttps ) )
            {
                return false;
            }
        }

        for ( final PwmSettingCategory childCategory : category.getChildCategories() )
        {
            if ( categoryMatcher( pwmApplication, childCategory, storedConfiguration, modifiedOnly, minLevel, text ) )
            {
                return true;
            }
        }

        if ( category.hasProfiles() )
        {
            final List<String> profileIDs = storedConfiguration.profilesForSetting( category.getProfileSetting() );
            if ( profileIDs == null || profileIDs.isEmpty() )
            {
                return true;
            }
            for ( final String profileID : profileIDs )
            {
                for ( final PwmSetting setting : category.getSettings() )
                {
                    if ( settingMatches( storedConfiguration, setting, profileID, modifiedOnly, minLevel, text ) )
                    {
                        return true;
                    }
                }
            }

        }
        else
        {
            for ( final PwmSetting setting : category.getSettings() )
            {
                if ( settingMatches( storedConfiguration, setting, null, modifiedOnly, minLevel, text ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

    static List<PwmSettingCategory> filteredCategories(
            final PwmApplication pwmApplication,
            final StoredConfiguration storedConfiguration,
            final Locale locale,
            final boolean modifiedSettingsOnly,
            final double level,
            final String filterText
    )
    {
        final List<PwmSettingCategory> returnList = new ArrayList<>();

        for ( final PwmSettingCategory loopCategory : PwmSettingCategory.sortedValues( locale ) )
        {
            if ( NavTreeHelper.categoryMatcher(
                    pwmApplication,
                    loopCategory,
                    storedConfiguration,
                    modifiedSettingsOnly,
                    ( int ) level,
                    filterText
            ) )
            {
                returnList.add( loopCategory );
            }
        }

        return returnList;
    }

    /**
     * Produces a collection of {@code NavTreeItem}.
     */
    static List<NavTreeItem> makeSettingNavItems(
            final List<PwmSettingCategory> categories,
            final StoredConfiguration storedConfiguration,
            final Locale locale
    )
    {
        final List<NavTreeItem> navigationData = new ArrayList<>();

        for ( final PwmSettingCategory loopCategory : categories )
        {
            if ( !loopCategory.hasProfiles() )
            {
                // regular category, so output a standard nav tree item
                navigationData.add( navTreeItemForCategory( loopCategory, locale, null ) );
            }
            else
            {
                final List<String> profiles = StoredConfigurationUtil.profilesForCategory( loopCategory, storedConfiguration );
                final boolean topLevelProfileParent = loopCategory.isTopLevelProfile();

                if ( topLevelProfileParent )
                {
                    // edit profile option
                    navigationData.add( navTreeItemForCategory( loopCategory, locale, null ) );

                    {
                        final NavTreeItem profileEditorInfo = new NavTreeItem();
                        profileEditorInfo.setId( loopCategory.getKey() + "-EDITOR" );
                        final String editItemName = LocaleHelper.getLocalizedMessage( locale, Config.Label_ProfileListEditMenuItem, null );
                        profileEditorInfo.setName( editItemName );
                        profileEditorInfo.setType( NavTreeHelper.NavItemType.profileDefinition );
                        profileEditorInfo.setProfileSetting( loopCategory.getProfileSetting().getKey() );
                        profileEditorInfo.setParent( loopCategory.getKey() );
                        navigationData.add( profileEditorInfo );
                    }

                    for ( final String profileId : profiles )
                    {
                        final NavTreeItem profileInfo = navTreeItemForCategory( loopCategory, locale, profileId );
                        profileInfo.setName( profileId.isEmpty() ? "Default" : profileId );
                        profileInfo.setId( "profile-" + loopCategory.getKey() + "-" + profileId );
                        profileInfo.setParent( loopCategory.getKey() );
                        if ( loopCategory.getChildCategories().isEmpty() )
                        {
                            profileInfo.setType( NavItemType.category );
                        }
                        else
                        {
                            profileInfo.setType( NavItemType.navigation );
                        }

                        navigationData.add( profileInfo );
                    }
                }
                else
                {
                    for ( final String profileId : profiles )
                    {
                        navigationData.add( navTreeItemForCategory( loopCategory, locale, profileId ) );
                    }
                }
            }
        }

        return navigationData;
    }

    private static NavTreeItem navTreeItemForCategory(
            final PwmSettingCategory category,
            final Locale locale,
            final String profileId
    )
    {
        final NavTreeItem categoryItem = new NavTreeItem();
        categoryItem.setId( category.getKey() + ( profileId != null ? "-" + profileId : "" ) );
        categoryItem.setName( category.getLabel( locale ) );
        categoryItem.setCategory( category.getKey() );
        if ( category.getParent() != null )
        {
            if ( profileId != null )
            {
                categoryItem.setParent( "profile-" + category.getParent().getKey() + "-" + profileId );
            }
            else
            {
                categoryItem.setParent( category.getParent().getKey() );
            }
        }
        else
        {
            categoryItem.setParent( "ROOT" );
        }
        if ( category.getChildCategories().isEmpty() && !category.isTopLevelProfile() )
        {
            categoryItem.setType( NavTreeHelper.NavItemType.category );
        }
        else
        {
            categoryItem.setType( NavTreeHelper.NavItemType.navigation );
        }
        if ( profileId != null )
        {
            categoryItem.setProfile( profileId );
        }
        categoryItem.setMenuLocation( category.toMenuLocationDebug( profileId, locale ) );
        return categoryItem;
    }

    private static boolean settingMatches(
            final StoredConfiguration storedConfiguration,
            final PwmSetting setting,
            final String profileID,
            final boolean modifiedOnly,
            final int level,
            final String text
    )
    {
        if ( setting.isHidden() )
        {
            return false;
        }

        if ( modifiedOnly )
        {
            if ( storedConfiguration.isDefaultValue( setting, profileID ) )
            {
                return false;
            }
        }

        if ( level > 0 && setting.getLevel() > level )
        {
            return false;
        }


        if ( text == null || text.isEmpty() )
        {
            return true;
        }
        else
        {
            final StoredValue storedValue = storedConfiguration.readSetting( setting, profileID );
            for ( final String term : StringUtil.whitespaceSplit( text ) )
            {
                if ( StoredConfigurationUtil.matchSetting( storedConfiguration, setting, storedValue, term, PwmConstants.DEFAULT_LOCALE ) )
                {
                    return true;
                }
            }
        }

        return false;
    }

    static void moveNavItemToTopOfList( final String categoryID, final List<NavTreeItem> navigationData )
    {
        {
            // put templates on top
            NavTreeItem templateEntry = null;
            for ( final NavTreeItem entry : navigationData )
            {
                if ( categoryID.equals( entry.getId() ) )
                {
                    templateEntry = entry;
                }
            }
            if ( templateEntry != null )
            {
                navigationData.remove( templateEntry );
                navigationData.add( 0, templateEntry );
            }
        }
    }

    enum NavItemType
    {
        category,
        navigation,
        displayText,
        profile,
        profileDefinition,
    }

}

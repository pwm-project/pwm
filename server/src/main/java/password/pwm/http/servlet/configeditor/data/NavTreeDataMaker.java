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

package password.pwm.http.servlet.configeditor.data;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.StoredValue;
import password.pwm.i18n.Config;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Utility class for generating {@link NavTreeItem}s suitable for display in the
 * config editor UI.
 */
public class NavTreeDataMaker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( NavTreeDataMaker.class );
    private static final String ROOT_NODE_ID = "ROOT";

    public static List<NavTreeItem> makeNavTreeItems(
            final PwmApplication pwmApplication,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final Instant startTime = Instant.now();
        final ArrayList<NavTreeItem> navigationData = new ArrayList<>();

        // root node
        navigationData.add( makeRootNode() );

        // add setting nodes
        navigationData.addAll( makeSettingNavItems( pwmApplication, storedConfiguration, navTreeSettings ) );

        // add display text nodes
        navigationData.addAll( makeDisplayTextNavItems( pwmApplication, storedConfiguration, navTreeSettings ) );

        NavTreeDataMaker.moveNavItemToTopOfList( PwmSettingCategory.NOTES.toString(), navigationData );
        NavTreeDataMaker.moveNavItemToTopOfList( PwmSettingCategory.TEMPLATES.toString(), navigationData );
        LOGGER.trace( () -> "generated " + navigationData.size()
                + " navTreeItems for display menu with settings"
                + JsonUtil.serialize( navTreeSettings ),
                () -> TimeDuration.fromCurrent( startTime ) );
        return Collections.unmodifiableList( navigationData );
    }

    private static NavTreeItem makeRootNode()
    {
        return NavTreeItem.builder()
                .id( ROOT_NODE_ID )
                .name( ROOT_NODE_ID )
                .build();
    }

    private static List<NavTreeItem> makeDisplayTextNavItems(
            final PwmApplication pwmApplication,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final ArrayList<NavTreeItem> navigationData = new ArrayList<>();

        final int level = navTreeSettings.getLevel();
        final boolean modifiedSettingsOnly = navTreeSettings.isModifiedSettingsOnly();

        boolean includeDisplayText = false;
        if ( level >= 1 )
        {
            for ( final PwmLocaleBundle localeBundle : PwmLocaleBundle.values() )
            {
                if ( !localeBundle.isAdminOnly() )
                {
                    final List<String> modifiedKeys = modifiedSettingsOnly
                            ? new ArrayList<>( NavTreeDataMaker.determineModifiedDisplayKeysSettings( localeBundle, pwmApplication.getConfig(), storedConfiguration ) )
                            : Collections.emptyList();

                    if ( !modifiedSettingsOnly || !modifiedKeys.isEmpty() )
                    {
                        final List<String> outputKeys = modifiedSettingsOnly
                                ? modifiedKeys
                                : new ArrayList<>( localeBundle.getDisplayKeys() );
                        Collections.sort( outputKeys );

                        final NavTreeItem categoryInfo = NavTreeItem.builder()
                                .id( localeBundle.toString() )
                                .name( localeBundle.getTheClass().getSimpleName() )
                                .parent( "DISPLAY_TEXT" )
                                .type ( NavTreeItem.NavItemType.displayText )
                                .keys( outputKeys )
                                .build();
                        navigationData.add( categoryInfo );
                        includeDisplayText = true;
                    }
                }
            }
        }

        if ( includeDisplayText )
        {
            final NavTreeItem categoryInfo = NavTreeItem.builder()
                    .id( "DISPLAY_TEXT" )
                    .name( "Display Text" )
                    .type( NavTreeItem.NavItemType.navigation )
                    .parent( ROOT_NODE_ID )
                    .build();
            navigationData.add( categoryInfo );
        }

        return Collections.unmodifiableList( navigationData );
    }


    private static List<String> determineModifiedDisplayKeysSettings(
            final PwmLocaleBundle bundle,
            final Configuration config,
            final StoredConfiguration storedConfiguration
    )
    {
        final List<String> modifiedKeys = new ArrayList<>();
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
        return Collections.unmodifiableList( modifiedKeys );
    }

    /**
     * Produces a collection of {@code NavTreeItem}.
     */
    private static List<NavTreeItem> makeSettingNavItems(
            final PwmApplication pwmApplication,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final Locale locale = navTreeSettings.getLocale();
        final List<NavTreeItem> navigationData = new ArrayList<>();

        for ( final PwmSettingCategory loopCategory : PwmSettingCategory.sortedValues() )
        {
            if ( !loopCategory.hasProfiles() )
            {
                // regular category, so output a standard nav tree item
                if ( categoryMatcher( pwmApplication, loopCategory, null, storedConfiguration, navTreeSettings ) )
                {
                    navigationData.add( navTreeItemForCategory( loopCategory, locale, null ) );
                }
            }
            else
            {
                final List<String> profiles = StoredConfigurationUtil.profilesForCategory( loopCategory, storedConfiguration );

                if ( loopCategory.isTopLevelProfile() )
                {
                    // edit profile option
                    navigationData.add( navTreeItemForCategory( loopCategory, locale, null ) );

                    {
                        final String editItemName = LocaleHelper.getLocalizedMessage( locale, Config.Label_ProfileListEditMenuItem, null );
                        final PwmSetting profileSetting = loopCategory.getProfileSetting().orElseThrow( IllegalStateException::new );

                        final NavTreeItem profileEditorInfo = NavTreeItem.builder()
                                .id( loopCategory.getKey() + "-EDITOR" )
                                .name( editItemName )
                                .type(  NavTreeItem.NavItemType.profileDefinition )
                                .profileSetting( profileSetting.getKey() )
                                .parent( loopCategory.getKey() )
                                .build();
                        navigationData.add( profileEditorInfo );
                    }

                    for ( final String profileId : profiles )
                    {
                        final NavTreeItem.NavItemType type = !loopCategory.hasChildren()
                                ? NavTreeItem.NavItemType.category
                                : NavTreeItem.NavItemType.navigation;

                        final NavTreeItem profileInfo = navTreeItemForCategory( loopCategory, locale, profileId ).toBuilder()
                                .name(  profileId.isEmpty() ? "Default" : profileId )
                                .id( "profile-" + loopCategory.getKey() + "-" + profileId )
                                .parent( loopCategory.getKey() )
                                .type( type )
                                .build();

                        navigationData.add( profileInfo );
                    }
                }
                else
                {
                    for ( final String profileId : profiles )
                    {
                        if ( categoryMatcher( pwmApplication, loopCategory, profileId, storedConfiguration, navTreeSettings ) )
                        {
                            navigationData.add( navTreeItemForCategory( loopCategory, locale, profileId ) );
                        }
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
        final String parent = category.getParent() != null
                ? ( profileId != null ? "profile-" + category.getParent().getKey() + "-" + profileId : category.getParent().getKey() )
                : ROOT_NODE_ID;

        final NavTreeItem.NavItemType type = !category.hasChildren() && !category.isTopLevelProfile()
                ? NavTreeItem.NavItemType.category
                : NavTreeItem.NavItemType.navigation;

        return NavTreeItem.builder()
                .id( category.getKey() + ( profileId != null ? "-" + profileId : "" ) )
                .name( category.getLabel( locale ) )
                .category( category.getKey() )
                .parent( parent )
                .type( type )
                .profile( profileId )
                .menuLocation( category.toMenuLocationDebug( profileId, locale ) )
                .build();
    }

    private static boolean categoryMatcher(
            final PwmApplication pwmApplication,
            final PwmSettingCategory category,
            final String profile,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
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

        for ( final PwmSettingCategory childCategory : category.getChildren() )
        {
            if ( categoryMatcher( pwmApplication, childCategory, profile, storedConfiguration, navTreeSettings ) )
            {
                return true;
            }
        }

        for ( final PwmSetting setting : category.getSettings() )
        {
            if ( settingMatcher( storedConfiguration, setting, profile, navTreeSettings ) )
            {
                return true;
            }
        }

        return false;
    }

    private static boolean settingMatcher(
            final StoredConfiguration storedConfiguration,
            final PwmSetting setting,
            final String profileID,
            final NavTreeSettings navTreeSettings
    )
    {
        if ( setting.isHidden() )
        {
            return false;
        }

        if ( navTreeSettings.isModifiedSettingsOnly() )
        {
            if ( storedConfiguration.isDefaultValue( setting, profileID ) )
            {
                return false;
            }
        }

        final int level = navTreeSettings.getLevel();
        if ( setting.getLevel() > level )
        {
            return false;
        }

        if ( StringUtil.isEmpty( navTreeSettings.getFilterText() ) )
        {
            return true;
        }
        else
        {
            final StoredValue storedValue = storedConfiguration.readSetting( setting, profileID );
            for ( final String term : StringUtil.whitespaceSplit( navTreeSettings.getFilterText() ) )
            {
                if ( StoredConfigurationUtil.matchSetting( storedConfiguration, setting, storedValue, term, PwmConstants.DEFAULT_LOCALE ) )
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static void moveNavItemToTopOfList( final String categoryID, final List<NavTreeItem> navigationData )
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

}

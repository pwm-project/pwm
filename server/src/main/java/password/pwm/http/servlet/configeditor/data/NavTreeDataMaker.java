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

import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.PwmEnvironment;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingScope;
import password.pwm.config.stored.ConfigSearchMachine;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.StoredValue;
import password.pwm.http.servlet.configeditor.DomainManageMode;
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

    private static final String DISPLAY_TEXT_ID = "DISPLAY_TEXT";
    private static final String DISPLAY_TEXT_NAME = "Display Text";

    public static List<NavTreeItem> makeNavTreeItems(
            final PwmDomain pwmDomain,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final Instant startTime = Instant.now();
        final ArrayList<NavTreeItem> navigationData = new ArrayList<>();

        // root node
        navigationData.add( makeRootNode() );

        // add setting nodes
        navigationData.addAll( makeSettingNavItems( pwmDomain, storedConfiguration, navTreeSettings ) );

        // add display text nodes
        navigationData.addAll( makeDisplayTextNavItems( pwmDomain, storedConfiguration, navTreeSettings ) );

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
            final PwmDomain pwmDomain,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final ArrayList<NavTreeItem> navigationData = new ArrayList<>();

        final int level = navTreeSettings.getLevel();
        final boolean modifiedSettingsOnly = navTreeSettings.isModifiedSettingsOnly();

        if ( navTreeSettings.getDomainManageMode() != DomainManageMode.domain )
        {
            return Collections.emptyList();
        }

        boolean includeDisplayText = false;
        if ( level >= 1 )
        {
            for ( final PwmLocaleBundle localeBundle : PwmLocaleBundle.values() )
            {
                if ( !localeBundle.isAdminOnly() )
                {
                    final List<String> modifiedKeys = modifiedSettingsOnly
                            ? new ArrayList<>( NavTreeDataMaker.determineModifiedDisplayKeysSettings( localeBundle, pwmDomain.getConfig(), storedConfiguration ) )
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
                                .parent( DISPLAY_TEXT_ID )
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
                    .id( DISPLAY_TEXT_ID )
                    .name( DISPLAY_TEXT_NAME )
                    .type( NavTreeItem.NavItemType.navigation )
                    .parent( ROOT_NODE_ID )
                    .build();
            navigationData.add( categoryInfo );
        }

        return Collections.unmodifiableList( navigationData );
    }


    private static List<String> determineModifiedDisplayKeysSettings(
            final PwmLocaleBundle bundle,
            final DomainConfig config,
            final StoredConfiguration storedConfiguration
    )
    {
        final List<String> modifiedKeys = new ArrayList<>();
        for ( final String key : bundle.getDisplayKeys() )
        {
            final Map<String, String> storedBundle = storedConfiguration.readLocaleBundleMap( bundle, key );
            if ( !storedBundle.isEmpty() )
            {
                for ( final Locale locale : config.getAppConfig().getKnownLocales() )
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
            final PwmDomain pwmDomain,
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
                if ( categoryMatcher( pwmDomain, loopCategory, null, storedConfiguration, navTreeSettings ) )
                {
                    navigationData.add( navTreeItemForCategory( loopCategory, locale, null ) );
                }
            }
            else
            {
                final List<String> profiles = StoredConfigurationUtil.profilesForCategory( pwmDomain.getDomainID(), loopCategory, storedConfiguration );

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
                        if ( categoryMatcher( pwmDomain, loopCategory, profileId, storedConfiguration, navTreeSettings ) )
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
            final PwmDomain pwmDomain,
            final PwmSettingCategory category,
            final String profile,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        if ( category == PwmSettingCategory.HTTPS_SERVER )
        {
            if ( !pwmDomain.getPwmApplication().getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.ManageHttps ) )
            {
                return false;
            }
        }

        for ( final PwmSettingCategory childCategory : category.getChildren() )
        {
            if ( categoryMatcher( pwmDomain, childCategory, profile, storedConfiguration, navTreeSettings ) )
            {
                return true;
            }
        }

        for ( final PwmSetting setting : category.getSettings() )
        {
            if ( settingMatcher( pwmDomain, storedConfiguration, setting, profile, navTreeSettings ) )
            {
                return true;
            }
        }

        return false;
    }

    private static boolean settingMatcher(
            final PwmDomain pwmDomain,
            final StoredConfiguration storedConfiguration,
            final PwmSetting setting,
            final String profileID,
            final NavTreeSettings navTreeSettings
    )
    {
        final StoredConfigKey storedConfigKey = StoredConfigKey.forSetting( setting, profileID, pwmDomain.getDomainID() );
        final boolean valueIsDefault = StoredConfigurationUtil.isDefaultValue( storedConfiguration, storedConfigKey );

        if ( setting.isHidden() && !valueIsDefault )
        {
            return false;
        }

        final PwmSettingCategory settingCategory = setting.getCategory();
        if ( navTreeSettings.getDomainManageMode() == DomainManageMode.system
                && settingCategory.getScope() != PwmSettingScope.SYSTEM )
        {
            return false;
        }
        else if ( navTreeSettings.getDomainManageMode() == DomainManageMode.domain
                && settingCategory.getScope() != PwmSettingScope.DOMAIN )
        {
            return false;
        }

        if ( navTreeSettings.isModifiedSettingsOnly() && valueIsDefault )
        {
            return false;
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
            final StoredValue storedValue = storedConfiguration.readStoredValue( storedConfigKey ).orElseThrow();
            for ( final String term : StringUtil.whitespaceSplit( navTreeSettings.getFilterText() ) )
            {
                if ( ConfigSearchMachine.matchSetting( storedConfiguration, setting, storedValue, term, PwmConstants.DEFAULT_LOCALE ) )
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

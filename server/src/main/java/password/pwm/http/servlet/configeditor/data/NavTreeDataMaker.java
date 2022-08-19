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

package password.pwm.http.servlet.configeditor.data;

import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.PwmSettingScope;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.stored.ConfigSearchMachine;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.StoredValue;
import password.pwm.http.servlet.configeditor.DomainManageMode;
import password.pwm.i18n.Config;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

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
            final DomainID domainID,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final Instant startTime = Instant.now();
        final ArrayList<NavTreeItem> navigationData = new ArrayList<>();

        // root node
        navigationData.add( makeRootNode() );

        // add setting nodes
        navigationData.addAll( makeCategoryNavItems( domainID, storedConfiguration, navTreeSettings ) );

        // add display text nodes
        navigationData.addAll( makeDisplayTextNavItems( domainID, storedConfiguration, navTreeSettings ) );

        NavTreeDataMaker.moveNavItemToTopOfList( PwmSettingCategory.NOTES.toString(), navigationData );
        NavTreeDataMaker.moveNavItemToTopOfList( PwmSettingCategory.TEMPLATES.toString(), navigationData );
        LOGGER.trace( () -> "generated " + navigationData.size()
                        + " navTreeItems for display menu with settings"
                        + JsonFactory.get().serialize( navTreeSettings ),
                TimeDuration.fromCurrent( startTime ) );
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
            final DomainID domainId,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final DomainID domainID = navTreeSettings.getDomainManageMode() == DomainManageMode.domain
                ? domainId
                : DomainID.systemId();

        return makeDisplayTextNavItemsForDomain( domainID, storedConfiguration, navTreeSettings );
    }

    private static List<NavTreeItem> makeDisplayTextNavItemsForDomain(
            final DomainID domainID,
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
                            ? new ArrayList<>( NavTreeDataMaker.determineModifiedDisplayKeysSettings( domainID, localeBundle, storedConfiguration ) )
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
            final DomainID domainID,
            final PwmLocaleBundle bundle,
            final StoredConfiguration storedConfiguration
    )
    {
        final List<Locale> knownLocales = Collections.unmodifiableList( AppConfig.forStoredConfig( storedConfiguration ).getKnownLocales() );
        final List<String> modifiedKeys = new ArrayList<>();
        for ( final String key : bundle.getDisplayKeys() )
        {
            final Map<String, String> storedBundle = storedConfiguration.readLocaleBundleMap( bundle, key, domainID );
            if ( !storedBundle.isEmpty() )
            {
                for ( final Locale locale : knownLocales )
                {
                    final ResourceBundle defaultBundle = ResourceBundle.getBundle( bundle.getTheClass().getName(), locale );
                    final String localeKeyString = PwmConstants.DEFAULT_LOCALE.toString().equals( locale.toString() ) ? "" : locale.toString();

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
    private static List<NavTreeItem> makeCategoryNavItems(
            final DomainID domainId,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        return PwmSettingCategory.sortedValues().stream()
                .filter( loopCategory -> categoryMatcher( domainId, loopCategory, null, storedConfiguration, navTreeSettings ) )
                .flatMap( loopCategory -> navTreeItemsForCategory( loopCategory, domainId, storedConfiguration, navTreeSettings ).stream() )
                .collect( Collectors.toUnmodifiableList() );

    }

    private static List<NavTreeItem> navTreeItemsForCategory(
            final PwmSettingCategory loopCategory,
            final DomainID domainId,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final Locale locale = navTreeSettings.getLocale();

        if ( !loopCategory.hasProfiles() )
        {
            // regular category, so output a standard nav tree item
            return List.of( navTreeItemForCategory( loopCategory, locale, null ) );
        }

        final List<ProfileID> profiles = StoredConfigurationUtil.profilesForCategory( domainId, loopCategory, storedConfiguration );
        if ( loopCategory.isTopLevelProfile() )
        {
            final List<NavTreeItem> navigationData = new ArrayList<>( profiles.size() );

            // edit profile option
            navigationData.add( navTreeItemForCategory( loopCategory, locale, null ) );

            {
                final String editItemName = LocaleHelper.getLocalizedMessage( locale, Config.Label_ProfileListEditMenuItem, null );
                final PwmSetting profileSetting = loopCategory.getProfileSetting().orElseThrow( IllegalStateException::new );

                final NavTreeItem profileEditorInfo = NavTreeItem.builder()
                        .id( loopCategory.getKey() + "-EDITOR" )
                        .name( editItemName )
                        .type( NavTreeItem.NavItemType.profileDefinition )
                        .profileSetting( profileSetting.getKey() )
                        .parent( loopCategory.getKey() )
                        .build();
                navigationData.add( profileEditorInfo );
            }

            for ( final ProfileID profileId : profiles )
            {
                final NavTreeItem.NavItemType type = !loopCategory.hasChildren()
                        ? NavTreeItem.NavItemType.category
                        : NavTreeItem.NavItemType.navigation;

                final NavTreeItem profileInfo = navTreeItemForCategory( loopCategory, locale, profileId ).toBuilder()
                        .name( profileId == null ? "Default" : profileId.stringValue() )
                        .id( "profile-" + loopCategory.getKey() + "-" + profileId )
                        .parent( loopCategory.getKey() )
                        .type( type )
                        .build();

                navigationData.add( profileInfo );
            }

            return Collections.unmodifiableList( navigationData );
        }

        final List<NavTreeItem> navigationData = new ArrayList<>();
        for ( final ProfileID profileId : profiles )
        {
            if ( categoryMatcher( domainId, loopCategory, profileId, storedConfiguration, navTreeSettings ) )
            {
                navigationData.add( navTreeItemForCategory( loopCategory, locale, profileId ) );
            }
        }
        return Collections.unmodifiableList( navigationData );
    }


    private static NavTreeItem navTreeItemForCategory(
            final PwmSettingCategory category,
            final Locale locale,
            final ProfileID profileId
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
                .profile( profileId == null ? null : profileId.stringValue() )
                .menuLocation( category.toMenuLocationDebug( profileId, locale ) )
                .build();
    }

    private static boolean categoryMatcher(
            final DomainID domainID,
            final PwmSettingCategory category,
            final ProfileID profile,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        if ( category == PwmSettingCategory.HTTPS_SERVER )
        {
            if ( !navTreeSettings.isMangeHttps() )
            {
                return false;
            }
        }

        if ( category.isHidden() )
        {
            return false;
        }

        for ( final PwmSettingCategory childCategory : category.getChildren() )
        {
            if ( categoryMatcher( domainID, childCategory, profile, storedConfiguration, navTreeSettings ) )
            {
                return true;
            }
        }

        for ( final PwmSetting setting : category.getSettings() )
        {
            if ( settingMatcher( domainID, storedConfiguration, setting, profile, navTreeSettings ) )
            {
                return true;
            }
        }

        return false;
    }

    static boolean settingMatcher(
            final DomainID domainID,
            final StoredConfiguration storedConfiguration,
            final PwmSetting setting,
            final ProfileID profileID,
            final NavTreeSettings navTreeSettings
    )
    {
        final StoredConfigKey storedConfigKey = StoredConfigKey.forSetting( setting, profileID, domainID );

        if ( setting.getSyntax() == PwmSettingSyntax.PROFILE && !setting.isHidden() && setting.getCategory().getParent().isHidden() )
        {
            return true;
        }

        final boolean valueIsDefault = StoredConfigurationUtil.isDefaultValue( storedConfiguration, storedConfigKey );
        if ( setting.isHidden() && !valueIsDefault && setting.getSyntax() != PwmSettingSyntax.PROFILE )
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

        if ( setting.getFlags().contains( PwmSettingFlag.MultiDomain )
                && ( !( AppConfig.forStoredConfig( storedConfiguration ).isMultiDomain() ) ) )
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
        // put templates on top
        final Optional<NavTreeItem> templateEntry = navigationData.stream()
                .filter( entry -> categoryID.equals( entry.getId() ) )
                .findFirst();

        if ( templateEntry.isPresent() )
        {
            navigationData.remove( templateEntry.get() );
            navigationData.add( 0, templateEntry.get() );
        }
    }
}

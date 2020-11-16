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

import lombok.Value;
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
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Create a list of {@link NavTreeItem} values that represent a navigable tree menu.  The tree menu is displayed
 * for administers in the config editor.
 */
public class NavTreeDataMaker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( NavTreeDataMaker.class );

    private static final String ROOT_ITEM_ID = "ROOT";
    private static final String DISPLAY_TEXT_ITEM_ID = "DISPLAY_TEXT";

    private static final Supplier<NavTreeItem> DISPLAY_TEXT_ITEM = () -> NavTreeItem.builder()
            .id( DISPLAY_TEXT_ITEM_ID )
            .name( "Display Text" )
            .type( NavTreeItem.NavItemType.navigation )
            .parent( ROOT_ITEM_ID )
            .build();

    private static final Supplier<NavTreeItem> ROOT_ITEM = () -> NavTreeItem.builder()
            .id( ROOT_ITEM_ID )
            .name( ROOT_ITEM_ID )
            .build();

    public static List<NavTreeItem> makeNavTreeData(
            final PwmApplication pwmApplication,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final Instant startTime = Instant.now();
        final List<NavTreeItem> navigationData = new ArrayList<>();

        // root node
        navigationData.add( ROOT_ITEM.get() );

        // settings
        navigationData.addAll( NavTreeDataMaker.makeSettingNavItems( pwmApplication, navTreeSettings, storedConfiguration ) );

        boolean includeDisplayText = false;
        if ( navTreeSettings.getLevel() >= 1 )
        {
            final List<NavTreeItem> displayTextItems = makeDisplayBundleNavItems( navTreeSettings, storedConfiguration );
            if ( displayTextItems.isEmpty() )
            {
                includeDisplayText = true;
            }

            navigationData.addAll( displayTextItems );
        }

        if ( includeDisplayText )
        {

            navigationData.add( DISPLAY_TEXT_ITEM.get() );
        }

        NavTreeDataMaker.moveNavItemToTopOfList( PwmSettingCategory.NOTES.toString(), navigationData );
        NavTreeDataMaker.moveNavItemToTopOfList( PwmSettingCategory.TEMPLATES.toString(), navigationData );

        LOGGER.trace( () -> "completed navigation tree data request for " + navigationData.size()  + " items", () -> TimeDuration.fromCurrent( startTime ) );
        return Collections.unmodifiableList( navigationData );
    }

    private static List<NavTreeItem> makeDisplayBundleNavItems(
            final NavTreeSettings navTreeSettings,
            final StoredConfiguration storedConfiguration
    )
    {
        final List<NavTreeItem> navigationData = new ArrayList<>();
        for ( final PwmLocaleBundle localeBundle : PwmLocaleBundle.values() )
        {
            if ( !localeBundle.isAdminOnly() )
            {
                final Set<String> modifiedKeys = new TreeSet<>();
                if ( navTreeSettings.isModifiedSettingsOnly() )
                {
                    modifiedKeys.addAll( NavTreeDataMaker.determineModifiedDisplayBundleSettings( localeBundle, storedConfiguration ) );
                }

                if ( !navTreeSettings.isModifiedSettingsOnly() || !modifiedKeys.isEmpty() )
                {
                    final NavTreeItem categoryInfo = NavTreeItem.builder()
                            .id( localeBundle.toString() )
                            .name( localeBundle.getTheClass().getSimpleName() )
                            .parent( DISPLAY_TEXT_ITEM_ID )
                            .type( NavTreeItem.NavItemType.displayText )
                            .keys( new TreeSet<>( navTreeSettings.isModifiedSettingsOnly() ? modifiedKeys : localeBundle.getDisplayKeys() ) )
                            .build();
                    navigationData.add( categoryInfo );
                }
            }
        }
        return Collections.unmodifiableList( navigationData );
    }

    private static Set<String> determineModifiedDisplayBundleSettings(
            final PwmLocaleBundle bundle,
            final StoredConfiguration storedConfiguration
    )
    {
        final List<Locale> knownLocales = new Configuration( storedConfiguration ).getKnownLocales();
        final Set<String> modifiedKeys = new TreeSet<>();
        for ( final String key : bundle.getDisplayKeys() )
        {
            final Map<String, String> storedBundle = storedConfiguration.readLocaleBundleMap( bundle, key );
            if ( !storedBundle.isEmpty() )
            {
                for ( final Locale locale : knownLocales )
                {
                    final String localeKeyString = PwmConstants.DEFAULT_LOCALE.toString().equals( locale.toString() ) ? "" : locale.toString();
                    if ( storedBundle.containsKey( localeKeyString ) )
                    {
                        final String value = storedBundle.get( localeKeyString );
                        if ( value != null )
                        {
                            final ResourceBundle defaultBundle = ResourceBundle.getBundle( bundle.getTheClass().getName(), locale );
                            if ( !value.equals( defaultBundle.getString( key ) ) )
                            {
                                modifiedKeys.add( key );
                            }
                        }
                    }
                }
            }
        }
        return modifiedKeys;
    }


    private static List<ProfiledCategory> makeProfiledCategories(
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final List<ProfiledCategory> returnList = new ArrayList<>();
        for ( final PwmSettingCategory category : PwmSettingCategory.sortedValues( navTreeSettings.getLocale() ) )
        {
            if ( category.hasProfiles() )
            {
                if ( !category.getParent().hasProfiles() )
                {
                    returnList.add( new ProfiledCategory( category, null, ProfiledCategory.Type.Profile ) );
                }
                else
                {
                    final List<String> profileIDs = StoredConfigurationUtil.profilesForCategory( category, storedConfiguration );
                    for ( final String profileID : profileIDs )
                    {
                        returnList.add( new ProfiledCategory( category, profileID, ProfiledCategory.Type.CategoryWithProfile ) );
                    }
                }
            }
            else
            {
                returnList.add( new ProfiledCategory( category, null, ProfiledCategory.Type.StaticCategory ) );
            }
        }
        return Collections.unmodifiableList( returnList );
    }

    private static List<ProfiledCategory> filteredCategories(
            final PwmApplication pwmApplication,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        return Collections.unmodifiableList( makeProfiledCategories( storedConfiguration, navTreeSettings )
                .stream()
                .filter( pwmSettingCategory -> NavTreeDataMaker.categoryMatcher(
                        pwmApplication,
                        pwmSettingCategory,
                        storedConfiguration,
                        navTreeSettings
                ) )
                .collect( Collectors.toList() ) );
    }

    /**
     * Produces a collection of {@code NavTreeItem}.
     */
    private static List<NavTreeItem> makeSettingNavItems(
            final PwmApplication pwmApplication,
            final NavTreeSettings navTreeSettings,
            final StoredConfiguration storedConfiguration
    )
    {
        final List<ProfiledCategory> profiledCategories = NavTreeDataMaker.filteredCategories(
                pwmApplication,
                storedConfiguration,
                navTreeSettings
        );
        final Locale locale = navTreeSettings.getLocale();
        final List<NavTreeItem> navigationData = new ArrayList<>();
        final Map<PwmSettingCategory, List<String>> seenParentsAndProfiles = new HashMap<>();

        for ( final ProfiledCategory profiledCategory : profiledCategories )
        {
            final PwmSettingCategory loopCategory = profiledCategory.getPwmSettingCategory();
            if ( profiledCategory.getType() == ProfiledCategory.Type.StaticCategory )
            {
                navigationData.add( navTreeItemForCategory( loopCategory, locale, null ) );
            }
            else if ( profiledCategory.getType() == ProfiledCategory.Type.Profile )
            {
                navigationData.addAll( makeNavTreeItemsForProfile( profiledCategory, locale ) );
            }
            else if ( profiledCategory.getType() == ProfiledCategory.Type.CategoryWithProfile )
            {
                final String profileId = profiledCategory.getProfile();
                final PwmSettingCategory parentCategory = loopCategory.getParent();

                seenParentsAndProfiles.computeIfAbsent( parentCategory, pwmSettingCategory -> new ArrayList<>() );
                if ( !seenParentsAndProfiles.get( parentCategory ).contains( profileId ) )
                {
                    seenParentsAndProfiles.get( parentCategory ).add( profileId );

                    final NavTreeItem profileNode = navTreeItemForCategory( parentCategory, locale, profileId )
                            .toBuilder()
                            .name( StringUtil.isEmpty( profileId ) ? "Default" : profileId )
                            .id( "profile-" + parentCategory.getKey() + "-" + profileId )
                            .parent( parentCategory.getKey() )
                            .type( parentCategory.getChildCategories().isEmpty() ? NavTreeItem.NavItemType.category :  NavTreeItem.NavItemType.navigation )
                            .build();
                    navigationData.add( profileNode );
                }

                navigationData.add( navTreeItemForCategory( loopCategory, locale, profiledCategory.getProfile() ) );
            }
        }

        return Collections.unmodifiableList( navigationData );
    }

    private static List<NavTreeItem> makeNavTreeItemsForProfile(
            final ProfiledCategory profiledCategory,
            final Locale locale
    )
    {
        final List<NavTreeItem> returnData = new ArrayList<>();
        final PwmSettingCategory loopCategory = profiledCategory.getPwmSettingCategory();

        // add the category itself
        returnData.add( navTreeItemForCategory( loopCategory, locale, null ) );

        // add the "edit profiles" category
        {
            final PwmSetting profileSetting = loopCategory.getProfileSetting().orElseThrow( IllegalStateException::new );
            final NavTreeItem profileEditorInfo = NavTreeItem.builder()
                    .id( loopCategory.getKey() + "-EDITOR" )
                    .name( LocaleHelper.getLocalizedMessage( locale, Config.Label_ProfileListEditMenuItem, null ) )
                    .type( NavTreeItem.NavItemType.profileDefinition )
                    .profileSetting( profileSetting.getKey() )
                    .parent( loopCategory.getKey() )
                    .build();
            returnData.add( profileEditorInfo );
        }

        return Collections.unmodifiableList( returnData );
    }

    private static NavTreeItem navTreeItemForCategory(
            final PwmSettingCategory category,
            final Locale locale,
            final String profileId
    )
    {
        final NavTreeItem.NavTreeItemBuilder categoryItem = NavTreeItem.builder();
        categoryItem.id( category.getKey() + ( profileId != null ? "-" + profileId : "" ) );
        categoryItem.name( category.getLabel( locale ) );
        categoryItem.category( category.getKey() );
        if ( category.getParent() != null )
        {
            if ( profileId != null )
            {
                categoryItem.parent( "profile-" + category.getParent().getKey() + "-" + profileId );
            }
            else
            {
                categoryItem.parent( category.getParent().getKey() );
            }
        }
        else
        {
            categoryItem.parent( ROOT_ITEM_ID );
        }
        if ( category.getChildCategories().isEmpty() && !category.isTopLevelProfile() )
        {
            categoryItem.type( NavTreeItem.NavItemType.category );
        }
        else
        {
            categoryItem.type( NavTreeItem.NavItemType.navigation );
        }
        if ( profileId != null )
        {
            categoryItem.profile( profileId );
        }
        categoryItem.menuLocation( category.toMenuLocationDebug( profileId, locale ) );
        return categoryItem.build();
    }

    private static boolean categoryMatcher(
            final PwmApplication pwmApplication,
            final ProfiledCategory profiledCategory,
            final StoredConfiguration storedConfiguration,
            final NavTreeSettings navTreeSettings
    )
    {
        final PwmSettingCategory category = profiledCategory.getPwmSettingCategory();

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

        final String profileID = profiledCategory.getProfile();
        for ( final PwmSettingCategory childCategory : category.getChildCategories() )
        {
            final ProfiledCategory childProfiledCategory = new ProfiledCategory( childCategory, profileID, ProfiledCategory.Type.CategoryWithProfile );
            if ( categoryMatcher( pwmApplication, childProfiledCategory, storedConfiguration, navTreeSettings ) )
            {
                return true;
            }
        }

        for ( final PwmSetting setting : category.getSettings() )
        {
            if ( settingMatches( storedConfiguration, setting, profileID, navTreeSettings ) )
            {
                return true;
            }
        }

        return false;
    }

    private static boolean settingMatches(
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

        if ( setting.getLevel() > navTreeSettings.getLevel() )
        {
            return false;
        }

        final String text = navTreeSettings.getFilterText();
        if ( StringUtil.isEmpty( text ) )
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

    private static void moveNavItemToTopOfList( final String categoryID, final List<NavTreeItem> navigationData )
    {
        Objects.requireNonNull( categoryID );

        final Optional<NavTreeItem> optionalMatchItem = navigationData.stream()
                .filter( navTreeItem -> categoryID.equals( navTreeItem.getId() ) )
                .findAny();

        optionalMatchItem.ifPresent( navTreeItem ->
        {
            navigationData.remove( navTreeItem );
            navigationData.add( 0, navTreeItem );
        } );
    }

    @Value
    private static class ProfiledCategory
    {
        private final PwmSettingCategory pwmSettingCategory;
        private final String profile;
        private final Type type;

        enum Type
        {
            /** Standard (non-profiled) category. */
            StaticCategory,

            /** Profile root (needs edit menu and list of profile values as NavTree nodes). */
            Profile,

            /** Category that is part of a profile.  Repeated for each profile value parent. */
            CategoryWithProfile
        }
    }
}

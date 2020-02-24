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

package password.pwm.config.stored;

import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.BCrypt;
import password.pwm.util.secure.PwmRandom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public abstract class StoredConfigurationUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigurationUtil.class );

    public static List<String> profilesForSetting
            (
                    final PwmSetting pwmSetting,
                    final StoredConfiguration storedConfiguration
            )
    {
        if ( !pwmSetting.getCategory().hasProfiles() && pwmSetting.getSyntax() != PwmSettingSyntax.PROFILE )
        {
            throw new IllegalArgumentException( "cannot build profile list for non-profile setting " + pwmSetting.toString() );
        }

        final PwmSetting profileSetting;
        if ( pwmSetting.getSyntax() == PwmSettingSyntax.PROFILE )
        {
            profileSetting = pwmSetting;
        }
        else
        {
            profileSetting = pwmSetting.getCategory().getProfileSetting();
        }

        return profilesForProfileSetting( profileSetting, storedConfiguration );
    }

    public static List<String> profilesForCategory(
            final PwmSettingCategory category,
            final StoredConfiguration storedConfiguration
    )
    {
        final PwmSetting profileSetting = category.getProfileSetting();

        return profilesForProfileSetting( profileSetting, storedConfiguration );
    }

    private static List<String> profilesForProfileSetting(
            final PwmSetting pwmSetting,
            final StoredConfiguration storedConfiguration
    )
    {
        if ( !pwmSetting.getCategory().hasProfiles() && pwmSetting.getSyntax() != PwmSettingSyntax.PROFILE )
        {
            throw new IllegalArgumentException( "cannot build profile list for non-profile setting " + pwmSetting.toString() );
        }

        final PwmSetting profileSetting;
        if ( pwmSetting.getSyntax() == PwmSettingSyntax.PROFILE )
        {
            profileSetting = pwmSetting;
        }
        else
        {
            profileSetting = pwmSetting.getCategory().getProfileSetting();
        }

        final Object nativeObject = storedConfiguration.readSetting( profileSetting, null ).toNativeObject();
        final List<String> settingValues = ( List<String> ) nativeObject;
        final LinkedList<String> profiles = new LinkedList<>( settingValues );
        profiles.removeIf( profile -> StringUtil.isEmpty( profile ) );
        return Collections.unmodifiableList( profiles );

    }

    public static String changeLogAsDebugString(
            final StoredConfiguration storedConfiguration,
            final Set<StoredConfigItemKey> configChangeLog,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {

        final Map<String, String> outputMap = StoredConfigurationUtil.makeDebugMap( storedConfiguration, configChangeLog, locale );
        final StringBuilder output = new StringBuilder();
        if ( outputMap.isEmpty() )
        {
            output.append( "No setting changes." );
        }
        else
        {
            for ( final Map.Entry<String, String> entry : outputMap.entrySet() )
            {
                final String keyName = entry.getKey();
                final String value = entry.getValue();
                output.append( keyName );
                output.append( "\n" );
                output.append( " Value: " );
                output.append( value );
                output.append( "\n" );
            }
        }
        return output.toString();

    }

    public static StoredConfiguration copyConfigAndBlankAllPasswords( final StoredConfiguration input )
            throws PwmUnrecoverableException
    {
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( input );

        for ( final StoredConfigItemKey storedConfigItemKey : input.modifiedItems() )
        {
            if ( storedConfigItemKey.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
            {
                final PwmSetting pwmSetting = storedConfigItemKey.toPwmSetting();
                if ( pwmSetting.getSyntax() == PwmSettingSyntax.PASSWORD )
                {
                    final ValueMetaData valueMetaData = input.readSettingMetadata( pwmSetting, storedConfigItemKey.getProfileID() );
                    final UserIdentity userIdentity = valueMetaData == null ? null : valueMetaData.getUserIdentity();
                    final PasswordValue passwordValue = new PasswordValue( new PasswordData( PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT ) );
                    modifier.writeSetting( pwmSetting, storedConfigItemKey.getProfileID(), passwordValue, userIdentity );
                }
            }
        }


        final Optional<String> pwdHash = input.readConfigProperty( ConfigurationProperty.PASSWORD_HASH );
        if ( pwdHash.isPresent() )
        {
            modifier.writeConfigProperty( ConfigurationProperty.PASSWORD_HASH, PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT );
        }

        return modifier.newStoredConfiguration();
    }

    public static List<String> validateValues( final StoredConfiguration storedConfiguration )
    {
        final Instant startTime = Instant.now();
        final List<String> errorStrings = new ArrayList<>();

        for ( final StoredConfigItemKey storedConfigItemKey : storedConfiguration.modifiedItems() )
        {
            if ( storedConfigItemKey.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
            {
                final PwmSetting pwmSetting = storedConfigItemKey.toPwmSetting();
                final String profileID = storedConfigItemKey.getProfileID();
                final StoredValue loopValue = storedConfiguration.readSetting( pwmSetting, profileID );

                try
                {
                    final List<String> errors = loopValue.validateValue( pwmSetting );
                    for ( final String loopError : errors )
                    {
                        errorStrings.add( pwmSetting.toMenuLocationDebug( storedConfigItemKey.getProfileID(), PwmConstants.DEFAULT_LOCALE ) + " - " + loopError );
                    }
                }
                catch ( final Exception e )
                {
                    LOGGER.error( () -> "unexpected error during validate value for "
                            + pwmSetting.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE ) + ", error: "
                            + e.getMessage(), e );
                }
            }
        }

        LOGGER.trace( () -> "StoredConfiguration validator completed in " + TimeDuration.compactFromCurrent( startTime ) );
        return errorStrings;
    }

    public static Set<StoredConfigItemKey> search( final StoredConfiguration storedConfiguration, final String searchTerm, final Locale locale )
    {
        return new SettingSearchMachine( storedConfiguration, searchTerm, locale ).search();
    }

    public static boolean matchSetting(
            final StoredConfiguration storedConfiguration,
            final PwmSetting setting,
            final StoredValue storedValue,
            final String term,
            final Locale defaultLocale )
    {
        return new SettingSearchMachine( storedConfiguration, term, defaultLocale ).matchSetting( setting, storedValue, term );
    }

    private static class SettingSearchMachine
    {
        private final StoredConfiguration storedConfiguration;
        private final String searchTerm;
        private final Locale locale;

        private SettingSearchMachine( final StoredConfiguration storedConfiguration, final String searchTerm, final Locale locale )
        {
            this.storedConfiguration = storedConfiguration;
            this.searchTerm = searchTerm;
            this.locale = locale;
        }

        public Set<StoredConfigItemKey> search()
        {
            if ( StringUtil.isEmpty( searchTerm ) )
            {
                return Collections.emptySet();
            }

            return allPossibleSettingKeysForConfiguration( storedConfiguration )
                    .parallelStream()
                    .filter( s -> s.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
                    .filter( this::matchSetting )
                    .collect( Collectors.toCollection( TreeSet::new ) );
        }

        private boolean matchSetting(
                final StoredConfigItemKey storedConfigItemKey
        )
        {
            final PwmSetting pwmSetting = storedConfigItemKey.toPwmSetting();
            final StoredValue value = storedConfiguration.readSetting( pwmSetting, storedConfigItemKey.getProfileID() );

            return StringUtil.whitespaceSplit( searchTerm )
                    .parallelStream()
                    .allMatch( s -> matchSetting( pwmSetting, value, s ) );
        }

        private boolean matchSetting( final PwmSetting setting, final StoredValue value, final String searchTerm )
        {
            if ( setting.isHidden() || setting.getCategory().isHidden() )
            {
                return false;
            }

            if ( searchTerm == null || searchTerm.isEmpty() )
            {
                return false;
            }

            final String lowerSearchTerm = searchTerm.toLowerCase();

            {
                final String key = setting.getKey();
                if ( key.toLowerCase().contains( lowerSearchTerm ) )
                {
                    return true;
                }
            }
            {
                final String label = setting.getLabel( locale );
                if ( label.toLowerCase().contains( lowerSearchTerm ) )
                {
                    return true;
                }
            }
            {
                final String descr = setting.getDescription( locale );
                if ( descr.toLowerCase().contains( lowerSearchTerm ) )
                {
                    return true;
                }
            }
            {
                final String menuLocationString = setting.toMenuLocationDebug( null, locale );
                if ( menuLocationString.toLowerCase().contains( lowerSearchTerm ) )
                {
                    return true;
                }
            }

            if ( setting.isConfidential() )
            {
                return false;
            }
            {
                final String valueDebug = value.toDebugString( locale );
                if ( valueDebug != null && valueDebug.toLowerCase().contains( lowerSearchTerm ) )
                {
                    return true;
                }
            }
            if ( PwmSettingSyntax.SELECT == setting.getSyntax()
                    || PwmSettingSyntax.OPTIONLIST == setting.getSyntax()
                    || PwmSettingSyntax.VERIFICATION_METHOD == setting.getSyntax()
            )
            {
                for ( final String key : setting.getOptions().keySet() )
                {
                    if ( key.toLowerCase().contains( lowerSearchTerm ) )
                    {
                        return true;
                    }
                    final String optionValue = setting.getOptions().get( key );
                    if ( optionValue != null && optionValue.toLowerCase().contains( lowerSearchTerm ) )
                    {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static boolean verifyPassword( final StoredConfiguration storedConfiguration, final String password )
    {
        if ( !hasPassword( storedConfiguration ) )
        {
            return false;
        }
        final Optional<String> passwordHash = storedConfiguration.readConfigProperty( ConfigurationProperty.PASSWORD_HASH );
        return passwordHash.isPresent() && BCrypt.testAnswer( password, passwordHash.get(), new Configuration( storedConfiguration ) );
    }

    public static boolean hasPassword( final StoredConfiguration storedConfiguration )
    {
        final Optional<String> passwordHash = storedConfiguration.readConfigProperty( ConfigurationProperty.PASSWORD_HASH );
        return passwordHash.isPresent();
    }

    public static void setPassword( final StoredConfigurationModifier storedConfiguration, final String password )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( password ) )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            "can not set blank password",
                    }
            ) );
        }
        final String trimmedPassword = password.trim();
        if ( trimmedPassword.length() < 1 )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            "can not set blank password",
                    }
            ) );
        }


        final String passwordHash = BCrypt.hashPassword( password );
        storedConfiguration.writeConfigProperty( ConfigurationProperty.PASSWORD_HASH, passwordHash );
    }

    public static void initNewRandomSecurityKey( final StoredConfigurationModifier modifier )
            throws PwmUnrecoverableException
    {
        if ( !modifier.newStoredConfiguration().isDefaultValue( PwmSetting.PWM_SECURITY_KEY, null ) )
        {
            return;
        }

        modifier.writeSetting(
                PwmSetting.PWM_SECURITY_KEY, null,
                new PasswordValue( new PasswordData( PwmRandom.getInstance().alphaNumericString( 1024 ) ) ),
                null
        );

        LOGGER.debug( () -> "initialized new random security key" );
    }


    public static Map<String, String> makeDebugMap(
            final StoredConfiguration storedConfiguration,
            final Collection<StoredConfigItemKey> interestedItems,
            final Locale locale
    )
    {
        final Map<String, String> outputMap = interestedItems.stream()
                .filter( ( key ) -> key.getRecordType() != StoredConfigItemKey.RecordType.PROPERTY )
                .filter( ( key ) -> storedConfiguration.readStoredValue( key ).isPresent() )
                .collect( Collectors.toMap(
                        key -> key.getLabel( locale ),
                        key -> storedConfiguration.readStoredValue( key ).get().toDebugString( locale ) ) );

        return Collections.unmodifiableMap( new TreeMap<>( outputMap ) );
    }

    public static Set<StoredConfigItemKey> allPossibleSettingKeysForConfiguration(
            final StoredConfiguration storedConfiguration
    )
    {
        final Set<StoredConfigItemKey> loopResults = new HashSet<>();
        for ( final PwmSetting loopSetting : PwmSetting.values() )
        {
            if ( loopSetting.getCategory().hasProfiles() )
            {
                for ( final String profile : storedConfiguration.profilesForSetting( loopSetting ) )
                {
                    loopResults.add( StoredConfigItemKey.fromSetting( loopSetting, profile ) );
                }
            }
            else
            {
                loopResults.add( StoredConfigItemKey.fromSetting( loopSetting, null ) );
            }
        }
        return Collections.unmodifiableSet( loopResults );
    }

    public static Set<StoredConfigItemKey> changedValues (
            final StoredConfiguration originalConfiguration,
            final StoredConfiguration modifiedConfiguration
    )
    {
        final Instant startTime = Instant.now();

        final Set<StoredConfigItemKey> interestedReferences = new HashSet<>();
        interestedReferences.addAll( originalConfiguration.modifiedItems() );
        interestedReferences.addAll( modifiedConfiguration.modifiedItems() );

        final Set<StoredConfigItemKey> deltaReferences = interestedReferences
                .parallelStream()
                .filter( reference ->
                        {
                            final Optional<String> hash1 = originalConfiguration.readStoredValue( reference ).map( StoredValue::valueHash );
                            final Optional<String> hash2 = modifiedConfiguration.readStoredValue( reference ).map( StoredValue::valueHash );
                            return !hash1.equals( hash2 );
                        }
                ).collect( Collectors.toSet() );

        LOGGER.trace( () -> "generated changeLog items via compare in " + TimeDuration.compactFromCurrent( startTime ) );

        return Collections.unmodifiableSet( deltaReferences );
    }
}

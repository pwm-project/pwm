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

package password.pwm.config.stored;

import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.value.LocalizedStringArrayValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmExceptionLoggingConsumer;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class StoredConfigurationUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigurationUtil.class );

    public static List<String> profilesForSetting(
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
            profileSetting = pwmSetting.getCategory().getProfileSetting().orElseThrow( IllegalStateException::new );
        }

        return profilesForProfileSetting( profileSetting, storedConfiguration );
    }

    public static List<String> profilesForCategory(
            final PwmSettingCategory category,
            final StoredConfiguration storedConfiguration
    )
    {
        final PwmSetting profileSetting = category.getProfileSetting().orElseThrow( IllegalStateException::new );
        return profilesForProfileSetting( profileSetting, storedConfiguration );
    }

    private static List<String> profilesForProfileSetting(
            final PwmSetting profileSetting,
            final StoredConfiguration storedConfiguration
    )
    {
        final StoredValue storedValue = storedConfiguration.readSetting( profileSetting, null );
        final List<String> settingValues = ValueTypeConverter.valueToStringArray( storedValue );
        final List<String> profiles = new ArrayList<>( settingValues );
        profiles.removeIf( StringUtil::isEmpty );
        return Collections.unmodifiableList( profiles );
    }

    public static StoredConfiguration copyConfigAndBlankAllPasswords( final StoredConfiguration storedConfig )
            throws PwmUnrecoverableException
    {
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfig );

        final Consumer<StoredConfigItemKey> valueModifier = PwmExceptionLoggingConsumer.wrapConsumer( storedConfigItemKey ->
        {
            if ( storedConfigItemKey.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
            {
                final PwmSetting pwmSetting = storedConfigItemKey.toPwmSetting();
                if ( pwmSetting.getSyntax() == PwmSettingSyntax.PASSWORD )
                {
                    final ValueMetaData valueMetaData = storedConfig.readSettingMetadata( pwmSetting, storedConfigItemKey.getProfileID() );
                    final UserIdentity userIdentity = valueMetaData == null ? null : valueMetaData.getUserIdentity();
                    final PasswordValue passwordValue = new PasswordValue( new PasswordData( PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT ) );
                    modifier.writeSetting( pwmSetting, storedConfigItemKey.getProfileID(), passwordValue, userIdentity );
                }
            }
        } );

        storedConfig.modifiedItems()
                .parallelStream()
                .filter( ( key ) -> StoredConfigItemKey.RecordType.SETTING.equals( key.getRecordType() ) )
                .forEach( valueModifier );

        final Optional<String> pwdHash = storedConfig.readConfigProperty( ConfigurationProperty.PASSWORD_HASH );
        if ( pwdHash.isPresent() )
        {
            modifier.writeConfigProperty( ConfigurationProperty.PASSWORD_HASH, PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT );
        }

        return modifier.newStoredConfiguration();
    }

    public static List<String> validateValues( final StoredConfiguration storedConfiguration )
    {
        final Function<StoredConfigItemKey, Stream<String>> validateSettingFunction = storedConfigItemKey ->
        {
            final PwmSetting pwmSetting = storedConfigItemKey.toPwmSetting();
            final String profileID = storedConfigItemKey.getProfileID();
            final StoredValue loopValue = storedConfiguration.readSetting( pwmSetting, profileID );

            try
            {
                final List<String> errors = loopValue.validateValue( pwmSetting );
                for ( final String loopError : errors )
                {
                    return Stream.of( pwmSetting.toMenuLocationDebug( storedConfigItemKey.getProfileID(), PwmConstants.DEFAULT_LOCALE ) + " - " + loopError );
                }
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unexpected error during validate value for "
                        + pwmSetting.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE ) + ", error: "
                        + e.getMessage(), e );
            }
            return Stream.empty();
        };

        final Instant startTime = Instant.now();
        final List<String> errorStrings = storedConfiguration.modifiedItems()
                .parallelStream()
                .filter( key -> StoredConfigItemKey.RecordType.SETTING.equals( key.getRecordType() ) )
                .flatMap( validateSettingFunction )
                .collect( Collectors.toList() );


        LOGGER.trace( () -> "StoredConfiguration validator completed", () -> TimeDuration.fromCurrent( startTime ) );
        return Collections.unmodifiableList( errorStrings );
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
        return interestedItems.stream()
                .filter( ( key ) -> !key.isRecordType( StoredConfigItemKey.RecordType.PROPERTY ) )
                .collect( Collectors.toUnmodifiableMap(
                        ( key ) -> key.getLabel( locale ),
                        ( key ) -> StoredConfigurationUtil.getValueOrDefault( storedConfiguration, key ).toDebugString( locale )
                ) );
    }

    public static Set<StoredConfigItemKey> allPossibleSettingKeysForConfiguration(
            final StoredConfiguration storedConfiguration
    )
    {
        final Function<PwmSetting, Stream<StoredConfigItemKey>> function = loopSetting ->
        {
            if ( loopSetting.getCategory().hasProfiles() )
            {
                return storedConfiguration.profilesForSetting( loopSetting )
                        .stream()
                        .map( profileId -> StoredConfigItemKey.fromSetting( loopSetting, profileId ) )
                        .collect( Collectors.toList() )
                        .stream();
            }
            else
            {
                return Stream.of( StoredConfigItemKey.fromSetting( loopSetting, null ) );
            }
        };

        return PwmSetting.sortedValues().stream()
                .parallel()
                .flatMap( function )
                .collect( Collectors.toUnmodifiableSet() );
    }

    public static Set<StoredConfigItemKey> changedValues (
            final StoredConfiguration originalConfiguration,
            final StoredConfiguration modifiedConfiguration
    )
    {
        final Instant startTime = Instant.now();

        final Set<StoredConfigItemKey> interestedReferences = new HashSet<>( originalConfiguration.modifiedItems() );
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

        LOGGER.trace( () -> "generated changeLog items via compare", () -> TimeDuration.fromCurrent( startTime ) );

        return Collections.unmodifiableSet( deltaReferences );
    }

    public static StoredValue getValueOrDefault(
            final StoredConfiguration storedConfiguration,
            final StoredConfigItemKey key
    )
    {
        final Optional<StoredValue> storedValue = storedConfiguration.readStoredValue( key );

        if ( storedValue.isPresent() )
        {
            return storedValue.get();
        }

        switch ( key.getRecordType() )
        {
            case SETTING:
            {
                final PwmSettingTemplateSet templateSet = storedConfiguration.getTemplateSet();
                return key.toPwmSetting().getDefaultValue( templateSet );
            }

            case LOCALE_BUNDLE:
            {
                return new LocalizedStringArrayValue( Collections.emptyMap() );
            }

            case PROPERTY:
                return new StringValue( "" );

            default:
                JavaHelper.unhandledSwitchStatement( key );
        }

        throw new IllegalStateException();
    }
}

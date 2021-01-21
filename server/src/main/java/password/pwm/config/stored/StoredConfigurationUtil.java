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
import password.pwm.bean.DomainID;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.value.LocalizedStringArrayValue;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueFactory;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmExceptionLoggingConsumer;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.BCrypt;
import password.pwm.util.secure.HmacAlgorithm;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.SecureEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class StoredConfigurationUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigurationUtil.class );

    public static List<String> profilesForSetting(
            final DomainID domainID,
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

        return profilesForProfileSetting( domainID, profileSetting, storedConfiguration );
    }

    public static List<String> profilesForCategory(
            final DomainID domainID,
            final PwmSettingCategory category,
            final StoredConfiguration storedConfiguration
    )
    {
        final PwmSetting profileSetting = category.getProfileSetting().orElseThrow( IllegalStateException::new );
        return profilesForProfileSetting( domainID, profileSetting, storedConfiguration );
    }

    private static List<String> profilesForProfileSetting(
            final DomainID domainID,
            final PwmSetting profileSetting,
            final StoredConfiguration storedConfiguration
    )
    {
        final StoredConfigKey key = StoredConfigKey.forSetting( profileSetting, null, domainID );
        final StoredValue storedValue = StoredConfigurationUtil.getValueOrDefault( storedConfiguration, key );
        final List<String> settingValues = ValueTypeConverter.valueToStringArray( storedValue );
        return settingValues.stream()
                .filter( value -> StringUtil.notEmpty( value ) )
                .collect( Collectors.toUnmodifiableList() );
    }

    public static StoredConfiguration copyConfigAndBlankAllPasswords( final StoredConfiguration storedConfig )
            throws PwmUnrecoverableException
    {
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfig );

        final Consumer<StoredConfigKey> valueModifier = PwmExceptionLoggingConsumer.wrapConsumer( storedConfigItemKey ->
        {
            if ( storedConfigItemKey.getRecordType() == StoredConfigKey.RecordType.SETTING )
            {
                final PwmSetting pwmSetting = storedConfigItemKey.toPwmSetting();
                if ( pwmSetting.getSyntax() == PwmSettingSyntax.PASSWORD )
                {
                    final Optional<ValueMetaData> valueMetaData = storedConfig.readSettingMetadata( storedConfigItemKey );
                    final UserIdentity userIdentity = valueMetaData.map( ValueMetaData::getUserIdentity ).orElse( null );
                    final PasswordValue passwordValue = new PasswordValue( new PasswordData( PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT ) );
                    modifier.writeSetting( storedConfigItemKey, passwordValue, userIdentity );
                }
            }
        } );

        CollectionUtil.iteratorToStream( storedConfig.keys() )
                .filter( ( key ) -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
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
        final Function<StoredConfigKey, Stream<String>> validateSettingFunction = storedConfigItemKey ->
        {
            final PwmSetting pwmSetting = storedConfigItemKey.toPwmSetting();
            final String profileID = storedConfigItemKey.getProfileID();
            final Optional<StoredValue> loopValue = storedConfiguration.readStoredValue( storedConfigItemKey );

            if ( loopValue.isPresent() )
            {
                try
                {
                    final List<String> errors = loopValue.get().validateValue( pwmSetting );
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
            }
            return Stream.empty();
        };

        final Instant startTime = Instant.now();
        final List<String> errorStrings = CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                .filter( key -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                .flatMap( validateSettingFunction )
                .collect( Collectors.toList() );


        LOGGER.trace( () -> "StoredConfiguration validator completed", () -> TimeDuration.fromCurrent( startTime ) );
        return Collections.unmodifiableList( errorStrings );
    }

    public static boolean verifyPassword( final StoredConfiguration storedConfiguration, final String password )
    {
        if ( !hasPassword( storedConfiguration ) )
        {
            return false;
        }
        final Optional<String> passwordHash = storedConfiguration.readConfigProperty( ConfigurationProperty.PASSWORD_HASH );
        return passwordHash.isPresent() && BCrypt.testAnswer( password, passwordHash.get(), new AppConfig( storedConfiguration ) );
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
        final StoredConfigKey key = StoredConfigKey.forSetting( PwmSetting.PWM_SECURITY_KEY, null, DomainID.systemId() );

        if ( !isDefaultValue( modifier.newStoredConfiguration(), key ) )
        {
            return;
        }

        modifier.writeSetting(
                key,
                new PasswordValue( new PasswordData( PwmRandom.getInstance().alphaNumericString( 1024 ) ) ),
                null
        );

        LOGGER.debug( () -> "initialized new random security key" );
    }

    public static Map<String, String> makeDebugMap(
            final StoredConfiguration storedConfiguration,
            final List<StoredConfigKey> interestedItems,
            final Locale locale
    )
    {
        return Collections.unmodifiableMap( new TreeMap<>( interestedItems.stream()
                .filter( key -> !key.isRecordType( StoredConfigKey.RecordType.PROPERTY ) )
                .collect( Collectors.toMap(
                        key -> key.getLabel( locale ),
                        key -> StoredConfigurationUtil.getValueOrDefault( storedConfiguration, key ).toDebugString( locale )
                ) ) ) );
    }

    public static Set<StoredConfigKey> allPossibleSettingKeysForConfiguration(
            final StoredConfiguration storedConfiguration
    )
    {
        final List<DomainID> allDomainIds = new ArrayList<>( StoredConfigurationUtil.domainList( storedConfiguration ) );
        allDomainIds.add( DomainID.systemId() );

        return allDomainIds.stream()
                .flatMap( domainID -> allPossibleSettingKeysForDomain( storedConfiguration, domainID ) )
                .collect( Collectors.toUnmodifiableSet() );
    }

    private static Stream<StoredConfigKey> allPossibleSettingKeysForDomain(
            final StoredConfiguration storedConfiguration,
            final DomainID domainID
    )
    {
        final Function<PwmSetting, Stream<StoredConfigKey>> function = loopSetting ->
        {
            if ( loopSetting.getCategory().hasProfiles() )
            {
                return StoredConfigurationUtil.profilesForSetting( domainID, loopSetting, storedConfiguration )
                        .stream()
                        .map( profileId -> StoredConfigKey.forSetting( loopSetting, profileId, domainID ) )
                        .collect( Collectors.toList() )
                        .stream();
            }
            else
            {
                return Stream.of( StoredConfigKey.forSetting( loopSetting, null, domainID ) );
            }
        };

        return PwmSetting.sortedValues().stream()
                .filter( ( setting ) -> domainID.inScope( setting.getCategory().getScope() ) )
                .parallel()
                .flatMap( function )
                .collect( Collectors.toUnmodifiableSet() )
                .stream();
    }

    public static Set<StoredConfigKey> changedValues (
            final StoredConfiguration originalConfiguration,
            final StoredConfiguration modifiedConfiguration
    )
    {
        final Instant startTime = Instant.now();

        final Predicate<StoredConfigKey> hashTester = key ->
        {
            final Optional<String> hash1 = originalConfiguration.readStoredValue( key ).map( StoredValue::valueHash );
            final Optional<String> hash2 = modifiedConfiguration.readStoredValue( key ).map( StoredValue::valueHash );
            return !hash1.equals( hash2 );
        };

        final Set<StoredConfigKey> deltaReferences = Stream.concat(
                CollectionUtil.iteratorToStream( originalConfiguration.keys() ),
                CollectionUtil.iteratorToStream( modifiedConfiguration.keys() ) )
                .distinct()
                .filter( hashTester )
                .collect( Collectors.toUnmodifiableSet() );

        LOGGER.trace( () -> "generated " + deltaReferences.size() + " changeLog items via compare", () -> TimeDuration.fromCurrent( startTime ) );

        return deltaReferences;
    }

    public static StoredValue getValueOrDefault(
            final StoredConfiguration storedConfiguration,
            final StoredConfigKey key
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
                final PwmSettingTemplateSet templateSet = storedConfiguration.getTemplateSet().get( key.getDomainID() );
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

    public static List<DomainID> domainList( final StoredConfiguration storedConfiguration )
    {
        return storedConfiguration.getTemplateSet().keySet().stream()
                .filter( domain -> !Objects.equals( domain, DomainID.systemId() ) )
                .sorted()
                .collect( Collectors.toUnmodifiableList() );
    }

    public static StoredConfiguration copyProfileID(
            final StoredConfiguration oldStoredConfiguration,
            final DomainID domainID,
            final PwmSettingCategory category,
            final String sourceID,
            final String destinationID,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {

        if ( !category.hasProfiles() )
        {
            throw PwmUnrecoverableException.newException(
                    PwmError.ERROR_INVALID_CONFIG, "can not copy profile ID for category " + category + ", category does not have profiles" );
        }

        final PwmSetting profileSetting = category.getProfileSetting().orElseThrow( IllegalStateException::new );
        final List<String> existingProfiles = StoredConfigurationUtil.profilesForSetting( domainID, profileSetting, oldStoredConfiguration );
        if ( !existingProfiles.contains( sourceID ) )
        {
            throw PwmUnrecoverableException.newException(
                    PwmError.ERROR_INVALID_CONFIG, "can not copy profile ID for category, source profileID '" + sourceID + "' does not exist" );
        }

        if ( existingProfiles.contains( destinationID ) )
        {
            throw PwmUnrecoverableException.newException(
                    PwmError.ERROR_INVALID_CONFIG, "can not copy profile ID for category, destination profileID '" + destinationID + "' already exists" );
        }

        final Collection<PwmSettingCategory> interestedCategories = PwmSettingCategory.associatedProfileCategories( category );

        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( oldStoredConfiguration );
        for ( final PwmSettingCategory interestedCategory : interestedCategories )
        {
            for ( final PwmSetting pwmSetting : interestedCategory.getSettings() )
            {
                final StoredConfigKey existingKey = StoredConfigKey.forSetting( pwmSetting, sourceID, domainID );
                final Optional<StoredValue> existingValue = oldStoredConfiguration.readStoredValue( existingKey );
                if ( existingValue.isPresent() )
                {
                    final StoredConfigKey destinationKey = StoredConfigKey.forSetting( pwmSetting, destinationID, domainID );
                    modifier.writeSetting( destinationKey, existingValue.get(), userIdentity );
                }
            }
        }

        {
            final List<String> newProfileIDList = new ArrayList<>( existingProfiles );
            newProfileIDList.add( destinationID );
            final StoredConfigKey key = StoredConfigKey.forSetting( profileSetting, null, domainID );
            final StoredValue value = new StringArrayValue( newProfileIDList );
            modifier.writeSetting( key, value, userIdentity );
        }

        return modifier.newStoredConfiguration();
    }

    public static StoredConfiguration copyDomainID(
            final StoredConfiguration oldStoredConfiguration,
            final String source,
            final String destination,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final DomainID sourceID = DomainID.create( source );
        final DomainID destinationID = DomainID.create( destination );

        final List<DomainID> existingProfiles = StoredConfigurationUtil.domainList( oldStoredConfiguration );

        if ( !existingProfiles.contains( sourceID ) )
        {
            throw PwmUnrecoverableException.newException(
                    PwmError.ERROR_INVALID_CONFIG, "can not copy domain ID, source domainID '" + sourceID + "' does not exist" );
        }

        if ( existingProfiles.contains( destinationID ) )
        {
            throw PwmUnrecoverableException.newException(
                    PwmError.ERROR_INVALID_CONFIG, "can not copy domain ID for category, destination domainID '" + destinationID + "' already exists" );
        }

        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( oldStoredConfiguration );
        CollectionUtil.iteratorToStream( modifier.newStoredConfiguration().keys() )
                .filter( key -> key.getDomainID().equals( sourceID ) )
                .forEach( key ->
                {
                    final StoredConfigKey newKey = key.withNewDomain( destinationID );
                    final StoredValue storedValue = oldStoredConfiguration.readStoredValue( key ).orElseThrow();
                    try
                    {
                        modifier.writeSetting( newKey, storedValue, userIdentity );
                    }
                    catch ( final PwmUnrecoverableException e )
                    {
                        throw new IllegalStateException( "unexpected error copying domain setting values: " + e.getMessage() );
                    }
                } );

        {
            final StoredConfigKey key = StoredConfigKey.forSetting( PwmSetting.DOMAIN_LIST, null, DomainID.systemId() );
            final List<String> domainList = new ArrayList<>( ValueTypeConverter.valueToStringArray( StoredConfigurationUtil.getValueOrDefault( oldStoredConfiguration, key ) ) );
            domainList.add( destination );
            final StoredValue value = new StringArrayValue( domainList );
            modifier.writeSetting( key, value, userIdentity );
        }

        LOGGER.trace( () -> "copied " + modifier.modifications() + " domain settings from '" + source + "' to '" + destination + "' domain",
                () -> TimeDuration.fromCurrent( startTime ) );

        return modifier.newStoredConfiguration();
    }

    public static String valueHash( final StoredConfiguration storedConfiguration )
    {
        final Instant startTime = Instant.now();
        final StringBuilder sb = new StringBuilder();

        CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                .map( storedConfiguration::readStoredValue )
                .flatMap( Optional::stream )
                .forEach( v -> sb.append( v.valueHash() ) );

        final String output;
        try
        {
            output = SecureEngine.hmac( HmacAlgorithm.HMAC_SHA_512, storedConfiguration.getKey(), sb.toString() );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new IllegalStateException( e );
        }

        LOGGER.trace( () -> "calculated StoredConfiguration hash: " + output, () -> TimeDuration.fromCurrent( startTime ) );
        return output;
    }

    public static boolean isDefaultValue( final StoredConfiguration storedConfiguration, final StoredConfigKey key )
    {
        if ( !key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
        {
            throw new IllegalArgumentException( "key must be for SETTING type" );
        }

        final Optional<StoredValue> existingValue = storedConfiguration.readStoredValue( key );
        if ( existingValue.isEmpty() )
        {
            return true;
        }

        return ValueFactory.isDefaultValue( storedConfiguration.getTemplateSet().get( key.getDomainID( ) ), key.toPwmSetting(), existingValue.get() );
    }
}

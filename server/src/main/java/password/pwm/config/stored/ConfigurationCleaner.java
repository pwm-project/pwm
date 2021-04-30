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
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.option.RecoveryMinLifetimeOption;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.value.OptionListValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.PwmExceptionLoggingConsumer;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ConfigurationCleaner
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigurationCleaner.class );

    private static final List<PwmExceptionLoggingConsumer<StoredConfigurationModifier>> STORED_CONFIG_POST_PROCESSORS = List.of(
            new UpdateDeprecatedAdComplexitySettings(),
            new UpdateDeprecatedMinPwdLifetimeSetting(),
            new UpdateDeprecatedPublicHealthSetting(),
            new ProfileNonProfiledSettings(),
            new RemoveSuperfluousProfileSettings(),
            new RemoveDefaultSettings() );

    public static void postProcessStoredConfig(
            final StoredConfigurationModifier storedConfiguration
    )
    {
        STORED_CONFIG_POST_PROCESSORS.forEach( aClass -> PwmExceptionLoggingConsumer.wrapConsumer( aClass ).accept( storedConfiguration ) );
    }

    private static class UpdateDeprecatedAdComplexitySettings implements PwmExceptionLoggingConsumer<StoredConfigurationModifier>
    {
        @Override
        public void accept( final StoredConfigurationModifier modifier )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration existingConfig = modifier.newStoredConfiguration();

            CollectionUtil.iteratorToStream( modifier.newStoredConfiguration().keys() )
                    .filter( key -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                    .filter( key -> key.toPwmSetting() == PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY )
                    .forEach( key -> doConversion( existingConfig, key, modifier ) );
        }

        private static void doConversion(
                final StoredConfiguration existingConfig,
                final StoredConfigKey key,
                final StoredConfigurationModifier modifier
        )
        {
            final StoredValue storedValue = existingConfig.readStoredValue( key ).orElseThrow();

            final boolean ad2003Enabled = ValueTypeConverter.valueToBoolean( storedValue );
            final StoredValue value;
            if ( ad2003Enabled )
            {
                value = new StringValue( ADPolicyComplexity.AD2003.toString() );
            }
            else
            {
                value = new StringValue( ADPolicyComplexity.NONE.toString() );
            }

            final String profileID = key.getProfileID();

            LOGGER.info( () -> "converting deprecated non-default setting "
                    + PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY.getKey() + "/" + profileID
                    + " to replacement setting "
                    + PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_LEVEL + ", value="
                    + ValueTypeConverter.valueToString( value ) );
            final Optional<ValueMetaData> valueMetaData = existingConfig.readMetaData( key );
            final UserIdentity userIdentity = valueMetaData.map( ValueMetaData::getUserIdentity ).orElse( null );
            try
            {
                final StoredConfigKey writeKey = StoredConfigKey.forSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_LEVEL, profileID, key.getDomainID() );
                modifier.writeSetting( writeKey, value, userIdentity );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "error converting deprecated AD password policy setting: " + key + ", error: " + e.getMessage() );
            }
        }
    }

    private static class UpdateDeprecatedMinPwdLifetimeSetting implements PwmExceptionLoggingConsumer<StoredConfigurationModifier>
    {
        @Override
        public void accept( final StoredConfigurationModifier modifier )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration oldConfig = modifier.newStoredConfiguration();
            for ( final DomainID domainID : StoredConfigurationUtil.domainList( oldConfig ) )
            {
                for ( final String profileID : StoredConfigurationUtil.profilesForSetting( domainID, PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, oldConfig ) )
                {
                    final StoredConfigKey key = StoredConfigKey.forSetting( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID, domainID );
                    final Optional<StoredValue> oldValue = oldConfig.readStoredValue( key );
                    if ( oldValue.isPresent() && !StoredConfigurationUtil.isDefaultValue( oldConfig, key ) )
                    {
                        final boolean enforceEnabled = ValueTypeConverter.valueToBoolean( oldValue.get() );
                        final StoredValue value = enforceEnabled
                                ? new StringValue( RecoveryMinLifetimeOption.NONE.name() )
                                : new StringValue( RecoveryMinLifetimeOption.ALLOW.name() );
                        final Optional<ValueMetaData> existingData = oldConfig.readSettingMetadata( key );
                        final UserIdentity newActor = existingData.map( ValueMetaData::getUserIdentity ).orElse( null );
                        LOGGER.info( () -> "converting deprecated non-default setting "
                                + PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE ) + "/" + profileID
                                + " to replacement setting " + PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE )
                                + ", value="
                                + ValueTypeConverter.valueToString( value ) );

                        final StoredConfigKey destKey = StoredConfigKey.forSetting( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID, domainID );
                        modifier.writeSetting( destKey, value, newActor );
                    }
                }
            }
        }
    }

    private static class UpdateDeprecatedPublicHealthSetting implements PwmExceptionLoggingConsumer<StoredConfigurationModifier>
    {
        @Override
        public void accept( final StoredConfigurationModifier modifier )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration oldConfig = modifier.newStoredConfiguration();
            for ( final DomainID domainID : StoredConfigurationUtil.domainList( oldConfig ) )
            {
                final StoredConfigKey existingPubWebservicesKey = StoredConfigKey.forSetting( PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES, null, domainID );
                if ( oldConfig.readStoredValue( existingPubWebservicesKey ).isPresent() )
                {
                    LOGGER.info( () -> "converting deprecated non-default setting "
                            + PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE )
                            + " to replacement setting " + PwmSetting.WEBSERVICES_PUBLIC_ENABLE.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ) );
                    final StoredConfigKey existingPubEnableKey = StoredConfigKey.forSetting( PwmSetting.WEBSERVICES_PUBLIC_ENABLE, null, domainID );
                    final StoredValue existingStoredValue = StoredConfigurationUtil.getValueOrDefault( oldConfig, existingPubEnableKey );
                    final Set<String> existingValues =  ( Set<String> ) existingStoredValue.toNativeObject();
                    final Set<String> newValues = new LinkedHashSet<>( existingValues );
                    newValues.add( WebServiceUsage.Health.name() );
                    newValues.add( WebServiceUsage.Statistics.name() );

                    final Optional<ValueMetaData> valueMetaData = oldConfig.readMetaData( existingPubWebservicesKey );
                    final UserIdentity userIdentity = valueMetaData.map( ValueMetaData::getUserIdentity ).orElse( null );

                    final StoredConfigKey destKey = StoredConfigKey.forSetting( PwmSetting.WEBSERVICES_PUBLIC_ENABLE, null, domainID );
                    modifier.writeSetting( destKey, new OptionListValue( newValues ), userIdentity );

                }
            }
        }
    }

    private static class ProfileNonProfiledSettings implements PwmExceptionLoggingConsumer<StoredConfigurationModifier>
    {
        @Override
        public void accept( final StoredConfigurationModifier modifier )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration inputConfig = modifier.newStoredConfiguration();
            CollectionUtil.iteratorToStream( inputConfig.keys() )
                    .filter( ( key ) -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                    .filter( ( key ) -> key.toPwmSetting().getCategory().hasProfiles() )
                    .filter( ( key ) -> StringUtil.isEmpty( key.getProfileID() ) )
                    .forEach( ( key ) -> convertSetting( inputConfig, modifier, key ) );
        }

        private void convertSetting(
                final StoredConfiguration inputConfig,
                final StoredConfigurationModifier modifier,
                final StoredConfigKey key )
        {
            final PwmSetting pwmSetting = key.toPwmSetting();

            final List<String> targetProfiles = StoredConfigurationUtil.profilesForSetting(  key.getDomainID(), pwmSetting, inputConfig );
            final StoredValue value = inputConfig.readStoredValue( key ).orElseThrow();
            final Optional<ValueMetaData> valueMetaData = inputConfig.readMetaData( key );

            for ( final String destProfile : targetProfiles )
            {
                LOGGER.info( () -> "moving setting " + key.toString() + " without profile attribute to profile \"" + destProfile + "\"." );
                {
                    try
                    {
                        final var newKey = StoredConfigKey.forSetting( pwmSetting, destProfile, key.getDomainID() );
                        modifier.writeSettingAndMetaData( newKey, value, valueMetaData.orElse( null ) );
                    }
                    catch ( final PwmUnrecoverableException e )
                    {
                        LOGGER.warn( () -> "error moving setting " + pwmSetting.getKey() + " without profile attribute to profile \"" + destProfile
                                + "\", error: " + e.getMessage() );
                    }
                }
            }

            try
            {
                LOGGER.info( () -> "removing setting " + key.toString() + " without profile" );
                modifier.deleteKey( key );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.warn( () -> "error deleting setting " + pwmSetting.getKey() + " after adding profile settings: " + e.getMessage() );
            }
        }
    }

    private static class RemoveSuperfluousProfileSettings implements PwmExceptionLoggingConsumer<StoredConfigurationModifier>
    {
        @Override
        public void accept( final StoredConfigurationModifier modifier )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration inputConfig = modifier.newStoredConfiguration();
            CollectionUtil.iteratorToStream( inputConfig.keys() )
                    .filter( ( key ) -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                    .filter( ( key ) -> key.toPwmSetting().getCategory().hasProfiles() )
                    .filter( ( key ) -> verifyProfileIsValid( key, inputConfig ) )
                    .forEach( ( key ) -> removeSuperfluousProfile( key, modifier ) );
        }

        boolean verifyProfileIsValid( final StoredConfigKey key, final StoredConfiguration inputConfig )
        {
            final PwmSetting pwmSetting = key.toPwmSetting();
            final String recordID = key.getProfileID();
            final List<String> profiles = StoredConfigurationUtil.profilesForSetting( key.getDomainID(), pwmSetting, inputConfig );
            return !profiles.contains( recordID );
        }

        void removeSuperfluousProfile( final StoredConfigKey key, final StoredConfigurationModifier modifier )
        {
            try
            {
                LOGGER.info( () -> "removing setting " + key.toString() + " with non-existing profileID" );
                modifier.deleteKey( key );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.warn( () -> "error deleting setting " + key.toString() + " with non-existing profileID: " + e.getMessage() );
            }
        }
    }

    private static class RemoveDefaultSettings implements PwmExceptionLoggingConsumer<StoredConfigurationModifier>
    {
        @Override
        public void accept( final StoredConfigurationModifier modifier )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration inputConfig = modifier.newStoredConfiguration();
            CollectionUtil.iteratorToStream( inputConfig.keys() )
                    .filter( ( key ) -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                    .filter( key -> !valueIsDefault( key, inputConfig, inputConfig.getTemplateSet().get( key.getDomainID() ) ) )
                    .forEach( ( key ) -> removeDefaultValue( key, inputConfig, modifier ) );
        }

        private boolean valueIsDefault( final StoredConfigKey key, final StoredConfiguration inputConfig, final PwmSettingTemplateSet pwmSettingTemplateSet )
        {
            final StoredValue value = inputConfig.readStoredValue( key ).orElseThrow();
            final String loopHash = value.valueHash();
            final String defaultHash = key.toPwmSetting().getDefaultValue( pwmSettingTemplateSet ).valueHash();
            return !Objects.equals( loopHash, defaultHash );
        }

        private void removeDefaultValue( final StoredConfigKey key, final StoredConfiguration inputConfig, final StoredConfigurationModifier modifier )
        {
            try
            {
                final StoredValue value = inputConfig.readStoredValue( key ).orElseThrow();
                LOGGER.info( () -> "removing setting " + key.toString() + " with default value: " + value.toDebugString( PwmConstants.DEFAULT_LOCALE ) );
                modifier.deleteKey( key );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.warn( () -> "error deleting setting " + key.toString() + " with default value: " + e.getMessage() );
            }
        }
    }
}

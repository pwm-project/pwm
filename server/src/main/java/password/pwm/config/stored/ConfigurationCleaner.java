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
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.option.RecoveryMinLifetimeOption;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.value.OptionListValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.PwmExceptionLoggingConsumer;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class ConfigurationCleaner
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigurationCleaner.class );

    private static final List<PwmExceptionLoggingConsumer<StoredConfigurationModifier>> STORED_CONFIG_POST_PROCESSORS = Collections.unmodifiableList( Arrays.asList(
            new UpdateDeprecatedAdComplexitySettings(),
            new UpdateDeprecatedMinPwdLifetimeSetting(),
            new UpdateDeprecatedPublicHealthSetting(),
            new ProfileNonProfiledSettings(),
            new CheckForSuperfluousProfileSettings()
    ) );

    static void postProcessStoredConfig(
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
            final StoredConfiguration oldConfig = modifier.newStoredConfiguration();
            final Configuration configuration = new Configuration( oldConfig );
            for ( final String profileID : configuration.getPasswordProfileIDs() )
            {
                if ( !oldConfig.isDefaultValue( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY, profileID ) )
                {
                    final boolean ad2003Enabled = ValueTypeConverter.valueToBoolean( oldConfig.readSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY, profileID ) );
                    final StoredValue value;
                    if ( ad2003Enabled )
                    {
                        value = new StringValue( ADPolicyComplexity.AD2003.toString() );
                    }
                    else
                    {
                        value = new StringValue( ADPolicyComplexity.NONE.toString() );
                    }
                    LOGGER.info( () -> "converting deprecated non-default setting "
                            + PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY.getKey() + "/" + profileID
                            + " to replacement setting "
                            + PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_LEVEL + ", value="
                            + ValueTypeConverter.valueToString( value ) );
                    final Optional<ValueMetaData> valueMetaData = oldConfig.readMetaData(
                            StoredConfigItemKey.fromSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY, profileID ) );
                    final UserIdentity userIdentity = valueMetaData.map( ValueMetaData::getUserIdentity ).orElse( null );
                    modifier.writeSetting( PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_LEVEL, profileID, value, userIdentity );
                }
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
            for ( final String profileID : oldConfig.profilesForSetting( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME ) )
            {
                if ( !oldConfig.isDefaultValue( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID ) )
                {
                    final boolean enforceEnabled = ValueTypeConverter.valueToBoolean( oldConfig.readSetting( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID ) );
                    final StoredValue value = enforceEnabled
                            ? new StringValue( RecoveryMinLifetimeOption.NONE.name() )
                            : new StringValue( RecoveryMinLifetimeOption.ALLOW.name() );
                    final ValueMetaData existingData = oldConfig.readSettingMetadata( PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME, profileID );
                    final UserIdentity newActor = existingData != null && existingData.getUserIdentity() != null
                            ? existingData.getUserIdentity()
                            : null;
                    LOGGER.info( () -> "converting deprecated non-default setting "
                            + PwmSetting.RECOVERY_ENFORCE_MINIMUM_PASSWORD_LIFETIME.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE ) + "/" + profileID
                            + " to replacement setting " + PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS.toMenuLocationDebug( profileID, PwmConstants.DEFAULT_LOCALE )
                            + ", value="
                            + ValueTypeConverter.valueToString( value ) );
                    modifier.writeSetting( PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS, profileID, value, newActor );
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
            if ( !oldConfig.isDefaultValue( PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES, null ) )
            {
                LOGGER.info( () -> "converting deprecated non-default setting "
                        + PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE )
                        + " to replacement setting " + PwmSetting.WEBSERVICES_PUBLIC_ENABLE.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ) );
                final Set<String> existingValues = ( Set<String> ) oldConfig.readSetting( PwmSetting.WEBSERVICES_PUBLIC_ENABLE, null ).toNativeObject();
                final Set<String> newValues = new LinkedHashSet<>( existingValues );
                newValues.add( WebServiceUsage.Health.name() );
                newValues.add( WebServiceUsage.Statistics.name() );

                final Optional<ValueMetaData> valueMetaData = oldConfig.readMetaData(
                        StoredConfigItemKey.fromSetting( PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES, null ) );
                final UserIdentity userIdentity = valueMetaData.map( ValueMetaData::getUserIdentity ).orElse( null );

                modifier.writeSetting( PwmSetting.WEBSERVICES_PUBLIC_ENABLE, null, new OptionListValue( newValues ), userIdentity );
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
            inputConfig.modifiedItems()
                    .parallelStream()
                    .filter( ( key ) -> StoredConfigItemKey.RecordType.SETTING.equals( key.getRecordType() ) )
                    .filter( ( key ) -> key.toPwmSetting().getCategory().hasProfiles() )
                    .filter( ( key ) -> StringUtil.isEmpty( key.getProfileID() ) )
                    .forEach( ( key ) -> convertSetting( inputConfig, modifier, key ) );
        }

        private void convertSetting(
                final StoredConfiguration inputConfig,
                final StoredConfigurationModifier modifier,
                final StoredConfigItemKey key )
        {
            final PwmSetting pwmSetting = key.toPwmSetting();

            final List<String> targetProfiles = inputConfig.profilesForSetting( pwmSetting );
            final StoredValue value = inputConfig.readSetting( pwmSetting, null );
            final Optional<ValueMetaData> valueMetaData = inputConfig.readMetaData( key );

            for ( final String destProfile : targetProfiles )
            {
                LOGGER.info( () -> "moving setting " + key.toString() + " without profile attribute to profile \"" + destProfile + "\"." );
                {
                    try
                    {
                        modifier.writeSettingAndMetaData( pwmSetting, destProfile, value, valueMetaData.orElse( null ) );
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

    private static class CheckForSuperfluousProfileSettings implements PwmExceptionLoggingConsumer<StoredConfigurationModifier>
    {
        @Override
        public void accept( final StoredConfigurationModifier modifier )
                throws PwmUnrecoverableException
        {
            final StoredConfiguration inputConfig = modifier.newStoredConfiguration();
            inputConfig.modifiedItems()
                    .stream()
                    .filter( ( key ) -> StoredConfigItemKey.RecordType.SETTING.equals( key.getRecordType() ) )
                    .filter( ( key ) -> key.toPwmSetting().getCategory().hasProfiles() )
                    .filter( ( key ) -> verifyProfileIsValid( key, inputConfig ) )
                    .forEach( ( key ) -> removeSuperfluousProfile( key, modifier ) );
        }

        boolean verifyProfileIsValid( final StoredConfigItemKey key, final StoredConfiguration inputConfig )
        {
            final PwmSetting pwmSetting = key.toPwmSetting();
            final String recordID = key.getProfileID();
            final List<String> profiles = inputConfig.profilesForSetting( pwmSetting );
            return !profiles.contains( recordID );
        }

        void removeSuperfluousProfile( final StoredConfigItemKey key, final StoredConfigurationModifier modifier )
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
}

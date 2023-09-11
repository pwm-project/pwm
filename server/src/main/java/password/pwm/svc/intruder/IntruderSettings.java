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

package password.pwm.svc.intruder;

import password.pwm.AppProperty;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IntruderStorageMethod;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.secure.PwmHashAlgorithm;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record IntruderSettings(
        Map<IntruderRecordType, TypeSettings> targetSettings,
        IntruderStorageMethod intruderStorageMethod,
        PwmHashAlgorithm storageHashAlgorithm
)
{
    public IntruderSettings(
            final Map<IntruderRecordType, TypeSettings> targetSettings,
            final IntruderStorageMethod intruderStorageMethod,
            final PwmHashAlgorithm storageHashAlgorithm
    )
    {
        this.targetSettings = CollectionUtil.stripNulls( targetSettings );
        this.intruderStorageMethod = intruderStorageMethod;
        this.storageHashAlgorithm = storageHashAlgorithm;
    }

    public static IntruderSettings fromConfiguration( final DomainConfig config )
    {
        final PwmHashAlgorithm storageHashAlgorithm = EnumUtil.readEnumFromString(
                        PwmHashAlgorithm.class,
                        config.readAppProperty( AppProperty.INTRUDER_STORAGE_HASH_ALGORITHM ) )
                .orElse( PwmHashAlgorithm.SHA256 );

        return new IntruderSettings(
                makeTypeSettings( config ),
                config.getAppConfig().readSettingAsEnum( PwmSetting.INTRUDER_STORAGE_METHOD, IntruderStorageMethod.class ),
                storageHashAlgorithm );
    }

    private static Map<IntruderRecordType, TypeSettings> makeTypeSettings( final DomainConfig config )
    {
        final Map<IntruderRecordType, TypeSettings> targetSettings = new EnumMap<>( IntruderRecordType.class );

        {
            final TypeSettings settings = new TypeSettings(
                    TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_USER_CHECK_TIME ), TimeDuration.Unit.SECONDS ),
                    Math.toIntExact( config.readSettingAsLong( PwmSetting.INTRUDER_USER_MAX_ATTEMPTS ) ),
                    TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_USER_RESET_TIME ), TimeDuration.Unit.SECONDS ) );

            targetSettings.put( IntruderRecordType.USERNAME, settings );
            targetSettings.put( IntruderRecordType.USER_ID, settings );
        }

        {
            final TypeSettings settings = new TypeSettings(
                    TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_ATTRIBUTE_CHECK_TIME ), TimeDuration.Unit.MILLISECONDS ),
                    Math.toIntExact( config.readSettingAsLong( PwmSetting.INTRUDER_ATTRIBUTE_MAX_ATTEMPTS ) ),
                    TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_ATTRIBUTE_RESET_TIME ), TimeDuration.Unit.MILLISECONDS ) );

            targetSettings.put( IntruderRecordType.ATTRIBUTE, settings );
        }
        {
            final TypeSettings settings = new TypeSettings(
                    TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_TOKEN_DEST_CHECK_TIME ), TimeDuration.Unit.SECONDS ),
                    Math.toIntExact(  config.readSettingAsLong( PwmSetting.INTRUDER_TOKEN_DEST_MAX_ATTEMPTS ) ),
                    TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_TOKEN_DEST_RESET_TIME ), TimeDuration.Unit.SECONDS ) );

            targetSettings.put( IntruderRecordType.TOKEN_DEST, settings );
        }
        {
            final TypeSettings settings = new TypeSettings(
                    TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_ADDRESS_CHECK_TIME ), TimeDuration.Unit.SECONDS ),
                    Math.toIntExact(  config.readSettingAsLong( PwmSetting.INTRUDER_ADDRESS_MAX_ATTEMPTS ) ),
                    TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_ADDRESS_RESET_TIME ), TimeDuration.Unit.SECONDS ) );

            targetSettings.put( IntruderRecordType.ADDRESS, settings );
        }

        return Collections.unmodifiableMap( targetSettings );
    }

    public record TypeSettings(
            TimeDuration checkDuration,
            int checkCount,
            TimeDuration resetDuration
    )
    {
        boolean configured()
        {
            return checkCount() != 0
                    && checkDuration().asMillis() != 0
                    && resetDuration().asMillis() != 0;
        }
    }
}

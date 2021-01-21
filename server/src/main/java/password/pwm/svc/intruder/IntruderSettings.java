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

package password.pwm.svc.intruder;

import lombok.Builder;
import lombok.Value;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IntruderStorageMethod;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

@Value
@Builder
public class IntruderSettings
{
    private final Map<IntruderRecordType, IntruderRecordTypeSettings> targetSettings;
    private final IntruderStorageMethod intruderStorageMethod;

    public static IntruderSettings fromConfiguration( final AppConfig config )
    {
        return IntruderSettings.builder()
                .targetSettings( makeTypeSettings( config ) )
                .intruderStorageMethod( config.readSettingAsEnum( PwmSetting.INTRUDER_STORAGE_METHOD, IntruderStorageMethod.class ) )
                .build();
    }

    private static Map<IntruderRecordType, IntruderRecordTypeSettings> makeTypeSettings( final AppConfig config )
    {
        final Map<IntruderRecordType, IntruderRecordTypeSettings> targetSettings = new EnumMap<>( IntruderRecordType.class );

        {
            final IntruderRecordTypeSettings settings = IntruderRecordTypeSettings.builder()
                    .checkCount( ( int ) config.readSettingAsLong( PwmSetting.INTRUDER_USER_MAX_ATTEMPTS ) )
                    .resetDuration( TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_USER_RESET_TIME ), TimeDuration.Unit.SECONDS ) )
                    .checkDuration( TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_USER_CHECK_TIME ), TimeDuration.Unit.SECONDS ) )
                    .build();

            targetSettings.put( IntruderRecordType.USERNAME, settings );
            targetSettings.put( IntruderRecordType.USER_ID, settings );
        }

        {
            final IntruderRecordTypeSettings settings = IntruderRecordTypeSettings.builder()
                    .checkCount( ( int ) config.readSettingAsLong( PwmSetting.INTRUDER_ATTRIBUTE_MAX_ATTEMPTS ) )
                    .resetDuration( TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_ATTRIBUTE_RESET_TIME ), TimeDuration.Unit.MILLISECONDS ) )
                    .checkDuration( TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_ATTRIBUTE_CHECK_TIME ), TimeDuration.Unit.MILLISECONDS ) )
                    .build();

            targetSettings.put( IntruderRecordType.ATTRIBUTE, settings );
        }
        {
            final IntruderRecordTypeSettings settings = IntruderRecordTypeSettings.builder()
                    .checkCount( ( int ) config.readSettingAsLong( PwmSetting.INTRUDER_TOKEN_DEST_MAX_ATTEMPTS ) )
                    .resetDuration( TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_TOKEN_DEST_RESET_TIME ), TimeDuration.Unit.SECONDS ) )
                    .checkDuration( TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_TOKEN_DEST_CHECK_TIME ), TimeDuration.Unit.SECONDS ) )
                    .build();

            targetSettings.put( IntruderRecordType.TOKEN_DEST, settings );
        }
        {
            final IntruderRecordTypeSettings settings = IntruderRecordTypeSettings.builder()
                    .checkCount( ( int ) config.readSettingAsLong( PwmSetting.INTRUDER_ADDRESS_MAX_ATTEMPTS ) )
                    .resetDuration( TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_ADDRESS_RESET_TIME ), TimeDuration.Unit.SECONDS ) )
                    .checkDuration( TimeDuration.of( config.readSettingAsLong( PwmSetting.INTRUDER_ADDRESS_CHECK_TIME ), TimeDuration.Unit.SECONDS ) )
                    .build();

            targetSettings.put( IntruderRecordType.ADDRESS, settings );
        }

        return Collections.unmodifiableMap( targetSettings );
    }

    @Value
    @Builder
    public static class IntruderRecordTypeSettings implements Serializable
    {
        private TimeDuration checkDuration;
        private int checkCount;
        private TimeDuration resetDuration;

        boolean isConfigured()
        {
            return getCheckCount() != 0 && getCheckDuration().asMillis() != 0 && getResetDuration().asMillis() != 0;
        }
    }
}

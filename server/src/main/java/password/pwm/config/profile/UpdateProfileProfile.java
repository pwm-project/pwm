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

package password.pwm.config.profile;

import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.util.java.TimeDuration;

import java.util.Locale;
import java.util.Map;

public class UpdateProfileProfile extends AbstractProfile implements Profile
{

    private static final ProfileType PROFILE_TYPE = ProfileType.UpdateAttributes;

    protected UpdateProfileProfile( final String identifier, final Map<PwmSetting, StoredValue> storedValueMap )
    {
        super( identifier, storedValueMap );
    }

    public static UpdateProfileProfile makeFromStoredConfiguration( final StoredConfiguration storedConfiguration, final String identifier )
    {
        final Map<PwmSetting, StoredValue> valueMap = makeValueMap( storedConfiguration, identifier, PROFILE_TYPE.getCategory() );
        return new UpdateProfileProfile( identifier, valueMap );

    }

    @Override
    public String getDisplayName( final Locale locale )
    {
        return this.getIdentifier();
    }

    @Override
    public ProfileType profileType( )
    {
        return PROFILE_TYPE;
    }

    public TimeDuration getTokenDurationEmail( final Configuration configuration )
    {
        final long duration = readSettingAsLong( PwmSetting.UPDATE_PROFILE_TOKEN_LIFETIME_EMAIL );
        if ( duration < 1 )
        {
            final long defaultDuration = configuration.readSettingAsLong( PwmSetting.TOKEN_LIFETIME );
            return TimeDuration.of( defaultDuration, TimeDuration.Unit.SECONDS );
        }
        return TimeDuration.of( duration, TimeDuration.Unit.SECONDS );
    }

    public TimeDuration getTokenDurationSMS( final Configuration configuration )
    {
        final long duration = readSettingAsLong( PwmSetting.UPDATE_PROFILE_TOKEN_LIFETIME_SMS );
        if ( duration < 1 )
        {
            final long defaultDuration = configuration.readSettingAsLong( PwmSetting.TOKEN_LIFETIME );
            return TimeDuration.of( defaultDuration, TimeDuration.Unit.SECONDS );
        }
        return TimeDuration.of( duration, TimeDuration.Unit.SECONDS );
    }
}

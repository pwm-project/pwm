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

package password.pwm.config.profile;

import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.util.java.TimeDuration;

public class UpdateProfileProfile extends AbstractProfile implements Profile
{

    private static final ProfileDefinition PROFILE_TYPE = ProfileDefinition.UpdateAttributes;

    protected UpdateProfileProfile( final DomainID domainID, final ProfileID identifier, final StoredConfiguration storedConfiguration )
    {
        super( domainID, identifier, storedConfiguration );
    }

    @Override
    public ProfileDefinition profileType( )
    {
        return PROFILE_TYPE;
    }

    public TimeDuration getTokenDurationEmail( final DomainConfig domainConfig )
    {
        final long duration = readSettingAsLong( PwmSetting.UPDATE_PROFILE_TOKEN_LIFETIME_EMAIL );
        if ( duration < 1 )
        {
            final long defaultDuration = domainConfig.readSettingAsLong( PwmSetting.TOKEN_LIFETIME );
            return TimeDuration.of( defaultDuration, TimeDuration.Unit.SECONDS );
        }
        return TimeDuration.of( duration, TimeDuration.Unit.SECONDS );
    }

    public TimeDuration getTokenDurationSMS( final DomainConfig domainConfig )
    {
        final long duration = readSettingAsLong( PwmSetting.UPDATE_PROFILE_TOKEN_LIFETIME_SMS );
        if ( duration < 1 )
        {
            final long defaultDuration = domainConfig.readSettingAsLong( PwmSetting.TOKEN_LIFETIME );
            return TimeDuration.of( defaultDuration, TimeDuration.Unit.SECONDS );
        }
        return TimeDuration.of( duration, TimeDuration.Unit.SECONDS );
    }

    public static class UpdateProfileProfileFactory implements ProfileFactory
    {
        @Override
        public Profile makeFromStoredConfiguration( final StoredConfiguration storedConfiguration, final DomainID domainID, final ProfileID identifier )
        {
            return new UpdateProfileProfile( domainID, identifier, storedConfiguration );
        }
    }
}

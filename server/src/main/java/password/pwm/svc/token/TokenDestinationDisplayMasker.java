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

package password.pwm.svc.token;

import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.StringUtil;

public class TokenDestinationDisplayMasker
{
    private final Configuration configuration;
    private boolean enabled;

    public TokenDestinationDisplayMasker( final Configuration configuration )
    {
        this.configuration = configuration;
        this.enabled = configuration.readSettingAsBoolean( PwmSetting.TOKEN_ENABLE_VALUE_MASKING );
    }

    public String maskEmail( final String email )
    {
        if ( !enabled )
        {
            return email;
        }

        if ( StringUtil.isEmpty( email ) )
        {
            return "";
        }

        final String regex = configuration.readAppProperty( AppProperty.TOKEN_MASK_EMAIL_REGEX );
        final String replace = configuration.readAppProperty( AppProperty.TOKEN_MASK_EMAIL_REPLACE );
        return email.replaceAll( regex, replace );
    }

    public String maskPhone( final String phone )
    {
        if ( !enabled )
        {
            return phone;
        }

        if ( StringUtil.isEmpty( phone ) )
        {
            return "";
        }

        final String regex = configuration.readAppProperty( AppProperty.TOKEN_MASK_SMS_REGEX );
        final String replace = configuration.readAppProperty( AppProperty.TOKEN_MASK_SMS_REPLACE );
        return phone.replaceAll( regex, replace );
    }
}




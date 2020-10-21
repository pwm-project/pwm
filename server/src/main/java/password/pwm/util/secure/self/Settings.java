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

package password.pwm.util.secure.self;

import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.TimeDuration;

@Value
@Builder
public class Settings
{
    @Builder.Default
    private int keySize = 1024;

    @Builder.Default
    private String keyAlg = "RSA";

    @Builder.Default
    private long futureSeconds = TimeDuration.of( 30, TimeDuration.Unit.DAYS ).as( TimeDuration.Unit.SECONDS );

    @Builder.Default
    private String siteUrl = "http://" + PwmConstants.PWM_APP_NAME.toLowerCase() + ".example.com";

    public static Settings fromConfiguration ( final Configuration config )
    {
        return Settings.builder()
            .keySize( Integer.parseInt( config.readAppProperty( AppProperty.SECURITY_HTTPSSERVER_SELF_KEY_SIZE )  ) )
            .keyAlg( config.readAppProperty( AppProperty.SECURITY_HTTPSSERVER_SELF_ALG ) )
            .futureSeconds( Long.parseLong( config.readAppProperty( AppProperty.SECURITY_HTTPSSERVER_SELF_FUTURESECONDS ) ) )
            .siteUrl( config.readSettingAsString( PwmSetting.PWM_SITE_URL ) )
            .build();
    }
}

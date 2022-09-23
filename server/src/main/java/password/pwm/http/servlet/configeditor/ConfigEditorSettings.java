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

package password.pwm.http.servlet.configeditor;

import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.config.AppConfig;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;

@Value
public class ConfigEditorSettings
{
    private TimeDuration maxWaitSettingsFunction;

    static ConfigEditorSettings fromAppConfig( final AppConfig appConfig )
    {
        final TimeDuration waitTime = TimeDuration.of( JavaHelper.silentParseLong(
                        appConfig.readAppProperty( AppProperty.CONFIG_EDITOR_SETTING_FUNCTION_TIMEOUT_MS ), 30_000 ),
                TimeDuration.Unit.MILLISECONDS );

        return new ConfigEditorSettings( waitTime );
    }
}

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

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;

import java.io.Serializable;
import java.util.Locale;

@Value
class SearchResultItem implements Serializable
{
    private final String category;
    private final String value;
    private final String navigation;

    @SerializedName( "default" )
    private final boolean defaultValue;
    private final String profile;

    static SearchResultItem fromKey(
            final StoredConfigKey key,
            final StoredConfiguration storedConfiguration,
            final Locale locale )
    {
        final PwmSetting setting = key.toPwmSetting();
        return new SearchResultItem(
                setting.getCategory().toString(),
                storedConfiguration.readStoredValue( key ).orElseThrow().toDebugString( locale ),
                setting.getCategory().toMenuLocationDebug( key.getProfileID().orElse( null ), locale ),
                StoredConfigurationUtil.isDefaultValue( storedConfiguration, key ),
                key.getProfileID().map( v -> v.stringValue() ).orElse( null )
        );
    }
}

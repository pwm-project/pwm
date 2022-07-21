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

import lombok.Builder;
import lombok.Value;
import password.pwm.bean.UserIdentity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

@Value
@Builder
public class ReadSettingResponse implements Serializable
{
    private final boolean isDefault;
    private final String key;
    private final String category;
    private final Instant modifyTime;
    private final UserIdentity modifyUser;
    private final String syntax;
    private final Object value;
    private final Map<String, String> options;
}

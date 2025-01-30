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

package password.pwm.ws.client.rest.form;

import lombok.Builder;
import lombok.Value;
import password.pwm.bean.ProfileID;
import password.pwm.config.value.data.FormConfiguration;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class FormDataRequestBean
{
    private Map<String, String> formValues;
    private List<FormConfiguration> formConfigurations;

    @Value
    @Builder
    public static class FormInfo
    {
        private FormType module;
        private ProfileID moduleProfileID;
        private Mode mode;
        private String sessionID;
    }

    private FormInfo formInfo;
    private String userDN;
    private ProfileID ldapProfileID;

    public enum FormType
    {
        NewUser,
    }

    public enum Mode
    {
        edit,
        read,
        verify,
        write,
    }
}

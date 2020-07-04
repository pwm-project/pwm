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

package password.pwm.util.form;

import password.pwm.bean.SessionLabel;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FormState
{
    private final List<FormConfiguration> formConfigurations;
    private final Map<String, String> values;

    private FormState( final List<FormConfiguration> formConfigurations, final Map<String, String> values )
    {
        this.formConfigurations = Collections.unmodifiableList( formConfigurations );
        this.values = values;
    }

    public static FormState initialize( final List<FormConfiguration> formConfigurations )
    {
        final FormState formState = new FormState(
                Collections.unmodifiableList( formConfigurations ),
                new HashMap<>()
        );
        return formState;
    }

    public void readDataFromSources( final SessionLabel sessionLabel, final UserInfo userInfo )
            throws PwmUnrecoverableException
    {
        FormUtility.populateFormMapFromLdap( formConfigurations, sessionLabel, userInfo );
    }
}

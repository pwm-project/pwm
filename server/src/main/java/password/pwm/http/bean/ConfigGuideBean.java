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

package password.pwm.http.bean;

import lombok.Data;
import lombok.EqualsAndHashCode;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.config.value.FileValue;
import password.pwm.http.servlet.configguide.ConfigGuideForm;
import password.pwm.http.servlet.configguide.ConfigGuideFormField;
import password.pwm.http.servlet.configguide.GuideStep;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode( callSuper = false )
public class ConfigGuideBean extends PwmSessionBean
{

    private GuideStep step = GuideStep.START;
    private final Map<ConfigGuideFormField, String> formData = new EnumMap<>( ConfigGuideForm.defaultForm() );
    private List<X509Certificate> ldapCertificates;
    private boolean certsTrustedbyKeystore = false;
    private boolean useConfiguredCerts = false;
    private FileValue databaseDriver = null;

    @Override
    public Type getType( )
    {
        return Type.PUBLIC;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.singleton( SessionBeanMode.LOCAL );
    }
}

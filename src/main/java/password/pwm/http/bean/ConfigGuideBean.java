/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.http.bean;

import lombok.Getter;
import lombok.Setter;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.config.value.FileValue;
import password.pwm.http.servlet.configguide.ConfigGuideForm;
import password.pwm.http.servlet.configguide.ConfigGuideFormField;
import password.pwm.http.servlet.configguide.GuideStep;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class ConfigGuideBean extends PwmSessionBean {

    private GuideStep step = GuideStep.START;
    private final Map<ConfigGuideFormField,String> formData = new HashMap<>(ConfigGuideForm.defaultForm());
    private X509Certificate[] ldapCertificates;
    private boolean certsTrustedbyKeystore = false;
    private boolean useConfiguredCerts = false;
    private FileValue databaseDriver = null;

    public Type getType() {
        return Type.PUBLIC;
    }

    @Override
    public Set<SessionBeanMode> supportedModes() {
        return Collections.singleton(SessionBeanMode.LOCAL);
    }
}

/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.config.StoredConfiguration;
import password.pwm.http.servlet.ConfigGuideServlet;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ConfigGuideBean implements PwmSessionBean {

    private ConfigGuideServlet.STEP step = ConfigGuideServlet.STEP.START;
    private StoredConfiguration storedConfiguration = StoredConfiguration.getDefaultConfiguration();
    private Map<String,String> formData = ConfigGuideServlet.defaultForm(storedConfiguration.getTemplate());
    private X509Certificate[] ldapCertificates;
    private boolean certsTrustedbyKeystore = false;
    private boolean useConfiguredCerts = false;
    private boolean needsDbConfiguration = false;

    public ConfigGuideBean() {
        ConfigGuideServlet.updateLdapInfo(this.getStoredConfiguration(), new HashMap<>(ConfigGuideServlet.defaultForm(storedConfiguration.getTemplate())), Collections.<String,String>emptyMap());
        this.getStoredConfiguration().writeAppProperty(AppProperty.LDAP_PROMISCUOUS_ENABLE,"true");
    }

    public ConfigGuideServlet.STEP getStep() {
        return step;
    }

    public void setStep(ConfigGuideServlet.STEP step) {
        this.step = step;
    }

    public StoredConfiguration getStoredConfiguration() {
        return storedConfiguration;
    }

    public void setStoredConfiguration(StoredConfiguration storedConfiguration) {
        this.storedConfiguration = storedConfiguration;
    }

    public Map<String, String> getFormData() {
        return formData;
    }

    public void setFormData(Map<String, String> formData) {
        this.formData = formData;
    }

    public X509Certificate[] getLdapCertificates() {
        return ldapCertificates;
    }

    public void setLdapCertificates(X509Certificate[] ldapCertificates) {
        this.ldapCertificates = ldapCertificates;
    }

    public boolean isCertsTrustedbyKeystore() {
        return certsTrustedbyKeystore;
    }

    public void setCertsTrustedbyKeystore(boolean certsTrustedbyKeystore) {
        this.certsTrustedbyKeystore = certsTrustedbyKeystore;
    }

    public boolean isUseConfiguredCerts() {
        return useConfiguredCerts;
    }

    public void setUseConfiguredCerts(boolean useConfiguredCerts) {
        this.useConfiguredCerts = useConfiguredCerts;
    }
}

/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.value.FileValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.configguide.ConfigGuideForm;
import password.pwm.http.servlet.configguide.GuideStep;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class ConfigGuideBean implements PwmSessionBean {

    private GuideStep step = GuideStep.START;
    private StoredConfigurationImpl storedConfiguration;
    private PwmSettingTemplate selectedTemplate = null;
    private Map<ConfigGuideForm.FormParameter,String> formData = new HashMap<>();
    private X509Certificate[] ldapCertificates;
    private boolean certsTrustedbyKeystore = false;
    private boolean useConfiguredCerts = false;
    private FileValue databaseDriver = null;

    public ConfigGuideBean() {
        try {
            storedConfiguration = StoredConfigurationImpl.newStoredConfiguration();
        } catch (PwmUnrecoverableException e) {
            throw new IllegalStateException(e);
        }
    }

    public GuideStep getStep() {
        return step;
    }

    public void setStep(GuideStep step) {
        this.step = step;
    }

    public StoredConfigurationImpl getStoredConfiguration() {
        return storedConfiguration;
    }

    public void setStoredConfiguration(StoredConfigurationImpl storedConfiguration) {
        this.storedConfiguration = storedConfiguration;
    }

    public Map<ConfigGuideForm.FormParameter, String> getFormData() {
        return formData;
    }

    public void setFormData(Map<ConfigGuideForm.FormParameter, String> formData) {
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

    public PwmSettingTemplate getSelectedTemplate()
    {
        return selectedTemplate;
    }

    public void setSelectedTemplate(PwmSettingTemplate selectedTemplate)
    {
        this.selectedTemplate = selectedTemplate;
    }

    public FileValue getDatabaseDriver() {
        return databaseDriver;
    }

    public void setDatabaseDriver(FileValue databaseDriver) {
        this.databaseDriver = databaseDriver;
    }
}

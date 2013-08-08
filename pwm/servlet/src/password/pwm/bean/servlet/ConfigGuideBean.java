package password.pwm.bean.servlet;

import password.pwm.bean.PwmSessionBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.config.value.BooleanValue;
import password.pwm.servlet.ConfigGuideServlet;

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
        ConfigGuideServlet.updateLdapInfo(this.getStoredConfiguration(), new HashMap<String, String>(ConfigGuideServlet.defaultForm(storedConfiguration.getTemplate())), Collections.<String,String>emptyMap());
        this.getStoredConfiguration().writeSetting(PwmSetting.LDAP_PROMISCUOUS_SSL,new BooleanValue(true));
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

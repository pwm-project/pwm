package password.pwm.bean;

import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.config.value.BooleanValue;
import password.pwm.servlet.InstallManagerServlet;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class InstallManagerBean implements PwmSessionBean {

    private InstallManagerServlet.STEP step = InstallManagerServlet.STEP.START;
    private StoredConfiguration storedConfiguration = StoredConfiguration.getDefaultConfiguration();
    private Map<String,String> formData = InstallManagerServlet.defaultForm(storedConfiguration.getTemplate());
    private X509Certificate[] ldapCertificates = new X509Certificate[0];
    private boolean certsTrustedbyKeystore = false;
    private boolean useConfiguredCerts = false;
    private boolean needsDbConfiguration = false;

    public InstallManagerBean() {
        InstallManagerServlet.updateLdapInfo(this.getStoredConfiguration(),new HashMap<String,String>(InstallManagerServlet.defaultForm(storedConfiguration.getTemplate())));
        this.getStoredConfiguration().writeSetting(PwmSetting.LDAP_PROMISCUOUS_SSL,new BooleanValue(true));
    }

    public InstallManagerServlet.STEP getStep() {
        return step;
    }

    public void setStep(InstallManagerServlet.STEP step) {
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

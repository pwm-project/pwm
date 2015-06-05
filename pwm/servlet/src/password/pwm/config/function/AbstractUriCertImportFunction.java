package password.pwm.config.function;

import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingUIFunction;
import password.pwm.config.StoredConfiguration;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.util.X509Utils;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

abstract class AbstractUriCertImportFunction implements SettingUIFunction {

    @Override
    public String provideFunction(
            PwmRequest pwmRequest,
            StoredConfiguration storedConfiguration,
            PwmSetting setting,
            String profile
    )
            throws PwmOperationalException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Set<X509Certificate> resultCertificates = new LinkedHashSet<>();

        final String naafUrl = (String)storedConfiguration.readSetting(getSetting()).toNativeObject();
        if (naafUrl != null && !naafUrl.isEmpty()) {
            try {
                final X509Certificate[] certs = X509Utils.readRemoteCertificates(URI.create(naafUrl));
                if (certs != null) {
                    resultCertificates.addAll(Arrays.asList(certs));
                }
            } catch (Exception e) {
                if (e instanceof PwmException) {
                    throw new PwmOperationalException(((PwmException) e).getErrorInformation());
                }
                ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"error importing certificates: " + e.getMessage());
                throw new PwmOperationalException(errorInformation);
            }
        } else {
            ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"Setting " + getSetting().toMenuLocationDebug(null, null) + " must first be configured");
            throw new PwmOperationalException(errorInformation);
        }

        final UserIdentity userIdentity = pwmSession.getSessionStateBean().isAuthenticated() ? pwmSession.getUserInfoBean().getUserIdentity() : null;
        storedConfiguration.writeSetting(setting, new X509CertificateValue(resultCertificates), userIdentity);

        final StringBuffer returnStr = new StringBuffer();
        for (final X509Certificate loopCert : resultCertificates) {
            returnStr.append(X509Utils.makeDebugText(loopCert));
            returnStr.append("\n\n");
        }
        return returnStr.toString();
        //return Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), Message.Success_Unknown, pwmApplication.getConfig());
    }

    abstract PwmSetting getSetting();


}

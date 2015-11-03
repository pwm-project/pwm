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

package password.pwm.config.function;

import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingUIFunction;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.error.*;
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
            StoredConfigurationImpl storedConfiguration,
            PwmSetting setting,
            String profile
    )
            throws PwmOperationalException, PwmUnrecoverableException {
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

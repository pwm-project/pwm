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

package password.pwm.config.function;

import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingUIFunction;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.util.secure.X509Utils;

import java.net.URI;
import java.security.cert.X509Certificate;

abstract class AbstractUriCertImportFunction implements SettingUIFunction {

    @Override
    public String provideFunction(
            final PwmRequest pwmRequest,
            final StoredConfigurationImpl storedConfiguration,
            final PwmSetting setting,
            final String profile,
            final String extraData)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final X509Certificate[] certs;

        final String urlString = getUri(storedConfiguration, setting, profile, extraData);
            try {
                certs = X509Utils.readRemoteCertificates(URI.create(urlString));
            } catch (Exception e) {
                if (e instanceof PwmException) {
                    throw new PwmOperationalException(((PwmException) e).getErrorInformation());
                }
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"error importing certificates: " + e.getMessage());
                throw new PwmOperationalException(errorInformation);
            }


        final UserIdentity userIdentity = pwmSession.isAuthenticated() ? pwmSession.getUserInfo().getUserIdentity() : null;
        store(certs, storedConfiguration, setting, profile, extraData, userIdentity);

        final StringBuffer returnStr = new StringBuffer();
        for (final X509Certificate loopCert : certs) {
            returnStr.append(X509Utils.makeDebugText(loopCert));
            returnStr.append("\n\n");
        }
        return returnStr.toString();
    }

    abstract String getUri(StoredConfigurationImpl storedConfiguration,  PwmSetting pwmSetting,  String profile,  String extraData) throws PwmOperationalException;


    void store(final X509Certificate[] certs, final StoredConfigurationImpl storedConfiguration, final PwmSetting pwmSetting, final String profile, final String extraData, final UserIdentity userIdentity) throws PwmOperationalException, PwmUnrecoverableException {
        storedConfiguration.writeSetting(pwmSetting, profile, new X509CertificateValue(certs), userIdentity);
    }


}

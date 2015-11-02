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
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.secure.HttpsServerCertificateManager;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

public class HttpsCertParseFunction extends AbstractUriCertImportFunction {

    @Override
    public String provideFunction(PwmRequest pwmRequest, StoredConfigurationImpl storedConfiguration, PwmSetting setting, String profile)
            throws PwmUnrecoverableException
    {
        final PwmEnvironment pwmEnvironment = pwmRequest.getPwmApplication().getPwmEnvironment().makeRuntimeInstance(new Configuration(storedConfiguration));
        final PwmApplication tempApplication = new PwmApplication(pwmEnvironment);
        final HttpsServerCertificateManager httpsCertificateManager = new HttpsServerCertificateManager(tempApplication);
        return keyStoreToStringOutput(httpsCertificateManager.configToKeystore());
    }

    @Override
    PwmSetting getSetting() {
        return null;
    }

    String keyStoreToStringOutput(final KeyStore keyStore) throws PwmUnrecoverableException {

        final StringBuilder sb = new StringBuilder();
        try {
            for (final Enumeration<String> aliases = keyStore.aliases(); aliases.hasMoreElements(); ) {
                final String alias = aliases.nextElement();
                final X509Certificate certificate = (X509Certificate)keyStore.getCertificate(alias);
                sb.append("--- Certificate alias \"").append(alias).append("\" ---\n");
                sb.append(certificate.toString());
                if (aliases.hasMoreElements()) {
                    sb.append("\n\n");
                }
            }
        } catch (KeyStoreException e) {
            final String errorMsg = "error parsing pkcs12 file: " + e.getMessage();
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorMsg, new String[]{errorMsg}));
        }
        return sb.toString();
    }

}

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

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.JavaHelper;

import java.net.URI;

public class OAuthCertImportFunction extends AbstractUriCertImportFunction {


    @Override
    String getUri(final StoredConfigurationImpl storedConfiguration, final PwmSetting pwmSetting, final String profile, final String extraData) throws PwmOperationalException {

        final String uriString;
        final String menuDebugLocation;

        switch (pwmSetting) {
            case OAUTH_ID_CERTIFICATE:
                uriString = (String)storedConfiguration.readSetting(PwmSetting.OAUTH_ID_CODERESOLVE_URL).toNativeObject();
                menuDebugLocation = PwmSetting.OAUTH_ID_CODERESOLVE_URL.toMenuLocationDebug(null, PwmConstants.DEFAULT_LOCALE);
                break;

            case RECOVERY_OAUTH_ID_CERTIFICATE:
                uriString = (String)storedConfiguration.readSetting(PwmSetting.RECOVERY_OAUTH_ID_CODERESOLVE_URL, profile).toNativeObject();
                menuDebugLocation = PwmSetting.RECOVERY_OAUTH_ID_CERTIFICATE.toMenuLocationDebug(profile, PwmConstants.DEFAULT_LOCALE);
                break;

            default:
                JavaHelper.unhandledSwitchStatement(pwmSetting);
                return null;
        }


        if (uriString.isEmpty()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"Setting " + menuDebugLocation + " must first be configured");
            throw new PwmOperationalException(errorInformation);
        }
        try {
            URI.create(uriString);
        } catch (IllegalArgumentException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"Setting " + menuDebugLocation + " has an invalid URL syntax");
            throw new PwmOperationalException(errorInformation);
        }
        return uriString;
    }
}

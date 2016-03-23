/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;

import java.net.URI;

public class NAAFCertImportFunction extends AbstractUriCertImportFunction {

    final static PwmSetting uriSourceSetting = PwmSetting.NAAF_WS_URL;

    @Override
    String getUri(StoredConfigurationImpl storedConfiguration, PwmSetting pwmSetting, String profile, String extraData) throws PwmOperationalException {
        final String uriString = (String)storedConfiguration.readSetting(uriSourceSetting).toNativeObject();
        if (uriString == null || uriString.isEmpty()) {
            ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"Setting " + uriSourceSetting.toMenuLocationDebug(profile, null) + " must first be configured");
            throw new PwmOperationalException(errorInformation);
        }
        try {
            URI.create(uriString);
        } catch (IllegalArgumentException e) {
            ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"Setting " + uriSourceSetting.toMenuLocationDebug(profile, null) + " has an invalid URL syntax");
            throw new PwmOperationalException(errorInformation);
        }
        return uriString;
    }

}

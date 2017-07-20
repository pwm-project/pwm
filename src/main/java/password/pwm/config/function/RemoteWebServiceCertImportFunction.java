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
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.value.RemoteWebServiceValue;
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class RemoteWebServiceCertImportFunction extends AbstractUriCertImportFunction {

    @Override
    String getUri(final StoredConfigurationImpl storedConfiguration, final PwmSetting pwmSetting, final String profile, final String extraData) throws PwmOperationalException {
        final RemoteWebServiceValue actionValue = (RemoteWebServiceValue)storedConfiguration.readSetting(pwmSetting, profile);
        final String serviceName = actionNameFromExtraData(extraData);
        final RemoteWebServiceConfiguration action =  actionValue.forName(serviceName);
        final String uriString = action.getUrl();

        if (uriString == null || uriString.isEmpty()) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"Setting " + pwmSetting.toMenuLocationDebug(profile, null) + " action " + serviceName + " must first be configured");
            throw new PwmOperationalException(errorInformation);
        }
        try {
            URI.create(uriString);
        } catch (IllegalArgumentException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"Setting " + pwmSetting.toMenuLocationDebug(profile, null) + " action " + serviceName + " has an invalid URL syntax");
            throw new PwmOperationalException(errorInformation);
        }
        return uriString;
    }

    private String actionNameFromExtraData(final String extraData) {
        return extraData;
    }

    void store(final X509Certificate[] certs, final StoredConfigurationImpl storedConfiguration, final PwmSetting pwmSetting, final String profile, final String extraData, final UserIdentity userIdentity) throws PwmOperationalException, PwmUnrecoverableException {
        final RemoteWebServiceValue actionValue = (RemoteWebServiceValue)storedConfiguration.readSetting(pwmSetting, profile);
        final String actionName = actionNameFromExtraData(extraData);
        final List<RemoteWebServiceConfiguration> newList = new ArrayList<>();
        for (final RemoteWebServiceConfiguration loopConfiguration : actionValue.toNativeObject()) {
            if (actionName.equals(loopConfiguration.getName())) {
                final RemoteWebServiceConfiguration newConfig = JsonUtil.cloneUsingJson(loopConfiguration, RemoteWebServiceConfiguration.class);
                newConfig.setCertificates(certs);
                newList.add(newConfig);
            } else {
                newList.add(JsonUtil.cloneUsingJson(loopConfiguration,RemoteWebServiceConfiguration.class));
            }
        }
        final RemoteWebServiceValue newActionValue = new RemoteWebServiceValue(newList);
        storedConfiguration.writeSetting(pwmSetting, profile, newActionValue, userIdentity);
    }

}

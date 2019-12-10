/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.config.function;

import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingUIFunction;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.i18n.Message;
import password.pwm.util.secure.X509Utils;

import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LdapCertImportFunction implements SettingUIFunction
{
    @Override
    public String provideFunction(
            final PwmRequest pwmRequest,
            final StoredConfigurationModifier modifier,
            final PwmSetting setting,
            final String profile,
            final String extraData
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final StringArrayValue ldapUrlsValue = ( StringArrayValue ) modifier.newStoredConfiguration().readSetting( PwmSetting.LDAP_SERVER_URLS, profile );
        final Set<X509Certificate> resultCertificates = new LinkedHashSet<>();
        if ( ldapUrlsValue != null && ldapUrlsValue.toNativeObject() != null )
        {
            final List<String> ldapUrlStrings = ldapUrlsValue.toNativeObject();
            resultCertificates.addAll( X509Utils.readCertsForListOfLdapUrls( ldapUrlStrings, pwmRequest.getConfig() ) );
        }

        final UserIdentity userIdentity = pwmSession.isAuthenticated() ? pwmSession.getUserInfo().getUserIdentity() : null;
        modifier.writeSetting( setting, profile, new X509CertificateValue( resultCertificates ), userIdentity );
        return Message.getLocalizedMessage( pwmSession.getSessionStateBean().getLocale(), Message.Success_Unknown, pwmApplication.getConfig() );
    }

}

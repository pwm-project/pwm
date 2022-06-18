/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.http.servlet.configeditor.function;

import password.pwm.bean.UserIdentity;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.i18n.Message;
import password.pwm.svc.email.EmailServerUtil;
import password.pwm.util.java.CollectionUtil;

import java.security.cert.X509Certificate;
import java.util.List;

public class SmtpCertImportFunction implements SettingUIFunction
{
    @Override
    public String provideFunction(
            final PwmRequest pwmRequest,
            final StoredConfigurationModifier modifier,
            final StoredConfigKey key,
            final String extraData
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final String profile = key.getProfileID();

        final List<X509Certificate> certs = EmailServerUtil.readCertificates( pwmRequest.getAppConfig(), profile, pwmRequest.getLabel() );
        if ( !CollectionUtil.isEmpty( certs ) )
        {
            final UserIdentity userIdentity = pwmSession.isAuthenticated() ? pwmSession.getUserInfo().getUserIdentity() : null;
            modifier.writeSetting( key, X509CertificateValue.fromX509( certs ), userIdentity );
        }

        return Message.getLocalizedMessage( pwmSession.getSessionStateBean().getLocale(), Message.Success_Unknown, pwmRequest.getDomainConfig() );
    }

}

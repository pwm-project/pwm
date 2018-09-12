/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.i18n.Message;
import password.pwm.util.secure.X509Utils;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LdapCertImportFunction implements SettingUIFunction
{

    @Override
    public String provideFunction(
            final PwmRequest pwmRequest,
            final StoredConfigurationImpl storedConfiguration,
            final PwmSetting setting,
            final String profile,
            final String extraData )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final StringArrayValue ldapUrlsValue = ( StringArrayValue ) storedConfiguration.readSetting( PwmSetting.LDAP_SERVER_URLS, profile );
        final Set<X509Certificate> resultCertificates = new LinkedHashSet<>();
        try
        {
            if ( ldapUrlsValue != null && ldapUrlsValue.toNativeObject() != null )
            {
                final List<String> ldapUrlStrings = ldapUrlsValue.toNativeObject();
                for ( final String ldapUrlString : ldapUrlStrings )
                {
                    final URI ldapURI = new URI( ldapUrlString );
                    final List<X509Certificate> certs = X509Utils.readRemoteCertificates( ldapURI );
                    if ( certs != null )
                    {
                        resultCertificates.addAll( certs );
                    }
                }
            }
        }
        catch ( Exception e )
        {
            if ( e instanceof PwmException )
            {
                throw new PwmOperationalException( ( ( PwmException ) e ).getErrorInformation() );
            }
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, "error importing certificates: " + e.getMessage() );
            throw new PwmOperationalException( errorInformation );
        }

        final UserIdentity userIdentity = pwmSession.isAuthenticated() ? pwmSession.getUserInfo().getUserIdentity() : null;
        storedConfiguration.writeSetting( setting, profile, new X509CertificateValue( resultCertificates ), userIdentity );
        return Message.getLocalizedMessage( pwmSession.getSessionStateBean().getLocale(), Message.Success_Unknown, pwmApplication.getConfig() );
    }
}

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

import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingUIFunction;
import password.pwm.config.stored.StoredConfigurationModifier;
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
import java.util.List;

abstract class AbstractUriCertImportFunction implements SettingUIFunction
{

    @Override
    public String provideFunction(
            final PwmRequest pwmRequest,
            final StoredConfigurationModifier modifier,
            final PwmSetting setting,
            final String profile,
            final String extraData )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final List<X509Certificate> certs;

        final String urlString = getUri( modifier, setting, profile, extraData );
        try
        {
            final URI uri = URI.create( urlString );
            if ( "https".equalsIgnoreCase( uri.getScheme() ) )
            {
                certs = X509Utils.readRemoteHttpCertificates( pwmRequest.getPwmApplication(), pwmRequest.getLabel(), uri );
            }
            else
            {
                final Configuration configuration = new Configuration( modifier.newStoredConfiguration() );
                certs = X509Utils.readRemoteCertificates( URI.create( urlString ), configuration );
            }
        }
        catch ( final Exception e )
        {
            if ( e instanceof PwmException )
            {
                throw new PwmOperationalException( ( ( PwmException ) e ).getErrorInformation() );
            }
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, "error importing certificates: " + e.getMessage() );
            throw new PwmOperationalException( errorInformation );
        }


        final UserIdentity userIdentity = pwmSession.isAuthenticated() ? pwmSession.getUserInfo().getUserIdentity() : null;
        store( certs, modifier, setting, profile, extraData, userIdentity );

        final StringBuffer returnStr = new StringBuffer();
        for ( final X509Certificate loopCert : certs )
        {
            returnStr.append( X509Utils.makeDebugText( loopCert ) );
            returnStr.append( "\n\n" );
        }
        return returnStr.toString();
    }

    abstract String getUri(
            StoredConfigurationModifier modifier,
            PwmSetting pwmSetting,
            String profile,
            String extraData
    )
            throws PwmOperationalException, PwmUnrecoverableException;


    void store(
            final List<X509Certificate> certs,
            final StoredConfigurationModifier storedConfiguration,
            final PwmSetting pwmSetting,
            final String profile,
            final String extraData,
            final UserIdentity userIdentity
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        storedConfiguration.writeSetting( pwmSetting, profile, new X509CertificateValue( certs ), userIdentity );
    }


}

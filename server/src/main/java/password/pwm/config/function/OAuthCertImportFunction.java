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

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;

import java.net.URI;

public class OAuthCertImportFunction extends AbstractUriCertImportFunction
{


    @Override
    String getUri( final StoredConfigurationModifier modifier, final PwmSetting pwmSetting, final String profile, final String extraData )
            throws PwmOperationalException, PwmUnrecoverableException
    {

        final String uriString;
        final String menuDebugLocation;

        switch ( pwmSetting )
        {
            case OAUTH_ID_CERTIFICATE:
                uriString = ( String ) modifier.newStoredConfiguration().readSetting( PwmSetting.OAUTH_ID_CODERESOLVE_URL, null ).toNativeObject();
                menuDebugLocation = PwmSetting.OAUTH_ID_CODERESOLVE_URL.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
                break;

            case RECOVERY_OAUTH_ID_CERTIFICATE:
                uriString = ( String ) modifier.newStoredConfiguration().readSetting( PwmSetting.RECOVERY_OAUTH_ID_CODERESOLVE_URL, profile ).toNativeObject();
                menuDebugLocation = PwmSetting.RECOVERY_OAUTH_ID_CERTIFICATE.toMenuLocationDebug( profile, PwmConstants.DEFAULT_LOCALE );
                break;

            default:
                JavaHelper.unhandledSwitchStatement( pwmSetting );
                return null;
        }


        if ( uriString.isEmpty() )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, "Setting " + menuDebugLocation + " must first be configured" );
            throw new PwmOperationalException( errorInformation );
        }
        try
        {
            URI.create( uriString );
        }
        catch ( final IllegalArgumentException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, "Setting " + menuDebugLocation + " has an invalid URL syntax" );
            throw new PwmOperationalException( errorInformation );
        }
        return uriString;
    }
}

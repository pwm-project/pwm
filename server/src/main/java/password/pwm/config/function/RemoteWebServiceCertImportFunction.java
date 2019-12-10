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
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.RemoteWebServiceValue;
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class RemoteWebServiceCertImportFunction extends AbstractUriCertImportFunction
{

    @Override
    String getUri( final StoredConfigurationModifier modifier, final PwmSetting pwmSetting, final String profile, final String extraData )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final RemoteWebServiceValue actionValue = ( RemoteWebServiceValue ) modifier.newStoredConfiguration().readSetting( pwmSetting, profile );
        final String serviceName = actionNameFromExtraData( extraData );
        final RemoteWebServiceConfiguration action = actionValue.forName( serviceName );
        final String uriString = action.getUrl();

        if ( uriString == null || uriString.isEmpty() )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR,
                    "Setting " + pwmSetting.toMenuLocationDebug( profile, null ) + " action " + serviceName + " must first be configured" );
            throw new PwmOperationalException( errorInformation );
        }
        try
        {
            URI.create( uriString );
        }
        catch ( final IllegalArgumentException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR,
                    "Setting " + pwmSetting.toMenuLocationDebug( profile, null ) + " action " + serviceName + " has an invalid URL syntax" );
            throw new PwmOperationalException( errorInformation );
        }
        return uriString;
    }

    private String actionNameFromExtraData( final String extraData )
    {
        return extraData;
    }

    void store(
            final List<X509Certificate> certs,
            final StoredConfigurationModifier modifier,
            final PwmSetting pwmSetting,
            final String profile,
            final String extraData,
            final UserIdentity userIdentity
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final RemoteWebServiceValue actionValue = ( RemoteWebServiceValue ) modifier.newStoredConfiguration().readSetting( pwmSetting, profile );
        final String actionName = actionNameFromExtraData( extraData );
        final List<RemoteWebServiceConfiguration> newList = new ArrayList<>();
        for ( final RemoteWebServiceConfiguration loopConfiguration : actionValue.toNativeObject() )
        {
            if ( actionName.equals( loopConfiguration.getName() ) )
            {
                final RemoteWebServiceConfiguration newConfig = loopConfiguration.toBuilder()
                        .certificates( certs )
                        .build();
                newList.add( newConfig );
            }
            else
            {
                newList.add( loopConfiguration );
            }
        }
        final RemoteWebServiceValue newActionValue = new RemoteWebServiceValue( newList );
        modifier.writeSetting( pwmSetting, profile, newActionValue, userIdentity );
    }

}

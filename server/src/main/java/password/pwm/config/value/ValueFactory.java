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

package password.pwm.config.value;

import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.XmlElement;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

public class ValueFactory
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ValueFactory.class );

    public static StoredValue fromJson( final PwmSetting setting, final String input )
            throws PwmOperationalException
    {
        try
        {
            final StoredValue.StoredValueFactory factory = setting.getSyntax().getFactory();
            return factory.fromJson( input );
        }
        catch ( final Exception e )
        {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append( "error parsing value stored configuration value: " ).append( e.getMessage() );
            if ( e.getCause() != null )
            {
                errorMsg.append( ", cause: " ).append( e.getCause().getMessage() );
            }
            LOGGER.error( () -> errorMsg, e );
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg.toString() ) );
        }
    }

    public static StoredValue fromXmlValues( final PwmSetting setting, final XmlElement settingElement, final PwmSecurityKey key )
    {
        try
        {
            final StoredValue.StoredValueFactory factory = setting.getSyntax().getFactory();
            return factory.fromXmlElement( setting, settingElement, key );
        }
        catch ( final Exception e )
        {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append( "error parsing stored configuration value: " ).append( e.getMessage() );
            if ( e.getCause() != null )
            {
                errorMsg.append( ", cause: " ).append( e.getCause().getMessage() );
            }
            LOGGER.error( () -> errorMsg, e );
            throw new IllegalStateException( "unable to read xml element '" + settingElement.getName() + "' from setting '" + setting.getKey() + "' error: " + e.getMessage(), e );
        }
    }
}


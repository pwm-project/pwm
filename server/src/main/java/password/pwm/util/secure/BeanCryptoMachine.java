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

package password.pwm.util.secure;

import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.CommonValues;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;

public class BeanCryptoMachine<T extends Serializable>
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( BeanCryptoMachine.class );
    private static final String DELIMITER = ".";

    private final CommonValues commonValues;
    private final TimeDuration maxIdleTimeout;

    private String key;

    public BeanCryptoMachine( final CommonValues commonValues, final TimeDuration maxIdleTimeout )
    {
        this.commonValues = commonValues;
        this.maxIdleTimeout = maxIdleTimeout;
    }

    private String newKey()
    {
        final int length = Integer.parseInt( commonValues.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_NONCE_LENGTH ) );

        final String random = commonValues.getPwmApplication().getSecureService().pwmRandom().alphaNumericString( length );

        // timestamp component for uniqueness
        final String prefix = Long.toString( System.currentTimeMillis(), Character.MAX_RADIX );

        return random + prefix;
    }

    public Optional<T> decryprt(
            final String input
    )
            throws PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return Optional.empty();
        }

        final SecureService secureService = commonValues.getPwmApplication().getSecureService();
        final int delimiterIndex = input.indexOf( DELIMITER );
        final String key = input.substring( 0, delimiterIndex );
        final String payload = input.substring( delimiterIndex + 1 );
        final PwmSecurityKey pwmSecurityKey = secureService.appendedSecurityKey( key );
        final Wrapper wrapper = secureService.decryptObject( payload, pwmSecurityKey, Wrapper.class );

        final TimeDuration stateAge = TimeDuration.fromCurrent( wrapper.getTimestamp() );
        if ( stateAge.isLongerThan( maxIdleTimeout ) )
        {
            LOGGER.trace( commonValues.getSessionLabel(), () -> "state in request is " + stateAge.asCompactString() + " old" );
            return Optional.empty();
        }

        try
        {
            final Class restoreClass = Class.forName( wrapper.getClassName() );
            final Object bean = JsonUtil.deserialize( wrapper.getBean(), restoreClass );

            this.key = key;
            return Optional.of( ( T ) bean );
        }
        catch ( final ClassNotFoundException e )
        {
            final String msg = "error clasting return bean class";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, msg ) );
        }
    }

    public String encrypt( final T bean ) throws PwmUnrecoverableException
    {
        if ( key == null )
        {
            this.key = newKey();
        }

        final SecureService secureService = commonValues.getPwmApplication().getSecureService();
        final PwmSecurityKey pwmSecurityKey = secureService.appendedSecurityKey( key );
        final String className = bean.getClass().getName();
        final String jsonBean = JsonUtil.serialize( bean );
        final String payload = secureService.encryptObjectToString( new Wrapper( Instant.now(), className, jsonBean ), pwmSecurityKey );
        return key + DELIMITER + payload;
    }

    @Value
    static class Wrapper implements Serializable
    {
        private Instant timestamp;
        private String className;
        private String bean;
    }
}

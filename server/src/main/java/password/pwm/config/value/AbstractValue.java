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

package password.pwm.config.value;

import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.config.StoredValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.Locale;

public abstract class AbstractValue implements StoredValue
{
    static final String ENC_PW_PREFIX = "ENC-PW:";

    public String toString( )
    {
        return toDebugString( null );
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        return JsonUtil.serialize( ( Serializable ) this.toNativeObject(), JsonUtil.Flag.PrettyPrint );
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        return ( Serializable ) this.toNativeObject();
    }

    public boolean requiresStoredUpdate( )
    {
        return false;
    }

    @Override
    public int currentSyntaxVersion( )
    {
        return 0;
    }

    @Override
    public String valueHash( ) throws PwmUnrecoverableException
    {
        return SecureEngine.hash( JsonUtil.serialize( ( Serializable ) this.toNativeObject() ), PwmConstants.SETTING_CHECKSUM_HASH_METHOD );
    }

    static String decryptPwValue( final String input, final PwmSecurityKey pwmSecurityKey ) throws PwmOperationalException
    {
        if ( input == null )
        {
            return "";
        }

        if ( input.startsWith( ENC_PW_PREFIX ) )
        {
            try
            {
                final String pwValueSuffix = input.substring( ENC_PW_PREFIX.length(), input.length() );
                final String decrpytedValue = SecureEngine.decryptStringValue( pwValueSuffix, pwmSecurityKey, PwmBlockAlgorithm.CONFIG );
                final StoredPwData storedPwData = JsonUtil.deserialize( decrpytedValue, StoredPwData.class );
                return storedPwData.getValue();
            }
            catch ( Exception e )
            {
                final String errorMsg = "unable to decrypt password value for setting: " + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                throw new PwmOperationalException( errorInfo );
            }
        }

        return input;
    }

    static String encryptPwValue( final String input, final PwmSecurityKey pwmSecurityKey )
            throws PwmOperationalException
    {
        if ( input == null )
        {
            return "";
        }

        if ( !input.startsWith( ENC_PW_PREFIX ) )
        {
            try
            {
                final String salt = PwmRandom.getInstance().alphaNumericString( 32 );
                final StoredPwData storedPwData = new StoredPwData( salt, input );
                final String jsonData = JsonUtil.serialize( storedPwData );
                final String encryptedValue = SecureEngine.encryptToString( jsonData, pwmSecurityKey, PwmBlockAlgorithm.CONFIG );
                return ENC_PW_PREFIX + encryptedValue;
            }
            catch ( Exception e )
            {
                final String errorMsg = "unable to encrypt password value for setting: " + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                throw new PwmOperationalException( errorInfo );
            }
        }

        return input;
    }

    @Value
    static class StoredPwData implements Serializable
    {
        private String salt;
        private String value;
    }

}

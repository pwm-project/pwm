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

package password.pwm.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.Arrays;

/*
 * A in-memory password value wrapper.  Instances of this class cannot be serialized.  The actual password value is encrypted using a
 * a per-jvm instance key.
 *
 */
public class PasswordData implements Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordData.class );

    private final byte[] passwordData;

    // not a secure value, used to detect if key is same over time.
    private final String keyHash;

    private static final transient PwmSecurityKey STATIC_KEY;
    private static final transient String STATIC_KEY_HASH;
    private static final transient ErrorInformation INITIALIZATION_ERROR;

    private static final PwmBlockAlgorithm IN_MEMORY_PASSWORD_ENCRYPT_METHOD = PwmBlockAlgorithm.AES;

    private String passwordHashCache;

    static
    {
        PwmSecurityKey newKey = null;
        String newKeyHash = null;
        ErrorInformation newInitializationError = null;
        try
        {
            final byte[] randomBytes = new byte[ 1024 * 10 ];
            PwmRandom.getInstance().nextBytes( randomBytes );
            newKey = new PwmSecurityKey( randomBytes );
            newKeyHash = SecureEngine.hash( randomBytes, PwmHashAlgorithm.SHA512 );
        }
        catch ( Exception e )
        {
            LOGGER.fatal( "can't initialize PasswordData handler: " + e.getMessage(), e );
            e.printStackTrace();
            if ( e instanceof PwmException )
            {
                newInitializationError = ( ( PwmException ) e ).getErrorInformation();
            }
            else
            {
                newInitializationError = new ErrorInformation( PwmError.ERROR_UNKNOWN, "error initializing password data class: " + e.getMessage() );
            }
        }
        STATIC_KEY = newKey;
        STATIC_KEY_HASH = newKeyHash;
        INITIALIZATION_ERROR = newInitializationError;
    }

    public PasswordData( final String passwordData )
            throws PwmUnrecoverableException
    {
        checkInitStatus();
        if ( passwordData == null )
        {
            throw new NullPointerException( "password data can not be null" );
        }
        if ( passwordData.isEmpty() )
        {
            throw new NullPointerException( "password data can not be empty" );
        }
        this.passwordData = SecureEngine.encryptToBytes( passwordData, STATIC_KEY, IN_MEMORY_PASSWORD_ENCRYPT_METHOD );
        this.keyHash = STATIC_KEY_HASH;
    }

    private void checkInitStatus( )
            throws PwmUnrecoverableException
    {
        if ( STATIC_KEY == null || STATIC_KEY_HASH == null || INITIALIZATION_ERROR != null )
        {
            throw new PwmUnrecoverableException( INITIALIZATION_ERROR );
        }
    }

    private void checkCurrentStatus( )
            throws PwmUnrecoverableException
    {
        if ( !keyHash.equals( STATIC_KEY_HASH ) )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, "in-memory password is no longer valid" ) );
        }
    }

    public String getStringValue( )
            throws PwmUnrecoverableException
    {
        checkCurrentStatus();
        return SecureEngine.decryptBytes( passwordData, STATIC_KEY, IN_MEMORY_PASSWORD_ENCRYPT_METHOD );
    }

    @Override
    public String toString( )
    {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    @Override
    @SuppressFBWarnings( "EQ_UNUSUAL" )
    public boolean equals( final Object obj )
    {
        return equals( obj, false );
    }

    @Override
    public int hashCode( )
    {
        int result = Arrays.hashCode( passwordData );
        result = 31 * result + keyHash.hashCode();
        return result;
    }

    public boolean equalsIgnoreCase( final PasswordData obj )
    {
        return equals( obj, true );
    }

    private boolean equals( final Object obj, final boolean ignoreCase )
    {
        if ( obj == null )
        {
            return false;
        }
        if ( !( obj instanceof PasswordData ) )
        {
            return false;
        }

        try
        {
            final String strValue = this.getStringValue();
            final String objValue = ( ( PasswordData ) obj ).getStringValue();
            return ignoreCase ? strValue.equalsIgnoreCase( objValue ) : strValue.equals( objValue );
        }
        catch ( PwmUnrecoverableException e )
        {
            e.printStackTrace();
        }
        return super.equals( obj );
    }

    public static PasswordData forStringValue( final String input )
            throws PwmUnrecoverableException
    {
        return input == null || input.isEmpty()
                ? null
                : new PasswordData( input );
    }

    public String hash( ) throws PwmUnrecoverableException
    {
        if ( passwordHashCache == null )
        {
            passwordHashCache = SecureEngine.hash( this.getStringValue(), PwmHashAlgorithm.SHA1 );
        }
        return passwordHashCache;
    }
}

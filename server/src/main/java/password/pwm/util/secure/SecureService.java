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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class SecureService implements PwmService
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( SecureService.class );

    private PwmSecurityKey pwmSecurityKey;
    private PwmBlockAlgorithm defaultBlockAlgorithm;
    private PwmHashAlgorithm defaultHashAlgorithm;
    private PwmRandom pwmRandom;

    @Override
    public STATUS status( )
    {
        return STATUS.OPEN;
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        final Configuration config = pwmApplication.getConfig();
        pwmSecurityKey = config.getSecurityKey();
        {
            final String defaultBlockAlgString = config.readAppProperty( AppProperty.SECURITY_DEFAULT_EPHEMERAL_BLOCK_ALG );
            defaultBlockAlgorithm = JavaHelper.readEnumFromString( PwmBlockAlgorithm.class, PwmBlockAlgorithm.AES, defaultBlockAlgString );
            LOGGER.debug( () -> "using default ephemeral block algorithm: " + defaultBlockAlgorithm.getLabel() );
        }
        {
            final String defaultHashAlgString = config.readAppProperty( AppProperty.SECURITY_DEFAULT_EPHEMERAL_HASH_ALG );
            defaultHashAlgorithm = JavaHelper.readEnumFromString( PwmHashAlgorithm.class, PwmHashAlgorithm.SHA512, defaultHashAlgString );
            LOGGER.debug( () -> "using default ephemeral hash algorithm: " + defaultHashAlgString );
        }
    }

    @Override
    public void close( )
    {

    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return null;
    }

    public PwmBlockAlgorithm getDefaultBlockAlgorithm( )
    {
        return defaultBlockAlgorithm;
    }

    public PwmHashAlgorithm getDefaultHashAlgorithm( )
    {
        return defaultHashAlgorithm;
    }

    public String encryptToString( final String value )
            throws PwmUnrecoverableException
    {
        return SecureEngine.encryptToString( value, pwmSecurityKey, defaultBlockAlgorithm, SecureEngine.Flag.URL_SAFE );
    }

    public String encryptToString( final String value, final PwmSecurityKey securityKey )
            throws PwmUnrecoverableException
    {
        return SecureEngine.encryptToString( value, securityKey, defaultBlockAlgorithm, SecureEngine.Flag.URL_SAFE );
    }

    public String encryptObjectToString( final Serializable serializableObject ) throws PwmUnrecoverableException
    {
        final String jsonValue = JsonUtil.serialize( serializableObject );
        return encryptToString( jsonValue );
    }

    public String encryptObjectToString( final Serializable serializableObject, final PwmSecurityKey securityKey ) throws PwmUnrecoverableException
    {
        final String jsonValue = JsonUtil.serialize( serializableObject );
        return encryptToString( jsonValue, securityKey );
    }

    public String decryptStringValue(
            final String value
    )
            throws PwmUnrecoverableException
    {
        return SecureEngine.decryptStringValue( value, pwmSecurityKey, defaultBlockAlgorithm, SecureEngine.Flag.URL_SAFE );
    }

    public String decryptStringValue(
            final String value,
            final PwmSecurityKey securityKey
    )
            throws PwmUnrecoverableException
    {
        return SecureEngine.decryptStringValue( value, securityKey, defaultBlockAlgorithm, SecureEngine.Flag.URL_SAFE );
    }

    public <T extends Serializable> T decryptObject( final String value, final Class<T> returnClass ) throws PwmUnrecoverableException
    {
        final String decryptedValue = decryptStringValue( value );
        return JsonUtil.deserialize( decryptedValue, returnClass );
    }

    public <T extends Serializable> T decryptObject( final String value, final PwmSecurityKey securityKey, final Class<T> returnClass ) throws PwmUnrecoverableException
    {
        final String decryptedValue = decryptStringValue( value, securityKey );
        return JsonUtil.deserialize( decryptedValue, returnClass );
    }

    public String hash(
            final String input
    )
            throws PwmUnrecoverableException
    {
        return SecureEngine.hash( input, defaultHashAlgorithm );
    }

    public String hash(
            final PwmHashAlgorithm pwmHashAlgorithm,
            final String input
    )
            throws PwmUnrecoverableException
    {
        return SecureEngine.hash( input, pwmHashAlgorithm );
    }

    public DigestInputStream digestInputStream(
            final InputStream inputStream
    )
            throws PwmUnrecoverableException
    {
        return digestInputStream( this.getDefaultHashAlgorithm(), inputStream );
    }

    public DigestInputStream digestInputStream(
            final PwmHashAlgorithm pwmHashAlgorithm,
            final InputStream inputStream
    )
            throws PwmUnrecoverableException
    {
        try
        {
            final MessageDigest messageDigest = MessageDigest.getInstance( pwmHashAlgorithm.getAlgName() );
            return new DigestInputStream( inputStream, messageDigest );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_CRYPT_ERROR, "can't create digest inputstream: " + e.getMessage() );
        }
    }

    public String hash(
            final byte[] input
    )
            throws PwmUnrecoverableException
    {
        return SecureEngine.hash( input, defaultHashAlgorithm );
    }

    public String hash(
            final File file
    )
            throws IOException, PwmUnrecoverableException
    {
        return SecureEngine.hash( file, defaultHashAlgorithm );
    }

    public PwmRandom pwmRandom()
    {
        if ( pwmRandom == null )
        {
            pwmRandom = PwmRandom.getInstance();
        }
        return pwmRandom;
    }

    public PwmSecurityKey appendedSecurityKey( final String appendage ) throws PwmUnrecoverableException
    {
        final String hash = this.pwmSecurityKey.keyHash( this  );
        return new PwmSecurityKey( hash + appendage );
    }
}

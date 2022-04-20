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

package password.pwm.svc.secure;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.CopyingInputStream;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.HmacAlgorithm;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public abstract class AbstractSecureService extends AbstractPwmService implements PwmService, SecureService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DomainSecureService.class );

    protected PwmSecurityKey pwmSecurityKey;
    private PwmBlockAlgorithm defaultBlockAlgorithm;
    private PwmHashAlgorithm defaultHashAlgorithm;
    private HmacAlgorithm defaultHmacAlgorithm;
    private PwmRandom pwmRandom;

    private final StatisticCounterBundle<StatKey> stats = new StatisticCounterBundle<>( StatKey.class );

    enum StatKey
    {
        hashOperations,
        hashBytes,
        hmacOperations,
        hmacBytes,
        encryptOperations,
        encryptBytes,
        decryptOperations,
        decryptBytes,
    }

    enum DebugKeys
    {
        blockAlgorithm,
        hashAlgorithm,
    }

    @Override
    protected Set<PwmApplication.Condition> openConditions()
    {
        return Collections.emptySet();
    }

    protected STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        preAbstractSecureInit( pwmApplication, domainID );

        {
            final String defaultBlockAlgString = pwmApplication.getConfig().readAppProperty( AppProperty.SECURITY_DEFAULT_EPHEMERAL_BLOCK_ALG );
            defaultBlockAlgorithm = JavaHelper.readEnumFromString( PwmBlockAlgorithm.class, PwmBlockAlgorithm.AES, defaultBlockAlgString );
        }
        {
            final String defaultHashAlgString = pwmApplication.getConfig().readAppProperty( AppProperty.SECURITY_DEFAULT_EPHEMERAL_HASH_ALG );
            defaultHashAlgorithm = JavaHelper.readEnumFromString( PwmHashAlgorithm.class, PwmHashAlgorithm.SHA512, defaultHashAlgString );
        }
        {
            final String defaultHmacAlgString = pwmApplication.getConfig().readAppProperty( AppProperty.SECURITY_DEFAULT_EPHEMERAL_HMAC_ALG );
            defaultHmacAlgorithm = JavaHelper.readEnumFromString( HmacAlgorithm.class, HmacAlgorithm.HMAC_SHA_512, defaultHmacAlgString );
        }
        LOGGER.debug( getSessionLabel(), () -> "using default algorithms: " + StringUtil.mapToString( debugData() ) );

        return STATUS.OPEN;
    }

    protected abstract void preAbstractSecureInit( PwmApplication pwmApplication, DomainID domainID )
            throws PwmException;

    @Override
    public void close( )
    {
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    private Map<DebugKeys, String> debugData()
    {
        return Map.of(
                DebugKeys.blockAlgorithm, defaultBlockAlgorithm.getLabel(),
                DebugKeys.hashAlgorithm, defaultHashAlgorithm.getAlgName() );
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder()
                .debugProperties( CollectionUtil.combineOrderedMaps( List.of(
                        stats.debugStats(),
                        CollectionUtil.enumMapToStringMap( debugData() ) ) ) )
                .build();
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
        stats.increment( StatKey.encryptOperations );
        stats.increment( StatKey.encryptBytes, value.length() );
        return SecureEngine.encryptToString( value, pwmSecurityKey, defaultBlockAlgorithm, SecureEngine.Flag.URL_SAFE );
    }

    public String encryptToString( final String value, final PwmSecurityKey securityKey )
            throws PwmUnrecoverableException
    {
        stats.increment( StatKey.encryptOperations );
        stats.increment( StatKey.encryptBytes, value.length() );
        return SecureEngine.encryptToString( value, securityKey, defaultBlockAlgorithm, SecureEngine.Flag.URL_SAFE );
    }

    public String encryptObjectToString( final Serializable serializableObject ) throws PwmUnrecoverableException
    {
        final String jsonValue = JsonFactory.get().serialize( serializableObject );
        stats.increment( StatKey.encryptOperations );
        stats.increment( StatKey.encryptBytes, jsonValue.length() );
        return encryptToString( jsonValue );
    }

    public String encryptObjectToString( final Serializable serializableObject, final PwmSecurityKey securityKey ) throws PwmUnrecoverableException
    {
        final String jsonValue = JsonFactory.get().serialize( serializableObject );
        stats.increment( StatKey.encryptOperations );
        stats.increment( StatKey.encryptBytes, jsonValue.length() );
        return encryptToString( jsonValue, securityKey );
    }

    public String decryptStringValue(
            final String value
    )
            throws PwmUnrecoverableException
    {
        stats.increment( StatKey.decryptOperations );
        stats.increment( StatKey.decryptBytes, value.length() );
        return SecureEngine.decryptStringValue( value, pwmSecurityKey, defaultBlockAlgorithm, SecureEngine.Flag.URL_SAFE );
    }

    public String decryptStringValue(
            final String value,
            final PwmSecurityKey securityKey
    )
            throws PwmUnrecoverableException
    {
        stats.increment( StatKey.decryptOperations );
        stats.increment( StatKey.decryptBytes, value.length() );
        return SecureEngine.decryptStringValue( value, securityKey, defaultBlockAlgorithm, SecureEngine.Flag.URL_SAFE );
    }

    public <T extends Serializable> T decryptObject( final String value, final Class<T> returnClass ) throws PwmUnrecoverableException
    {
        final String decryptedValue = decryptStringValue( value );
        stats.increment( StatKey.decryptOperations );
        stats.increment( StatKey.decryptBytes, value.length() );
        return JsonFactory.get().deserialize( decryptedValue, returnClass );
    }

    public <T extends Serializable> T decryptObject( final String value, final PwmSecurityKey securityKey, final Class<T> returnClass ) throws PwmUnrecoverableException
    {
        final String decryptedValue = decryptStringValue( value, securityKey );
        stats.increment( StatKey.decryptOperations );
        stats.increment( StatKey.decryptBytes, value.length() );
        return JsonFactory.get().deserialize( decryptedValue, returnClass );
    }

    public String hash(
            final String input
    )
            throws PwmUnrecoverableException
    {
        stats.increment( StatKey.hashOperations );
        stats.increment( StatKey.hashBytes, input.length() );
        return SecureEngine.hash( input, defaultHashAlgorithm );
    }

    public String ephemeralHmac(
            final String input
    )
            throws PwmUnrecoverableException
    {
        stats.increment( StatKey.hmacOperations );
        stats.increment( StatKey.hmacBytes, input.length() );
        return SecureEngine.computeHmacToString( defaultHmacAlgorithm,  pwmSecurityKey, input );
    }

    @Override
    public String hash(
            final PwmHashAlgorithm pwmHashAlgorithm,
            final String input
    )
            throws PwmUnrecoverableException
    {
        stats.increment( StatKey.hashOperations );
        stats.increment( StatKey.hashBytes, input.length() );
        return SecureEngine.hash( input, pwmHashAlgorithm );
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
            stats.increment( StatKey.hashOperations );
            final Consumer<byte[]> consumer = bytes -> stats.increment( StatKey.hashBytes, bytes.length );
            return new DigestInputStream( new CopyingInputStream( inputStream, consumer ), messageDigest );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_CRYPT_ERROR, "can't create digest inputstream: " + e.getMessage() );
        }
    }

    @Override
    public String hash(
            final byte[] input
    )
            throws PwmUnrecoverableException
    {
        stats.increment( StatKey.hashOperations );
        stats.increment( StatKey.hashBytes, input.length );
        return SecureEngine.hash( input, defaultHashAlgorithm );
    }

    @Override
    public String hash(
            final InputStream input
    )
            throws PwmUnrecoverableException
    {
        stats.increment( StatKey.hashOperations );
        final Consumer<byte[]> consumer = bytes -> stats.increment( StatKey.hashBytes, bytes.length );
        return SecureEngine.hash( new CopyingInputStream( input, consumer ), defaultHashAlgorithm );
    }

    @Override
    public String hash(
            final File file
    )
            throws IOException, PwmUnrecoverableException
    {
        stats.increment( StatKey.hashOperations );
        stats.increment( StatKey.hashBytes, file.length() );
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

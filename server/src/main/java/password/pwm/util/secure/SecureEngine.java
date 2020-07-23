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

import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Random;


/**
 * Primary static security/crypto library for app.
 */
public class SecureEngine
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( SecureEngine.class );

    private static final int HASH_BUFFER_SIZE = 1024 * 4;
    private static final int HASH_FILE_BUFFER_SIZE = 1024 * 64;

    private static final NonceGenerator AES_GCM_NONCE_GENERATOR = new NonceGenerator( 8, 8 );

    private SecureEngine( )
    {
    }

    public enum Flag
    {
        URL_SAFE,
    }

    public static String encryptToString(
            final String value,
            final PwmSecurityKey key,
            final PwmBlockAlgorithm blockAlgorithm,
            final Flag... flags
    )
            throws PwmUnrecoverableException
    {
        try
        {
            final byte[] encrypted = encryptToBytes( value, key, blockAlgorithm );
            return Arrays.asList( flags ).contains( Flag.URL_SAFE )
                    ? StringUtil.base64Encode( encrypted, StringUtil.Base64Options.URL_SAFE, StringUtil.Base64Options.GZIP )
                    : StringUtil.base64Encode( encrypted );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error b64 encoding crypto result: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            LOGGER.error( () -> errorInformation.toDebugStr() );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    // in bytes
    static final int GCM_TAG_LENGTH = 16;

    public static byte[] encryptToBytes(
            final String value,
            final PwmSecurityKey key,
            final PwmBlockAlgorithm blockAlgorithm
    )
            throws PwmUnrecoverableException
    {
        try
        {
            if ( value == null || value.length() < 1 )
            {
                return null;
            }

            final SecretKey aesKey = key.getKey( blockAlgorithm.getBlockKey() );
            final byte[] nonce;
            final Cipher cipher;
            if ( blockAlgorithm == PwmBlockAlgorithm.AES128_GCM )
            {
                nonce = AES_GCM_NONCE_GENERATOR.nextValue();
                final GCMParameterSpec spec = new GCMParameterSpec( GCM_TAG_LENGTH * 8, nonce );
                cipher = Cipher.getInstance( blockAlgorithm.getAlgName() );
                cipher.init( Cipher.ENCRYPT_MODE, aesKey, spec );
            }
            else
            {
                cipher = Cipher.getInstance( blockAlgorithm.getAlgName() );
                cipher.init( Cipher.ENCRYPT_MODE, aesKey, cipher.getParameters() );
                nonce = null;
            }
            final byte[] encryptedBytes = cipher.doFinal( value.getBytes( PwmConstants.DEFAULT_CHARSET ) );

            final byte[] output;
            if ( blockAlgorithm.getHmacAlgorithm() != null )
            {
                final byte[] hashChecksum = computeHmacToBytes( blockAlgorithm.getHmacAlgorithm(), key, encryptedBytes );
                output = appendByteArrays( blockAlgorithm.getPrefix(), hashChecksum, encryptedBytes );
            }
            else
            {
                if ( nonce == null )
                {
                    output = appendByteArrays( blockAlgorithm.getPrefix(), encryptedBytes );
                }
                else
                {
                    final byte[] nonceLength = new byte[ 1 ];
                    nonceLength[ 0 ] = ( byte ) nonce.length;
                    output = appendByteArrays( blockAlgorithm.getPrefix(), nonceLength, nonce, encryptedBytes );
                }
            }
            return output;

        }
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error performing simple crypt operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            LOGGER.error( () -> errorInformation.toDebugStr() );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }


    public static String decryptStringValue(
            final String value,
            final PwmSecurityKey key,
            final PwmBlockAlgorithm blockAlgorithm,
            final Flag... flags
    )
            throws PwmUnrecoverableException
    {
        try
        {
            if ( value == null || value.length() < 1 )
            {
                return "";
            }

            final byte[] decoded = Arrays.asList( flags ).contains( Flag.URL_SAFE )
                    ? StringUtil.base64Decode( value, StringUtil.Base64Options.URL_SAFE, StringUtil.Base64Options.GZIP )
                    : StringUtil.base64Decode( value );
            return decryptBytes( decoded, key, blockAlgorithm );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error performing simple decrypt operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    public static String decryptBytes(
            final byte[] value,
            final PwmSecurityKey key,
            final PwmBlockAlgorithm blockAlgorithm
    )
            throws PwmUnrecoverableException
    {
        try
        {
            if ( value == null || value.length < 1 )
            {
                return null;
            }

            byte[] workingValue = verifyAndStripPrefix( blockAlgorithm, value );

            final SecretKey aesKey = key.getKey( blockAlgorithm.getBlockKey() );
            if ( blockAlgorithm.getHmacAlgorithm() != null )
            {
                final HmacAlgorithm hmacAlgorithm = blockAlgorithm.getHmacAlgorithm();
                final int checksumSize = hmacAlgorithm.getLength();
                if ( workingValue.length <= checksumSize )
                {
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_CRYPT_ERROR,
                            "incoming " + blockAlgorithm.toString() + " data is missing checksum" ) );
                }
                final byte[] inputChecksum = Arrays.copyOfRange( workingValue, 0, checksumSize );
                final byte[] inputPayload = Arrays.copyOfRange( workingValue, checksumSize, workingValue.length );
                final byte[] computedChecksum = computeHmacToBytes( hmacAlgorithm, key, inputPayload );
                if ( !Arrays.equals( inputChecksum, computedChecksum ) )
                {
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_CRYPT_ERROR,
                            "incoming " + blockAlgorithm.toString() + " data has incorrect checksum" ) );
                }
                workingValue = inputPayload;
            }
            final Cipher cipher;
            if ( blockAlgorithm == PwmBlockAlgorithm.AES128_GCM )
            {
                final int nonceLength = workingValue[ 0 ];
                workingValue = Arrays.copyOfRange( workingValue, 1, workingValue.length );
                if ( workingValue.length <= nonceLength )
                {
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, "incoming " + blockAlgorithm.toString() + " data is missing nonce" ) );
                }
                final byte[] nonce = Arrays.copyOfRange( workingValue, 0, nonceLength );
                workingValue = Arrays.copyOfRange( workingValue, nonceLength, workingValue.length );
                final GCMParameterSpec spec = new GCMParameterSpec( GCM_TAG_LENGTH * 8, nonce );
                cipher = Cipher.getInstance( blockAlgorithm.getAlgName() );
                cipher.init( Cipher.DECRYPT_MODE, aesKey, spec );
            }
            else
            {
                cipher = Cipher.getInstance( blockAlgorithm.getAlgName() );
                cipher.init( Cipher.DECRYPT_MODE, aesKey );
            }
            final byte[] decrypted = cipher.doFinal( workingValue );
            return new String( decrypted, PwmConstants.DEFAULT_CHARSET );
        }
        catch ( final GeneralSecurityException e )
        {
            final String errorMsg = "unexpected error performing simple decrypt operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    public static String hash(
            final byte[] input,
            final PwmHashAlgorithm algorithm
    )
            throws PwmUnrecoverableException
    {
        if ( input == null || input.length < 1 )
        {
            return null;
        }
        return hash( new ByteArrayInputStream( input ), algorithm );
    }

    public static String hash(
            final File file,
            final PwmHashAlgorithm hashAlgorithm
    )
            throws PwmUnrecoverableException
    {
        try
        {
            final MessageDigest messageDigest = MessageDigest.getInstance( hashAlgorithm.getAlgName() );
            final int bufferSize = (int) Math.min( file.length(), HASH_FILE_BUFFER_SIZE );
            final FileChannel fileChannel = FileChannel.open( file.toPath() );
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect( bufferSize );

            while ( fileChannel.read( byteBuffer ) > 0 )
            {
                // redundant cast to buffer to solve jdk8/9 inter-op issue
                ( ( Buffer ) byteBuffer ).flip();

                messageDigest.update( byteBuffer );

                // redundant cast to buffer to solve jdk8/9 inter-op issue
                ( ( Buffer ) byteBuffer ).clear();
            }

            return JavaHelper.byteArrayToHexString( messageDigest.digest() );

        }
        catch ( final NoSuchAlgorithmException | IOException e )
        {
            final String errorMsg = "unexpected error during file hash operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    public static String hash(
            final String input,
            final PwmHashAlgorithm algorithm
    )
            throws PwmUnrecoverableException
    {
        if ( input == null || input.length() < 1 )
        {
            return null;
        }
        return hash( new ByteArrayInputStream( input.getBytes( PwmConstants.DEFAULT_CHARSET ) ), algorithm );
    }

    public static String hash(
            final InputStream is,
            final PwmHashAlgorithm algorithm
    )
            throws PwmUnrecoverableException
    {
        return JavaHelper.byteArrayToHexString( computeHashToBytes( is, algorithm ) );
    }

    public static String hmac(
            final HmacAlgorithm hmacAlgorithm,
            final PwmSecurityKey pwmSecurityKey,
            final String input
    )
            throws PwmUnrecoverableException
    {
        return JavaHelper.byteArrayToHexString( computeHmacToBytes( hmacAlgorithm, pwmSecurityKey, input.getBytes( PwmConstants.DEFAULT_CHARSET ) ) );
    }

    public static byte[] computeHmacToBytes(
            final HmacAlgorithm hmacAlgorithm,
            final PwmSecurityKey pwmSecurityKey,
            final byte[] input
    )
            throws PwmUnrecoverableException
    {
        try
        {

            final Mac mac = Mac.getInstance( hmacAlgorithm.getAlgorithmName() );
            final SecretKey secretKey = pwmSecurityKey.getKey( hmacAlgorithm.getKeyType() );
            mac.init( secretKey );
            return mac.doFinal( input );
        }
        catch ( final GeneralSecurityException e )
        {
            final String errorMsg = "error during hmac operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }


    public static byte[] computeHashToBytes(
            final InputStream is,
            final PwmHashAlgorithm algorithm
    )
            throws PwmUnrecoverableException
    {

        final InputStream bis = is instanceof BufferedInputStream ? is : new BufferedInputStream( is );

        final MessageDigest messageDigest;
        try
        {
            messageDigest = MessageDigest.getInstance( algorithm.getAlgName() );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            final String errorMsg = "missing hash algorithm: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        try
        {
            final byte[] buffer = new byte[ HASH_BUFFER_SIZE ];
            int length;
            while ( true )
            {
                length = bis.read( buffer, 0, buffer.length );
                if ( length == -1 )
                {
                    break;
                }
                messageDigest.update( buffer, 0, length );
            }
            bis.close();

            return messageDigest.digest();
        }
        catch ( final IOException e )
        {
            final String errorMsg = "unexpected error during hash operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    private static byte[] appendByteArrays( final byte[]... input )
    {
        if ( input == null || input.length == 0 )
        {
            return new byte[ 0 ];
        }

        if ( input.length == 1 )
        {
            return input[ 0 ];
        }

        int totalLength = 0;
        for ( final byte[] loopBa : input )
        {
            totalLength += loopBa.length;
        }

        final byte[] output = new byte[ totalLength ];

        int position = 0;
        for ( final byte[] loopBa : input )
        {
            System.arraycopy( loopBa, 0, output, position, loopBa.length );
            position += loopBa.length;
        }
        return output;
    }

    static byte[] verifyAndStripPrefix( final PwmBlockAlgorithm blockAlgorithm, final byte[] input ) throws PwmUnrecoverableException
    {
        final byte[] definedPrefix = blockAlgorithm.getPrefix();
        if ( definedPrefix.length == 0 )
        {
            return input;
        }
        final byte[] inputPrefix = Arrays.copyOf( input, definedPrefix.length );
        if ( !Arrays.equals( definedPrefix, inputPrefix ) )
        {
            final String errorMsg = "value is missing valid prefix for decryption type";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        return Arrays.copyOfRange( input, definedPrefix.length, input.length );
    }

    static class NonceGenerator
    {
        private final byte[] value;

        private final int fixedComponentLength;

        NonceGenerator( final int fixedComponentLength, final int counterComponentLength )
        {
            this.fixedComponentLength = fixedComponentLength;
            value = new byte[ fixedComponentLength + counterComponentLength ];
            PwmRandom.getInstance().nextBytes( value );
        }

        public synchronized byte[] nextValue( )
        {
            increment( value.length - 1 );
            return Arrays.copyOf( value, value.length );
        }

        private void increment( final int index )
        {
            if ( value[ index ] == Byte.MAX_VALUE )
            {
                value[ index ] = 0;
                if ( index > fixedComponentLength )
                {
                    increment( index - 1 );
                }
            }
            else
            {
                value[ index ]++;
            }
        }
    }

    public static void benchmark( final Writer outputData ) throws PwmUnrecoverableException, IOException
    {
        final int testIterations = 10 * 1000;
        final Random random = new Random();
        final byte[] noise = new byte[ 1024 * 10 ];
        final PwmSecurityKey key = new PwmSecurityKey( PwmRandom.getInstance().newBytes( 1024 ) );
        for ( int i = 0; i < 10; i++ )
        {
            for ( final PwmBlockAlgorithm alg : PwmBlockAlgorithm.values() )
            {
                final Instant startTime = Instant.now();
                for ( int j = 0; j < testIterations; j++ )
                {
                    random.nextBytes( noise );
                    SecureEngine.encryptToString(
                            JavaHelper.binaryArrayToHex( noise ),
                            key,
                            alg
                    );
                }
                final TimeDuration executionDuration = TimeDuration.fromCurrent( startTime );
                outputData.write( "processed " + testIterations + " iterations using "
                        + alg.toString() + " (" + alg.getLabel() + ") in "
                        + executionDuration.asMillis() + "ms" );
                outputData.write( "\n" );
            }
        }
    }
}

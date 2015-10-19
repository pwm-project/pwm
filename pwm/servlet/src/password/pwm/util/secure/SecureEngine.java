/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.util.secure;

import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


/**
 * Primary static security/crypto library for app.
 */
public class SecureEngine {

    private static final PwmLogger LOGGER = PwmLogger.forClass(SecureEngine.class);

    private static final int HASH_BUFFER_SIZE = 1024 * 4;

    private SecureEngine() {
    }

    public enum Flag {
        URL_SAFE,
    }

    public static String encryptToString(
            final String value,
            final PwmSecurityKey key,
            final PwmBlockAlgorithm blockAlgorithm,
            final Flag... flags
    )
            throws PwmUnrecoverableException {
        try {
            final byte[] encrypted = encryptToBytes(value, key, blockAlgorithm);
            return Arrays.asList(flags).contains(Flag.URL_SAFE)
                    ? StringUtil.base64Encode(encrypted, StringUtil.Base64Options.URL_SAFE, StringUtil.Base64Options.GZIP)
                    : StringUtil.base64Encode(encrypted);
        } catch (Exception e) {
            final String errorMsg = "unexpected error b64 encoding crypto result: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            LOGGER.error(errorInformation.toDebugStr());
            throw new PwmUnrecoverableException(errorInformation);
        }
    }


    public static byte[] encryptToBytes(
            final String value,
            final PwmSecurityKey key,
            final PwmBlockAlgorithm blockAlgorithm
    )
            throws PwmUnrecoverableException {
        try {
            if (value == null || value.length() < 1) {
                return null;
            }

            final SecretKey aesKey = key.getKey(blockAlgorithm.getBlockKey());
            final Cipher cipher = Cipher.getInstance(blockAlgorithm.getAlgName());
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, cipher.getParameters());
            final byte[] encryptedBytes = cipher.doFinal(value.getBytes(PwmConstants.DEFAULT_CHARSET));

            final byte[] output;
            if (blockAlgorithm.getHmacAlgorithm() != null) {
                final byte[] hashChecksum = computeHmacToBytes(blockAlgorithm.getHmacAlgorithm(), key, encryptedBytes);
                output = appendByteArrays(blockAlgorithm.getPrefix(), hashChecksum, encryptedBytes);
            } else {
                output = appendByteArrays(blockAlgorithm.getPrefix(), encryptedBytes);
            }
            return output;

        } catch (Exception e) {
            final String errorMsg = "unexpected error performing simple crypt operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            LOGGER.error(errorInformation.toDebugStr());
            throw new PwmUnrecoverableException(errorInformation);
        }
    }


    public static String decryptStringValue(
            final String value,
            final PwmSecurityKey key,
            final PwmBlockAlgorithm blockAlgorithm,
            final Flag... flags
    )
            throws PwmUnrecoverableException {
        try {
            if (value == null || value.length() < 1) {
                return "";
            }

            final byte[] decoded = Arrays.asList(flags).contains(Flag.URL_SAFE)
                    ? StringUtil.base64Decode(value, StringUtil.Base64Options.URL_SAFE, StringUtil.Base64Options.GZIP)
                    : StringUtil.base64Decode(value);
            return decryptBytes(decoded, key, blockAlgorithm);
        } catch (Exception e) {
            final String errorMsg = "unexpected error performing simple decrypt operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    public static String decryptBytes(
            byte[] value,
            final PwmSecurityKey key,
            final PwmBlockAlgorithm blockAlgorithm
    )
            throws PwmUnrecoverableException {
        try {
            if (value == null || value.length < 1) {
                return null;
            }

            value = verifyAndStripPrefix(blockAlgorithm, value);

            final SecretKey aesKey = key.getKey(blockAlgorithm.getBlockKey());
            if (blockAlgorithm.getHmacAlgorithm() != null) {
                final HmacAlgorithm hmacAlgorithm = blockAlgorithm.getHmacAlgorithm();
                final int CHECKSUM_SIZE = hmacAlgorithm.getLength();
                if (value.length <= CHECKSUM_SIZE) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, "incoming " + blockAlgorithm.toString()  + " data is missing checksum"));
                }
                final byte[] inputChecksum = Arrays.copyOfRange(value, 0, CHECKSUM_SIZE);
                final byte[] inputPayload = Arrays.copyOfRange(value, CHECKSUM_SIZE, value.length);
                final byte[] computedChecksum = computeHmacToBytes(hmacAlgorithm, key, inputPayload);
                if (!Arrays.equals(inputChecksum, computedChecksum)) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, "incoming " + blockAlgorithm.toString()  + " data has incorrect checksum"));
                }
                value = inputPayload;
            }
            final Cipher cipher = Cipher.getInstance(blockAlgorithm.getAlgName());
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            final byte[] decrypted = cipher.doFinal(value);
            return new String(decrypted, PwmConstants.DEFAULT_CHARSET);
        } catch (Exception e) {
            final String errorMsg = "unexpected error performing simple decrypt operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    public static String md5sum(final String input)
            throws PwmUnrecoverableException {
        return hash(input, PwmHashAlgorithm.MD5);
    }

    public static String md5sum(final InputStream is)
            throws PwmUnrecoverableException {
        return hash(is, PwmHashAlgorithm.MD5);
    }

    public static String hash(
            final byte[] input,
            final PwmHashAlgorithm algorithm
    )
            throws PwmUnrecoverableException {
        if (input == null || input.length < 1) {
            return null;
        }
        return hash(new ByteArrayInputStream(input), algorithm);
    }

    public static String hash(
            final File file,
            final PwmHashAlgorithm hashAlgorithm
    )
            throws IOException, PwmUnrecoverableException {
        if (file == null || !file.exists()) {
            return null;
        }
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            return hash(fileInputStream, hashAlgorithm);
        } finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
        }
    }

    public static String hash(
            final String input,
            final PwmHashAlgorithm algorithm
    )
            throws PwmUnrecoverableException {
        if (input == null || input.length() < 1) {
            return null;
        }
        return hash(new ByteArrayInputStream(input.getBytes(PwmConstants.DEFAULT_CHARSET)), algorithm);
    }

    public static String hash(
            final InputStream is,
            final PwmHashAlgorithm algorithm
    )
            throws PwmUnrecoverableException {
        return Helper.byteArrayToHexString(computeHashToBytes(is, algorithm));
    }

    static byte[] computeHmacToBytes(
            final HmacAlgorithm hmacAlgorithm,
            final PwmSecurityKey pwmSecurityKey,
            final byte[] input
    )
            throws PwmUnrecoverableException
    {
        try {

            final Mac mac = Mac.getInstance(hmacAlgorithm.getAlgorithmName());
            final SecretKey secret_key = pwmSecurityKey.getKey(hmacAlgorithm.getKeyType());
            mac.init(secret_key);
            return mac.doFinal(input);
        } catch (GeneralSecurityException e) {
            final String errorMsg = "error during hmac operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }


    public static byte[] computeHashToBytes(
            final InputStream is,
            final PwmHashAlgorithm algorithm
    )
            throws PwmUnrecoverableException {

        final InputStream bis = is instanceof BufferedInputStream ? is : new BufferedInputStream(is);

        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(algorithm.getAlgName());
        } catch (NoSuchAlgorithmException e) {
            final String errorMsg = "missing hash algorithm: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try {
            final byte[] buffer = new byte[HASH_BUFFER_SIZE];
            int length;
            while (true) {
                length = bis.read(buffer, 0, buffer.length);
                if (length == -1) {
                    break;
                }
                messageDigest.update(buffer, 0, length);
            }
            bis.close();

            return messageDigest.digest();
        } catch (IOException e) {
            final String errorMsg = "unexpected error during hash operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    static byte[] appendByteArrays(final byte[]... input) {
        if (input == null || input.length == 0) {
            return new byte[0];
        }

        if (input.length == 1) {
            return input[0];
        }

        int totalLength = 0;
        for (final byte[] loopBa : input) {
            totalLength += loopBa.length;
        }

        final byte[] output = new byte[totalLength];

        int position = 0;
        for (final byte[] loopBa : input) {
            System.arraycopy(loopBa,0,output,position,loopBa.length);
            position += loopBa.length;
        }
        return output;
    }

    static byte[] verifyAndStripPrefix(final PwmBlockAlgorithm blockAlgorithm, final byte[] input) throws PwmUnrecoverableException {
        byte[] definedPrefix = blockAlgorithm.getPrefix();
        if (definedPrefix.length == 0) {
            return input;
        }
        byte[] inputPrefix = Arrays.copyOf(input, definedPrefix.length);
        if (!Arrays.equals(definedPrefix, inputPrefix)) {
            final String errorMsg = "value is missing valid prefix for decrpyption type";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        return Arrays.copyOfRange(input,definedPrefix.length,input.length);
    }

}

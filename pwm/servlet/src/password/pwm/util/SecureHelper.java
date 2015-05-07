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

package password.pwm.util;

import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SecureHelper {

    private static final PwmLogger LOGGER = PwmLogger.forClass(SecureHelper.class);

    private static final int HASH_BUFFER_SIZE = 1024;

    public static final HashAlgorithm DEFAULT_HASH_ALGORITHM = HashAlgorithm.SHA512;
    public static final BlockAlgorithm DEFAULT_BLOCK_ALGORITHM = BlockAlgorithm.AES;

    public enum HashAlgorithm {
        MD5("MD5"),
        SHA1("SHA1"),
        SHA256("SHA-256"),
        SHA512("SHA-512"),

        ;

        private final String algName;

        HashAlgorithm(String algName)
        {
            this.algName = algName;
        }

        public String getAlgName()
        {
            return algName;
        }
    }

    public enum BlockAlgorithm {
        AES("AES"),
        AES_CHECKSUM("AES/CBC/PKCS5Padding"),
        CONFIG("AES"),

        ;

        private final String algName;

        BlockAlgorithm(String algName)
        {
            this.algName = algName;
        }

        public String getAlgName()
        {
            return algName;
        }
    }

    public static String encryptToString(
            final String value,
            final SecretKey key
    )
            throws PwmUnrecoverableException
    {
        return encryptToString(value, key, false);
    }

    public static String encryptToString(
            final String value,
            final SecretKey key,
            final boolean urlSafe
    )
            throws PwmUnrecoverableException
    {
        try {
            final byte[] encrypted = encryptToBytes(value, key);
            return urlSafe
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
            final SecretKey key
    )
            throws PwmUnrecoverableException
    {
        return encryptToBytes(value, key, DEFAULT_BLOCK_ALGORITHM);
    }

    public static byte[] encryptToBytes(
            final String value,
            final SecretKey key,
            final BlockAlgorithm blockAlgorithm
    )
            throws PwmUnrecoverableException
    {
        try {
            if (value == null || value.length() < 1) {
                return null;
            }

            final Cipher cipher = Cipher.getInstance(blockAlgorithm.getAlgName());
            cipher.init(Cipher.ENCRYPT_MODE, key, cipher.getParameters());
            return cipher.doFinal(value.getBytes(PwmConstants.DEFAULT_CHARSET));
        } catch (Exception e) {
            final String errorMsg = "unexpected error performing simple crypt operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            LOGGER.error(errorInformation.toDebugStr());
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    public static String decryptStringValue(
            final String value,
            final SecretKey key
    )
            throws PwmUnrecoverableException
    {
        return decryptStringValue(value, key, false);
    }

    public static String decryptStringValue(
            final String value,
            final SecretKey key,
            final boolean urlSafe
    )
            throws PwmUnrecoverableException
    {

        return decryptStringValue(value, key, urlSafe, DEFAULT_BLOCK_ALGORITHM);
    }

    public static String decryptStringValue(
            final String value,
            final SecretKey key,
            final boolean urlSafe,
            final BlockAlgorithm blockAlgorithm
    )
            throws PwmUnrecoverableException
    {
        try {
            if (value == null || value.length() < 1) {
                return "";
            }

            final byte[] decoded = urlSafe
                    ? StringUtil.base64Decode(value, StringUtil.Base64Options.URL_SAFE,StringUtil.Base64Options.GZIP)
                    : StringUtil.base64Decode(value);
            return decryptBytes(decoded, key, blockAlgorithm);
        } catch (Exception e) {
            final String errorMsg = "unexpected error performing simple decrypt operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    public static String decryptBytes(
            final byte[] value,
            final SecretKey key
    )
            throws PwmUnrecoverableException
    {
        return decryptBytes(value, key, DEFAULT_BLOCK_ALGORITHM);
    }

    public static String decryptBytes(
            final byte[] value,
            final SecretKey key,
            final BlockAlgorithm blockAlgorithm
    )
            throws PwmUnrecoverableException
    {
        try {
            if (value == null || value.length < 1) {
                return null;
            }

            final Cipher cipher = Cipher.getInstance(blockAlgorithm.getAlgName());
            cipher.init(Cipher.DECRYPT_MODE, key);
            final byte[] decrypted = cipher.doFinal(value);
            return new String(decrypted,PwmConstants.DEFAULT_CHARSET);
        } catch (Exception e) {
            final String errorMsg = "unexpected error performing simple decrypt operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    public static SecretKey makeKey(final String text)
            throws PwmUnrecoverableException
    {
        try {
        final byte[] key = text.getBytes("iso-8859-1");
        return makeKey(key);
        } catch ( UnsupportedEncodingException e) {
            final String errorMsg = "unexpected error converting input text to crypto key bytes: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    public static SecretKey makeKey(final byte[] inputBytes)
            throws PwmUnrecoverableException {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(inputBytes, 0, inputBytes.length);
            final byte[] key = new byte[16];
            System.arraycopy(md.digest(), 0, key, 0, 16);
            return new SecretKeySpec(key, "AES");
        } catch (NoSuchAlgorithmException e) {
            final String errorMsg = "unexpected error generating simple crypto key: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    public static String md5sum(final String input)
            throws PwmUnrecoverableException
    {
        return hash(input, HashAlgorithm.MD5);
    }

    public static String md5sum(final File theFile)
            throws PwmUnrecoverableException, IOException {
        return md5sum(new FileInputStream(theFile));
    }

    public static String md5sum(final InputStream is)
            throws PwmUnrecoverableException {
        return hash(is, HashAlgorithm.MD5);
    }

    public static String hash(
            final byte[] input,
            final HashAlgorithm algorithm
    )
            throws PwmUnrecoverableException
    {
        if (input == null || input.length < 1) {
            return null;
        }
        return hash(new ByteArrayInputStream(input), algorithm);
    }

    public static String hash(
            final String input
    )
            throws PwmUnrecoverableException
    {
        if (input == null || input.length() < 1) {
            return null;
        }
        return hash(new ByteArrayInputStream(input.getBytes(PwmConstants.DEFAULT_CHARSET)), DEFAULT_HASH_ALGORITHM);
    }

    public static String hash(
            final String input,
            final HashAlgorithm algorithm
    )
            throws PwmUnrecoverableException
    {
        if (input == null || input.length() < 1) {
            return null;
        }
        return hash(new ByteArrayInputStream(input.getBytes(PwmConstants.DEFAULT_CHARSET)), algorithm);
    }

    public static String hash(
            final InputStream is,
            final HashAlgorithm algorithm
    )
            throws PwmUnrecoverableException
    {

        final InputStream bis = is instanceof BufferedInputStream ? is : new BufferedInputStream(is);

        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(algorithm.getAlgName());
        } catch (NoSuchAlgorithmException e) {
            final String errorMsg = "missing hash algorithm: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try
        {
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

            final byte[] bytes = messageDigest.digest();

            return Helper.byteArrayToHexString(bytes);
        } catch (IOException e) {
            final String errorMsg = "unexepected error during hash operation: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }
}

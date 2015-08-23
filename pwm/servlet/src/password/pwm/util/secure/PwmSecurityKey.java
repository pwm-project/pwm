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

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PwmSecurityKey {

    enum Type {
        AES,
        AES_256,
        HMAC_256,
        HMAC_512,
    }

    final private byte[] keyData;
    final private Map<Type,SecretKey> keyCache = new HashMap<>();

    public PwmSecurityKey(byte[] keyData) {
        this.keyData = keyData;
    }

    public PwmSecurityKey(String keyData) throws PwmUnrecoverableException {
        this.keyData = stringToKeyData(keyData);
    }

    byte[] stringToKeyData(final String input) throws PwmUnrecoverableException {
        try {
            return input.getBytes("iso-8859-1");
        } catch (UnsupportedEncodingException e) {
            final String errorMsg = "unexpected error converting input text to crypto key bytes: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    SecretKey getKey(Type keyType)
            throws PwmUnrecoverableException
    {
        if (!keyCache.containsKey(keyType)) {
            keyCache.put(keyType, getKeyImpl(keyType));
        }
        return keyCache.get(keyType);
    }

    private SecretKey getKeyImpl(Type keyType)
            throws PwmUnrecoverableException {
        switch (keyType) {
            case AES: {
                try {
                    final int KEY_LENGTH = 16;
                    final byte[] sha1Hash = SecureEngine.computeHashToBytes(new ByteArrayInputStream(keyData), PwmHashAlgorithm.SHA1);
                    final byte[] key = Arrays.copyOfRange(sha1Hash,0,KEY_LENGTH);
                    return new SecretKeySpec(key, "AES");
                } catch (Exception e) {
                    final String errorMsg = "unexpected error generating simple crypto key: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }

            case AES_256: {
                try {
                    final int KEY_LENGTH = 32;
                    final byte[] sha2Hash = SecureEngine.computeHashToBytes(new ByteArrayInputStream(keyData), PwmHashAlgorithm.SHA256);
                    final byte[] key = Arrays.copyOfRange(sha2Hash,0,KEY_LENGTH);
                    return new SecretKeySpec(key, "AES");
                } catch (Exception e) {
                    final String errorMsg = "unexpected error generating simple crypto key: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }

            case HMAC_256: {
                return new SecretKeySpec(keyData, "HmacSHA256");
            }

            case HMAC_512: {
                return new SecretKeySpec(keyData, "HmacSHA512");
            }
        }

        throw new IllegalStateException("unknown key type: " + keyType);
    }
}

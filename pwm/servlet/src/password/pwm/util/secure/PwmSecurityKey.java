package password.pwm.util.secure;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PwmSecurityKey {

    enum Type {
        AES,
        HMAC_256,
    }

    final private byte[] keyData;

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
            throws PwmUnrecoverableException {
        switch (keyType) {
            case AES: {
                try {
                    final int KEY_LENGTH = 16;
                    final MessageDigest md = MessageDigest.getInstance("SHA1");
                    md.update(keyData, 0, keyData.length);
                    final byte[] key = new byte[KEY_LENGTH];
                    System.arraycopy(md.digest(), 0, key, 0, KEY_LENGTH);
                    return new SecretKeySpec(key, "AES");
                } catch (NoSuchAlgorithmException e) {
                    final String errorMsg = "unexpected error generating simple crypto key: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }

            case HMAC_256: {
                return new SecretKeySpec(keyData, "HmacSHA256");
            }
        }

        throw new IllegalStateException("unknown key type: " + keyType);
    }
}

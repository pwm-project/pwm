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
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

/*
 * A in-memory password value wrapper.  Instances of this class cannot be serialized.  The actual password value is encrypted using a
 * a per-jvm instance key.
 *
 */
public class PasswordData {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PasswordData.class);

    private final byte[] passwordData;
    private final String keyHash; // not a secure value, used to detect if key is same over time.

    private static final PwmSecurityKey staticKey;
    private static final String staticKeyHash;
    private static final ErrorInformation initializationError;

    private String passwordHashCache;

    static {
        PwmSecurityKey newKey = null;
        String newKeyHash = null;
        ErrorInformation newInitializationError = null;
        try {
            final byte[] randomBytes = new byte[1024 * 10];
            PwmRandom.getInstance().nextBytes(randomBytes);
            newKey = new PwmSecurityKey(randomBytes);
            newKeyHash = SecureEngine.hash(randomBytes, PwmHashAlgorithm.SHA512);
        } catch (Exception e) {
            LOGGER.fatal("can't initialize PasswordData handler: " + e.getMessage(),e);
            e.printStackTrace();
            if (e instanceof PwmException) {
                newInitializationError = ((PwmException) e).getErrorInformation();
            } else {
                newInitializationError = new ErrorInformation(PwmError.ERROR_UNKNOWN,"error initializing password data class: " + e.getMessage());
            }
        }
        staticKey = newKey;
        staticKeyHash = newKeyHash;
        initializationError = newInitializationError;
    }

    public PasswordData(String passwordData)
            throws PwmUnrecoverableException
    {
        checkInitStatus();
        if (passwordData == null) {
            throw new NullPointerException("password data can not be null");
        }
        if (passwordData.isEmpty()) {
            throw new NullPointerException("password data can not be empty");
        }
        this.passwordData = SecureEngine.encryptToBytes(passwordData, staticKey, PwmConstants.IN_MEMORY_PASSWORD_ENCRYPT_METHOD);
        this.keyHash = staticKeyHash;
    }

    private void checkInitStatus()
            throws PwmUnrecoverableException
    {
        if (staticKey == null || staticKeyHash == null || initializationError != null) {
            throw new PwmUnrecoverableException(initializationError);
        }
    }

    private void checkCurrentStatus()
            throws PwmUnrecoverableException
    {
        if (!keyHash.equals(staticKeyHash)) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CRYPT_ERROR,"in-memory password is no longer valid"));
        }
    }

    public String getStringValue()
            throws PwmUnrecoverableException
    {
        checkCurrentStatus();
        return SecureEngine.decryptBytes(passwordData, staticKey, PwmConstants.IN_MEMORY_PASSWORD_ENCRYPT_METHOD);
    }

    @Override
    public String toString()
    {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    @Override
    public boolean equals(Object obj)
    {
        return equals(obj, false);
    }

    public boolean equalsIgnoreCase(PasswordData obj) {
        return equals(obj, true);
    }

    private boolean equals(Object obj, boolean ignoreCase)
    {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof PasswordData)) {
            return false;
        }

        try {
            final String strValue = this.getStringValue();
            final String objValue = ((PasswordData)obj).getStringValue();
            return ignoreCase ? strValue.equalsIgnoreCase(objValue) : strValue.equals(objValue);
        } catch (PwmUnrecoverableException e) {
            e.printStackTrace();
        }
        return super.equals(obj);
    }

    public static PasswordData forStringValue(final String input)
            throws PwmUnrecoverableException
    {
        return input == null || input.isEmpty()
                ? null
                : new PasswordData(input);
    }

    public String hash() throws PwmUnrecoverableException {
        if (passwordHashCache == null) {
            passwordHashCache = SecureEngine.hash(this.getStringValue(), PwmHashAlgorithm.SHA1);
        }
        return passwordHashCache;
    }
}

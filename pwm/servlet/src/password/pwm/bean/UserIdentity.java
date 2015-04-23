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

package password.pwm.bean;

import password.pwm.config.Configuration;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.JsonUtil;
import password.pwm.util.SecureHelper;

import javax.crypto.SecretKey;
import java.io.Serializable;
import java.util.StringTokenizer;

public class UserIdentity implements Serializable, Comparable {
    private static final String CRYPO_HEADER = "ui_C-";
    private static final String DELIM_SEPARATOR = "|";

    private transient String obfuscatedValue;

    private String userDN;
    private String ldapProfile;

    public UserIdentity(final String userDN, final String ldapProfile) {
        if (userDN == null || userDN.length() < 1) {
            throw new IllegalArgumentException("UserIdentity: userDN value cannot be empty");
        }
        this.userDN = userDN;
        this.ldapProfile = ldapProfile == null ? "" : ldapProfile;
    }

    public String getUserDN() {
        return userDN;
    }

    public String getLdapProfileID() {
        return ldapProfile;
    }

    public LdapProfile getLdapProfile(final Configuration configuration) {
        if (configuration == null) {
            return null;
        }
        if (configuration.getLdapProfiles().containsKey(this.getLdapProfileID())) {
            return configuration.getLdapProfiles().get(this.getLdapProfileID());
        } else {
            return null;
        }
    }

    public String toString() {
        return "UserIdentity: " + JsonUtil.serialize(this);
    }

    public String toObfuscatedKey(final Configuration configuration)
            throws PwmUnrecoverableException
    {
        final String cachedValue = obfuscatedValue;
        if (cachedValue != null) {
            return cachedValue;
        }
        try {
            final SecretKey secretKey = configuration.getSecurityKey();
            final String jsonValue = JsonUtil.serialize(this);
            final String localValue = CRYPO_HEADER + SecureHelper.encryptToString(jsonValue, secretKey, true);
            this.obfuscatedValue = localValue;
            return localValue;
        } catch (Exception e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected error making obfuscated user key: " + e.getMessage()));
        }
    }

    public String toDelimitedKey() {
        return this.getLdapProfileID() + DELIM_SEPARATOR + this.getUserDN();
    }

    public String toDisplayString() {
        return this.getUserDN() + ((this.getLdapProfileID() != null && !this.getLdapProfileID().isEmpty()) ? " (" + this.getLdapProfileID() + ")" : "");
    }

    public static UserIdentity fromObfuscatedKey(final String key, final Configuration configuration) throws PwmUnrecoverableException {
        if (key == null || key.length() < 1) {
            return null;
        }

        if (!key.startsWith(CRYPO_HEADER)) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"cannot reverse obfuscated user key: missing header; value=" + key));
        }

        try {
            final String input = key.substring(CRYPO_HEADER.length(),key.length());
            final SecretKey secretKey = configuration.getSecurityKey();
            final String jsonValue = SecureHelper.decryptStringValue(input, secretKey, true);
            return JsonUtil.deserialize(jsonValue,UserIdentity.class);
        } catch (Exception e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected error reversing obfuscated user key: " + e.getMessage()));
        }
    }

    public static UserIdentity fromDelimitedKey(final String key) throws PwmUnrecoverableException {
        if (key == null || key.length() < 1) {
            return null;
        }

        final StringTokenizer st = new StringTokenizer(key, DELIM_SEPARATOR);
        if (st.countTokens() < 2) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"not enough tokens while parsing delimited identity key"));
        } else if (st.countTokens() > 2) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"too many string tokens while parsing delimited identity key"));
        }
        final String profileID = st.nextToken();
        final String userDN = st.nextToken();
        return new UserIdentity(userDN,profileID);
    }

    public static UserIdentity fromKey(final String key, final Configuration configuration) throws PwmUnrecoverableException {
        if (key == null || key.length() < 1) {
            return null;
        }

        if (key.startsWith(CRYPO_HEADER)) {
            return fromObfuscatedKey(key,configuration);
        }

        return fromDelimitedKey(key);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserIdentity that = (UserIdentity) o;

        if (!ldapProfile.equals(that.ldapProfile)) return false;
        if (!userDN.equals(that.userDN)) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = userDN.hashCode();
        result = 31 * result + ldapProfile.hashCode();
        return result;
    }

    @Override
    public int compareTo(Object o) {
        String thisStr = (ldapProfile == null ? "_" : ldapProfile) + userDN;
        UserIdentity otherIdentity = (UserIdentity)o;
        String otherStr = (otherIdentity.ldapProfile == null ? "_" : otherIdentity.ldapProfile) + otherIdentity.userDN;

        return thisStr.compareTo(otherStr);
    }
}

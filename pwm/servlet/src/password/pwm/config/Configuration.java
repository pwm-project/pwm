/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.config;

import com.novell.ldapchai.cr.CrMode;
import password.pwm.PwmPasswordPolicy;

import java.io.Serializable;
import java.util.*;

/**
 * Simple data object, contains configuration information defined in the servlet .properties
 * configuration file.
 *
 * @author Jason D. Rivard
 */
public class Configuration implements Serializable {
// ------------------------------ FIELDS ------------------------------

    Map<String, String> newUserWriteAttributes = Collections.emptyMap();
    Map<String, String> activateUserWriteAttributes = Collections.emptyMap();
    Map<String, String> updateAttributesWriteAttributes = Collections.emptyMap();
    Map<String, String> loginContexts = Collections.emptyMap();

    PwmPasswordPolicy globalPasswordPolicy = PwmPasswordPolicy.defaultPolicy();
    private final Properties configProperties = new Properties();

// --------------------------- CONSTRUCTORS ---------------------------

    public Configuration()
    {
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public PwmPasswordPolicy getGlobalPasswordPolicy()
    {
        return globalPasswordPolicy;
    }

    public Map<String, String> getLoginContexts()
    {
        return loginContexts;
    }

    public Map<String, String> getUpdateAttributesWriteAttributes()
    {
        return updateAttributesWriteAttributes;
    }

// ------------------------ CANONICAL METHODS ------------------------

    public String toString()
    {
        final StringBuilder sb = new StringBuilder();

        for (final PwmSetting setting : PwmSetting.values()) {
            if (!setting.isConfidential()) {
                sb.append(setting.getKey());
                sb.append("=");
                sb.append(readSetting(setting));
                sb.append(", ");
            }
        }

        if (sb.length() > 2) {  // chop off last ", "
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.toString();
    }

    public Object readSetting(final PwmSetting setting)
    {
        return setting.parse(configProperties.getProperty(setting.getKey()));
    }

// -------------------------- OTHER METHODS --------------------------


    public String readSettingAsString(final PwmSetting setting)
    {
        final Object object = setting.parse(configProperties.getProperty(setting.getKey()));
        if (!(object instanceof String)) {
            throw new IllegalArgumentException("attempt to retreive string setting for " + setting + ", but setting type is not string");
        }

        return (String) object;
    }

    public Map<String, String> getActivateUserWriteAttributes()
    {
        return Collections.unmodifiableMap(activateUserWriteAttributes);
    }

    public Set<String> getAllUsedLdapAttributes()
    {
        final Set<String> returnSet = new HashSet<String>();

        returnSet.addAll(this.getActivateUserWriteAttributes().keySet());
        returnSet.add(this.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE));
        returnSet.add(this.readSettingAsString(PwmSetting.EVENT_LOG_ATTRIBUTE));
        returnSet.addAll(this.getGlobalPasswordPolicy().getRuleHelper().getDisallowedAttributes());
        returnSet.addAll(this.getNewUserCreationUniqueAttributes());
        returnSet.addAll(this.getNewUserWriteAttributes().keySet());
        returnSet.add(this.readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE));
        returnSet.addAll(this.getUpdateAttributesWriteAttributes().keySet());

        return returnSet;
    }

    public Set<String> getNewUserCreationUniqueAttributes()
    {
        final String[] values = (String[]) readSetting(PwmSetting.NEWUSER_UNIQUE_ATTRIBUES);
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }

    public Set<String> getAutoAddObjectClasses()
    {
        final String[] values = (String[]) readSetting(PwmSetting.AUTO_ADD_OBJECT_CLASSES);
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }

    public CR_RANDOM_STYLE getChallengeRandomStyle()
    {
        return (CR_RANDOM_STYLE) readSetting(PwmSetting.CHALLENGE_RANDOM_STYLE);
    }

    public int readSettingAsInt(final PwmSetting setting)
    {
        final Object object = setting.parse(configProperties.getProperty(setting.getKey()));
        if (!(object instanceof Integer)) {
            throw new IllegalArgumentException("attempt to retreive int setting for " + setting + ", but setting type is not int");
        }

        return (Integer) object;
    }



    public Set<String> getExternalPasswordMethods()
    {
        final String[] values = (String[]) readSetting(PwmSetting.EXTERNAL_PASSWORD_METHODS);
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }

    public List<String> getLdapServerURLs()
    {
        final String[] sA = (String[]) readSetting(PwmSetting.LDAP_SERVER_URLS);
        final List<String> ldapURLs = new ArrayList<String>(Arrays.asList(sA));
        return ldapURLs;
    }

    public Map<String, String> getNewUserWriteAttributes()
    {
        return Collections.unmodifiableMap(newUserWriteAttributes);
    }

    public int getPasswordSyncMaxWaitTime()
    {
        return readSettingAsInt(PwmSetting.PASSWORD_SYNC_MAX_WAIT_TIME);
    }

    public List<CR_POLICY_READ_METHOD> getPolicyReadMethod()
    {
        final CR_POLICY_READ_METHOD[] values = (CR_POLICY_READ_METHOD[]) readSetting(PwmSetting.CHALLENGE_POLICY_METHOD);
        return Arrays.asList(values);
    }

    public CrMode[] getResponseStorageMethod()
    {
        return (CrMode[]) readSetting(PwmSetting.CHALLENGE_STORAGE_METHOD);
    }

    public boolean readSettingAsBoolean(final PwmSetting setting)
    {
        final Object object = setting.parse(configProperties.getProperty(setting.getKey()));
        if (!(object instanceof Boolean)) {
            throw new IllegalArgumentException("attempt to retreive boolean setting for " + setting + ", but setting type is not boolean");
        }

        return (Boolean) object;
    }

    void setSetting(final PwmSetting setting, final String value)
    {
        configProperties.setProperty(setting.getKey(), value);
    }

    public String toString(final PwmSetting setting)
    {
        final Object value = readSetting(setting);
        return setting.debugValueString(value);
    }

// -------------------------- ENUMERATIONS --------------------------

// ----------------------------- CONSTANTS ----------------------------
    public enum CR_RANDOM_STYLE {
        SETUP,
        RECOVER
    }

    public enum CR_POLICY_READ_METHOD {
        PWM, NMAS
    }
}


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

package password.pwm;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;

import java.util.Map;
import java.util.Properties;

/**
 * @author Jason D. Rivard
 */
public enum Permission {
    PWMADMIN(PwmSetting.QUERY_MATCH_PWM_ADMIN),
    CHANGE_PASSWORD(PwmSetting.QUERY_MATCH_CHANGE_PASSWORD),
    ACTIVATE_USER(PwmSetting.QUERY_MATCH_ACTIVATE_USER),
    SETUP_RESPONSE(PwmSetting.QUERY_MATCH_SETUP_RESPONSE);

// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Permission.class);

    private PwmSetting pwmSetting;

// -------------------------- STATIC METHODS --------------------------

    public static boolean checkPermission(final Permission permission, final PwmSession pwmSession)
            throws ChaiUnavailableException, PwmException
    {
        PERMISSION_STATUS status = pwmSession.getUserInfoBean().getPermission(permission);
        if (status == PERMISSION_STATUS.UNCHECKED) {
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser actor = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
            final PwmSetting setting = permission.getPwmSetting();
            final boolean result = testQueryMatch(actor, pwmSession.getConfig().readSettingAsString(setting), permission.toString(), pwmSession);
            status = result ? PERMISSION_STATUS.GRANTED : PERMISSION_STATUS.DENIED;
            pwmSession.getUserInfoBean().setPermission(permission, status);
        }
        return status == PERMISSION_STATUS.GRANTED;
    }

    public static boolean testQueryMatch(final ChaiUser theUser, final String queryMatch, final String permissionName, final PwmSession pwmSession)
    {
        LOGGER.trace(pwmSession, "begin check for permission for " + theUser.getEntryDN() + " for " + permissionName + " using queryMatch: " + queryMatch);

        boolean result = false;

        if (queryMatch == null || queryMatch.length() < 1) {
            LOGGER.trace(pwmSession, "no " + permissionName + " defined, skipping check for " + permissionName);
        } else if ("(objectClass=*)".equals(queryMatch) || "objectClass=*".equalsIgnoreCase(queryMatch)) {
            LOGGER.trace(pwmSession, "permission check is guarenteed to be true, skipping ldap query");
            result = true;
        } else {
            try {
                LOGGER.trace(pwmSession, "checking ldap to see if " + theUser.getEntryDN() + " matches '" + queryMatch + "'");
                final Map<String, Properties> results = theUser.getChaiProvider().search(theUser.getEntryDN(), queryMatch, new String[]{}, ChaiProvider.SEARCH_SCOPE.BASE);
                if (results.size() == 1 && results.keySet().contains(theUser.getEntryDN())) {
                    result = true;
                }
            } catch (ChaiException e) {
                LOGGER.warn(pwmSession, "LDAP error during check for " + permissionName + " using " + queryMatch + ", " + e.getMessage());
            }
        }

        if (result) {
            LOGGER.debug(pwmSession, "user " + theUser.getEntryDN() + " is a match for '" + queryMatch + "', granting privilege for " + permissionName);
        } else {
            LOGGER.debug(pwmSession, "user " + theUser.getEntryDN() + " is not a match for '" + queryMatch + "', not granting privilege for " + permissionName);
        }
        return result;
    }

// --------------------------- CONSTRUCTORS ---------------------------

    Permission(final PwmSetting pwmSetting)
    {
        this.pwmSetting = pwmSetting;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public PwmSetting getPwmSetting()
    {
        return pwmSetting;
    }

// -------------------------- ENUMERATIONS --------------------------

    public enum PERMISSION_STATUS {
        UNCHECKED,
        GRANTED,
        DENIED
    }
}

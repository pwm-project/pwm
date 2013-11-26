/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;

import java.util.Collections;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public enum Permission {
    PWMADMIN(PwmSetting.QUERY_MATCH_PWM_ADMIN),
    CHANGE_PASSWORD(PwmSetting.QUERY_MATCH_CHANGE_PASSWORD),
    ACTIVATE_USER(PwmSetting.ACTIVATE_USER_QUERY_MATCH),
    SETUP_RESPONSE(PwmSetting.QUERY_MATCH_SETUP_RESPONSE),
    SETUP_OTP_SECRET(PwmSetting.QUERY_MATCH_OTP_SETUP_RESPONSE),
    GUEST_REGISTRATION(PwmSetting.GUEST_ADMIN_GROUP),
    PEOPLE_SEARCH(PwmSetting.PEOPLE_SEARCH_QUERY_MATCH),
    HELPDESK(PwmSetting.HELPDESK_QUERY_MATCH),
    PROFILE_UPDATE(PwmSetting.UPDATE_PROFILE_QUERY_MATCH),
    WEBSERVICE_THIRDPARTY(PwmSetting.WEBSERVICES_THIRDPARTY_QUERY_MATCH),

    ;
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Permission.class);

    private PwmSetting pwmSetting;

// -------------------------- STATIC METHODS --------------------------

    public static boolean checkPermission(final Permission permission, final PwmSession pwmSession, final PwmApplication pwmApplication)
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        LOGGER.trace(String.format("Enter: checkPermission(%s, %s, %s)", permission, pwmSession, pwmApplication));
        PERMISSION_STATUS status = pwmSession.getUserInfoBean().getPermission(permission);
        if (status == PERMISSION_STATUS.UNCHECKED) {
            LOGGER.debug(String.format("Checking permission %s for user %s", permission.toString(), pwmSession.getUserInfoBean().getUsername()));
            final PwmSetting setting = permission.getPwmSetting();
            final boolean result = testQueryMatch(pwmApplication, pwmSession, pwmSession.getUserInfoBean().getUserIdentity(), pwmApplication.getConfig().readSettingAsString(setting), permission.toString());
            status = result ? PERMISSION_STATUS.GRANTED : PERMISSION_STATUS.DENIED;
            LOGGER.debug(String.format("Permission status %s for user %s is %s", permission.toString(), pwmSession.getUserInfoBean().getUsername(), status.toString()));
            pwmSession.getUserInfoBean().setPermission(permission, status);
        }
        LOGGER.debug(String.format("Permission status: %s", status));
        return status == PERMISSION_STATUS.GRANTED;
    }

    public static boolean testQueryMatch(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity,
            final String queryMatch,
            final String permissionName
    ) throws PwmUnrecoverableException {
        LOGGER.trace(pwmSession, "begin check for permission for " + userIdentity + " for " + permissionName + " using queryMatch: " + queryMatch);

        boolean result = false;

        if (queryMatch == null || queryMatch.length() < 1) {
            LOGGER.trace(pwmSession, "no " + permissionName + " defined, skipping check for " + permissionName);
        } else if ("(objectClass=*)".equals(queryMatch) || "objectClass=*".equalsIgnoreCase(queryMatch)) {
            LOGGER.trace(pwmSession, "permission check is guaranteed to be true, skipping ldap query");
            result = true;
        } else {
            try {
                LOGGER.trace(pwmSession, "checking ldap to see if " + userIdentity + " matches '" + queryMatch + "'");
                final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
                final Map<String, Map<String,String>> results = theUser.getChaiProvider().search(theUser.getEntryDN(), queryMatch, Collections.<String>emptySet(), ChaiProvider.SEARCH_SCOPE.BASE);
                if (results.size() == 1 && results.keySet().contains(theUser.getEntryDN())) {
                    result = true;
                }
            } catch (ChaiException e) {
                LOGGER.warn(pwmSession, "LDAP error during check for " + permissionName + " using " + queryMatch + ", " + e.getMessage());
            }
        }

        if (result) {
            LOGGER.debug(pwmSession, "user " + userIdentity + " is a match for '" + queryMatch + "', granting privilege for " + permissionName);
        } else {
            LOGGER.debug(pwmSession, "user " + userIdentity + " is not a match for '" + queryMatch + "', not granting privilege for " + permissionName);
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

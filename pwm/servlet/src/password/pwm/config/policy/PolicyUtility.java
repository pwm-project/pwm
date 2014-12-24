/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.config.policy;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.util.logging.PwmLogger;

import java.util.List;
import java.util.Locale;

public class PolicyUtility {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PolicyUtility.class);

    protected static String determineChallengeProfileForUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final Locale locale
    ) {
        final List<String> profiles = pwmApplication.getConfig().getChallengeProfiles();
        if (profiles.isEmpty()) {
            throw new IllegalStateException("no available challenge profiles");
        } else if (profiles.size() == 1) {
            LOGGER.trace(sessionLabel, "only one challenge profile defined, returning default");
            return "";
        }

        for (final String profile : profiles) {
            if (!PwmConstants.DEFAULT_CHALLENGE_PROFILE.equalsIgnoreCase(profile)) {
                final ChallengeProfile loopPolicy = pwmApplication.getConfig().getChallengeProfile(profile, locale);
                final List<UserPermission> queryMatch = loopPolicy.getUserPermissions();
                if (queryMatch != null && !queryMatch.isEmpty()) {
                    LOGGER.debug(sessionLabel, "testing challenge profiles '" + profile + "'");
                    try {
                        boolean match = LdapPermissionTester.testUserPermissions(pwmApplication, sessionLabel, userIdentity,
                                queryMatch);
                        if (match) {
                            return profile;
                        }
                    } catch (PwmUnrecoverableException e) {
                        LOGGER.error(sessionLabel, "unexpected error while testing password policy profile '" + profile + "', error: " + e.getMessage());
                    }
                }
            }
        }

        return PwmConstants.DEFAULT_CHALLENGE_PROFILE;
    }

}

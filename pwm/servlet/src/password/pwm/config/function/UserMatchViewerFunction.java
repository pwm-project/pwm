/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2013 The PWM Project
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

package password.pwm.config.function;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingUIFunction;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.util.PwmLogger;

import java.util.*;

public class UserMatchViewerFunction implements SettingUIFunction {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserMatchViewerFunction.class);
    private static final int MAX_RESULT_SIZE = 5 * 1000;

    @Override
    public String provideFunction(
            PwmApplication pwmApplication,
            StoredConfiguration storedConfiguration,
            PwmSetting setting,
            String profile
    )
            throws PwmOperationalException
    {
        final String queryMatchString = (String)storedConfiguration.readSetting(setting,profile).toNativeObject();
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setFilter(queryMatchString);
        final StringBuilder output = new StringBuilder();
        try {
            final Map<UserIdentity, Map<String, String>> results = userSearchEngine.performMultiUserSearch(null,searchConfiguration,MAX_RESULT_SIZE, Collections.<String>emptyList());
            if (results.isEmpty()) {
                output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,"Display_SearchResultsNone",pwmApplication.getConfig()));
            } else {
                final Map<String,List<String>> sortedMap = new TreeMap<String,List<String>>();

                for (final UserIdentity userIdentity : results.keySet()) {
                    if (!sortedMap.containsKey(userIdentity.getLdapProfileID())) {
                        sortedMap.put(userIdentity.getLdapProfileID(), new ArrayList<String>());
                    }
                    sortedMap.get(userIdentity.getLdapProfileID()).add(userIdentity.getUserDN());
                }
                output.append("<table>");

                output.append("<tr><td class=\"title\">");
                output.append("Profile");
                output.append("</td><td class=\"title\">");
                output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,"Field_UserDN",pwmApplication.getConfig()));
                output.append("</td></tr>");

                for (final String loopProfile : sortedMap.keySet()) {
                    for (final String loopDN : sortedMap.get(loopProfile)) {
                        output.append("<tr><td>");
                        output.append(loopProfile == null || "".equals(loopProfile) ? "Default" : loopProfile);
                        output.append("</td><td>");
                        output.append(loopDN);
                        output.append("</td></tr>");
                    }
                }
                output.append("</table>");
                if (results.size() == MAX_RESULT_SIZE) {
                    output.append("<br/>");
                    output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,"Display_SearchResultsExceeded",pwmApplication.getConfig()));
                }
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("error reading matching users: " + e.getMessage());
        } catch (ChaiUnavailableException e) {
            LOGGER.error("error reading matching users: " + e.getMessage());
        }
        return output.toString();
    }
}

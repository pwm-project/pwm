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

package password.pwm.config.function;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingUIFunction;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.util.PwmLogger;

import java.util.*;

public class UserMatchViewerFunction implements SettingUIFunction {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserMatchViewerFunction.class);

    @Override
    public String provideFunction(
            PwmApplication pwmApplication,
            PwmSession pwmSession,
            StoredConfiguration storedConfiguration,
            PwmSetting setting,
            String profile
    )
            throws PwmOperationalException
    {
        final Locale userLocale = pwmSession == null ? PwmConstants.DEFAULT_LOCALE : pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmApplication.getConfig();
        final int maxResultSize = Integer.parseInt(
                config.readAppProperty(AppProperty.CONFIG_EDITOR_QUERY_FILTER_TEST_LIMIT));

        final String queryMatchString = (String)storedConfiguration.readSetting(setting,profile).toNativeObject();
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();

        searchConfiguration.setFilter(queryMatchString);
        final String output;
        try {
            final Map<UserIdentity, Map<String, String>> results = userSearchEngine.performMultiUserSearch(
                    null,
                    searchConfiguration,
                    maxResultSize,
                    Collections.<String>emptyList()
            );
            final Map<String,List<String>> sortedMap = sortResults(results);
            output = convertResultsToHtmlTable(
                    pwmApplication, userLocale, sortedMap, maxResultSize
            );
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("error reading matching users: " + e.getMessage());
            throw new PwmOperationalException(e.getErrorInformation());
        } catch (ChaiUnavailableException e) {
            LOGGER.error("error reading matching users: " + e.getMessage());
            throw new PwmOperationalException(PwmError.forChaiError(e.getErrorCode()));
        }
        return output;
    }

    private String convertResultsToHtmlTable(
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final Map<String,List<String>> sortedMap,
            final int maxResultSize
    )
    {
        final StringBuilder output = new StringBuilder();
        final Configuration config = pwmApplication.getConfig();

        if (sortedMap.isEmpty()) {
            output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,"Display_SearchResultsNone",pwmApplication.getConfig()));
        } else {
            output.append("<table>");
            output.append("<tr><td class=\"title\">");
            output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,"Field_LdapProfile",pwmApplication.getConfig()));
            output.append("</td><td class=\"title\">");
            output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,"Field_UserDN",pwmApplication.getConfig()));
            output.append("</td></tr>");

            for (final String loopProfile : sortedMap.keySet()) {
                final String profileName = config.getLdapProfiles().get(loopProfile).getDisplayName(userLocale);
                for (final String loopDN : sortedMap.get(loopProfile)) {
                    output.append("<tr><td>");
                    output.append(profileName);
                    output.append("</td><td>");
                    output.append(loopDN);
                    output.append("</td></tr>");
                }
            }
            output.append("</table>");
            if (sortedMap.size() >= maxResultSize) {
                output.append("<br/>");
                output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,"Display_SearchResultsExceeded",pwmApplication.getConfig()));
            }
        }

        return output.toString();
    }

    private Map<String,List<String>> sortResults(final Map<UserIdentity, Map<String, String>> results) {
        final Map<String,List<String>> sortedMap = new TreeMap<String,List<String>>();

        for (final UserIdentity userIdentity : results.keySet()) {
            if (!sortedMap.containsKey(userIdentity.getLdapProfileID())) {
                sortedMap.put(userIdentity.getLdapProfileID(), new ArrayList<String>());
            }
            sortedMap.get(userIdentity.getLdapProfileID()).add(userIdentity.getUserDN());
        }

        return sortedMap;
    }
}

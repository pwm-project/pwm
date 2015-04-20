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

package password.pwm.config.function;

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.*;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.i18n.Display;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.util.logging.PwmLogger;

import java.util.*;

public class UserMatchViewerFunction implements SettingUIFunction {
    private static final PwmLogger LOGGER = PwmLogger.forClass(UserMatchViewerFunction.class);

    @Override
    public String provideFunction(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final StoredConfiguration storedConfiguration,
            final PwmSetting setting,
            final String profile
    )
            throws Exception
    {
        final Locale userLocale = pwmSession == null ? PwmConstants.DEFAULT_LOCALE : pwmSession.getSessionStateBean().getLocale();
        final int maxResultSize = Integer.parseInt(
                pwmApplication.getConfig().readAppProperty(AppProperty.CONFIG_EDITOR_QUERY_FILTER_TEST_LIMIT));
        final Map<String,List<String>> matchingUsers = discoverMatchingUsers(maxResultSize, storedConfiguration, setting, profile);
        return convertResultsToHtmlTable(
                pwmApplication, userLocale, matchingUsers, maxResultSize
        );
    }

    public Map<String,List<String>> discoverMatchingUsers(
            final int maxResultSize,
            final StoredConfiguration storedConfiguration,
            final PwmSetting setting,
            final String profile
    )
            throws Exception
    {
        final Configuration config = new Configuration(storedConfiguration);
        final PwmApplication tempApplication = new PwmApplication.PwmEnvironment()
                .setConfig(config)
                .setApplicationMode(PwmApplication.MODE.CONFIGURATION)
                .setApplicationPath(null).setInitLogging(false)
                .setConfigurationFile(null)
                .setWebInfPath(null)
                .createPwmApplication();
        final List<UserPermission> permissions = (List<UserPermission>)storedConfiguration.readSetting(setting,profile).toNativeObject();

        for (final UserPermission userPermission : permissions) {
            if (userPermission.getType() == UserPermission.Type.ldapQuery) {
                if (userPermission.getLdapBase() != null && !userPermission.getLdapBase().isEmpty()) {
                    testIfLdapDNIsValid(tempApplication, userPermission.getLdapBase(), userPermission.getLdapProfileID());
                }
            } else if (userPermission.getType() == UserPermission.Type.ldapGroup) {
                testIfLdapDNIsValid(tempApplication, userPermission.getLdapBase(), userPermission.getLdapProfileID());
            }
        }

        final Map<UserIdentity, Map<String, String>> results = LdapPermissionTester.discoverMatchingUsers(tempApplication, maxResultSize, permissions);
        return sortResults(results);
    }

    public String convertResultsToHtmlTable(
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final Map<String,List<String>> sortedMap,
            final int maxResultSize
    )
    {
        final StringBuilder output = new StringBuilder();
        final Configuration config = pwmApplication.getConfig();

        if (sortedMap.isEmpty()) {
            output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,Display.Display_SearchResultsNone,pwmApplication.getConfig()));
        } else {
            output.append("<table>");
            output.append("<tr><td class=\"title\">");
            output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,Display.Field_LdapProfile,pwmApplication.getConfig()));
            output.append("</td><td class=\"title\">");
            output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,Display.Field_UserDN,pwmApplication.getConfig()));
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
                output.append(Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,Display.Display_SearchResultsExceeded,pwmApplication.getConfig()));
            }
        }

        return output.toString();
    }

    private Map<String,List<String>> sortResults(final Map<UserIdentity, Map<String, String>> results) {
        final Map<String,List<String>> sortedMap = new TreeMap<>();

        for (final UserIdentity userIdentity : results.keySet()) {
            if (!sortedMap.containsKey(userIdentity.getLdapProfileID())) {
                sortedMap.put(userIdentity.getLdapProfileID(), new ArrayList<String>());
            }
            sortedMap.get(userIdentity.getLdapProfileID()).add(userIdentity.getUserDN());
        }

        return sortedMap;
    }

    private void testIfLdapDNIsValid(final PwmApplication pwmApplication, final String baseDN, final String profileID)
            throws PwmOperationalException, PwmUnrecoverableException {
        final Set<String> profileIDsToTest = new LinkedHashSet<>();
        if (profileID == null || profileID.isEmpty()) {
            profileIDsToTest.add(pwmApplication.getConfig().getDefaultLdapProfile().getIdentifier());
        } else if (profileID.equals(PwmConstants.PROFILE_ID_ALL)) {
            profileIDsToTest.addAll(pwmApplication.getConfig().getLdapProfiles().keySet());
        } else {
            profileIDsToTest.add(profileID);
        }
        for (final String loopID : profileIDsToTest) {
            ChaiEntry chaiEntry = null;
            try {
                final ChaiProvider proxiedProvider = pwmApplication.getProxyChaiProvider(loopID);
                chaiEntry = ChaiFactory.createChaiEntry(baseDN, proxiedProvider);
            } catch (Exception e) {
                LOGGER.error("error while testing entry DN for profile '" + profileID + "', error:" + profileID);
            }
            if (chaiEntry != null && !chaiEntry.isValid()) {
                final String errorMsg = "entry DN '" + baseDN + "' is not valid for profile " + loopID;
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_LDAP_DATA_ERROR, errorMsg));
            }
        }
    }
}

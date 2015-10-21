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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.ShortcutItem;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.*;

@WebServlet(
        name="ShortcutServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/shortcuts",
                PwmConstants.URL_PREFIX_PRIVATE + "/Shortcuts",
        }
)
public class ShortcutServlet extends AbstractPwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.forClass(ShortcutServlet.class);

    public enum ShortcutAction implements AbstractPwmServlet.ProcessAction {
        selectShortcut,
        ;

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(HttpMethod.POST);
        }
    }

    protected ShortcutAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return ShortcutAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SHORTCUT_ENABLE)) {
            pwmRequest.respondWithError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            return;
        }

        if (pwmSession.getSessionStateBean().getVisibleShortcutItems() == null) {
            LOGGER.debug(pwmSession, "building visible shortcut list for user");
            final Map<String, ShortcutItem> visibleItems = figureVisibleShortcuts(pwmRequest);
            pwmSession.getSessionStateBean().setVisibleShortcutItems(visibleItems);
        } else {
            LOGGER.trace(pwmSession, "using cashed shortcut values");
        }

        final ShortcutAction action = readProcessAction(pwmRequest);
        if (action != null) {
            pwmRequest.validatePwmFormID();
            switch (action) {
                case selectShortcut:
                    handleUserSelection(pwmRequest);
                    return;
            }
        }

        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.SHORTCUT);
    }


    /**
     * Loop through each configured shortcut setting to determine if the shortcut is is able to the user pwmSession.
     */
    private static Map<String, ShortcutItem> figureVisibleShortcuts(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Collection<String> configValues = pwmRequest.getConfig().readSettingAsLocalizedStringArray(PwmSetting.SHORTCUT_ITEMS, pwmRequest.getLocale());

        final Set<String> labelsFromHeader = new HashSet<>();
        {
            final Map<String,List<String>> headerValueMap = pwmRequest.readHeaderValuesMap();
            final List<String> interestedHeaderNames = pwmRequest.getConfig().readSettingAsStringArray(PwmSetting.SHORTCUT_HEADER_NAMES);

            for (final String headerName : headerValueMap.keySet()) {
                if (interestedHeaderNames.contains(headerName)) {
                    for (final String loopValues : headerValueMap.get(headerName)) {
                        labelsFromHeader.addAll(StringHelper.tokenizeString(loopValues, ","));
                    }
                }
            }
        }

        final List<ShortcutItem> configuredItems = new ArrayList<>();
        for (final String loopStr : configValues) {
            final ShortcutItem item = ShortcutItem.parsePwmConfigInput(loopStr);
            configuredItems.add(item);
        }

        final Map<String, ShortcutItem> visibleItems = new LinkedHashMap<>();

        if (!labelsFromHeader.isEmpty()) {
            LOGGER.trace("detected the following labels from headers: " + StringHelper.stringCollectionToString(labelsFromHeader, ","));
            visibleItems.keySet().retainAll(labelsFromHeader);
        } else {
            for (final ShortcutItem item : configuredItems) {
                final boolean queryMatch = LdapPermissionTester.testQueryMatch(
                        pwmRequest.getPwmApplication(),
                        pwmRequest.getSessionLabel(),
                        pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity(),
                        item.getLdapQuery()
                );

                if (queryMatch) {
                    visibleItems.put(item.getLabel(), item);
                }
            }
        }

        return visibleItems;
    }

    private void handleUserSelection(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final String link = pwmRequest.readParameterAsString("link");
        final Map<String, ShortcutItem> visibleItems = pwmSession.getSessionStateBean().getVisibleShortcutItems();

        if (link != null && visibleItems.keySet().contains(link)) {
            final ShortcutItem item = visibleItems.get(link);

            pwmApplication.getStatisticsManager().incrementValue(Statistic.SHORTCUTS_SELECTED);
            LOGGER.trace(pwmSession, "shortcut link selected: " + link + ", setting link for 'forwardURL' to " + item.getShortcutURI());
            pwmSession.getSessionStateBean().setForwardURL(item.getShortcutURI().toString());

            pwmRequest.sendRedirectToContinue();
            return;
        }

        LOGGER.error(pwmSession, "unknown/unexpected link requested to " + link);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.SHORTCUT);
    }
}

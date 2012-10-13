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

package password.pwm.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.*;
import password.pwm.config.PwmSetting;
import password.pwm.config.ShortcutItem;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class ShortcutServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ShortcutServlet.class);

    protected void processRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SHORTCUT_ENABLE)) {
            pwmSession.getSessionStateBean().setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }


        if (pwmSession.getSessionStateBean().getVisibleShortcutItems() == null) {
            LOGGER.debug(pwmSession, "building visible shortcut list for user");
            final Map<String, ShortcutItem> visibleItems = figureVisibleShortcuts(pwmSession, pwmApplication, req);
            pwmSession.getSessionStateBean().setVisibleShortcutItems(visibleItems);
        } else {
            LOGGER.trace(pwmSession, "using cashed shortcut values");
        }


        final String action = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
        LOGGER.trace(pwmSession, "received request for action " + action);

        if (action.equalsIgnoreCase("selectShortcut")) {
            handleUserSelection(req, resp);
            return;
        }

        this.forwardToJSP(req, resp);
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_SHORTCUT).forward(req, resp);
    }


    /**
     * Loop through each configured shortcut setting to determine if the shortcut is is able to the user pwmSession.
     *
     * @param pwmSession Valid (authenticated) PwmSession
     * @param request    httpRequest
     * @return List of visible ShortcutItems
     * @throws password.pwm.error.PwmUnrecoverableException             if something goes wrong
     * @throws ChaiUnavailableException if ldap is unavailable.
     */
    private static Map<String, ShortcutItem> figureVisibleShortcuts(final PwmSession pwmSession, final PwmApplication pwmApplication, final HttpServletRequest request)
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Collection<String> configValues = pwmApplication.getConfig().readSettingAsLocalizedStringArray(PwmSetting.SHORTCUT_ITEMS, pwmSession.getSessionStateBean().getLocale());

        final Set<String> labelsFromHeader = new HashSet<String>();
        {
            for (final String headerName : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.SHORTCUT_HEADER_NAMES)) {
                final Set<String> headerStrings = Validator.readStringsFromRequest(request, headerName, 10 * 1024);
                for (final String loopString : headerStrings) {
                    labelsFromHeader.addAll(StringHelper.tokenizeString(loopString, ","));
                }
            }
        }

        final List<ShortcutItem> configuredItems = new ArrayList<ShortcutItem>();
        for (final String loopStr : configValues) {
            final ShortcutItem item = ShortcutItem.parsePwmConfigInput(loopStr);
            configuredItems.add(item);
        }

        final Map<String, ShortcutItem> visibleItems = new LinkedHashMap<String, ShortcutItem>();

        if (!labelsFromHeader.isEmpty()) {
            LOGGER.trace("detected the following labels from headers: " + StringHelper.stringCollectionToString(labelsFromHeader, ","));
            visibleItems.keySet().retainAll(labelsFromHeader);
        } else {
            for (final ShortcutItem item : configuredItems) {
                final boolean queryMatch = Permission.testQueryMatch(
                        pwmSession.getSessionManager().getActor(),
                        item.getLdapQuery(),
                        null,
                        pwmSession
                );

                if (queryMatch) {
                    visibleItems.put(item.getLabel(), item);
                }
            }
        }

        return visibleItems;
    }

    private void handleUserSelection(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        final String link = Validator.readStringFromRequest(req, "link");
        final Map<String, ShortcutItem> visibleItems = pwmSession.getSessionStateBean().getVisibleShortcutItems();

        if (link != null && visibleItems.keySet().contains(link)) {
            final ShortcutItem item = visibleItems.get(link);

            pwmApplication.getStatisticsManager().incrementValue(Statistic.SHORTCUTS_SELECTED);
            LOGGER.trace(pwmSession, "shortcut link selected: " + link + ", setting link for 'forwardURL' to " + item.getShortcutURI());
            pwmSession.getSessionStateBean().setForwardURL(item.getShortcutURI().toString());

            final StringBuilder continueURL = new StringBuilder();
            continueURL.append(req.getContextPath());
            continueURL.append("/public/" + PwmConstants.URL_SERVLET_COMMAND);
            continueURL.append("?processAction=continue");
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(continueURL.toString(), req, resp));
            return;
        }

        LOGGER.error(pwmSession, "unknown/unexpected link requested to " + link);
        forwardToJSP(req, resp);
    }
}

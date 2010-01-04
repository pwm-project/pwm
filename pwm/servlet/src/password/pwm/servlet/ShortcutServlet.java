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

package password.pwm.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.config.PwmSetting;
import password.pwm.config.ShortcutItem;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShortcutServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ShortcutServlet.class);

    protected void processRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (pwmSession.getSessionStateBean().getVisableShortcutItems() == null) {
            LOGGER.debug(pwmSession,"building visable shortcut list for user");
            final Map<String,ShortcutItem> visableItems  = figureVisableShortcuts(pwmSession);
            pwmSession.getSessionStateBean().setVisableShortcutItems(visableItems);
        } else {
            LOGGER.trace(pwmSession,"using cashed shortcut values");
        }

        final String action = Validator.readStringFromRequest(req, Constants.PARAM_ACTION_REQUEST, 255);
        LOGGER.trace(pwmSession, "received request for action " + action);

        if (action.equalsIgnoreCase("selectShortcut")) {
            handleUserSelection(req, resp, pwmSession);
            return;
        }

        this.forwardToJSP(req,resp);
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_SHORTCUT).forward(req, resp);
    }


    /**
     * Loop through each configured shortcut setting to determine if the shortcut is is able to the user session.
     * @param session Valid (authenticated) PwmSession
     * @return List of visible ShortcutItems
     * @throws PwmException if something goes wrong
     * @throws ChaiUnavailableException if ldap is unavailable.
     */
    private static Map<String,ShortcutItem> figureVisableShortcuts(final PwmSession session) throws PwmException, ChaiUnavailableException {
        final List<ShortcutItem> configuredItems = session.getLocaleConfig().getShortcutItems();
        final Map<String, ShortcutItem> visableItems = new HashMap<String,ShortcutItem>();

        for (final ShortcutItem item : configuredItems ) {
            final boolean queryMatch = Permission.testQueryMatch(
                    session.getSessionManager().getActor(),
                    item.getLdapQuery(),
                    null,
                    session
            );

            if (queryMatch) {
                visableItems.put(item.getLabel(), item);
            }
        }

        return visableItems;
    }

    private void handleUserSelection(final HttpServletRequest req, final HttpServletResponse resp, final PwmSession pwmSession)
            throws PwmException, ChaiUnavailableException, IOException, ServletException
    {
        final String link = Validator.readStringFromRequest(req, "link", 255);
        final Map<String,ShortcutItem> visableItems  = figureVisableShortcuts(pwmSession);

        if (link != null && visableItems.keySet().contains(link)) {
            final ShortcutItem item = visableItems.get(link);

            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.SHORTCUTS_SELECTED);
            LOGGER.trace(pwmSession, "shortcut link selected: " + link + ", setting link for 'forwardURL' to " + item.getShortcutURI());
            pwmSession.getSessionStateBean().setForwardURL(item.getShortcutURI().toString());

            final StringBuilder continueURL = new StringBuilder();
            continueURL.append(pwmSession.getContextManager().getConfig().readSettingAsString(PwmSetting.URL_SERVET_RELATIVE));
            continueURL.append("/public/" + Constants.URL_SERVLET_COMMAND);
            continueURL.append("?processAction=continue");
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(continueURL.toString(), req, resp));
            return;
        }

        LOGGER.error(pwmSession,"unknown/unexpected link requested to " + link);
        forwardToJSP(req,resp);
    }
}

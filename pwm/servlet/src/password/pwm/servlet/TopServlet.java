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
import password.pwm.Helper;
import password.pwm.PwmSession;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Message;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * An abstract parent of all PWM servlets.  This is the parent class of most, if not all, PWM
 * servlets.
 *
 * @author Jason D. Rivard
 */
public abstract class TopServlet extends HttpServlet {
// ------------------------------ FIELDS ------------------------------

    /**
     * Logger used by this class *
     */
    private static final PwmLogger LOGGER = PwmLogger.getLogger(TopServlet.class);

// -------------------------- OTHER METHODS --------------------------
    public void doGet(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException
    {
        this.handleRequest(req, resp);
    }

    private void handleRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        ssBean.setLastParameterValues(req);

        try {
            this.processRequest(req, resp);
        } catch (ChaiUnavailableException e) {
            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
            pwmSession.getContextManager().setLastLdapFailure();
            ssBean.setSessionError(Message.ERROR_DIRECTORY_UNAVAILABLE.toInfo());
            pwmSession.getContextManager().setLastLdapFailure();
            LOGGER.fatal(pwmSession, "unable to contact ldap directory: " + e.getMessage());
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
        } catch (PwmException e) {
            if (Message.ERROR_UNKNOWN.equals(e.getError().getError())) {
                LOGGER.warn(pwmSession, "unexpected pwm error during page generation: " + e.getMessage(),e);
                pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.PWM_UNKNOWN_ERRORS);
            } else {
                LOGGER.error(pwmSession, "pwm error during page generation: " + e.getMessage());
            }
            if (e.getMessage() == null) {
                ssBean.setSessionError(Message.ERROR_UNKNOWN.toInfo());
            } else {
                ssBean.setSessionError(e.getError());
            }
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
        } catch (Exception e) {
            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.PWM_UNKNOWN_ERRORS);
            LOGGER.warn(pwmSession, "unexpected exception during page generation: " + e.getMessage(), e);
            ssBean.setSessionError(Message.ERROR_UNKNOWN.toInfo());
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
            //throw new ServletException(e);
        }
    }

    protected abstract void processRequest(
            HttpServletRequest req,
            HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmException;

    public void doPost(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException
    {
        this.handleRequest(req, resp);
    }
}


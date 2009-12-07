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

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiPasswordPolicy;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AuthenticationFilter;
import password.pwm.Constants;
import password.pwm.PwmSession;
import password.pwm.Validator;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;


/**
 * @author Jason D. Rivard
 */
public class UserDebugServlet extends HttpServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(DebugServlet.class);
    private static final String[] attributes = new String[]{
            ChaiUser.ATTR_COMMON_NAME,
            ChaiUser.ATTR_GIVEN_NAME,
            ChaiUser.ATTR_SURNAME,
            ChaiUser.ATTR_EMAIL,

            ChaiUser.ATTR_INTRUDER_RESET_TIME,
            ChaiUser.ATTR_LOCKED_BY_INTRUDER,

            ChaiUser.ATTR_PASSWORD_EXPIRE_INTERVAL,
            ChaiUser.ATTR_PASSWORD_EXPIRE_TIME,
            ChaiUser.ATTR_PASSWORD_MINIMUM_LENGTH,

            ChaiUser.ATTR_LOGIN_DISABLED,
            ChaiConstant.ATTR_LDAP_LAST_LOGIN_TIME,

            "nspmPasswordPolicyDN"
    };

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Servlet ---------------------

    public void init(final ServletConfig config)
            throws ServletException
    {
        super.init(config);
    }

// -------------------------- OTHER METHODS --------------------------

    public void doGet(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException
    {
        doService(req, resp);
    }

    public static void doService(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException
    {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final String username = Validator.readStringFromRequest(req, "username", 255);
            resp.setContentType("text/html");

            final StringWriter htmlOutput = new StringWriter();


            htmlOutput.write("<html>");
            htmlOutput.write("<p><center><b><FONT SIZE=+2>PWM UserDebugServlet</FONT></b></center>");

            //username form
            htmlOutput.write("<p/><form method=get name=\"userInfoForm\">");
            htmlOutput.write("Username: <input type=text width=400 name=username value=" + username + ">");
            htmlOutput.write("</form>");
            htmlOutput.write("<p>");

            try {
                final String report = doReport(username, pwmSession);
                htmlOutput.write("<pre>");
                htmlOutput.write(report);
                LOGGER.debug(report);
                htmlOutput.write("</pre>");
            } catch (Exception e) {
                htmlOutput.write("error generating user report: " + e.getMessage());
            }

            resp.getOutputStream().print(htmlOutput.toString());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static String doReport(final String username, final PwmSession pwmSession)
            throws ChaiUnavailableException, PwmException, ChaiOperationException
    {
        String userDN = null;
        if (username.length() > 0) {
            userDN = AuthenticationFilter.convertUsernameFieldtoDN(username, pwmSession, null);
        }

        if (userDN == null || userDN.length() < 1) {
            return "User '" + username + "' not found.";
        }

        final StringBuilder sb = new StringBuilder();

        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();

        sb.append("Report for: ").append(userDN).append("\n\n");

        {
            final Set<String> additionalAttrs = new HashSet<String>();
            additionalAttrs.add(pwmSession.getContextManager().getParameter(Constants.CONTEXT_PARAM.LDAP_NAMING_ATTRIBUTE));
            additionalAttrs.add(pwmSession.getConfig().readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE));
            additionalAttrs.add(pwmSession.getConfig().readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE));

            final Properties values = provider.readStringAttributes(userDN, additionalAttrs.toArray(new String[additionalAttrs.size()]));

            sb.append("--selected user attributes--\n");
            for (final String attrName : attributes) {
                sb.append(attrName).append("=").append(values.getProperty(attrName, "")).append("\n");
            }
            sb.append("\n");
        }

        {
            sb.append("-- user password policy --\n");

            final ChaiPasswordPolicy policy = ChaiFactory.createChaiUser(userDN,provider).getPasswordPolicy();

            sb.append("policyDN: ").append(policy.getPolicyEntry().getEntryDN()).append("\n");
            sb.append(policy.toString()).append("\n");
        }


        return sb.toString();
    }

    public void doPost(
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws ServletException, IOException
    {
        doService(request, response);
    }
}
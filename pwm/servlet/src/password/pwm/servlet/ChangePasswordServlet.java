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
import password.pwm.bean.ChangePasswordBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Message;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.PwmLogger;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.stats.Statistic;
import org.json.simple.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * User interaction servlet for changing (self) passwords.
 *
 * @author Jason D. Rivard.
 */
public class ChangePasswordServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ChangePasswordServlet.class);

    public static final int MAX_CACHE_SIZE = 50;
    private static final int DEFAULT_INPUT_LENGTH = 1024;

// -------------------------- OTHER METHODS --------------------------

    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String processRequestParam = Validator.readStringFromRequest(req, Constants.PARAM_ACTION_REQUEST, DEFAULT_INPUT_LENGTH);

        if (!ssBean.isAuthenticated()) {
            ssBean.setSessionError(new ErrorInformation(Message.ERROR_AUTHENTICATION_REQUIRED));
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (!Permission.checkPermission(Permission.CHANGE_PASSWORD, pwmSession)) {
            ssBean.setSessionError(new ErrorInformation(Message.ERROR_UNAUTHORIZED));
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (processRequestParam != null) {
            if (processRequestParam.equalsIgnoreCase("validate")) {
                //randomize response delay - useful for for developer testing only
                /* final int randomInt = new java.util.Random().nextInt(1000 * 3);
                if (randomInt < 50) {
                    LOGGER.fatal("random delay: pause");
                    resp.getOutputStream().close();
                    return;
                } else {
                    LOGGER.fatal("random delay: " + randomInt);
                    Helper.pause(randomInt);
                }
                */
                handleValidatePasswords(req,resp);
                return;
            } else if (processRequestParam.equalsIgnoreCase("getrandom")) {     // ajax random generator
                handleGetRandom(req, resp);
                return;
            } else if (processRequestParam.equalsIgnoreCase("change")) {        // change request
                this.handleChangeRequest(req, resp);
            } else if (processRequestParam.equalsIgnoreCase("doChange")) {      // wait page call-back
                this.handleDoChangeRequest(req, resp);
            } else {
                final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();
                if (cpb.getPasswordChangeError() != null) {
                    ssBean.setSessionError(cpb.getPasswordChangeError());
                    cpb.setPasswordChangeError(null);
                }
            }
        }

        if (!resp.isCommitted()) {
            this.forwardToJSP(req, resp);
        }
    }

    /**
     * Write the pwm password pre-validation response.  A format such as the following is used:
     * <p/>
     * <pre>pwm:[status]:[pre-localized error/success message]</pre>
     *
     * @param req  request
     * @param resp response
     * @throws IOException      for an error
     * @throws ServletException for an error
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException if ldap server becomes unavailable
     * @throws password.pwm.error.PwmException if an unexpected error occurs
     */
    protected static void handleValidatePasswords(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException, PwmException, ChaiUnavailableException
    {
        final long startTime = System.currentTimeMillis();

        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        final Map<String, ChangePasswordBean.PasswordCacheEntry> cache = pwmSession.getChangePasswordBean().getPasswordTestCache();
        final String password1 = Validator.readStringFromRequest(req, "password1", DEFAULT_INPUT_LENGTH);
        final String password2 = Validator.readStringFromRequest(req, "password2", DEFAULT_INPUT_LENGTH);

        final boolean foundInCache = cache.containsKey(password1);
        final ChangePasswordBean.PasswordCacheEntry result = foundInCache ? cache.get(password1) : checkEnteredPassword(pwmSession, password1);
        final MATCH_STATUS matchStatus = figureMatchStatus(pwmSession, password1, password2);
        cache.put(password1, result); //update the cache

        final String outputString = generateOutputString(pwmSession, result, matchStatus);

        {
            final StringBuilder sb = new StringBuilder();
            sb.append("real-time password validator called for ").append(pwmSession.getUserInfoBean().getUserDN());
            sb.append("\n");
            sb.append("  process time: ").append((int) (System.currentTimeMillis() - startTime)).append("ms");
            sb.append(", cached: ").append(foundInCache);
            sb.append(", cacheSize: ").append(cache.size());
            sb.append(", pass: ").append(result.isPassed());
            sb.append(", confirm: ").append(matchStatus);
            sb.append(", strength: ").append(result.getStrength());
            if (!result.isPassed()) {
                sb.append(", err: ").append(result.getUserStr());
            }
            sb.append("\n");
            sb.append("  result string: ").append(outputString);
            LOGGER.trace(pwmSession, sb.toString());
        }

        pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.PASSWORD_RULE_CHECKS);

        resp.setContentType("text/plain;charset=utf-8");
        resp.getWriter().print(outputString);
    }

    private static ChangePasswordBean.PasswordCacheEntry checkEnteredPassword(
            final PwmSession pwmSession,
            final String password1
    )
            throws PwmException, ChaiUnavailableException
    {
        int strength = 0;
        boolean pass = false;
        String userMessage;

        if (password1.length() < 0) {
            userMessage = new ErrorInformation(Message.PASSWORD_MISSING).toUserStr(pwmSession);
        } else {
            try {
                Validator.testPasswordAgainstPolicy(password1, pwmSession, true);
                userMessage = new ErrorInformation(Message.PASSWORD_MEETS_RULES).toUserStr(pwmSession);
                pass = true;
            } catch (ValidationException e) {
                userMessage = e.getError().toUserStr(pwmSession);
                pass = false;
            }

            strength = Validator.checkPasswordStrength(pwmSession, password1);
        }

        return new ChangePasswordBean.PasswordCacheEntry(userMessage, pass, strength);
    }

    private static MATCH_STATUS figureMatchStatus(final PwmSession session, final String password1, final String password2) {
        final MATCH_STATUS matchStatus;
        if (password2.length() < 1) {
            matchStatus = MATCH_STATUS.EMPTY;
        } else {
            if (session.getUserInfoBean().getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.CaseSensitive)) {
                matchStatus = password1.equals(password2) ? MATCH_STATUS.MATCH : MATCH_STATUS.NO_MATCH;
            } else {
                matchStatus = password1.equalsIgnoreCase(password2) ? MATCH_STATUS.MATCH : MATCH_STATUS.NO_MATCH;
            }
        }

        return matchStatus;
    }

    private static String generateOutputString(
            final PwmSession pwmSession,
            final ChangePasswordBean.PasswordCacheEntry cacheEntry,
            final MATCH_STATUS matchStatus
    )
    {
        final String userMessage;
        if (cacheEntry.isPassed()) {
            switch (matchStatus) {
                case EMPTY:
                    userMessage = new ErrorInformation(Message.PASSWORD_MISSING_CONFIRM).toUserStr(pwmSession);
                    break;
                case MATCH:
                    userMessage = new ErrorInformation(Message.PASSWORD_MEETS_RULES).toUserStr(pwmSession);
                    break;
                case NO_MATCH:
                    userMessage = new ErrorInformation(Message.PASSWORD_DOESNOTMATCH).toUserStr(pwmSession);
                    break;
                default:
                    userMessage = "";
            }
        } else {
            userMessage = cacheEntry.getUserStr();
        }

        final Map<String,String> outputMap = new HashMap<String,String>();
        outputMap.put("version","1");
        outputMap.put("strength",String.valueOf(cacheEntry.getStrength()));
        outputMap.put("match",matchStatus.toString());
        outputMap.put("message", userMessage);
        outputMap.put("passed",String.valueOf(cacheEntry.isPassed()));

        return JSONObject.toJSONString(outputMap);
    }

    /**
     * Write the pwm password pre-validation response.  A format such as the following is used:
     * <p/>
     * <pre>pwm:[status]:[pre-localized error/success message</pre>
     *
     * @param req  request
     * @param resp response
     * @throws IOException      for an error
     * @throws ServletException for an error
     */
    protected static void handleGetRandom(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException
    {
        final long startTime = System.currentTimeMillis();

        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final String randomPassword = RandomPasswordGenerator.createRandomPassword(pwmSession);

        final Map<String,String> outputMap = new HashMap<String,String>();
        outputMap.put("version","1");
        outputMap.put("password",randomPassword);

        resp.setContentType("text/plain;charset=utf-8");
        resp.getOutputStream().print(JSONObject.toJSONString(outputMap));

        {
            final StringBuilder sb = new StringBuilder();
            sb.append("real-time random password generator called");
            sb.append(" (").append((int) (System.currentTimeMillis() - startTime)).append("ms");
            sb.append(")");
            LOGGER.trace(pwmSession, sb.toString());
        }
    }

    /**
     * Action handler for when user clicks "change password" button.  This copies the
     * password into the changepasswordbean, redirects to the please wait screen, then
     * directs back to the actual doChange.
     *
     * @param req  http request
     * @param resp http response
     * @throws ServletException should never throw
     * @throws IOException      if error writing response
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException if ldap server becomes unavailable
     * @throws password.pwm.error.PwmException if an unexpected error occurs
     */
    private void handleChangeRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, PwmException, ChaiUnavailableException
    {
        //Fetch the required managers/beans
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ContextManager theManager = ContextManager.getContextManager(this.getServletContext());

        final String password1 = Validator.readStringFromRequest(req, "password1", DEFAULT_INPUT_LENGTH);
        final String password2 = Validator.readStringFromRequest(req, "password2", DEFAULT_INPUT_LENGTH);

        // check the password meets the requirements
        {
            try {
                Validator.testPasswordAgainstPolicy(password1, pwmSession, true);
            } catch (ValidationException e) {
                ssBean.setSessionError(e.getError());
                LOGGER.debug(pwmSession, "failed password vaildation check: " + e.getMessage());
                this.forwardToJSP(req, resp);
                return;
            }
        }

        //make sure the two passwords match
        if (MATCH_STATUS.MATCH != figureMatchStatus(pwmSession, password1, password2)) {
            ssBean.setSessionError(new ErrorInformation(Message.PASSWORD_DOESNOTMATCH));
            this.forwardToJSP(req, resp);
            return;
        }

        // password accepted, setup change password
        {
            final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();
            cpb.setNewPassword(password1);
            LOGGER.trace(pwmSession,"wrote password to changePasswordBean");

            final StringBuilder returnURL = new StringBuilder();
            returnURL.append(theManager.getConfig().readSettingAsString(PwmSetting.URL_SERVET_RELATIVE));
            returnURL.append(req.getServletPath());
            returnURL.append("?" + Constants.PARAM_ACTION_REQUEST + "=" + "doChange");
            Helper.forwardToWaitPage(req, resp, this.getServletContext(), returnURL.toString());
        }
    }

    /**
     * Handles the actual change password request.  This action is called via a redirect
     * from the "Please Wait" screen.
     *
     * @param req  http request
     * @param resp http response
     * @throws ServletException         should never throw
     * @throws IOException              if error writing response
     * @throws ChaiUnavailableException if ldap disappears
     * @throws password.pwm.error.PwmException             if there is an unexpected error setting password
     */
    private void handleDoChangeRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ContextManager theManager = pwmSession.getContextManager();

        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();
        final String newPassword = cpb.getNewPassword();

        if (newPassword == null || newPassword.length() < 1) {
            LOGGER.warn(pwmSession, "entered doChange, but bean does not have a valid password stored");
            cpb.clearPassword();
            return;
        }

        LOGGER.trace(pwmSession, "retreived password from changePasswordBean");

        final boolean success = PasswordUtility.setUserPassword(pwmSession, newPassword);

        if (success) {
            if (theManager.getConfig().readSettingAsBoolean(PwmSetting.LOGOUT_AFTER_PASSWORD_CHANGE)) {
                ssBean.setFinishAction(SessionStateBean.FINISH_ACTION.LOGOUT);
            }

            ssBean.setSessionSuccess(new ErrorInformation(Message.SUCCESS_PASSWORDCHANGE));

            UserHistory.updateUserHistory(pwmSession, UserHistory.Record.Event.CHANGE_PASSWORD, null);

            Helper.forwardToSuccessPage(req, resp, this.getServletContext());
        } else {
            final ErrorInformation errorMsg = ssBean.getSessionError();
            if (errorMsg != null) { // add the badd password to the history cache
                cpb.getPasswordTestCache().put(newPassword,new ChangePasswordBean.PasswordCacheEntry(
                        errorMsg.toUserStr(pwmSession),
                        false,
                        Validator.checkPasswordStrength(pwmSession, newPassword)
                ));
            }
            cpb.setPasswordChangeError(errorMsg);
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(Constants.URL_SERVLET_CHANGE_PASSWORD, req, resp));
        }

        cpb.clearPassword();
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_PASSWORD_CHANGE).forward(req, resp);
    }

// -------------------------- ENUMERATIONS --------------------------

    private enum MATCH_STATUS {
        MATCH, NO_MATCH, EMPTY
    }
}


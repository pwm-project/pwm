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

package password.pwm.ws.server.rest;


import com.google.gson.Gson;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.config.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Path("/checkpassword")
public class RestCheckPasswordServer {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestHealthServer.class);

    @Context
    HttpServletRequest request;

	@POST
	@Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doPasswordRuleCheckJsonPost(
            final @FormParam("password1") String password1,
            final @FormParam("password2") String password2,
            final @FormParam("username") String username
    )
    {
        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            final PwmSession pwmSession = PwmSession.getPwmSession(request);

            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            }
            
            final String userDN = (username != null && username.length() > 0) ? username : pwmSession.getUserInfoBean().getUserDN();
            final PasswordCheckRequest checkRequest = new PasswordCheckRequest(userDN, password1, password2);

            return doPasswordRuleCheck(pwmApplication, pwmSession, checkRequest);
        } catch (Exception e) {
            LOGGER.error("unexpected error building json response for /checkpassword rest service: " + e.getMessage());
        }
        return "";
    }

    private static class PasswordCheckRequest {
        final String userDN;
        final String password1;
        final String password2;

        private PasswordCheckRequest(String userDN, String password1, String password2) {
            this.userDN = userDN;
            this.password1 = password1;
            this.password2 = password2;
        }

        public String getUserDN() {
            return userDN;
        }

        public String getPassword1() {
            return password1;
        }

        public String getPassword2() {
            return password2;
        }
    }


	public String doPasswordRuleCheck(PwmApplication pwmApplication, PwmSession pwmSession, PasswordCheckRequest checkRequest)
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final long startTime = System.currentTimeMillis();
        final PasswordCheckInfo passwordCheckInfo = checkEnteredPassword(pwmApplication, pwmSession, checkRequest.getUserDN(), checkRequest.getPassword1());
        final MATCH_STATUS matchStatus = figureMatchStatus(pwmSession, checkRequest.getPassword1(), checkRequest.getPassword2());

        final String outputString = generateJsonOutputString(pwmSession, pwmApplication, passwordCheckInfo, matchStatus);
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("real-time password validator called for ").append(checkRequest.getUserDN());
            sb.append("\n");
            sb.append("  process time: ").append((int) (System.currentTimeMillis() - startTime)).append("ms");
            sb.append(", pass: ").append(passwordCheckInfo.isPassed());
            sb.append(", confirm: ").append(matchStatus);
            sb.append(", strength: ").append(passwordCheckInfo.getStrength());
            if (!passwordCheckInfo.isPassed()) {
                sb.append(", err: ").append(passwordCheckInfo.getUserStr());
            }
            sb.append("\n");
            sb.append("  passwordCheckInfo string: ").append(outputString);
            LOGGER.trace(pwmSession, sb.toString());
        }

        pwmApplication.getStatisticsManager().incrementValue(Statistic.PASSWORD_RULE_CHECKS);
        return outputString;
	}

    private static PasswordCheckInfo checkEnteredPassword(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String userDN,
            final String password1
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        boolean pass = false;
        String userMessage;

        if (password1.length() < 0) {
            userMessage = new ErrorInformation(PwmError.PASSWORD_MISSING).toUserStr(pwmSession, pwmApplication);
        } else {
            try {
                if (userDN.equals(pwmSession.getUserInfoBean().getUserDN())) {
                    Validator.testPasswordAgainstPolicy(password1, pwmSession, pwmApplication);
                } else {
                    final ChaiUser user = ChaiFactory.createChaiUser(userDN,pwmSession.getSessionManager().getChaiProvider());
                    final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                            pwmApplication,
                            pwmSession,
                            user,
                            pwmSession.getSessionStateBean().getLocale()
                    );
                    Validator.testPasswordAgainstPolicy(password1,null,pwmSession, pwmApplication, passwordPolicy, false);
                }
                userMessage = new ErrorInformation(PwmError.PASSWORD_MEETS_RULES).toUserStr(pwmSession, pwmApplication);
                pass = true;
            } catch (PwmDataValidationException e) {
                userMessage = e.getErrorInformation().toUserStr(pwmSession, pwmApplication);
                pass = false;
            }
        }

        final int strength = PasswordUtility.checkPasswordStrength(pwmApplication.getConfig(), pwmSession, password1);
        return new PasswordCheckInfo(userMessage, pass, strength);
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

    private static String generateJsonOutputString(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final PasswordCheckInfo checkInfo,
            final MATCH_STATUS matchStatus
    ) {
        final String userMessage;
        if (checkInfo.isPassed()) {
            switch (matchStatus) {
                case EMPTY:
                    userMessage = new ErrorInformation(PwmError.PASSWORD_MISSING_CONFIRM).toUserStr(pwmSession, pwmApplication);
                    break;
                case MATCH:
                    userMessage = new ErrorInformation(PwmError.PASSWORD_MEETS_RULES).toUserStr(pwmSession, pwmApplication);
                    break;
                case NO_MATCH:
                    userMessage = new ErrorInformation(PwmError.PASSWORD_DOESNOTMATCH).toUserStr(pwmSession, pwmApplication);
                    break;
                default:
                    userMessage = "";
            }
        } else {
            userMessage = checkInfo.getUserStr();
        }

        final Map<String, String> outputMap = new HashMap<String, String>();
        outputMap.put("version", "2");
        outputMap.put("strength", String.valueOf(checkInfo.getStrength()));
        outputMap.put("match", matchStatus.toString());
        outputMap.put("message", userMessage);
        outputMap.put("passed", String.valueOf(checkInfo.isPassed()));

        final Gson gson = new Gson();
        return gson.toJson(outputMap);
    }

    private enum MATCH_STATUS {
        MATCH, NO_MATCH, EMPTY
    }

    public static class PasswordCheckInfo implements Serializable {
        private final String userStr;
        private final boolean passed;
        private final int strength;

        public PasswordCheckInfo(final String userStr, final boolean passed, final int strength) {
            this.userStr = userStr;
            this.passed = passed;
            this.strength = strength;
        }

        public String getUserStr() {
            return userStr;
        }

        public boolean isPassed() {
            return passed;
        }

        public int getStrength() {
            return strength;
        }
    }

}



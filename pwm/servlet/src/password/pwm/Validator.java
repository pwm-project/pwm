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

package password.pwm;

import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmSession;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Static utility class for validating parameters, passwords and user input.
 *
 * @author Jason D. Rivard
 */
public class Validator {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(Validator.class);

    public static final String PARAM_CONFIRM_SUFFIX = "_confirm";



// -------------------------- STATIC METHODS --------------------------


    public static void validatePwmFormID(final HttpServletRequest req)
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final String pwmFormID = ssBean.getSessionVerificationKey();

        final String submittedPwmFormID = req.getParameter(PwmConstants.PARAM_FORM_ID);

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SECURITY_ENABLE_FORM_NONCE)) {
            if (submittedPwmFormID == null || submittedPwmFormID.length() < pwmFormID.length()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_INVALID_FORMID);
            }

            if (!pwmFormID.equals(submittedPwmFormID.substring(0,pwmFormID.length()))) {
                throw new PwmUnrecoverableException(PwmError.ERROR_INVALID_FORMID);
            }
        }
    }

    public static void validatePwmRequestCounter(final HttpServletRequest req)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final String sessionVerificationKey = ssBean.getSessionVerificationKey();
        final String requestVerificationKey = ssBean.getRequestVerificationKey();

        final String submittedPwmFormID = req.getParameter(PwmConstants.PARAM_FORM_ID);
        if (submittedPwmFormID == null || submittedPwmFormID.isEmpty()) {
            return;
        }

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SECURITY_ENABLE_REQUEST_SEQUENCE)) {
            try {
                final String submittedReqestVerificationKey = submittedPwmFormID.substring(sessionVerificationKey.length(),submittedPwmFormID.length());
                if (requestVerificationKey != null && !requestVerificationKey.equals(submittedReqestVerificationKey)) {
                    final String debugMsg = "expectedPageID=" + requestVerificationKey
                            + ", submittedPageID=" + submittedReqestVerificationKey
                            +  ", url=" + req.getRequestURI();

                    throw new PwmOperationalException(PwmError.ERROR_INCORRECT_REQUEST_SEQUENCE, debugMsg);
                }
            } catch (StringIndexOutOfBoundsException | NumberFormatException e) {
                throw new PwmOperationalException(PwmError.ERROR_INCORRECT_REQUEST_SEQUENCE);
            }
        }
    }



    public static String sanitizeInputValue(
            final Configuration config,
            final String input
    ) {
        return sanitizeInputValue(config, input, 10 *1024);
    }

    public static String sanitizeInputValue(
            final Configuration config,
            final String input,
            int maxLength
    ) {

        String theString = input == null ? "" : input.trim();

        if (maxLength < 1) {
            maxLength = 10 * 1024;
        }

        // strip off any length beyond the specified maxLength.
        if (theString.length() > maxLength) {
            theString = theString.substring(0, maxLength);
        }

        // strip off any disallowed chars.
        if (config != null) {
            final List<String> disallowedInputs = config.readSettingAsStringArray(PwmSetting.DISALLOWED_HTTP_INPUTS);
            for (final String testString : disallowedInputs) {
                final String newString = theString.replaceAll(testString, "");
                if (!newString.equals(theString)) {
                    LOGGER.warn("removing potentially malicious string values from input, converting '" + input + "' newValue=" + newString + "' pattern='" + testString + "'");
                    theString = newString;
                }
            }
        }

        return theString;
    }


}


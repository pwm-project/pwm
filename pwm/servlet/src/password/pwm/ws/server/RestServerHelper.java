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

package password.pwm.ws.server;

import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.Validator;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;

import javax.servlet.http.HttpServletRequest;

public abstract class RestServerHelper {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestServerHelper.class);

    public static boolean determineIfRestClientIsExternal(HttpServletRequest request)
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(request);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);

        final boolean allowExternal = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.ENABLE_EXTERNAL_WEBSERVICES);
        boolean requestHasCorrectID = false;

        try {
            Validator.validatePwmFormID(request);
            requestHasCorrectID = true;
        } catch (PwmUnrecoverableException e) {
            if (e.getError() == PwmError.ERROR_INVALID_FORMID) {
                if (!allowExternal) {
                    LOGGER.warn(pwmSession, "attempt to use web service without correct FormID value and external web services are disabled");
                    throw e;
                }
            } else {
                throw e;
            }
        }

        return !requestHasCorrectID;
    }
}

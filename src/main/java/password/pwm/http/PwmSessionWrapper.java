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

package password.pwm.http;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class PwmSessionWrapper {

    private transient PwmSession pwmSession;
    
    private PwmSessionWrapper() {

    }

    public static void sessionMerge(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpSession httpSession
    ) 
            throws PwmUnrecoverableException 
    {
        final int sessionIdle = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);
        pwmSession.setSessionTimeout(httpSession, sessionIdle);
        httpSession.setAttribute(PwmConstants.SESSION_ATTR_PWM_SESSION, pwmSession);
    }
    
    
    public static PwmSession readPwmSession(final HttpSession httpSession)
            throws PwmUnrecoverableException
    {
        final PwmSession returnSession = (PwmSession) httpSession.getAttribute(PwmConstants.SESSION_ATTR_PWM_SESSION);
        if (returnSession == null) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"attempt to read PwmSession from HttpSession failed"));
        }
        return returnSession;
    }

    public static PwmSession readPwmSession(final HttpServletRequest httpRequest) throws PwmUnrecoverableException {
        return readPwmSession(httpRequest.getSession());
    }
}

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

package password.pwm.http.tag;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.util.Helper;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;

/**
 * @author Jason D. Rivard
 */
public class ErrorMessageTag extends PwmAbstractTag {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(ErrorMessageTag.class);

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        try {
            final PwmRequest pwmRequest = PwmRequest.forRequest((HttpServletRequest)pageContext.getRequest(), (HttpServletResponse)pageContext.getResponse());
            PwmApplication pwmApplication = null;
            try {
                pwmApplication = ContextManager.getPwmApplication(pageContext.getSession());
            } catch (PwmException e) { /* noop */ }

            final ErrorInformation error = (ErrorInformation)pwmRequest.getAttribute(PwmConstants.REQUEST_ATTR.PwmErrorInfo);

            if (error != null) {
                final boolean showErrorDetail = Helper.determineIfDetailErrorMsgShown(pwmApplication);

                String outputMsg;
                if (showErrorDetail) {
                    final String errorDetail = error.toDebugStr() == null ? "" : " { " + error.toDebugStr() + " }";
                    outputMsg = error.toUserStr(pwmRequest.getPwmSession(), pwmApplication) + errorDetail;
                }  else {
                    outputMsg = error.toUserStr(pwmRequest.getPwmSession(), pwmApplication);
                }

                final boolean allowHtml = pwmApplication != null && Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_HEADER_SEND_XVERSION));
                if (!allowHtml) {
                    outputMsg = StringUtil.escapeHtml(outputMsg);
                }

                outputMsg = outputMsg.replace("\n","<br/>");

                if (pwmRequest != null) {
                    final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmApplication);
                    outputMsg = macroMachine.expandMacros(outputMsg);
                }

                pageContext.getOut().write(outputMsg);
            }
        } catch (PwmUnrecoverableException e) {
            /* app not running */
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}
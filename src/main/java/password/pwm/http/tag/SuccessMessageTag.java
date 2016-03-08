/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import password.pwm.http.PwmRequest;
import password.pwm.i18n.Message;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;

/**
 * @author Jason D. Rivard
 */
public class SuccessMessageTag extends PwmAbstractTag {
// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final PwmRequest pwmRequest = PwmRequest.forRequest(req, (HttpServletResponse) pageContext.getResponse());

            final String successMsg = (String)pwmRequest.getAttribute(PwmRequest.Attribute.SuccessMessage);

            final String outputMsg;
            if (successMsg == null || successMsg.isEmpty()) {
                outputMsg = Message.getLocalizedMessage(pwmRequest.getLocale(), Message.Success_Unknown, pwmRequest.getConfig());
            } else {
                if (pwmRequest.isAuthenticated()) {
                    final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmRequest.getPwmApplication());
                    outputMsg = macroMachine.expandMacros(successMsg);
                } else {
                    outputMsg = successMsg;
                }
            }

            pageContext.getOut().write(outputMsg);
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}


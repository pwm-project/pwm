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

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;

public class PwmMacroTag extends TagSupport {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmMacroTag.class);

    private String value;

    public String getValue()
    {
        return value;
    }

    public void setValue(final String value)
    {
        this.value = value;
    }

    public int doEndTag()
            throws JspTagException
    {
        try {
            final PwmRequest pwmRequest = PwmRequest.forRequest((HttpServletRequest) pageContext.getRequest(), (HttpServletResponse) pageContext.getResponse());
            final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmRequest.getPwmApplication());
            final String outputValue = macroMachine.expandMacros(value);
            pageContext.getOut().write(outputValue);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("error while processing PwmMacroTag: " + e.getMessage());
        } catch (Exception e) {
            throw new JspTagException(e.getMessage(),e);
        }
        return EVAL_PAGE;
    }
}

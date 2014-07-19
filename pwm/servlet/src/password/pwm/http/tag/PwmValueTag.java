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

package password.pwm.http.tag;

import password.pwm.http.PwmRequest;
import password.pwm.util.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * @author Jason D. Rivard
 */
public class PwmValueTag extends TagSupport {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmValueTag.class);

    private String name;

    public String getName()
    {
        return name;
    }

    public void setName(final String name)
    {
        this.name = name;
    }

    public int doEndTag()
            throws JspTagException
    {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final PwmRequest pwmRequest = PwmRequest.forRequest(req, (HttpServletResponse) pageContext.getResponse());
            try {
                final VALUE value = VALUE.valueOf(getName());
                final String output = calcValue(pwmRequest,value);
                pageContext.getOut().write(output);

            } catch (IllegalArgumentException e) {
                LOGGER.error("can't output requested value name '" + getName() + "'");
            }
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }

    public static String calcValue(
            final PwmRequest pwmRequest,
            final VALUE value
    ) {
        if (value == null) {
            return "";
        }

        switch (value) {
            case cspNonce:
                return pwmRequest.getPwmSession().getSessionStateBean().getSessionVerificationKey();
        }

        return "";
    }

    enum VALUE {
        cspNonce,

    }
}


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

package password.pwm.tag;

import password.pwm.PwmSession;
import password.pwm.bean.SessionStateBean;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * @author Jason D. Rivard
 */
public class ParamValueTag extends TagSupport {
// ------------------------------ FIELDS ------------------------------

    private String name;

// --------------------- GETTER / SETTER METHODS ---------------------

    public void setName(final String name)
    {
        this.name = name;
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final SessionStateBean ssBean = PwmSession.getSessionStateBean(req.getSession());
            final String paramValue = ssBean.getLastParameterValues().getProperty(name);
            if (paramValue != null && paramValue.length() > 0) {
                pageContext.getOut().write(paramValue);
            }
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}


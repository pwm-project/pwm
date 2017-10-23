/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import password.pwm.http.JspUtility;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.util.java.StringUtil;

import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * @author Jason D. Rivard
 */
public class UserInfoTag extends TagSupport {
    private String attribute;

    public void setAttribute(final String attribute)
    {
        this.attribute = attribute;
    }

    public int doEndTag()
            throws JspTagException
    {
        try {
            final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext);
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            if (pwmSession.isAuthenticated()) {
                final String ldapValue = pwmSession.getUserInfo().readStringAttribute(attribute);
                pageContext.getOut().write(StringUtil.escapeHtml(ldapValue == null ? "" : ldapValue));
            }
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}


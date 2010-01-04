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

import password.pwm.ContextManager;
import password.pwm.PwmSession;
import password.pwm.bean.SessionStateBean;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class DisplayLocationOptionsTag extends PwmAbstractTag {
// ------------------------------ FIELDS ------------------------------

    private String name;

// -------------------------- STATIC METHODS --------------------------

    private static String buildOptionListHTML(final HttpServletRequest request, final String selectedValue)
    {
        final ContextManager contextManager = ContextManager.getContextManager(request.getSession().getServletContext());

        final Map<String, String> locationsMap = contextManager.getConfig().getLoginContexts();

        if (locationsMap == null || locationsMap.isEmpty()) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (final String contextDN : locationsMap.keySet()) {
            final String displayName = locationsMap.get(contextDN);

            sb.append("<option value=\"").append(contextDN).append("\"");
            if (contextDN.equals(selectedValue)) {
                sb.append("selected=\"selected\"");
            }
            sb.append(">");
            sb.append(displayName);
            sb.append("</option>");
        }

        return sb.toString();
    }

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
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();
            final String selectedValue = ssBean.getLastParameterValue(name);
            final String optionListHtml = buildOptionListHTML(req, selectedValue);
            pageContext.getOut().write(optionListHtml);
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}


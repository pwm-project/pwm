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

package password.pwm.http.tag.value;

import password.pwm.PwmApplicationMode;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * @author Jason D. Rivard
 */
public class PwmValueTag extends TagSupport {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmValueTag.class);

    private PwmValue name;

    public PwmValue getName()
    {
        return name;
    }

    public void setName(final PwmValue name)
    {
        this.name = name;
    }

    public int doEndTag()
            throws JspTagException
    {
        if (PwmApplicationMode.determineMode((HttpServletRequest) pageContext.getRequest()) == PwmApplicationMode.ERROR) {
            return EVAL_PAGE;
        }

        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final PwmRequest pwmRequest = PwmRequest.forRequest(req, (HttpServletResponse) pageContext.getResponse());
            try {
                // final VALUE value = Helper.readEnumFromString(VALUE.class, null, getName());
                final PwmValue value = getName();
                final String output = calcValue(pwmRequest, pageContext, value);
                pageContext.getOut().write(StringUtil.escapeHtml(output));

            } catch (IllegalArgumentException e) {
                LOGGER.error("can't output requested value name '" + getName() + "'");
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("error while processing PwmValueTag: " + e.getMessage());
        } catch (Exception e) {
            throw new JspTagException(e.getMessage(),e);
        }
        return EVAL_PAGE;
    }

    public String calcValue(
            final PwmRequest pwmRequest,
            final PageContext pageContext,
            final PwmValue value
    ) {

        if (value != null) {
            try {
                return value.getValueOutput().valueOutput(pwmRequest, pageContext);
            } catch (Exception e) {
                LOGGER.error("error executing value tag option '" + value.toString() + "', error: " + e.getMessage());
            }
        }

        return "";
    }
}

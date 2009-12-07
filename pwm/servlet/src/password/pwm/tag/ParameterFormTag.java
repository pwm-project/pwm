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
import password.pwm.config.Message;
import password.pwm.config.ParameterConfig;
import password.pwm.util.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;
import java.util.Map;
import java.util.Properties;

/**
 * Output form html elements.
 *
 * //@todo Could be greatly improved to avoid hardcoding html inside this class.
 *
 * @author Jason D. Rivard
 */
public class ParameterFormTag extends TagSupport {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ParameterFormTag.class);

    private String formName;

// -------------------------- STATIC METHODS --------------------------

    private static String getForm(
            final Map<String,
                    ParameterConfig> parameters,
            final Properties values,
            final PwmSession pwmSession)
    {
        if (parameters == null) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();

        for (final String key : parameters.keySet()) {
            final ParameterConfig param = parameters.get(key);
            sb.append(getFormLine(param, values.getProperty(param.getAttributeName(), ""), false, pwmSession));
            if (param.isConfirmationRequired()) {
                sb.append(getFormLine(param, values.getProperty(param.getAttributeName() + "_confirm", ""), true, pwmSession));
            }
        }
        return sb.toString();
    }

    private static String getFormLine(
            final ParameterConfig param,
            final String value,
            final boolean confirm,
            final PwmSession pwmSession
    )
    {
        final StringBuilder sb = new StringBuilder();

        {
            sb.append("<h2>");
            if (confirm) {
                final String confirmPrefix = Message.getDisplayString("Field_Confirm_Prefix",pwmSession.getSessionStateBean().getLocale());
                sb.append(confirmPrefix);
                sb.append(" ");
            }
            sb.append(param.getLabel());
            sb.append("</h2>");
            sb.append("\n");

            {
                sb.append("<input");
                if (param.getType() == ParameterConfig.Type.PASSWORD) {
                    sb.append(" type=\"password\"");
                } else {
                    sb.append(" type=\"text\"");
                }

                sb.append(" name=\"").append(param.getAttributeName());
                if (confirm) {
                    sb.append("_confirm");
                }
                sb.append('\"');
                sb.append(" class=\"inputfield\"");
                sb.append(" maxlength=\"").append(param.getMaximumLength()).append('\"');
                sb.append(" value=\"").append(value).append('\"');
                sb.append("/>");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getFormName()
    {
        return formName;
    }

    public void setFormName(final String formName)
    {
        this.formName = formName;
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final PwmSession pwmSession = PwmSession.getPwmSession(req);

            final Properties lastValues = pwmSession.getSessionStateBean().getLastParameterValues();
            final String formText = getForm(this.getParameterMap(pwmSession), lastValues, pwmSession);

            pageContext.getOut().write(formText);
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }

// -------------------------- OTHER METHODS --------------------------

    private Map<String, ParameterConfig> getParameterMap(final PwmSession pwmSession)
    {
        if (formName.equalsIgnoreCase("newuser")) {
            return pwmSession.getNewUserServletBean().getCreationParams();
        } else if (formName.equalsIgnoreCase("activateuser")) {
            return pwmSession.getActivateUserServletBean().getActivateUserParams();
        } else if (formName.equalsIgnoreCase("updateattributes")) {
            return pwmSession.getUpdateAttributesServletBean().getUpdateAttributesParams();
        } else {
            LOGGER.warn("unknown form '" + formName + "' while generating ParamterFormTag");
        }
        return null;
    }
}


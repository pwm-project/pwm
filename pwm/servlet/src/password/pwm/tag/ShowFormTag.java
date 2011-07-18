/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

import org.apache.commons.lang.StringEscapeUtils;
import password.pwm.PwmSession;
import password.pwm.config.Configuration;
import password.pwm.config.Display;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Output form html elements.
 * <p/>
 * //@todo Could be greatly improved to avoid hardcoding html inside this class.
 *
 * @author Jason D. Rivard
 */
public class ShowFormTag extends TagSupport {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ShowFormTag.class);

    private String formName;

// -------------------------- STATIC METHODS --------------------------

    private static String getForm(
            final List<FormConfiguration> formFields,
            final Properties values,
            final PwmSession pwmSession) throws PwmUnrecoverableException {
        if (formFields == null) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();

        for (final FormConfiguration formField : formFields) {
            sb.append(getFormLine(formField, values.getProperty(formField.getAttributeName(), ""), false, pwmSession));
            if (formField.isConfirmationRequired()) {
                sb.append(getFormLine(formField, values.getProperty(formField.getAttributeName() + "_confirm", ""), true, pwmSession));
            }
        }
        return sb.toString();
    }

    private static String getFormLine(
            final FormConfiguration param,
            final String value,
            final boolean confirm,
            final PwmSession pwmSession
    ) throws PwmUnrecoverableException {
        final StringBuilder sb = new StringBuilder();
        final Configuration config = pwmSession.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        {
            sb.append("<h2>");
            if (confirm) {
                final String confirmPrefix = Display.getLocalizedMessage(locale, "Field_Confirm_Prefix", config);
                sb.append(confirmPrefix);
                sb.append(" ");
            }
            sb.append(param.getLabel());
            sb.append("</h2>");
            sb.append("\n");

            {
                sb.append("<input");
                if (FormConfiguration.Type.PASSWORD == param.getType()) {
                    sb.append(" type=\"password\"");
                } else if (FormConfiguration.Type.EMAIL == param.getType()) {
                    sb.append(" type=\"email\"");
                } else if (FormConfiguration.Type.READONLY == param.getType()) {
                    sb.append(" type=\"text\" readonly=\"true\"");
                } else if (FormConfiguration.Type.NUMBER == param.getType()) {
                    sb.append(" type=\"number\"");
                } else {
                    sb.append(" type=\"text\"");
                }

                if (param.isRequired()) {
                    sb.append(" required=\"true\"");
                }

                sb.append(" name=\"").append(param.getAttributeName());
                if (confirm) {
                    sb.append("_confirm");
                }
                sb.append('\"');
                sb.append(" class=\"inputfield\"");
                sb.append(" maxlength=\"").append(param.getMaximumLength()).append('\"');
                if ((FormConfiguration.Type.RANDOM == param.getType()) &&
                    (value == null || value.length() == 0)) {
	                final String randomChars = config.readSettingAsString(PwmSetting.CHALLENGE_TOKEN_CHARACTERS);
	                final int randomLength = (param.getMaximumLength()<=0)?(int)config.readSettingAsLong(PwmSetting.CHALLENGE_TOKEN_LENGTH):param.getMaximumLength();
    	        	final String randvalue = PwmRandom.getInstance().alphaNumericString(randomChars, randomLength);
		            sb.append(" value=\"").append(randvalue).append('\"');
                } else {
                sb.append(" value=\"").append(StringEscapeUtils.escapeHtml(value)).append('\"');
                }
                sb.append("/>");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getFormName() {
        return formName;
    }

    public void setFormName(final String formName) {
        this.formName = formName;
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final PwmSession pwmSession = PwmSession.getPwmSession(req);

            final Properties lastValues = pwmSession.getSessionStateBean().getLastParameterValues();
            final String formText = getForm(this.getForm(pwmSession), lastValues, pwmSession);

            pageContext.getOut().write(formText);
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }

// -------------------------- OTHER METHODS --------------------------

    private List<FormConfiguration> getForm(final PwmSession pwmSession) throws PwmUnrecoverableException {
        if (formName.equalsIgnoreCase("newuser")) {
            return pwmSession.getConfig().readSettingAsForm(PwmSetting.NEWUSER_FORM, pwmSession.getSessionStateBean().getLocale());
        } else if (formName.equalsIgnoreCase("activateuser")) {
            return pwmSession.getConfig().readSettingAsForm(PwmSetting.ACTIVATE_USER_FORM, pwmSession.getSessionStateBean().getLocale());
        } else if (formName.equalsIgnoreCase("updateprofile")) {
            return pwmSession.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM, pwmSession.getSessionStateBean().getLocale());
        } else if (formName.equalsIgnoreCase("forgottenusername")) {
            return pwmSession.getConfig().readSettingAsForm(PwmSetting.FORGOTTEN_USERNAME_FORM, pwmSession.getSessionStateBean().getLocale());
        } else if (formName.equalsIgnoreCase("newguest")) {
            return pwmSession.getConfig().readSettingAsForm(PwmSetting.GUEST_FORM, pwmSession.getSessionStateBean().getLocale());
        } else if (formName.equalsIgnoreCase("updateguest")) {
            return pwmSession.getGuestUpdateServletBean().getUpdateParams();
        } else {
            LOGGER.warn("unknown form '" + formName + "' while generating ParamterFormTag");
        }
        return null;
    }
}


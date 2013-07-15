/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmPasswordPolicy;
import password.pwm.PwmSession;
import password.pwm.config.Configuration;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.util.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

/**
 * @author Jason D. Rivard
 */
public class PasswordRequirementsTag extends TagSupport {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PasswordRequirementsTag.class);
    private String separator;
    private String prepend;
    private String form;

// -------------------------- STATIC METHODS --------------------------

    public static List<String> getPasswordRequirementsStrings(
            final PwmPasswordPolicy pwordPolicy,
            final Configuration config,
            final Locale locale
    ) {
        final List<String> returnValues = new ArrayList<String>();

        int value = 0;

        final PwmPasswordPolicy.RuleHelper ruleHelper = pwordPolicy.getRuleHelper();

        if (ruleHelper.readBooleanValue(PwmPasswordRule.CaseSensitive)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_CASESENSITIVE, value, locale, config));
        } else {
            returnValues.add(getLocalString(Message.REQUIREMENT_NOTCASESENSITIVE, value, locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumLength);
        if (value > 0 || ruleHelper.readBooleanValue(PwmPasswordRule.ADComplexity)) {
            if (value < 6 && ruleHelper.readBooleanValue(PwmPasswordRule.ADComplexity)) {
                value = 6;
            }
            returnValues.add(getLocalString(Message.REQUIREMENT_MINLENGTH, value, locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MaximumLength);
        if (value > 0 && value < 64) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXLENGTH, value, locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumAlpha);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MINALPHA, value, locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MaximumAlpha);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXALPHA, value, locale, config));
        }

        if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowNumeric)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_ALLOWNUMERIC, value, locale, config));
        } else {
            value = ruleHelper.readIntValue(PwmPasswordRule.MinimumNumeric);
            if (value > 0) {
                returnValues.add(getLocalString(Message.REQUIREMENT_MINNUMERIC, value, locale, config));
            }

            value = ruleHelper.readIntValue(PwmPasswordRule.MaximumNumeric);
            if (value > 0) {
                returnValues.add(getLocalString(Message.REQUIREMENT_MAXNUMERIC, value, locale, config));
            }

            if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowFirstCharNumeric)) {
                returnValues.add(getLocalString(Message.REQUIREMENT_FIRSTNUMERIC, value, locale, config));
            }

            if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowLastCharNumeric)) {
                returnValues.add(getLocalString(Message.REQUIREMENT_LASTNUMERIC, value, locale, config));
            }
        }

        if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowSpecial)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_ALLOWSPECIAL, value, locale, config));
        } else {
            value = ruleHelper.readIntValue(PwmPasswordRule.MinimumSpecial);
            if (value > 0) {
                returnValues.add(getLocalString(Message.REQUIREMENT_MINSPECIAL, value, locale, config));
            }

            value = ruleHelper.readIntValue(PwmPasswordRule.MaximumSpecial);
            if (value > 0) {
                returnValues.add(getLocalString(Message.REQUIREMENT_MAXSPECIAL, value, locale, config));
            }

            if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowFirstCharSpecial)) {
                returnValues.add(getLocalString(Message.REQUIREMENT_FIRSTSPECIAL, value, locale, config));
            }

            if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowLastCharSpecial)) {
                returnValues.add(getLocalString(Message.REQUIREMENT_LASTSPECIAL, value, locale, config));
            }
        }

        value = pwordPolicy.getRuleHelper().readIntValue(PwmPasswordRule.MaximumRepeat);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXREPEAT, value, locale, config));
        }

        value = pwordPolicy.getRuleHelper().readIntValue(PwmPasswordRule.MaximumSequentialRepeat);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXSEQREPEAT, value, locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumLowerCase);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MINLOWER, value, locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MaximumLowerCase);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXLOWER, value, locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumUpperCase);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MINUPPER, value, locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MaximumUpperCase);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXUPPER, value, locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumUnique);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MINUNIQUE, value, locale, config));
        }

        List<String> setValue = ruleHelper.getDisallowedValues();
        if (!setValue.isEmpty()) {
            final StringBuilder fieldValue = new StringBuilder();
            for (final String loopValue : setValue) {
                fieldValue.append(" ");
                fieldValue.append(StringEscapeUtils.escapeHtml(loopValue));
            }
            returnValues.add(getLocalString(Message.REQUIREMENT_DISALLOWEDVALUES, fieldValue.toString(), locale, config));
        }

        setValue = ruleHelper.getDisallowedAttributes();
        if (!setValue.isEmpty() || ruleHelper.readBooleanValue(PwmPasswordRule.ADComplexity)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_DISALLOWEDATTRIBUTES, "", locale, config));
        }

        if (ruleHelper.readBooleanValue(PwmPasswordRule.EnableWordlist)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_WORDLIST, "", locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MaximumOldChars);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_OLDCHAR, value, locale, config));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumLifetime);
        if (value > 0) {
            final int SECONDS_PER_DAY = 60 * 60 * 24;

            final String durationStr;
            if (value % SECONDS_PER_DAY == 0) {
                final int valueAsDays = value / (60 * 60 * 24);
                final String key = valueAsDays <= 1 ? "Display_Day" : "Display_Days";
                durationStr = valueAsDays + " " + Display.getLocalizedMessage(locale, key, config);
            } else {
                final int valueAsHours = value / (60 * 60);
                final String key = valueAsHours <= 1 ? "Display_Hour" : "Display_Hours";
                durationStr = valueAsHours + " " + Display.getLocalizedMessage(locale, key, config);
            }

            final String userMsg = Message.getLocalizedMessage(locale, Message.REQUIREMENT_MINIMUMFREQUENCY, config, durationStr);
            returnValues.add(userMsg);
        }

        if (ruleHelper.readBooleanValue(PwmPasswordRule.ADComplexity)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_AD_COMPLEXITY, "", locale, config));
        }

        if (ruleHelper.readBooleanValue(PwmPasswordRule.UniqueRequired)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_UNIQUE_REQUIRED, "", locale, config));
        }

        return returnValues;
    }

    private static String getLocalString(final Message message, final int size, final Locale locale, final Configuration config) {
        if (size > 1) {
            try {
                try {
                    final Message pluralMessage = Message.valueOf(message.toString() + "PLURAL");
                    return Message.getLocalizedMessage(locale, pluralMessage, config, String.valueOf(size));
                } catch (IllegalArgumentException e) {/*noop*/ }
            } catch (MissingResourceException e) {
                LOGGER.error("unable to display requirement tag for message '" + message.toString() + "PLURAL': " + e.getMessage());
            }
        }

        try {
            return Message.getLocalizedMessage(locale, message, config, String.valueOf(size));
        } catch (MissingResourceException e) {
            LOGGER.error("unable to display requirement tag for message '" + message.toString() + "': " + e.getMessage());
        }
        return "UNKNOWN MESSAGE STRING";
    }

    private static String getLocalString(final Message message, final String field, final Locale locale, final Configuration config) {
        try {
            return Message.getLocalizedMessage(locale, message, config, field);
        } catch (MissingResourceException e) {
            LOGGER.error("unable to display requirement tag for message '" + message.toString() + "': " + e.getMessage());
        }
        return "UNKNOWN MESSAGE STRING";
    }
// --------------------- GETTER / SETTER METHODS ---------------------

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(final String separator) {
        this.separator = separator;
    }

    public String getPrepend() {
        return prepend;
    }

    public void setPrepend(final String prepend) {
        this.prepend = prepend;
    }

    public String getForm() {
        return form;
    }

    public void setForm(String form) {
        this.form = form;
    }

    // ------------------------ INTERFACE METHODS ------------------------


    // --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
            final Configuration config = pwmApplication.getConfig();

            final String configuredRuleText = config.readSettingAsLocalizedString(PwmSetting.PASSWORD_POLICY_RULE_TEXT, pwmSession.getSessionStateBean().getLocale());

            if (configuredRuleText != null && configuredRuleText.length() > 0) {
                pageContext.getOut().write(configuredRuleText);
            } else {
                final PwmPasswordPolicy passwordPolicy;
                if (getForm() != null && getForm().equalsIgnoreCase("newuser")) {
                    passwordPolicy = config.getNewUserPasswordPolicy(pwmApplication, pwmSession.getSessionStateBean().getLocale());
                } else {
                    passwordPolicy = pwmSession.getUserInfoBean().getPasswordPolicy();
                }
                final String pre = prepend != null && prepend.length() > 0 ? prepend : "";
                final String sep = separator != null && separator.length() > 0 ? separator : "<br/>";
                final List<String> requirementsList = getPasswordRequirementsStrings(passwordPolicy, config, pwmSession.getSessionStateBean().getLocale());

                final StringBuilder requirementsText = new StringBuilder();
                for (final String requirementStatement : requirementsList) {
                    requirementsText.append(pre);
                    requirementsText.append(requirementStatement);
                    requirementsText.append(sep);
                }

                pageContext.getOut().write(requirementsText.toString());
            }
        } catch (Exception e) {
            LOGGER.error("unexpected error during password requirements generation: " + e.getMessage(), e);
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}


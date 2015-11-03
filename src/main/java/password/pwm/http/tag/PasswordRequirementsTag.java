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

import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.servlet.NewUserServlet;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.util.LocaleHelper;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

    private static final PwmLogger LOGGER = PwmLogger.forClass(PasswordRequirementsTag.class);
    private String separator;
    private String prepend;
    private String form;

// -------------------------- STATIC METHODS --------------------------

    public static List<String> getPasswordRequirementsStrings(
            final PwmPasswordPolicy pwordPolicy,
            final Configuration config,
            final Locale locale
    ) {
        final List<String> returnValues = new ArrayList<>();
        final ADPolicyComplexity ADPolicyLevel = pwordPolicy.getRuleHelper().getADComplexityLevel();


        final PwmPasswordPolicy.RuleHelper ruleHelper = pwordPolicy.getRuleHelper();

        if (ruleHelper.readBooleanValue(PwmPasswordRule.CaseSensitive)) {
            returnValues.add(getLocalString(Message.Requirement_CaseSensitive, null, locale, config));
        } else {
            returnValues.add(getLocalString(Message.Requirement_NotCaseSensitive, null, locale, config));
        }

        {
            int value = ruleHelper.readIntValue(PwmPasswordRule.MinimumLength);
            if (ADPolicyLevel == ADPolicyComplexity.AD2003) {
                value = 6;
            }
            if (value == 0 && ADPolicyLevel == ADPolicyComplexity.AD2008) {
                value = 6;
            }
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_MinLength, value, locale, config));
            }
        }

        {
            final int value = ruleHelper.readIntValue(PwmPasswordRule.MaximumLength);
            if (value > 0 && value < 64) {
                returnValues.add(getLocalString(Message.Requirement_MaxLength, value, locale, config));
            }
        }

        {
            final int value = ruleHelper.readIntValue(PwmPasswordRule.MinimumAlpha);
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_MinAlpha, value, locale, config));
            }
        }

        {
            final int value = ruleHelper.readIntValue(PwmPasswordRule.MaximumAlpha);
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_MaxAlpha, value, locale, config));
            }
        }

        {
            if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowNumeric)) {
                returnValues.add(getLocalString(Message.Requirement_AllowNumeric, null, locale, config));
            } else {
                    final int minValue = ruleHelper.readIntValue(PwmPasswordRule.MinimumNumeric);
                    if (minValue > 0) {
                        returnValues.add(getLocalString(Message.Requirement_MinNumeric, minValue, locale, config));
                    }

                    final int maxValue = ruleHelper.readIntValue(PwmPasswordRule.MaximumNumeric);
                    if (maxValue > 0) {
                        returnValues.add(getLocalString(Message.Requirement_MaxNumeric, maxValue, locale, config));
                    }

                    if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowFirstCharNumeric)) {
                        returnValues.add(getLocalString(Message.Requirement_FirstNumeric, maxValue, locale, config));
                    }

                    if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowLastCharNumeric)) {
                        returnValues.add(getLocalString(Message.Requirement_LastNumeric, maxValue, locale, config));
                    }
            }
        }

        {
            if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowSpecial)) {
                returnValues.add(getLocalString(Message.Requirement_AllowSpecial, null, locale, config));
            } else {
                final int minValue = ruleHelper.readIntValue(PwmPasswordRule.MinimumSpecial);
                if (minValue > 0) {
                    returnValues.add(getLocalString(Message.Requirement_MinSpecial, minValue, locale, config));
                }

                final int maxValue = ruleHelper.readIntValue(PwmPasswordRule.MaximumSpecial);
                if (maxValue > 0) {
                    returnValues.add(getLocalString(Message.Requirement_MaxSpecial, maxValue, locale, config));
                }

                if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowFirstCharSpecial)) {
                    returnValues.add(getLocalString(Message.Requirement_FirstSpecial, maxValue, locale, config));
                }

                if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowLastCharSpecial)) {
                    returnValues.add(getLocalString(Message.Requirement_LastSpecial, maxValue, locale, config));
                }
            }
        }

        {
            final int value = pwordPolicy.getRuleHelper().readIntValue(PwmPasswordRule.MaximumRepeat);
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_MaxRepeat, value, locale, config));
            }
        }

        {
            final int value = pwordPolicy.getRuleHelper().readIntValue(PwmPasswordRule.MaximumSequentialRepeat);
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_MaxSeqRepeat, value, locale, config));
            }
        }

        {
            final int value = ruleHelper.readIntValue(PwmPasswordRule.MinimumLowerCase);
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_MinLower, value, locale, config));
            }
        }

        {
            final int value = ruleHelper.readIntValue(PwmPasswordRule.MaximumLowerCase);
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_MaxLower, value, locale, config));
            }
        }

        {
            final int value = ruleHelper.readIntValue(PwmPasswordRule.MinimumUpperCase);
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_MinUpper, value, locale, config));
            }
        }

        {
            final int value = ruleHelper.readIntValue(PwmPasswordRule.MaximumUpperCase);
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_MaxUpper, value, locale, config));
            }
        }

        {
            final int value = ruleHelper.readIntValue(PwmPasswordRule.MinimumUnique);
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_MinUnique, value, locale, config));
            }
        }

        {
            final List<String> setValue = ruleHelper.getDisallowedValues();
            if (!setValue.isEmpty()) {
                final StringBuilder fieldValue = new StringBuilder();
                for (final String loopValue : setValue) {
                    fieldValue.append(" ");
                    fieldValue.append(StringUtil.escapeHtml(loopValue));
                }
                returnValues.add(
                        getLocalString(Message.Requirement_DisAllowedValues, fieldValue.toString(), locale, config));
            }
        }

        {
            final List<String> setValue = ruleHelper.getDisallowedAttributes();
            if (!setValue.isEmpty() || ADPolicyLevel == ADPolicyComplexity.AD2003) {
                returnValues.add(getLocalString(Message.Requirement_DisAllowedAttributes, "", locale, config));
            }
        }

        if (ruleHelper.readBooleanValue(PwmPasswordRule.EnableWordlist)) {
            returnValues.add(getLocalString(Message.Requirement_WordList, "", locale, config));
        }

        {
            final int value = ruleHelper.readIntValue(PwmPasswordRule.MaximumOldChars);
            if (value > 0) {
                returnValues.add(getLocalString(Message.Requirement_OldChar, value, locale, config));
            }
        }

        {
            final int value = ruleHelper.readIntValue(PwmPasswordRule.MinimumLifetime);
            if (value > 0) {
                final int SECONDS_PER_DAY = 60 * 60 * 24;

                final String durationStr;
                if (value % SECONDS_PER_DAY == 0) {
                    final int valueAsDays = value / (60 * 60 * 24);
                    final Display key = valueAsDays <= 1 ? Display.Display_Day : Display.Display_Days;
                    durationStr = valueAsDays + " " + LocaleHelper.getLocalizedMessage(locale, key, config);
                } else {
                    final int valueAsHours = value / (60 * 60);
                    final Display key = valueAsHours <= 1 ? Display.Display_Hour : Display.Display_Hours;
                    durationStr = valueAsHours + " " + LocaleHelper.getLocalizedMessage(locale, key, config);
                }

                final String userMsg = Message.getLocalizedMessage(locale, Message.Requirement_MinimumFrequency, config,
                        durationStr);
                returnValues.add(userMsg);
            }
        }

        if (ADPolicyLevel == ADPolicyComplexity.AD2003) {
            returnValues.add(getLocalString(Message.Requirement_ADComplexity, "", locale, config));
        } else if (ADPolicyLevel == ADPolicyComplexity.AD2008) {
            final int maxViolations = ruleHelper.readIntValue(PwmPasswordRule.ADComplexityMaxViolations);
            final int minGroups = 5 - maxViolations;
            returnValues.add(getLocalString(Message.Requirement_ADComplexity2008, String.valueOf(minGroups), locale, config));
        }

        if (ruleHelper.readBooleanValue(PwmPasswordRule.UniqueRequired)) {
            returnValues.add(getLocalString(Message.Requirement_UniqueRequired, "", locale, config));
        }

        return returnValues;
    }

    private static String getLocalString(final Message message, final int size, final Locale locale, final Configuration config) {
        final Message effectiveMessage = size > 1 && message.getPluralMessage() != null
                ? message.getPluralMessage()
                : message;

        try {
            return Message.getLocalizedMessage(locale, effectiveMessage, config, String.valueOf(size));
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
            final PwmRequest pwmRequest = PwmRequest.forRequest((HttpServletRequest)pageContext.getRequest(),(HttpServletResponse)pageContext.getResponse());
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
            final Configuration config = pwmApplication.getConfig();


            final PwmPasswordPolicy passwordPolicy;
            if (getForm() != null && getForm().equalsIgnoreCase("newuser")) {
                final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile(pwmRequest);
                passwordPolicy = newUserProfile.getNewUserPasswordPolicy(pwmApplication, pwmSession.getSessionStateBean().getLocale());
            } else {
                passwordPolicy = pwmSession.getUserInfoBean().getPasswordPolicy();
            }

            final String configuredRuleText = passwordPolicy.getRuleText();
            if (configuredRuleText != null && configuredRuleText.length() > 0) {
                pageContext.getOut().write(configuredRuleText);
            } else {
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


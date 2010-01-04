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
import password.pwm.PwmPasswordPolicy;
import password.pwm.PwmSession;
import password.pwm.config.Message;
import password.pwm.config.PwmPasswordRule;
import password.pwm.util.PwmLogger;
import password.pwm.wordlist.WordlistStatus;

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
public class PasswordRequirementsTag extends TagSupport
{
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PasswordRequirementsTag.class);
    private String seperator;
    private String prepend;

// -------------------------- STATIC METHODS --------------------------

    public static List<String> getPasswordRequirementsStrings(
            final PwmPasswordPolicy pwordPolicy,
            final ContextManager contextManager,
            final Locale locale
    )
    {
        final List<String> returnValues = new ArrayList<String>();

        int value = 0;

        final PwmPasswordPolicy.RuleHelper ruleHelper = pwordPolicy.getRuleHelper();

        if (ruleHelper.readBooleanValue(PwmPasswordRule.CaseSensitive)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_CASESENSITIVE, value, locale));
        } else {
            returnValues.add(getLocalString(Message.REQUIREMENT_NOTCASESENSITIVE, value, locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumLength);
        if (value > 0 || ruleHelper.readBooleanValue(PwmPasswordRule.ADComplexity)) {
            if (value < 6 && ruleHelper.readBooleanValue(PwmPasswordRule.ADComplexity)) {
                value = 6;
            }
            returnValues.add(getLocalString(Message.REQUIREMENT_MINLENGTH, value, locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MaximumLength);
        if (value > 0 && value < 64) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXLENGTH, value, locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumAlpha);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MINALPHA, value, locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MaximumAlpha);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXALPHA, value, locale));
        }

        if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowNumeric)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_ALLOWNUMERIC, value, locale));
        } else {
            value = ruleHelper.readIntValue(PwmPasswordRule.MinimumNumeric);
            if (value > 0) {
                returnValues.add(getLocalString(Message.REQUIREMENT_MINNUMERIC, value, locale));
            }

            value = ruleHelper.readIntValue(PwmPasswordRule.MaximumNumeric);
            if (value > 0) {
                returnValues.add(getLocalString(Message.REQUIREMENT_MAXNUMERIC, value, locale));
            }

            if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowFirstCharNumeric)) {
                returnValues.add(getLocalString(Message.REQUIREMENT_FIRSTNUMERIC, value, locale));
            }

            if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowLastCharNumeric)) {
                returnValues.add(getLocalString(Message.REQUIREMENT_LASTNUMERIC, value, locale));
            }
        }

        if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowSpecial)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_ALLOWSPECIAL, value, locale));
        } else {
            value = ruleHelper.readIntValue(PwmPasswordRule.MinimumSpecial);
            if (value > 0) {
                returnValues.add(getLocalString(Message.REQUIREMENT_MINSPECIAL, value, locale));
            }

            value = ruleHelper.readIntValue(PwmPasswordRule.MaximumSpecial);
            if (value > 0) {
                returnValues.add(getLocalString(Message.REQUIREMENT_MAXSPECIAL, value, locale));
            }

            if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowFirstCharSpecial)) {
                returnValues.add(getLocalString(Message.REQUIREMENT_FIRSTSPECIAL, value, locale));
            }

            if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowLastCharSpecial)) {
                returnValues.add(getLocalString(Message.REQUIREMENT_LASTSPECIAL, value, locale));
            }
        }

        value = pwordPolicy.getRuleHelper().readIntValue(PwmPasswordRule.MaximumRepeat);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXREPEAT, value, locale));
        }

        value = pwordPolicy.getRuleHelper().readIntValue(PwmPasswordRule.MaximumSequentialRepeat);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXSEQREPEAT, value, locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumLowerCase);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MINLOWER, value, locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MaximumLowerCase);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXLOWER, value, locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumUpperCase);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MINUPPER, value, locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MaximumUpperCase);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MAXUPPER, value, locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumUnique);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_MINUNIQUE, value, locale));
        }

        List<String> setValue = ruleHelper.getDisallowedValues();
        if (!setValue.isEmpty()) {
            final StringBuilder fieldValue = new StringBuilder();
            for (final String loopValue : setValue) {
                fieldValue.append(" ");
                fieldValue.append(loopValue);
            }
            returnValues.add(getLocalString(Message.REQUIREMENT_DISALLOWEDVALUES, fieldValue.toString(), locale));
        }

        setValue = ruleHelper.getDisallowedAttributes();
        if (!setValue.isEmpty() || ruleHelper.readBooleanValue(PwmPasswordRule.ADComplexity)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_DISALLOWEDATTRIBUTES, "", locale));
        }

        if (contextManager.getWordlistManager().getStatus() != WordlistStatus.CLOSED) {
            returnValues.add(getLocalString(Message.REQUIREMENT_WORDLIST, "", locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MaximumOldChars);
        if (value > 0) {
            returnValues.add(getLocalString(Message.REQUIREMENT_OLDCHAR, value, locale));
        }

        value = ruleHelper.readIntValue(PwmPasswordRule.MinimumLifetime);
        if (value > 0) {
            final int SECONDS_PER_DAY = 60 * 60 * 24;
            String userMsg = getLocalString(Message.REQUIREMENT_MINIMUMFREQUENCY, 0, locale);

            final String durationStr;
            if (value % SECONDS_PER_DAY == 0) {
                final int valueAsDays = value / (60 * 60 * 24);
                final String key = valueAsDays <= 1 ? "Display_Day" : "Display_Days";
                durationStr = valueAsDays + " " + Message.getDisplayString(key, locale);
            } else {
                final int valueAsHours = value / (60 * 60);
                final String key = valueAsHours <= 1 ? "Display_Hour" : "Display_Hours";
                durationStr = valueAsHours + " " + Message.getDisplayString(key, locale);
            }

            userMsg = userMsg.replace(Message.FIELD_REPLACE_VALUE,durationStr);
            returnValues.add(userMsg);
        }

        if (ruleHelper.readBooleanValue(PwmPasswordRule.ADComplexity)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_AD_COMPLEXITY, "", locale));
        }

        if (ruleHelper.readBooleanValue(PwmPasswordRule.UniqueRequired)) {
            returnValues.add(getLocalString(Message.REQUIREMENT_UNIQUE_REQUIRED, "", locale));
        }

        return returnValues;
    }

    private static String getLocalString(final Message message, final int size, final Locale locale)
    {
        try {
            if (size > 1) {
                final Message pluralMessage = Message.forResourceKey(message.getResourceKey() + "Plural");
                if (pluralMessage != null) {
                    return Message.getLocalizedMessage(locale,pluralMessage,String.valueOf(size));
                }
            }
        } catch (MissingResourceException e) {
            //LOGGER.trace("unable to display requirement tag for message '" + message.toString() + "': " + e.getMessage());
        }

        try {
            return Message.getLocalizedMessage(locale,message,String.valueOf(size));
        } catch (MissingResourceException e) {
            LOGGER.error("unable to display requirement tag for message '" + message.toString() + "': " + e.getMessage());
        }
        return "UNKNOWN MESSAGE STRING";
    }

    private static String getLocalString(final Message message, final String field, final Locale locale)
    {
        try {
            return Message.getLocalizedMessage(locale,message,field);
        } catch (MissingResourceException e) {
            LOGGER.error("unable to display requirement tag for message '" + message.toString() + "': " + e.getMessage());
        }
        return "UNKNOWN MESSAGE STRING";
    }
// --------------------- GETTER / SETTER METHODS ---------------------

    public String getSeperator()
    {
        return seperator;
    }

    public void setSeperator(final String seperator)
    {
        this.seperator = seperator;
    }

    public String getPrepend() {
        return prepend;
    }

    public void setPrepend(final String prepend) {
        this.prepend = prepend;
    }

    // ------------------------ INTERFACE METHODS ------------------------


    // --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        try {
            final String pre = prepend != null && prepend.length() > 0 ? prepend : "";
            final String sep = seperator != null && seperator.length() > 0 ? seperator : "<br/>";
            final List<String> requirementsList = getPasswordRequirementsStrings(pwmSession.getUserInfoBean().getPasswordPolicy(), pwmSession.getContextManager(), pwmSession.getSessionStateBean().getLocale());

            final StringBuilder requirementsText = new StringBuilder();
            for (final String requirementStatement : requirementsList) {
                requirementsText.append(pre);
                requirementsText.append(requirementStatement);
                requirementsText.append(sep);
            }

            pageContext.getOut().write(requirementsText.toString());
        } catch (Exception e) {
            LOGGER.error(pwmSession, "unexpected error during password requirements generation: " + e.getMessage(),e);
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}


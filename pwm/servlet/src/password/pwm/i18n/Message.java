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

package password.pwm.i18n;

import password.pwm.config.Configuration;
import password.pwm.util.PwmLogger;

import java.util.Locale;


/**
 * Utility class for managing messages returned by the servlet for inclusion in UI screens.
 * This class contains a set of constants that match a corresponding properties file which
 * follows ResourceBundle rules for structure and internationalization.
 *
 * @author Jason D. Rivard
 */
public enum Message {
    SUCCESS_PASSWORDCHANGE("Success_PasswordChange"),
    SUCCESS_SETUP_RESPONSES("Success_SetupResponse"),
    SUCCESS_CLEAR_RESPONSES("Success_ClearResponse"),
    SUCCESS_UNKNOWN("Success_Unknown"),
    SUCCESS_CREATE_USER("Success_CreateUser"),
    SUCCESS_NEWUSER_FORM("Success_NewUserForm"),
    SUCCESS_UPDATE_FORM("Success_UpdateForm"),
    SUCCESS_CREATE_GUEST("Success_CreateGuest"),
    SUCCESS_UPDATE_GUEST("Success_UpdateGuest"),
    SUCCESS_ACTIVATE_USER("Success_ActivateUser"),
    SUCCESS_UPDATE_ATTRIBUTES("Success_UpdateProfile"),
    SUCCESS_RESPONSES_MEET_RULES("Success_ResponsesMeetRules"),
    SUCCESS_UNLOCK_ACCOUNT("Success_UnlockAccount"),
    SUCCESS_FORGOTTEN_USERNAME("Success_ForgottenUsername"),
    SUCCESS_CONFIG_UPLOAD("Success_ConfigFileUpload"),
    SUCCESS_PASSWORDRESET("Success_PasswordReset"),
    SUCCESS_PASSWORDSEND("Success_PasswordSend"),
    SUCCESS_ACTION("Success_Action"),

    EVENT_LOG_AUTHENTICATE("EventLog_Authenticate"),
    EVENT_LOG_CHANGE_PASSWORD("EventLog_ChangePassword"),
    EVENT_LOG_RECOVER_PASSWORD("EventLog_RecoverPassword"),
    EVENT_LOG_SETUP_RESPONSES("EventLog_SetupResponses"),
    EVENT_LOG_ACTIVATE_USER("EventLog_ActivateUser"),
    EVENT_LOG_CREATE_USER("EventLog_CreateUser"),
    EVENT_LOG_UPDATE_PROFILE("EventLog_UpdateProfile"),
    EVENT_LOG_INTRUDER_LOCKOUT("EventLog_IntruderLockout"),
    EVENT_LOG_HELPDESK_SET_PASSWORD("EventLog_HelpdeskSetPassword"),
    EVENT_LOG_HELPDESK_UNLOCK_PASSWORD("EventLog_HelpdeskUnlockPassword"),
    EVENT_LOG_HELPDESK_CLEAR_RESPONSES("EventLog_HelpdeskClearResponses"),

    REQUIREMENT_MINLENGTH("Requirement_MinLength"),
    REQUIREMENT_MINLENGTHPLURAL("Requirement_MinLengthPlural"),
    REQUIREMENT_MAXLENGTH("Requirement_MaxLength"),
    REQUIREMENT_MAXLENGTHPLURAL("Requirement_MaxLengthPlural"),
    REQUIREMENT_MINALPHA("Requirement_MinAlpha"),
    REQUIREMENT_MINALPHAPLURAL("Requirement_MinAlphaPlural"),
    REQUIREMENT_MAXALPHA("Requirement_MaxAlpha"),
    REQUIREMENT_MAXALPHAPLURAL("Requirement_MaxAlphaPlural"),
    REQUIREMENT_ALLOWNUMERIC("Requirement_AllowNumeric"),
    REQUIREMENT_MINNUMERIC("Requirement_MinNumeric"),
    REQUIREMENT_MINNUMERICPLURAL("Requirement_MinNumericPlural"),
    REQUIREMENT_MAXNUMERIC("Requirement_MaxNumeric"),
    REQUIREMENT_MAXNUMERICPLURAL("Requirement_MaxNumericPlural"),
    REQUIREMENT_FIRSTNUMERIC("Requirement_FirstNumeric"),
    REQUIREMENT_LASTNUMERIC("Requirement_LastNumeric"),
    REQUIREMENT_ALLOWSPECIAL("Requirement_AllowSpecial"),
    REQUIREMENT_MINSPECIAL("Requirement_MinSpecial"),
    REQUIREMENT_MINSPECIALPLURAL("Requirement_MinSpecialPlural"),
    REQUIREMENT_MAXSPECIAL("Requirement_MaxSpecial"),
    REQUIREMENT_MAXSPECIALPLURAL("Requirement_MaxSpecialPlural"),
    REQUIREMENT_FIRSTSPECIAL("Requirement_FirstSpecial"),
    REQUIREMENT_LASTSPECIAL("Requirement_LastSpecial"),
    REQUIREMENT_MAXREPEAT("Requirement_MaxRepeat"),
    REQUIREMENT_MAXREPEATPLURAL("Requirement_MaxRepeatPlural"),
    REQUIREMENT_MAXSEQREPEAT("Requirement_MaxSeqRepeat"),
    REQUIREMENT_MAXSEQREPEATPLURAL("Requirement_MaxSeqRepeatPlural"),
    REQUIREMENT_MINLOWER("Requirement_MinLower"),
    REQUIREMENT_MINLOWERPLURAL("Requirement_MinLowerPlural"),
    REQUIREMENT_MAXLOWER("Requirement_MaxLower"),
    REQUIREMENT_MAXLOWERPLURAL("Requirement_MaxLowerPlural"),
    REQUIREMENT_MINUPPER("Requirement_MinUpper"),
    REQUIREMENT_MINUPPERPLURAL("Requirement_MinUpperPlural"),
    REQUIREMENT_MAXUPPER("Requirement_MaxUpper"),
    REQUIREMENT_MAXUPPERPLURAL("Requirement_MaxUpperPlural"),
    REQUIREMENT_MINUNIQUE("Requirement_MinUnique"),
    REQUIREMENT_MINUNIQUEPLURAL("Requirement_MinUniquePlural"),
    REQUIREMENT_REQUIREDCHARS("Requirement_RequiredChars"),
    REQUIREMENT_DISALLOWEDVALUES("Requirement_DisAllowedValues"),
    REQUIREMENT_DISALLOWEDATTRIBUTES("Requirement_DisAllowedAttributes"),
    REQUIREMENT_WORDLIST("Requirement_WordList"),
    REQUIREMENT_OLDCHAR("Requirement_OldChar"),
    REQUIREMENT_OLDCHARPLURAL("Requirement_OldCharPlural"),
    REQUIREMENT_CASESENSITIVE("Requirement_CaseSensitive"),
    REQUIREMENT_NOTCASESENSITIVE("Requirement_NotCaseSensitive"),
    REQUIREMENT_MINIMUMFREQUENCY("Requirement_MinimumFrequency"),
    REQUIREMENT_AD_COMPLEXITY("Requirement_ADComplexity"),
    REQUIREMENT_UNIQUE_REQUIRED("Requirement_UniqueRequired");

// ------------------------------ FIELDS ------------------------------

    public static String FIELD_REPLACE_VALUE = "%1%";

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Message.class);

    private final String resourceKey;

// -------------------------- STATIC METHODS --------------------------

    public static String getLocalizedMessage(final Locale locale, final Message message, final Configuration config, final String... fieldValue) {
        return LocaleHelper.getLocalizedMessage(locale, message.getResourceKey(),config , Message.class, fieldValue);
    }

// --------------------------- CONSTRUCTORS ---------------------------

    Message(final String resourceKey) {
        this.resourceKey = resourceKey;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getResourceKey() {
        return resourceKey;
    }

// -------------------------- OTHER METHODS --------------------------

    public String getLocalizedMessage(final Locale locale, final Configuration config, final String... fieldValue) {
        return Message.getLocalizedMessage(locale, this, config, fieldValue);
    }

}

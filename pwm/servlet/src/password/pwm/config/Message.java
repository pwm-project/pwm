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

package password.pwm.config;

import com.novell.ldapchai.exception.ChaiErrorCode;
import password.pwm.error.ErrorInformation;
import password.pwm.util.PwmLogger;
import com.novell.security.nmas.NMASConstants;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Utility class for managing messages returned by the servlet for inclusion in UI screens.
 * This class contains a set of constants that match a corresponding properties file which
 * follows ResourceBundle rules for structure and internationalization.
 *
 * @author Jason D. Rivard
 */
public enum Message {
    PASSWORD_MISSING_CONFIRM("Password_MissingConfirm",null),
    PASSWORD_MISSING("Password_Missing",null),
    PASSWORD_DOESNOTMATCH("Password_DoesNotMatch",null),
    PASSWORD_PREVIOUSLYUSED("Password_PreviouslyUsed",ChaiErrorCode.DUPLICATE_PASSWORD),
    PASSWORD_BADOLDPASSWORD("Password_BadOldPassword",null),
    PASSWORD_BADPASSWORD("Password_BadPassword",null),
    PASSWORD_TOO_SHORT("Password_TooShort",ChaiErrorCode.PASSWORD_TOO_SHORT),
    PASSWORD_TOO_LONG("Password_TooLong",null),
    PASSWORD_NOT_ENOUGH_NUM("Password_NotEnoughNum",null),
    PASSWORD_NOT_ENOUGH_ALPHA("Password_NotEnoughAlpha",null),
    PASSWORD_NOT_ENOUGH_SPECIAL("Password_NotEnoughSpecial",null),
    PASSWORD_NOT_ENOUGH_LOWER("Password_NotEnoughLower",null),
    PASSWORD_NOT_ENOUGH_UPPER("Password_NotEnoughUpper",null),
    PASSWORD_NOT_ENOUGH_UNIQUE("Password_NotEnoughUnique",null),
    PASSWORD_TOO_MANY_REPEAT("Password_TooManyRepeat",null),
    PASSWORD_TOO_MANY_NUMERIC("Password_TooManyNumeric",null),
    PASSWORD_TOO_MANY_ALPHA("Password_TooManyAlpha",null),
    PASSWORD_TOO_MANY_LOWER("Password_TooManyLower",null),
    PASSWORD_TOO_MANY_UPPER("Password_TooManyUpper",null),
    PASSWORD_FIRST_IS_NUMERIC("Password_FirstIsNumeric",null),
    PASSWORD_LAST_IS_NUMERIC("Password_LastIsNumeric",null),
    PASSWORD_FIRST_IS_SPECIAL("Password_FirstIsSpecial",null),
    PASSWORD_LAST_IS_SPECIAL("Password_LastIsSpecial",null),
    PASSWORD_TOO_MANY_SPECIAL("Password_TooManyNonAlphaSpecial",null),
    PASSWORD_INVALID_CHAR("Password_InvalidChar",null),
    PASSWORD_REQUIREDMISSING("Password_RequiredMissing",null),
    PASSWORD_INWORDLIST("Password_InWordlist",null),
    PASSWORD_SAMEASOLD("Password_SameAsOld",null),
    PASSWORD_SAMEASATTR("Password_SameAsAttr",null),
    PASSWORD_MEETS_RULES("Password_MeetsRules",null),
    PASSWORD_TOO_MANY_OLD_CHARS("Password_TooManyOldChars",null),
    PASSWORD_HISTORY_FULL("Password_HistoryFull",null),
    PASSWORD_TOO_SOON("Password_TooSoon",null),
    PASSWORD_USING_DISALLOWED_VALUE("Password_UsingDisallowedValue",null),

    ERROR_WRONGPASSWORD("Error_WrongPassword",null),
    ERROR_WRONGANSWER("Error_WrongAnswer",null),
    ERROR_USERAUTHENTICATED("Error_UserAuthenticated",null),
    ERROR_AUTHENTICATION_REQUIRED("Error_AuthenticationRequired",null),
    ERROR_RESPONSE_WRONGUSER("Error_Response_WrongUsername",null),
    ERROR_RESPONSES_NORESPONSES("Error_Response_NoResponse",null),
    ERROR_RESPONSE_WORDLIST("Error_Response_Wordlist",null),
    ERROR_RESPONSE_TOO_SHORT("Error_Response_TooShort",null),
    ERROR_RESPONSE_TOO_LONG("Error_Response_TooLong",null),
    ERROR_RESPONSE_DUPLICATE("Error_Response_Duplicate",null),
    ERROR_CHALLENGE_DUPLICATE("Error_Challenge_Duplicate",null),
    ERROR_MISSING_CHALLENGE_TEXT("Error_Missing_Challenge_Text",null),
    ERROR_MISSING_PARAMETER("Error_MissingParameter",null),
    ERROR_FIELDS_DONT_MATCH("Error_FieldsDontMatch",null),
    ERROR_UNKNOWN("Error_Unknown",null),
    ERROR_CANT_MATCH_USER("Error_CantMatchUser",null),
    ERROR_DIRECTORY_UNAVAILABLE("Error_DirectoryUnavailable",null),
    ERROR_NEW_USER_VALIDATION_FAILED("Error_NewUserValidationFailed",null),
    ERROR_SERVICE_NOT_AVAILABLE("Error_ServiceNotAvailable",null),
    ERROR_USER_MISMATCH("Error_UserMisMatch",null),
    ERROR_ACTIVATE_USER_NO_QUERY_MATCH("Error_ActivateUserNoQueryMatch",null),
    ERROR_NO_CHALLENGES("Error_NoChallenges",null),
    ERROR_INTRUDER_USER("Error_UserIntruder",null),
    ERROR_INTRUDER_ADDRESS("Error_AddressIntruder",null),
    ERROR_INTRUDER_SESSION("Error_SessionIntruder",null),
    ERROR_BAD_SESSION_PASSWORD("Error_BadSessionPassword",null),
    ERROR_UNAUTHORIZED("Error_Unauthorized",null),
    ERROR_BAD_SESSION("Error_BadSession",null),
    ERROR_MISSING_REQUIRED_RESPONSE("Error_MissingRequiredResponse",null),
    ERROR_MISSING_RANDOM_RESPONSE("Error_MissingRandomResponse",null),
    ERROR_BAD_CAPTCHA_RESPONSE("Error_BadCaptchaResponse",null),
    ERROR_CAPTCHA_API_ERROR("Error_CaptchaAPIError",null),
    ERROR_INVALID_CONFIG("Error_InvalidConfig",null),


    ERROR_FIELD_REQUIRED("Error_FieldRequired",null),
    ERROR_FIELD_NOT_A_NUMBER("Error_FieldNotANumber",null),
    ERROR_FIELD_INVALID_EMAIL("Error_FieldInvalidEmail",null),
    ERROR_FIELD_TOO_SHORT("Error_FieldTooShort",null),
    ERROR_FIELD_TOO_LONG("Error_FieldTooLong",null),
    ERROR_FIELD_DUPLICATE("Error_FieldDuplicate",null),
    ERROR_FIELD_BAD_CONFIRM("Error_FieldBadConfirm",null),

    SUCCESS_PASSWORDCHANGE("Success_PasswordChange",null),
    SUCCESS_SETUP_RESPONSES("Success_SetupResponse",null),
    SUCCESS_UNKNOWN("Success_Unknown",null),
    SUCCESS_CREATE_USER("Success_CreateUser",null),
    SUCCESS_ACTIVATE_USER("Success_ActivateUser",null),
    SUCCESS_UPDATE_ATTRIBUTES("Success_UpdateAttributes",null),
    SUCCESS_RESPONSES_MEET_RULES("Success_ResponsesMeetRules",null),
    SUCCESS_UNLOCK_ACCOUNT("Success_UnlockAccount",null),

    EVENT_LOG_CHANGE_PASSWORD("EventLog_ChangePassword",null),
    EVENT_LOG_RECOVER_PASSWORD("EventLog_RecoverPassword",null),
    EVENT_LOG_SETUP_RESPONSES("EventLog_SetupResponses",null),
    EVENT_LOG_ACTIVATE_USER("EventLog_ActivateUser",null),
    EVENT_UPDATE_ATTRIBUTES("EventLog_UpdateAttributes",null),
    EVENT_INTRUDER_LOCKOUT("EventLog_IntruderLockout",null),

    REQUIREMENT_MINLENGTH("Requirement_MinLength",null),
    REQUIREMENT_MINLENGTHPLURAL("Requirement_MinLengthPlural",null),
    REQUIREMENT_MAXLENGTH("Requirement_MaxLength",null),
    REQUIREMENT_MAXLENGTHPLURAL("Requirement_MaxLengthPlural",null),
    REQUIREMENT_MINALPHA("Requirement_MinAlpha",null),
    REQUIREMENT_MINALPHAPLURAL("Requirement_MinAlphaPlural",null),
    REQUIREMENT_MAXALPHA("Requirement_MaxAlpha",null),
    REQUIREMENT_MAXALPHAPLURAL("Requirement_MaxAlphaPlural",null),
    REQUIREMENT_ALLOWNUMERIC("Requirement_AllowNumeric",null),
    REQUIREMENT_MINNUMERIC("Requirement_MinNumeric",null),
    REQUIREMENT_MINNUMERICPLURAL("Requirement_MinNumericPlural",null),
    REQUIREMENT_MAXNUMERIC("Requirement_MaxNumeric",null),
    REQUIREMENT_MAXNUMERICPLURAL("Requirement_MaxNumericPlural",null),
    REQUIREMENT_FIRSTNUMERIC("Requirement_FirstNumeric",null),
    REQUIREMENT_LASTNUMERIC("Requirement_LastNumeric",null),
    REQUIREMENT_ALLOWSPECIAL("Requirement_AllowSpecial",null),
    REQUIREMENT_MINSPECIAL("Requirement_MinSpecial",null),
    REQUIREMENT_MINSPECIALPLURAL("Requirement_MinSpecialPlural",null),
    REQUIREMENT_MAXSPECIAL("Requirement_MaxSpecial",null),
    REQUIREMENT_MAXSPECIALPLURAL("Requirement_MaxSpecialPlural",null),
    REQUIREMENT_FIRSTSPECIAL("Requirement_FirstSpecial",null),
    REQUIREMENT_LASTSPECIAL("Requirement_LastSpecial",null),
    REQUIREMENT_MAXREPEAT("Requirement_MaxRepeat",null),
    REQUIREMENT_MAXREPEATPLURAL("Requirement_MaxRepeatPlural",null),
    REQUIREMENT_MAXSEQREPEAT("Requirement_MaxSeqRepeat",null),
    REQUIREMENT_MAXSEQREPEATPLURAL("Requirement_MaxSeqRepeatPlural",null),
    REQUIREMENT_MINLOWER("Requirement_MinLower",null),
    REQUIREMENT_MINLOWERPLURAL("Requirement_MinLowerPlural",null),
    REQUIREMENT_MAXLOWER("Requirement_MaxLower",null),
    REQUIREMENT_MAXLOWERPLURAL("Requirement_MaxLowerPlural",null),
    REQUIREMENT_MINUPPER("Requirement_MinUpper",null),
    REQUIREMENT_MINUPPERPLURAL("Requirement_MinUpperPlural",null),
    REQUIREMENT_MAXUPPER("Requirement_MaxUpper",null),
    REQUIREMENT_MAXUPPERPLURAL("Requirement_MaxUpperPlural",null),
    REQUIREMENT_MINUNIQUE("Requirement_MinUnique",null),
    REQUIREMENT_MINUNIQUEPLURAL("Requirement_MinUniquePlural",null),
    REQUIREMENT_REQUIREDCHARS("Requirement_RequiredChars",null),
    REQUIREMENT_DISALLOWEDVALUES("Requirement_DisAllowedValues",null),
    REQUIREMENT_DISALLOWEDATTRIBUTES("Requirement_DisAllowedAttributes",null),
    REQUIREMENT_WORDLIST("Requirement_WordList",null),
    REQUIREMENT_OLDCHAR("Requirement_OldChar",null),
    REQUIREMENT_OLDCHARPLURAL("Requirement_OldCharPlural",null),
    REQUIREMENT_CASESENSITIVE("Requirement_CaseSensitive",null),
    REQUIREMENT_NOTCASESENSITIVE("Requirement_NotCaseSensitive",null),
    REQUIREMENT_MINIMUMFREQUENCY("Requirement_MinimumFrequency",null),
    REQUIREMENT_AD_COMPLEXITY("Requirement_ADComplexity",null),
    REQUIREMENT_UNIQUE_REQUIRED("Requirement_UniqueRequired",null);

// ------------------------------ FIELDS ------------------------------

    public static String FIELD_REPLACE_VALUE = "%field%";

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Message.class);

    private final String resourceKey;
    private final ChaiErrorCode chaiErrorCode;

// -------------------------- STATIC METHODS --------------------------

    public static Message forResourceKey(final String key) {
        for (final Message m : Message.values()) {
            if (m.getResourceKey().equals(key)) {
                return m;
            }
        }

        LOGGER.trace("attempt to find message for unknown key: " + key);
        return null;
    }

    public static String getLocalizedMessage(final Locale locale, final Message message)
    {
        return getLocalizedMessage(locale, message, null);
    }

    public static String getLocalizedMessage(final Locale locale, final Message message, final String fieldValue)
    {
        final ResourceBundle bundle = getMessageBundle(locale);
        String result = message.getResourceKey();
        try {
            result = bundle.getString(message.getResourceKey());
            if (fieldValue != null) {
                result = result.replaceAll(FIELD_REPLACE_VALUE, fieldValue);
            }
        } catch (Exception e) {
            LOGGER.trace("error fetching localized key for '" + message + "', error: " + e.getMessage());
        }
        return result;
    }

    private static ResourceBundle getMessageBundle(final Locale locale)
    {
        final ResourceBundle messagesBundle;
        if (locale == null) {
            messagesBundle = ResourceBundle.getBundle(Messages.class.getName());
        } else {
            messagesBundle = ResourceBundle.getBundle(Messages.class.getName(), locale);
        }

        return messagesBundle;
    }

    public static Message convertErrorString(final String errorMsg)
    {
        if (errorMsg.indexOf(ChaiErrorCode.DUPLICATE_PASSWORD.getErrorCode()) != -1) {
            return PASSWORD_PREVIOUSLYUSED;
        } else if (errorMsg.indexOf(ChaiErrorCode.PASSWORD_TOO_SHORT.getErrorCode()) != -1) {
            return  PASSWORD_TOO_SHORT;
        } else if (errorMsg.indexOf(ChaiErrorCode.BAD_PASSWORD.getErrorCode()) != -1) {
            return PASSWORD_BADPASSWORD;
        }

        LOGGER.error("unable to translate error message '" + errorMsg + "'");


        return ERROR_UNKNOWN;
    }

    /**
     * For a given NMAS error code, convert it to a localized PWM error string
     *
     * @param nmasErrorCode error code to translate
     * @return A localized PWM error string
     */
    public static Message forNmasCode(final int nmasErrorCode)
    {
        switch (nmasErrorCode) {
            case -215:
                return PASSWORD_PREVIOUSLYUSED;
            case -1696:
                return PASSWORD_HISTORY_FULL;
            case NMASConstants.NMAS_E_PASSWORD_TOO_LONG: // -16000;
                return PASSWORD_TOO_LONG;
            case NMASConstants.NMAS_E_PASSWORD_UPPER_MIN: // -16001;
                return PASSWORD_NOT_ENOUGH_UPPER;
            case NMASConstants.NMAS_E_PASSWORD_UPPER_MAX: // -16002;
                return PASSWORD_TOO_MANY_UPPER;
            case NMASConstants.NMAS_E_PASSWORD_LOWER_MIN: // -16003;
                return PASSWORD_NOT_ENOUGH_LOWER;
            case NMASConstants.NMAS_E_PASSWORD_LOWER_MAX: // -16004;
                return PASSWORD_TOO_MANY_LOWER;
            case NMASConstants.NMAS_E_PASSWORD_NUMERIC_DISALLOWED: // -16005;
                return PASSWORD_TOO_MANY_NUMERIC;
            case NMASConstants.NMAS_E_PASSWORD_NUMERIC_FIRST: // -16006;
                return PASSWORD_FIRST_IS_NUMERIC;
            case NMASConstants.NMAS_E_PASSWORD_NUMERIC_LAST: // -16007;
                return PASSWORD_LAST_IS_NUMERIC;
            case NMASConstants.NMAS_E_PASSWORD_NUMERIC_MIN: // -16008;
                return PASSWORD_NOT_ENOUGH_NUM;
            case NMASConstants.NMAS_E_PASSWORD_NUMERIC_MAX: // -16009;
                return PASSWORD_TOO_MANY_NUMERIC;
            case NMASConstants.NMAS_E_PASSWORD_SPECIAL_DISALLOWED: // -16010;
                return PASSWORD_TOO_MANY_SPECIAL;
            case NMASConstants.NMAS_E_PASSWORD_SPECIAL_FIRST: // -16011;
                return PASSWORD_FIRST_IS_SPECIAL;
            case NMASConstants.NMAS_E_PASSWORD_SPECIAL_LAST: // -16012;
                return PASSWORD_LAST_IS_SPECIAL;
            case NMASConstants.NMAS_E_PASSWORD_SPECIAL_MIN: // -16013;
                return PASSWORD_NOT_ENOUGH_SPECIAL;
            case NMASConstants.NMAS_E_PASSWORD_SPECIAL_MAX: // -16014;
                return PASSWORD_TOO_MANY_SPECIAL;
            case NMASConstants.NMAS_E_PASSWORD_REPEAT_CHAR_MAX: // -16015;
                return PASSWORD_TOO_MANY_REPEAT;
            case NMASConstants.NMAS_E_PASSWORD_CONSECUTIVE_MAX: // -16016;
                return PASSWORD_TOO_MANY_REPEAT;
            case NMASConstants.NMAS_E_PASSWORD_UNIQUE_MIN: // -16017;
                return PASSWORD_NOT_ENOUGH_UNIQUE;
            case NMASConstants.NMAS_E_PASSWORD_LIFE_MIN: // -16018;
                return PASSWORD_TOO_SOON;
            case NMASConstants.NMAS_E_PASSWORD_EXCLUDE: // -16019;
                return PASSWORD_INWORDLIST;
            case NMASConstants.NMAS_E_PASSWORD_ATTR_VALUE: // -16020;
                return PASSWORD_SAMEASATTR;
            case NMASConstants.NMAS_E_PASSWORD_EXTENDED_DISALLOWED: // -16021;
                return PASSWORD_INVALID_CHAR;
            case NMASConstants.NMAS_E_PASSWORD_RESERVED: // -16022;
                //@todo need an error code
        }

        LOGGER.error("unable to translate password error message '" + nmasErrorCode + "'");
        return PASSWORD_BADPASSWORD;
    }

    public static String getDisplayString(final String key, final Locale locale) {
        final ResourceBundle bundle = ResourceBundle.getBundle(Display.class.getName(), locale);
        return bundle.getString(key);
    }

// --------------------------- CONSTRUCTORS ---------------------------

    Message(final String resourceKey, final ChaiErrorCode chaiErrorCode) {
        this.resourceKey = resourceKey;
        this.chaiErrorCode = chaiErrorCode;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getResourceKey() {
        return resourceKey;
    }

// -------------------------- OTHER METHODS --------------------------

    public String getLocalizedMessage(final Locale locale) {
        return Message.getLocalizedMessage(locale,this);
    }

    public String getLocalizedMessage(final Locale locale, final String fieldValue) {
        return Message.getLocalizedMessage(locale,this,fieldValue);
    }

    public ErrorInformation toInfo() {
        return new ErrorInformation(this);
    }

    public static Message forChaiPasswordError(final ChaiErrorCode errorCode) {
        if (errorCode == null) {
            return null;
        }

        for (final Message m : values()) {
            if (m.chaiErrorCode == errorCode) {
                return m;
            }
        }

        return null;
    }

}



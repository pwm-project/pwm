/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

package password.pwm.error;

import com.novell.ldapchai.exception.ChaiErrorCode;
import password.pwm.config.Display;
import password.pwm.util.PwmLogger;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Utility class for managing messages returned by the servlet for inclusion in UI screens.
 * This class contains a set of constants that match a corresponding properties file which
 * follows ResourceBundle rules for structure and internationalization.
 *
 * @author Jason D. Rivard
 */
public enum PwmError {
    PASSWORD_MISSING_CONFIRM(           "Password_MissingConfirm",          4001, null),
    PASSWORD_MISSING(                   "Password_Missing",                 4002, null),
    PASSWORD_DOESNOTMATCH(              "Password_DoesNotMatch",            4003, null),
    PASSWORD_PREVIOUSLYUSED(            "Password_PreviouslyUsed",          4004, ChaiErrorCode.DUPLICATE_PASSWORD),
    PASSWORD_BADOLDPASSWORD(            "Password_BadOldPassword",          4005, null),
    PASSWORD_BADPASSWORD(               "Password_BadPassword",             4006, null),
    PASSWORD_TOO_SHORT(                 "Password_TooShort",                4007, ChaiErrorCode.PASSWORD_TOO_SHORT),
    PASSWORD_TOO_LONG(                  "Password_TooLong",                 4008, null),
    PASSWORD_NOT_ENOUGH_NUM(            "Password_NotEnoughNum",            4009, null),
    PASSWORD_NOT_ENOUGH_ALPHA(          "Password_NotEnoughAlpha",          4010, null),
    PASSWORD_NOT_ENOUGH_SPECIAL(        "Password_NotEnoughSpecial",        4011, null),
    PASSWORD_NOT_ENOUGH_LOWER(          "Password_NotEnoughLower",          4012, null),
    PASSWORD_NOT_ENOUGH_UPPER(          "Password_NotEnoughUpper",          4013, null),
    PASSWORD_NOT_ENOUGH_UNIQUE(         "Password_NotEnoughUnique",         4014, null),
    PASSWORD_TOO_MANY_REPEAT(           "Password_TooManyRepeat",           4015, null),
    PASSWORD_TOO_MANY_NUMERIC(          "Password_TooManyNumeric",          4016, null),
    PASSWORD_TOO_MANY_ALPHA(            "Password_TooManyAlpha",            4017, null),
    PASSWORD_TOO_MANY_LOWER(            "Password_TooManyLower",            4018, null),
    PASSWORD_TOO_MANY_UPPER(            "Password_TooManyUpper",            4019, null),
    PASSWORD_FIRST_IS_NUMERIC(          "Password_FirstIsNumeric",          4020, null),
    PASSWORD_LAST_IS_NUMERIC(           "Password_LastIsNumeric",           4021, null),
    PASSWORD_FIRST_IS_SPECIAL(          "Password_FirstIsSpecial",          4022, null),
    PASSWORD_LAST_IS_SPECIAL(           "Password_LastIsSpecial",           4023, null),
    PASSWORD_TOO_MANY_SPECIAL(          "Password_TooManyNonAlphaSpecial",  4024, null),
    PASSWORD_INVALID_CHAR(              "Password_InvalidChar",             4025, null),
    PASSWORD_REQUIREDMISSING(           "Password_RequiredMissing",         4026, null),
    PASSWORD_INWORDLIST(                "Password_InWordlist",              4027, null),
    PASSWORD_SAMEASOLD(                 "Password_SameAsOld",               4028, null),
    PASSWORD_SAMEASATTR(                "Password_SameAsAttr",              4029, null),
    PASSWORD_MEETS_RULES(               "Password_MeetsRules",              4030, null),
    PASSWORD_TOO_MANY_OLD_CHARS(        "Password_TooManyOldChars",         4031, null),
    PASSWORD_HISTORY_FULL(              "Password_HistoryFull",             4032, null),
    PASSWORD_TOO_SOON(                  "Password_TooSoon",                 4033, null),
    PASSWORD_USING_DISALLOWED_VALUE(    "Password_UsingDisallowedValue",    4034, null),

    ERROR_WRONGPASSWORD(                "Error_WrongPassword",              5001, null),
    ERROR_WRONGANSWER(                  "Error_WrongAnswer",                5002, null),
    ERROR_USERAUTHENTICATED(            "Error_UserAuthenticated",          5003, null),
    ERROR_AUTHENTICATION_REQUIRED(      "Error_AuthenticationRequired",     5004, null),
    ERROR_RESPONSE_WRONGUSER(           "Error_Response_WrongUsername",     5005, null),
    ERROR_RESPONSES_NORESPONSES(        "Error_Response_NoResponse",        5006, null),
    ERROR_RESPONSE_WORDLIST(            "Error_Response_Wordlist",          5007, null),
    ERROR_RESPONSE_TOO_SHORT(           "Error_Response_TooShort",          5008, null),
    ERROR_RESPONSE_TOO_LONG(            "Error_Response_TooLong",           5009, null),
    ERROR_RESPONSE_DUPLICATE(           "Error_Response_Duplicate",         5010, null),
    ERROR_CHALLENGE_DUPLICATE(          "Error_Challenge_Duplicate",        5011, null),
    ERROR_MISSING_CHALLENGE_TEXT(       "Error_Missing_Challenge_Text",     5012, null),
    ERROR_MISSING_PARAMETER(            "Error_MissingParameter",           5013, null),
    ERROR_FIELDS_DONT_MATCH(            "Error_FieldsDontMatch",            5014, null),
    ERROR_UNKNOWN(                      "Error_Unknown",                    5015, null),
    ERROR_CANT_MATCH_USER(              "Error_CantMatchUser",              5016, null),
    ERROR_DIRECTORY_UNAVAILABLE(        "Error_DirectoryUnavailable",       5017, null),
    ERROR_NEW_USER_VALIDATION_FAILED(   "Error_NewUserValidationFailed",    5018, null),
    ERROR_SERVICE_NOT_AVAILABLE(        "Error_ServiceNotAvailable",        5019, null),
    ERROR_USER_MISMATCH(                "Error_UserMisMatch",               5020, null),
    ERROR_ACTIVATE_USER_NO_QUERY_MATCH( "Error_ActivateUserNoQueryMatch",   5021, null),
    ERROR_NO_CHALLENGES(                "Error_NoChallenges",               5022, null),
    ERROR_INTRUDER_USER(                "Error_UserIntruder",               5023, null),
    ERROR_INTRUDER_ADDRESS(             "Error_AddressIntruder",            5024, null),
    ERROR_INTRUDER_SESSION(             "Error_SessionIntruder",            5025, null),
    ERROR_BAD_SESSION_PASSWORD(         "Error_BadSessionPassword",         5026, null),
    ERROR_UNAUTHORIZED(                 "Error_Unauthorized",               5027, null),
    ERROR_BAD_SESSION(                  "Error_BadSession",                 5028, null),
    ERROR_MISSING_REQUIRED_RESPONSE(    "Error_MissingRequiredResponse",    5029, null),
    ERROR_MISSING_RANDOM_RESPONSE(      "Error_MissingRandomResponse",      5030, null),
    ERROR_BAD_CAPTCHA_RESPONSE(         "Error_BadCaptchaResponse",         5031, null),
    ERROR_CAPTCHA_API_ERROR(            "Error_CaptchaAPIError",            5032, null),
    ERROR_INVALID_CONFIG(               "Error_InvalidConfig",              5033, null),
    ERROR_INVALID_FORMID(               "Error_InvalidFormID",              5034, null),

    ERROR_FIELD_REQUIRED(               "Error_FieldRequired",              5100, null),
    ERROR_FIELD_NOT_A_NUMBER(           "Error_FieldNotANumber",            5101, null),
    ERROR_FIELD_INVALID_EMAIL(          "Error_FieldInvalidEmail",          5102, null),
    ERROR_FIELD_TOO_SHORT(              "Error_FieldTooShort",              5103, null),
    ERROR_FIELD_TOO_LONG(               "Error_FieldTooLong",               5104, null),
    ERROR_FIELD_DUPLICATE(              "Error_FieldDuplicate",             5105, null),
    ERROR_FIELD_BAD_CONFIRM(            "Error_FieldBadConfirm",            5106, null),
    ;

// ------------------------------ FIELDS ------------------------------

    public static String FIELD_REPLACE_VALUE = "%field%";

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmError.class);

    private final int errorCode;
    private final String resourceKey;
    private final ChaiErrorCode chaiErrorCode;

// -------------------------- STATIC METHODS --------------------------

    public static PwmError forResourceKey(final String key) {
        for (final PwmError m : PwmError.values()) {
            if (m.getResourceKey().equals(key)) {
                return m;
            }
        }

        LOGGER.trace("attempt to find error for unknown key: " + key);
        return null;
    }

    public static String getLocalizedMessage(final Locale locale, final PwmError message)
    {
        return getLocalizedMessage(locale, message, null);
    }

    public static String getLocalizedMessage(final Locale locale, final PwmError message, final String fieldValue)
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
            messagesBundle = ResourceBundle.getBundle(PwmError.class.getName());
        } else {
            messagesBundle = ResourceBundle.getBundle(PwmError.class.getName(), locale);
        }

        return messagesBundle;
    }

    public static String getDisplayString(final String key, final Locale locale) {
        final ResourceBundle bundle = ResourceBundle.getBundle(Display.class.getName(), locale);
        return bundle.getString(key);
    }

// --------------------------- CONSTRUCTORS ---------------------------

    PwmError(final String resourceKey, final int errorCode, final ChaiErrorCode chaiErrorCode) {
        this.resourceKey = resourceKey;
        this.chaiErrorCode = chaiErrorCode;
        this.errorCode = errorCode;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getResourceKey() {
        return resourceKey;
    }

    public int getErrorCode() {
        return errorCode;
    }

    // -------------------------- OTHER METHODS --------------------------

    public String getLocalizedMessage(final Locale locale) {
        return PwmError.getLocalizedMessage(locale,this);
    }

    public String getLocalizedMessage(final Locale locale, final String fieldValue) {
        return PwmError.getLocalizedMessage(locale,this,fieldValue);
    }

    public ErrorInformation toInfo() {
        return new ErrorInformation(this);
    }

    public static PwmError forChaiPasswordError(final ChaiErrorCode errorCode) {
        if (errorCode == null) {
            return null;
        }

        for (final PwmError m : values()) {
            if (m.chaiErrorCode == errorCode) {
                return m;
            }
        }

        return null;
    }

}



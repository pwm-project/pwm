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

package password.pwm.error;

import com.novell.ldapchai.exception.ChaiError;
import password.pwm.config.Configuration;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import java.util.Locale;

/**
 * @author Jason D. Rivard
 */
public enum PwmError {
    PASSWORD_MISSING_CONFIRM("Password_MissingConfirm", 4001, false),
    PASSWORD_MISSING("Password_Missing", 4002, false),
    PASSWORD_DOESNOTMATCH("Password_DoesNotMatch", 4003, false),
    PASSWORD_PREVIOUSLYUSED("Password_PreviouslyUsed", 4004, false, ChaiError.PASSWORD_PREVIOUSLYUSED),
    PASSWORD_BADOLDPASSWORD("Password_BadOldPassword", 4005, false, ChaiError.PASSWORD_BADOLDPASSWORD),
    PASSWORD_BADPASSWORD("Password_BadPassword", 4006, false, ChaiError.PASSWORD_BADPASSWORD),
    PASSWORD_TOO_SHORT("Password_TooShort", 4007, false, ChaiError.PASSWORD_TOO_SHORT),
    PASSWORD_TOO_LONG("Password_TooLong", 4008, false, ChaiError.PASSWORD_TOO_LONG),
    PASSWORD_NOT_ENOUGH_NUM("Password_NotEnoughNum", 4009, false, ChaiError.PASSWORD_NOT_ENOUGH_NUM),
    PASSWORD_NOT_ENOUGH_ALPHA("Password_NotEnoughAlpha", 4010, false, ChaiError.PASSWORD_NOT_ENOUGH_ALPHA),
    PASSWORD_NOT_ENOUGH_SPECIAL("Password_NotEnoughSpecial", 4011, false, ChaiError.PASSWORD_NOT_ENOUGH_SPECIAL),
    PASSWORD_NOT_ENOUGH_LOWER("Password_NotEnoughLower", 4012, false, ChaiError.PASSWORD_NOT_ENOUGH_LOWER),
    PASSWORD_NOT_ENOUGH_UPPER("Password_NotEnoughUpper", 4013, false, ChaiError.PASSWORD_NOT_ENOUGH_UPPER),
    PASSWORD_NOT_ENOUGH_UNIQUE("Password_NotEnoughUnique", 4014, false, ChaiError.PASSWORD_NOT_ENOUGH_UNIQUE),
    PASSWORD_TOO_MANY_REPEAT("Password_TooManyRepeat", 4015, false, ChaiError.PASSWORD_TOO_MANY_REPEAT),
    PASSWORD_TOO_MANY_NUMERIC("Password_TooManyNumeric", 4016, false, ChaiError.PASSWORD_TOO_MANY_NUMERIC, ChaiError.PASSWORD_NUMERIC_DISALLOWED),
    PASSWORD_TOO_MANY_ALPHA("Password_TooManyAlpha", 4017, false, ChaiError.PASSWORD_TOO_MANY_ALPHA),
    PASSWORD_TOO_MANY_LOWER("Password_TooManyLower", 4018, false, ChaiError.PASSWORD_TOO_MANY_LOWER),
    PASSWORD_TOO_MANY_UPPER("Password_TooManyUpper", 4019, false, ChaiError.PASSWORD_TOO_MANY_UPPER),
    PASSWORD_FIRST_IS_NUMERIC("Password_FirstIsNumeric", 4020, false, ChaiError.PASSWORD_FIRST_IS_NUMERIC),
    PASSWORD_LAST_IS_NUMERIC("Password_LastIsNumeric", 4021, false, ChaiError.PASSWORD_LAST_IS_NUMERIC),
    PASSWORD_FIRST_IS_SPECIAL("Password_FirstIsSpecial", 4022, false, ChaiError.PASSWORD_FIRST_IS_SPECIAL),
    PASSWORD_LAST_IS_SPECIAL("Password_LastIsSpecial", 4023, false, ChaiError.PASSWORD_LAST_IS_SPECIAL),
    PASSWORD_TOO_MANY_SPECIAL("Password_TooManyNonAlphaSpecial", 4024, false, ChaiError.PASSWORD_TOO_MANY_SPECIAL, ChaiError.PASSWORD_NUMERIC_DISALLOWED),
    PASSWORD_INVALID_CHAR("Password_InvalidChar", 4025, false, ChaiError.PASSWORD_INVALID_CHAR),
    PASSWORD_REQUIREDMISSING("Password_RequiredMissing", 4026, false),
    PASSWORD_INWORDLIST("Password_InWordlist", 4027, false, ChaiError.PASSWORD_INWORDLIST),
    PASSWORD_SAMEASOLD("Password_SameAsOld", 4028, false),
    PASSWORD_SAMEASATTR("Password_SameAsAttr", 4029, false, ChaiError.PASSWORD_SAMEASATTR),
    PASSWORD_MEETS_RULES("Password_MeetsRules", 4030, false),
    PASSWORD_TOO_MANY_OLD_CHARS("Password_TooManyOldChars", 4031, false),
    PASSWORD_HISTORY_FULL("Password_HistoryFull", 4032, false, ChaiError.PASSWORD_HISTORY_FULL),
    PASSWORD_TOO_SOON("Password_TooSoon", 4033, false, ChaiError.PASSWORD_TOO_SOON),
    PASSWORD_USING_DISALLOWED_VALUE("Password_UsingDisallowedValue", 4034, false),
    PASSWORD_TOO_WEAK("Password_TooWeak", 4035, false),
    PASSWORD_TOO_MANY_NON_ALPHA("Password_TooManyNonAlpha", 4036, false),
    PASSWORD_NOT_ENOUGH_NON_ALPHA("Password_NotEnoughNonAlpha", 4037, false),
    PASSWORD_UNKNOWN_VALIDATION("Password_UnknownValidation", 4038, false),
    PASSWORD_NEW_PASSWORD_REQUIRED("Password_NewPasswordRequired", 4039, false, ChaiError.NEW_PASSWORD_REQUIRED),
    PASSWORD_EXPIRED("Password_Expired", 4040, false, ChaiError.PASSWORD_EXPIRED),
    PASSWORD_CUSTOM_ERROR("Password_CustomError", 4041, false),
    PASSWORD_NOT_ENOUGH_GROUPS("Password_NotEnoughGroups", 4042, false, (ChaiError[]) null),
    PASSWORD_TOO_MANY_CONSECUTIVE("Password_TooManyConsecutive", 4043, false),

    ERROR_WRONGPASSWORD("Error_WrongPassword", 5001, false, ChaiError.FAILED_AUTHENTICATION),
    ERROR_INCORRECT_RESPONSE("Error_WrongResponse", 5002, false),
    ERROR_USERAUTHENTICATED("Error_UserAuthenticated", 5003, false),
    ERROR_AUTHENTICATION_REQUIRED("Error_AuthenticationRequired", 5004, false),
    ERROR_RESPONSES_NORESPONSES("Error_Response_NoResponse", 5006, false),
    ERROR_RESPONSE_WORDLIST("Error_Response_Wordlist", 5007, false),
    ERROR_RESPONSE_TOO_SHORT("Error_Response_TooShort", 5008, false),
    ERROR_RESPONSE_TOO_LONG("Error_Response_TooLong", 5009, false),
    ERROR_RESPONSE_DUPLICATE("Error_Response_Duplicate", 5010, false),
    ERROR_CHALLENGE_DUPLICATE("Error_Challenge_Duplicate", 5011, false),
    ERROR_MISSING_CHALLENGE_TEXT("Error_Missing_Challenge_Text", 5012, false),
    ERROR_MISSING_PARAMETER("Error_MissingParameter", 5013, false),
    ERROR_UNKNOWN("Error_Unknown", 5015, false),
    ERROR_CANT_MATCH_USER("Error_CantMatchUser", 5016, false),
    ERROR_DIRECTORY_UNAVAILABLE("Error_DirectoryUnavailable", 5017, false),
    ERROR_ACTIVATION_VALIDATION_FAILED("Error_ActivationValidationFailed", 5018, false),
    ERROR_SERVICE_NOT_AVAILABLE("Error_ServiceNotAvailable", 5019, false),
    ERROR_USER_MISMATCH("Error_UserMisMatch", 5020, false),
    ERROR_ACTIVATE_USER_NO_QUERY_MATCH("Error_ActivateUserNoQueryMatch", 5021, false),
    ERROR_NO_CHALLENGES("Error_NoChallenges", 5022, false),
    ERROR_INTRUDER_USER("Error_UserIntruder", 5023, true),
    ERROR_INTRUDER_ADDRESS("Error_AddressIntruder", 5024, true),
    ERROR_INTRUDER_SESSION("Error_SessionIntruder", 5025, true),
    ERROR_BAD_SESSION_PASSWORD("Error_BadSessionPassword", 5026, false),
    ERROR_UNAUTHORIZED("Error_Unauthorized", 5027, false),
    ERROR_BAD_SESSION("Error_BadSession", 5028, false),
    ERROR_MISSING_REQUIRED_RESPONSE("Error_MissingRequiredResponse", 5029, false),
    ERROR_MISSING_RANDOM_RESPONSE("Error_MissingRandomResponse", 5030, false),
    ERROR_BAD_CAPTCHA_RESPONSE("Error_BadCaptchaResponse", 5031, false),
    ERROR_CAPTCHA_API_ERROR("Error_CaptchaAPIError", 5032, false),
    ERROR_INVALID_CONFIG("Error_InvalidConfig", 5033, false),
    ERROR_INVALID_FORMID("Error_InvalidFormID", 5034, false),
    ERROR_INCORRECT_REQUEST_SEQUENCE("Error_IncorrectRequestSequence", 5035, false),
    ERROR_TOKEN_MISSING_CONTACT("Error_TokenMissingContact", 5036, false),
    ERROR_TOKEN_INCORRECT("Error_TokenIncorrect", 5037, false),
    ERROR_BAD_CURRENT_PASSWORD("Error_BadCurrentPassword", 5038, false),
    ERROR_CLOSING("Error_Closing", 5039, false),
    ERROR_MISSING_GUID("Error_Missing_GUID", 5040, false),
    ERROR_TOKEN_EXPIRED("Error_TokenExpired", 5041, false),
    ERROR_MULTI_USERNAME("Error_Multi_Username", 5042, false),
    ERROR_ORIG_ADMIN_ONLY("Error_Orig_Admin_Only", 5043, false),
    ERROR_SECURE_REQUEST_REQUIRED("Error_SecureRequestRequired", 5044, false),
    ERROR_WRITING_RESPONSES("Error_Writing_Responses",5045, false),
    ERROR_UNLOCK_FAILURE("Error_Unlock_Failure",5046, false),
    ERROR_UPDATE_ATTRS_FAILURE("Error_Update_Attrs_Failure",5047, false),
    ERROR_ACTIVATION_FAILURE("Error_Activation_Failure",5048, false),
    ERROR_NEW_USER_FAILURE("Error_NewUser_Failure",5049, false),
    ERROR_ACTIVATION("Error_Activation",5050, false),
    ERROR_DB_UNAVAILABLE("Error_DB_Unavailable",5051, false),
    ERROR_LOCALDB_UNAVAILABLE("Error_LocalDB_Unavailable",5052, false),
    ERROR_APP_UNAVAILABLE("Error_App_Unavailable",5053, false),
    ERROR_UNREACHABLE_CLOUD_SERVICE("Error_UnreachableCloudService", 5054, false),
    ERROR_INVALID_SECURITY_KEY("Error_InvalidSecurityKey", 5055, false),
    ERROR_CLEARING_RESPONSES("Error_Clearing_Responses",5056, false),
    ERROR_SERVICE_UNREACHABLE("Error_ServiceUnreachable",5057, false),
    ERROR_CHALLENGE_IN_RESPONSE("Error_ChallengeInResponse", 5058, false),
    ERROR_CERTIFICATE_ERROR("Error_CertificateError", 5059, false),
    ERROR_SYSLOG_WRITE_ERROR("Error_SyslogWriteError",5060, false),
    ERROR_TOO_MANY_THREADS("Error_TooManyThreads",5061, false),
    ERROR_PASSWORD_REQUIRED("Error_PasswordRequired",5062, false),
    ERROR_SECURITY_VIOLATION("Error_SecurityViolation",5063, false),
    ERROR_TRIAL_VIOLATION("Error_TrialViolation",5064, false),
    ERROR_ACCOUNT_DISABLED("Error_AccountDisabled",5065, false, ChaiError.ACCOUNT_DISABLED),
    ERROR_ACCOUNT_EXPIRED("Error_AccountExpired",5066, false, ChaiError.ACCOUNT_EXPIRED),
    ERROR_NO_OTP_CONFIGURATION("Error_NoOtpConfiguration",5064, false),
    ERROR_INCORRECT_OTP_TOKEN("Error_WrongOtpToken", 5065, false),
    ERROR_WRITING_OTP_SECRET("Error_Writing_Otp_Secret", 5066, false),
    ERROR_INTRUDER_ATTR_SEARCH("Error_AttrIntruder", 5067, true),
    ERROR_AUDIT_WRITE("Error_AuditWrite", 5068, false),
    ERROR_INTRUDER_LDAP("Error_LdapIntruder", 5069, true, ChaiError.INTRUDER_LOCKOUT),
    ERROR_NO_LDAP_CONNECTION("Error_NoLdapConnection", 5070, true),
    ERROR_OAUTH_ERROR("Error_OAuthError",5071, true),
    ERROR_REPORTING_ERROR("Error_ReportingError", 5072, true),
    ERROR_INTRUDER_TOKEN_DEST("Error_TokenDestIntruder", 5073, true),
    ERROR_OTP_RECOVERY_USED("Error_OtpRecoveryUsed", 5074, true),
    ERROR_REDIRECT_ILLEGAL("Error_RedirectIllegal", 5075, true),
    ERROR_CRYPT_ERROR("Error_CryptError", 5076, true),
    ERROR_SMS_SEND_ERROR("Error_SmsSendError",5078, true),
    ERROR_LDAP_DATA_ERROR("Error_LdapDataError",5079, true),
    ERROR_MACRO_PARSE_ERROR("Error_MacroParseError",5080,true),
    ERROR_NO_PROFILE_ASSIGNED("Error_NoProfileAssigned",5081,true),
    ERROR_STARTUP_ERROR("Error_StartupError",5082,true),

    ERROR_FIELD_REQUIRED("Error_FieldRequired", 5100, false),
    ERROR_FIELD_NOT_A_NUMBER("Error_FieldNotANumber", 5101, false),
    ERROR_FIELD_INVALID_EMAIL("Error_FieldInvalidEmail", 5102, false),
    ERROR_FIELD_TOO_SHORT("Error_FieldTooShort", 5103, false),
    ERROR_FIELD_TOO_LONG("Error_FieldTooLong", 5104, false),
    ERROR_FIELD_DUPLICATE("Error_FieldDuplicate", 5105, false),
    ERROR_FIELD_BAD_CONFIRM("Error_FieldBadConfirm", 5106, false),
    ERROR_FIELD_REGEX_NOMATCH("Error_FieldRegexNoMatch", 5107, false),

    CONFIG_UPLOAD_SUCCESS("Error_ConfigUploadSuccess", 5200, false),
    CONFIG_UPLOAD_FAILURE("Error_ConfigUploadFailure", 5201, false),
    CONFIG_SAVE_SUCCESS("Error_ConfigSaveSuccess", 5202, false),
    CONFIG_FORMAT_ERROR("Error_ConfigFormatError", 5203, false),
    CONFIG_LDAP_FAILURE("Error_ConfigLdapFailure", 5204, false),
    CONFIG_LDAP_SUCCESS("Error_ConfigLdapSuccess", 5205, false),

    ERROR_HTTP_404("Error_HTTP_404",5300, false),

    ;


// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmError.class);

    private final int errorCode;
    private final String resourceKey;
    private final ChaiError[] chaiErrorCode;
    private final boolean errorIsPermanent;

// -------------------------- STATIC METHODS --------------------------

    public String getLocalizedMessage(final Locale locale, final Configuration config, final String... fieldValue) {
        return LocaleHelper.getLocalizedMessage(locale, this.getResourceKey(), config, password.pwm.i18n.Error.class, fieldValue);
    }

    public static PwmError forChaiError(final ChaiError errorCode) {
        if (errorCode == null) {
            return null;
        }

        for (final PwmError pwmError : values()) {
            if (pwmError.chaiErrorCode != null) {
                for (final ChaiError loopCode : pwmError.chaiErrorCode) {
                    if (loopCode == errorCode) {
                        return pwmError;
                    }
                }
            }
        }

        return null;
    }

    public static PwmError forErrorNumber(final int code) {
        for (final PwmError pwmError : values()) {
            if (pwmError.getErrorCode() == code) {
                return pwmError;
            }
        }

        return null;
    }

    public boolean isErrorIsPermanent() {
        return errorIsPermanent;
    }

    // --------------------------- CONSTRUCTORS ---------------------------

    PwmError(final String resourceKey, final int errorCode, boolean errorIsPermanent, final ChaiError... chaiErrorCode) {
        this.resourceKey = resourceKey;
        this.errorCode = errorCode;
        this.errorIsPermanent = errorIsPermanent;
        this.chaiErrorCode = chaiErrorCode;

    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getResourceKey() {
        return resourceKey;
    }

    public int getErrorCode() {
        return errorCode;
    }

    // -------------------------- OTHER METHODS --------------------------

    public ErrorInformation toInfo() {
        return new ErrorInformation(this);
    }

}



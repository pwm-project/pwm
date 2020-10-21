/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.error;

import com.novell.ldapchai.exception.ChaiError;
import password.pwm.config.Configuration;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Jason D. Rivard
 */
public enum PwmError
{
    PASSWORD_MISSING_CONFIRM(
            4001, "Password_MissingConfirm", null ),
    PASSWORD_MISSING(
            4002, "Password_Missing", null ),
    PASSWORD_DOESNOTMATCH(
            4003, "Password_DoesNotMatch", null ),
    PASSWORD_PREVIOUSLYUSED(
            4004, "Password_PreviouslyUsed", Collections.singleton( ChaiError.PASSWORD_PREVIOUSLYUSED ) ),
    PASSWORD_BADOLDPASSWORD(
            4005, "Password_BadOldPassword", Collections.singleton( ChaiError.PASSWORD_BADOLDPASSWORD ) ),
    PASSWORD_BADPASSWORD(
            4006, "Password_BadPassword", Collections.singleton( ChaiError.PASSWORD_BADPASSWORD ) ),
    PASSWORD_TOO_SHORT(
            4007, "Password_TooShort", Collections.singleton( ChaiError.PASSWORD_TOO_SHORT ) ),
    PASSWORD_TOO_LONG(
            4008, "Password_TooLong", Collections.singleton( ChaiError.PASSWORD_TOO_LONG ) ),
    PASSWORD_NOT_ENOUGH_NUM(
            4009, "Password_NotEnoughNum", Collections.singleton( ChaiError.PASSWORD_NOT_ENOUGH_NUM ) ),
    PASSWORD_NOT_ENOUGH_ALPHA(
            4010, "Password_NotEnoughAlpha", Collections.singleton( ChaiError.PASSWORD_NOT_ENOUGH_ALPHA ) ),
    PASSWORD_NOT_ENOUGH_SPECIAL(
            4011, "Password_NotEnoughSpecial", Collections.singleton( ChaiError.PASSWORD_NOT_ENOUGH_SPECIAL ) ),
    PASSWORD_NOT_ENOUGH_LOWER(
            4012, "Password_NotEnoughLower", Collections.singleton( ChaiError.PASSWORD_NOT_ENOUGH_LOWER ) ),
    PASSWORD_NOT_ENOUGH_UPPER(
            4013, "Password_NotEnoughUpper", Collections.singleton( ChaiError.PASSWORD_NOT_ENOUGH_UPPER ) ),
    PASSWORD_NOT_ENOUGH_UNIQUE(
            4014, "Password_NotEnoughUnique", Collections.singleton( ChaiError.PASSWORD_NOT_ENOUGH_UNIQUE ) ),
    PASSWORD_TOO_MANY_REPEAT(
            4015, "Password_TooManyRepeat", Collections.singleton( ChaiError.PASSWORD_TOO_MANY_REPEAT ) ),
    PASSWORD_TOO_MANY_NUMERIC(
            4016, "Password_TooManyNumeric", Stream.of( ChaiError.PASSWORD_TOO_MANY_NUMERIC, ChaiError.PASSWORD_NUMERIC_DISALLOWED ).collect( Collectors.toSet() ) ),
    PASSWORD_TOO_MANY_ALPHA(
            4017, "Password_TooManyAlpha", Collections.singleton( ChaiError.PASSWORD_TOO_MANY_ALPHA ) ),
    PASSWORD_TOO_MANY_LOWER(
            4018, "Password_TooManyLower", Collections.singleton( ChaiError.PASSWORD_TOO_MANY_LOWER ) ),
    PASSWORD_TOO_MANY_UPPER(
            4019, "Password_TooManyUpper", Collections.singleton( ChaiError.PASSWORD_TOO_MANY_UPPER ) ),
    PASSWORD_FIRST_IS_NUMERIC(
            4020, "Password_FirstIsNumeric", Collections.singleton( ChaiError.PASSWORD_FIRST_IS_NUMERIC ) ),
    PASSWORD_LAST_IS_NUMERIC(
            4021, "Password_LastIsNumeric", Collections.singleton( ChaiError.PASSWORD_LAST_IS_NUMERIC ) ),
    PASSWORD_FIRST_IS_SPECIAL(
            4022, "Password_FirstIsSpecial", Collections.singleton( ChaiError.PASSWORD_FIRST_IS_SPECIAL ) ),
    PASSWORD_LAST_IS_SPECIAL(
            4023, "Password_LastIsSpecial", Collections.singleton( ChaiError.PASSWORD_LAST_IS_SPECIAL ) ),
    PASSWORD_TOO_MANY_SPECIAL(
            4024, "Password_TooManyNonAlphaSpecial", Stream.of( ChaiError.PASSWORD_TOO_MANY_SPECIAL, ChaiError.PASSWORD_NUMERIC_DISALLOWED ).collect( Collectors.toSet() ) ),
    PASSWORD_INVALID_CHAR(
            4025, "Password_InvalidChar", Collections.singleton( ChaiError.PASSWORD_INVALID_CHAR ) ),
    PASSWORD_REQUIREDMISSING(
            4026, "Password_RequiredMissing", null ),
    PASSWORD_INWORDLIST(
            4027, "Password_InWordlist", Collections.singleton( ChaiError.PASSWORD_INWORDLIST ) ),
    PASSWORD_SAMEASOLD(
            4028, "Password_SameAsOld", null ),
    PASSWORD_SAMEASATTR(
            4029, "Password_SameAsAttr", Collections.singleton( ChaiError.PASSWORD_SAMEASATTR ) ),
    PASSWORD_MEETS_RULES(
            4030, "Password_MeetsRules", null ),
    PASSWORD_TOO_MANY_OLD_CHARS(
            4031, "Password_TooManyOldChars", null ),
    PASSWORD_HISTORY_FULL(
            4032, "Password_HistoryFull", Collections.singleton( ChaiError.PASSWORD_HISTORY_FULL ) ),
    PASSWORD_TOO_SOON(
            4033, "Password_TooSoon", Collections.singleton( ChaiError.PASSWORD_TOO_SOON ) ),
    PASSWORD_USING_DISALLOWED(
            4034, "Password_UsingDisallowedValue", null ),
    PASSWORD_TOO_WEAK(
            4035, "Password_TooWeak", null ),
    PASSWORD_TOO_MANY_NONALPHA(
            4036, "Password_TooManyNonAlpha", null ),
    PASSWORD_NOT_ENOUGH_NONALPHA(
            4037, "Password_NotEnoughNonAlpha", null ),
    PASSWORD_UNKNOWN_VALIDATION(
            4038, "Password_UnknownValidation", null ),
    PASSWORD_NEW_PASSWORD_REQUIRED(
            4039, "Password_NewPasswordRequired", Collections.singleton( ChaiError.NEW_PASSWORD_REQUIRED ) ),
    PASSWORD_EXPIRED(
            4040, "Password_Expired", Collections.singleton( ChaiError.PASSWORD_EXPIRED ) ),
    PASSWORD_CUSTOM_ERROR(
            4041, "Password_CustomError", null ),
    PASSWORD_NOT_ENOUGH_GROUPS(
            4042, "Password_NotEnoughGroups", null ),
    PASSWORD_TOO_MANY_CONSECUTIVE(
            4043, "Password_TooManyConsecutive", null ),

    ERROR_WRONGPASSWORD(
            5001, "Error_WrongPassword", Collections.singleton( ChaiError.FAILED_AUTHENTICATION ) ),
    ERROR_INCORRECT_RESPONSE(
            5002, "Error_WrongResponse", null ),
    ERROR_USERAUTHENTICATED(
            5003, "Error_UserAuthenticated", null ),
    ERROR_AUTHENTICATION_REQUIRED(
            5004, "Error_AuthenticationRequired", null ),
    ERROR_RESPONSES_NORESPONSES(
            5006, "Error_Response_NoResponse", null ),
    ERROR_RESPONSE_WORDLIST(
            5007, "Error_Response_Wordlist", null ),
    ERROR_RESPONSE_TOO_SHORT(
            5008, "Error_Response_TooShort", null ),
    ERROR_RESPONSE_TOO_LONG(
            5009, "Error_Response_TooLong", null ),
    ERROR_RESPONSE_DUPLICATE(
            5010, "Error_Response_Duplicate", null ),
    ERROR_CHALLENGE_DUPLICATE(
            5011, "Error_Challenge_Duplicate", null ),
    ERROR_MISSING_CHALLENGE_TEXT(
            5012, "Error_Missing_Challenge_Text", null ),
    ERROR_MISSING_PARAMETER(
            5013, "Error_MissingParameter", null ),
    ERROR_INTERNAL(
            5015, "Error_Internal", null, ErrorFlag.ForceLogout ),
    ERROR_CANT_MATCH_USER(
            5016, "Error_CantMatchUser", null ),
    ERROR_DIRECTORY_UNAVAILABLE(
            5017, "Error_DirectoryUnavailable", null, ErrorFlag.ForceLogout ),
    ERROR_ACTIVATION_VALIDATIONFAIL(
            5018, "Error_ActivationValidationFailed", null ),
    ERROR_SERVICE_NOT_AVAILABLE(
            5019, "Error_ServiceNotAvailable", null ),
    ERROR_USER_MISMATCH(
            5020, "Error_UserMisMatch", null ),
    ERROR_ACTIVATE_NO_PERMISSION(
            5021, "Error_ActivateUserNoQueryMatch", null ),
    ERROR_NO_CHALLENGES(
            5022, "Error_NoChallenges", null ),
    ERROR_INTRUDER_USER(
            5023, "Error_UserIntruder", null, ErrorFlag.Permanent ),
    ERROR_INTRUDER_ADDRESS(
            5024, "Error_AddressIntruder", null, ErrorFlag.Permanent ),
    ERROR_INTRUDER_SESSION(
            5025, "Error_SessionIntruder", null, ErrorFlag.Permanent ),
    ERROR_BAD_SESSION_PASSWORD(
            5026, "Error_BadSessionPassword", null ),
    ERROR_UNAUTHORIZED(
            5027, "Error_Unauthorized", null, ErrorFlag.Permanent ),
    ERROR_BAD_SESSION(
            5028, "Error_BadSession", null ),
    ERROR_MISSING_REQUIRED_RESPONSE(
            5029, "Error_MissingRequiredResponse", null ),
    ERROR_MISSING_RANDOM_RESPONSE(
            5030, "Error_MissingRandomResponse", null ),
    ERROR_BAD_CAPTCHA_RESPONSE(
            5031, "Error_BadCaptchaResponse", null ),
    ERROR_CAPTCHA_API_ERROR(
            5032, "Error_CaptchaAPIError", null ),
    ERROR_INVALID_CONFIG(
            5033, "Error_InvalidConfig", null ),
    ERROR_INVALID_FORMID(
            5034, "Error_InvalidFormID", null ),
    ERROR_INCORRECT_REQ_SEQUENCE(
            5035, "Error_IncorrectRequestSequence", null ),
    ERROR_TOKEN_MISSING_CONTACT(
            5036, "Error_TokenMissingContact", null ),
    ERROR_TOKEN_INCORRECT(
            5037, "Error_TokenIncorrect", null ),
    ERROR_BAD_CURRENT_PASSWORD(
            5038, "Error_BadCurrentPassword", null ),
    ERROR_CLOSING(
            5039, "Error_Closing", null ),
    ERROR_MISSING_GUID(
            5040, "Error_Missing_GUID", null ),
    ERROR_TOKEN_EXPIRED(
            5041, "Error_TokenExpired", null ),
    ERROR_MULTI_USERNAME(
            5042, "Error_Multi_Username", null ),
    ERROR_ORIG_ADMIN_ONLY(
            5043, "Error_Orig_Admin_Only", null ),
    ERROR_SECURE_REQUEST_REQUIRED(
            5044, "Error_SecureRequestRequired", null ),
    ERROR_WRITING_RESPONSES(
            5045, "Error_Writing_Responses", null ),
    ERROR_UNLOCK_FAILURE(
            5046, "Error_Unlock_Failure", null ),
    ERROR_UPDATE_ATTRS_FAILURE(
            5047, "Error_Update_Attrs_Failure", null ),
    ERROR_ACTIVATION_FAILURE(
            5048, "Error_Activation_Failure", null ),
    ERROR_NEW_USER_FAILURE(
            5049, "Error_NewUser_Failure", null ),
    ERROR_ACTIVATION(
            5050, "Error_Activation", null ),
    ERROR_DB_UNAVAILABLE(
            5051, "Error_DB_Unavailable", null ),
    ERROR_LOCALDB_UNAVAILABLE(
            5052, "Error_LocalDB_Unavailable", null ),
    ERROR_APP_UNAVAILABLE(
            5053, "Error_App_Unavailable", null ),
    ERROR_UNREACHABLE_CLOUD_SERVICE(
            5054, "Error_UnreachableCloudService", null ),
    ERROR_INVALID_SECURITY_KEY(
            5055, "Error_InvalidSecurityKey", null ),
    ERROR_CLEARING_RESPONSES(
            5056, "Error_Clearing_Responses", null ),
    ERROR_SERVICE_UNREACHABLE(
            5057, "Error_ServiceUnreachable", null ),
    ERROR_CHALLENGE_IN_RESPONSE(
            5058, "Error_ChallengeInResponse", null ),
    ERROR_CERTIFICATE_ERROR(
            5059, "Error_CertificateError", null ),
    ERROR_SYSLOG_WRITE_ERROR(
            5060, "Error_SyslogWriteError", null ),
    ERROR_TOO_MANY_THREADS(
            5061, "Error_TooManyThreads", null ),
    ERROR_PASSWORD_REQUIRED(
            5062, "Error_PasswordRequired", null ),
    ERROR_SECURITY_VIOLATION(
            5063, "Error_SecurityViolation", null ),
    ERROR_TRIAL_VIOLATION(
            5064, "Error_TrialViolation", null ),
    ERROR_ACCOUNT_DISABLED(
            5065, "Error_AccountDisabled", Collections.singleton( ChaiError.ACCOUNT_DISABLED ) ),
    ERROR_ACCOUNT_EXPIRED(
            5066, "Error_AccountExpired", Collections.singleton( ChaiError.ACCOUNT_EXPIRED ) ),
    ERROR_NO_OTP_CONFIGURATION(
            5087, "Error_NoOtpConfiguration", null ),
    ERROR_INCORRECT_OTP_TOKEN(
            5088, "Error_WrongOtpToken", null ),
    ERROR_WRITING_OTP_SECRET(
            5086, "Error_Writing_Otp_Secret", null ),
    ERROR_INTRUDER_ATTR_SEARCH(
            5067, "Error_AttrIntruder", null, ErrorFlag.Permanent ),
    ERROR_AUDIT_WRITE(
            5068, "Error_AuditWrite", null ),
    ERROR_INTRUDER_LDAP(
            5069, "Error_LdapIntruder", Collections.singleton( ChaiError.INTRUDER_LOCKOUT ), ErrorFlag.Permanent ),
    ERROR_NO_LDAP_CONNECTION(
            5070, "Error_NoLdapConnection", null, ErrorFlag.Permanent ),
    ERROR_OAUTH_ERROR(
            5071, "Error_OAuthError", null, ErrorFlag.Permanent ),
    ERROR_REPORTING_ERROR(
            5072, "Error_ReportingError", null, ErrorFlag.Permanent ),
    ERROR_INTRUDER_TOKEN_DEST(
            5073, "Error_TokenDestIntruder", null, ErrorFlag.Permanent ),
    ERROR_OTP_RECOVERY_USED(
            5074, "Error_OtpRecoveryUsed", null, ErrorFlag.Permanent ),
    ERROR_REDIRECT_ILLEGAL(
            5075, "Error_RedirectIllegal", null, ErrorFlag.Permanent ),
    ERROR_CRYPT_ERROR(
            5076, "Error_CryptError", null, ErrorFlag.Permanent ),
    ERROR_SMS_SEND_ERROR(
            5078, "Error_SmsSendError", null, ErrorFlag.Permanent ),
    ERROR_LDAP_DATA_ERROR(
            5079, "Error_LdapDataError", null, ErrorFlag.Permanent ),
    ERROR_MACRO_PARSE_ERROR(
            5080, "Error_MacroParseError", null, ErrorFlag.Permanent ),
    ERROR_NO_PROFILE_ASSIGNED(
            5081, "Error_NoProfileAssigned", null, ErrorFlag.Permanent ),
    ERROR_STARTUP_ERROR(
            5082, "Error_StartupError", null, ErrorFlag.Permanent ),
    ERROR_ENVIRONMENT_ERROR(
            5083, "Error_EnvironmentError", null, ErrorFlag.Permanent ),
    ERROR_APPLICATION_NOT_RUNNING(
            5084, "Error_ApplicationNotRunning", null, ErrorFlag.Permanent ),
    ERROR_EMAIL_SEND_FAILURE(
            5085, "Error_EmailSendFailure", null, ErrorFlag.Permanent ),
    ERROR_PASSWORD_ONLY_BAD(
            5089, "Error_PasswordOnlyBad", null ),
    ERROR_RECOVERY_SEQUENCE_INCOMPLETE(
            5090, "Error_RecoverySequenceIncomplete", null ),
    ERROR_FILE_TYPE_INCORRECT(
            5091, "Error_FileTypeIncorrect", null ),
    ERROR_FILE_TOO_LARGE(
            5092, "Error_FileTooLarge", null ),
    ERROR_NODE_SERVICE_ERROR(
            5093, "Error_NodeServiceError", null ),
    ERROR_WORDLIST_IMPORT_ERROR(
            5094, "Error_WordlistImportError", null ),
    ERROR_PWNOTIFY_SERVICE_ERROR(
            5095, "Error_PwNotifyServiceError", null ),

    ERROR_REMOTE_ERROR_VALUE(
            6000, "Error_RemoteErrorValue", null, ErrorFlag.Permanent ),
    ERROR_TELEMETRY_SEND_ERROR(
            6001, "Error_TelemetrySendError", null ),

    ERROR_FIELD_REQUIRED(
            5100, "Error_FieldRequired", null ),
    ERROR_FIELD_NOT_A_NUMBER(
            5101, "Error_FieldNotANumber", null ),
    ERROR_FIELD_INVALID_EMAIL(
            5102, "Error_FieldInvalidEmail", null ),
    ERROR_FIELD_TOO_SHORT(
            5103, "Error_FieldTooShort", null ),
    ERROR_FIELD_TOO_LONG(
            5104, "Error_FieldTooLong", null ),
    ERROR_FIELD_DUPLICATE(
            5105, "Error_FieldDuplicate", null ),
    ERROR_FIELD_BAD_CONFIRM(
            5106, "Error_FieldBadConfirm", null ),
    ERROR_FIELD_REGEX_NOMATCH(
            5107, "Error_FieldRegexNoMatch", null ),

    CONFIG_UPLOAD_SUCCESS(
            5200, "Error_ConfigUploadSuccess", null ),
    CONFIG_UPLOAD_FAILURE(
            5201, "Error_ConfigUploadFailure", null ),
    CONFIG_SAVE_SUCCESS(
            5202, "Error_ConfigSaveSuccess", null ),
    CONFIG_FORMAT_ERROR(
            5203, "Error_ConfigFormatError", null ),
    CONFIG_LDAP_FAILURE(
            5204, "Error_ConfigLdapFailure", null ),
    CONFIG_LDAP_SUCCESS(
            5205, "Error_ConfigLdapSuccess", null ),

    ERROR_HTTP_404(
            5300, "Error_HTTP_404", null ),

    ERROR_REST_INVOCATION_ERROR(
            7000, "Error_RestInvocationError", null ),
    ERROR_REST_PARAMETER_CONFLICT(
            7001, "Error_RestParameterConflict", null ),

    /* End of list*/;

    enum ErrorFlag
    {
        Permanent,
        ForceLogout,
    }

    private final int errorCode;
    private final String resourceKey;
    private final ChaiError[] chaiErrorCode;
    private final boolean errorIsPermanent;
    private final boolean forceLogout;

    PwmError(
            final int errorCode,
            final String resourceKey,
            final Set<ChaiError> chaiErrorCode,
            final ErrorFlag... errorFlags
    )
    {
        this.resourceKey = resourceKey;
        this.errorCode = errorCode;
        this.errorIsPermanent = JavaHelper.enumArrayContainsValue( errorFlags, ErrorFlag.Permanent );
        this.forceLogout = JavaHelper.enumArrayContainsValue( errorFlags, ErrorFlag.ForceLogout );
        this.chaiErrorCode = chaiErrorCode == null ? null : chaiErrorCode.toArray( new ChaiError[0] );

    }

    public String getLocalizedMessage( final Locale locale, final Configuration config, final String... fieldValue )
    {
        return LocaleHelper.getLocalizedMessage( locale, this.getResourceKey(), config, password.pwm.i18n.Error.class, fieldValue );
    }

    public static PwmError forChaiError( final ChaiError errorCode )
    {
        if ( errorCode == null )
        {
            return null;
        }

        for ( final PwmError pwmError : values() )
        {
            if ( pwmError.chaiErrorCode != null )
            {
                for ( final ChaiError loopCode : pwmError.chaiErrorCode )
                {
                    if ( loopCode == errorCode )
                    {
                        return pwmError;
                    }
                }
            }
        }

        return null;
    }

    public static PwmError forErrorNumber( final int code )
    {
        for ( final PwmError pwmError : values() )
        {
            if ( pwmError.getErrorCode() == code )
            {
                return pwmError;
            }
        }

        return null;
    }

    private boolean isForceLogout( )
    {
        return forceLogout;
    }

    public boolean isErrorIsPermanent( )
    {
        return errorIsPermanent;
    }

    public String getResourceKey( )
    {
        return resourceKey;
    }

    public int getErrorCode( )
    {
        return errorCode;
    }

    public ErrorInformation toInfo( )
    {
        return new ErrorInformation( this );
    }
}



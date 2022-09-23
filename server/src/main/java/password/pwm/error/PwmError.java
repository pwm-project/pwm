/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import password.pwm.config.SettingReader;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Jason D. Rivard
 */
public enum PwmError
{
    PASSWORD_MISSING_CONFIRM(
            4001, "Password_MissingConfirm", Collections.emptySet() ),
    PASSWORD_MISSING(
            4002, "Password_Missing", Collections.emptySet() ),
    PASSWORD_DOESNOTMATCH(
            4003, "Password_DoesNotMatch", Collections.emptySet() ),
    PASSWORD_PREVIOUSLYUSED(
            4004, "Password_PreviouslyUsed", Set.of( ChaiError.PASSWORD_PREVIOUSLYUSED ) ),
    PASSWORD_BADOLDPASSWORD(
            4005, "Password_BadOldPassword", Set.of( ChaiError.PASSWORD_BADOLDPASSWORD ) ),
    PASSWORD_BADPASSWORD(
            4006, "Password_BadPassword", Set.of( ChaiError.PASSWORD_BADPASSWORD ) ),
    PASSWORD_TOO_SHORT(
            4007, "Password_TooShort", Set.of( ChaiError.PASSWORD_TOO_SHORT ) ),
    PASSWORD_TOO_LONG(
            4008, "Password_TooLong", Set.of( ChaiError.PASSWORD_TOO_LONG ) ),
    PASSWORD_NOT_ENOUGH_NUM(
            4009, "Password_NotEnoughNum", Set.of( ChaiError.PASSWORD_NOT_ENOUGH_NUM ) ),
    PASSWORD_NOT_ENOUGH_ALPHA(
            4010, "Password_NotEnoughAlpha", Set.of( ChaiError.PASSWORD_NOT_ENOUGH_ALPHA ) ),
    PASSWORD_NOT_ENOUGH_SPECIAL(
            4011, "Password_NotEnoughSpecial", Set.of( ChaiError.PASSWORD_NOT_ENOUGH_SPECIAL ) ),
    PASSWORD_NOT_ENOUGH_LOWER(
            4012, "Password_NotEnoughLower", Set.of( ChaiError.PASSWORD_NOT_ENOUGH_LOWER ) ),
    PASSWORD_NOT_ENOUGH_UPPER(
            4013, "Password_NotEnoughUpper", Set.of( ChaiError.PASSWORD_NOT_ENOUGH_UPPER ) ),
    PASSWORD_NOT_ENOUGH_UNIQUE(
            4014, "Password_NotEnoughUnique", Set.of( ChaiError.PASSWORD_NOT_ENOUGH_UNIQUE ) ),
    PASSWORD_TOO_MANY_REPEAT(
            4015, "Password_TooManyRepeat", Set.of( ChaiError.PASSWORD_TOO_MANY_REPEAT ) ),
    PASSWORD_TOO_MANY_NUMERIC(
            4016, "Password_TooManyNumeric", Set.of( ChaiError.PASSWORD_TOO_MANY_NUMERIC, ChaiError.PASSWORD_NUMERIC_DISALLOWED ) ),
    PASSWORD_TOO_MANY_ALPHA(
            4017, "Password_TooManyAlpha", Set.of( ChaiError.PASSWORD_TOO_MANY_ALPHA ) ),
    PASSWORD_TOO_MANY_LOWER(
            4018, "Password_TooManyLower", Set.of( ChaiError.PASSWORD_TOO_MANY_LOWER ) ),
    PASSWORD_TOO_MANY_UPPER(
            4019, "Password_TooManyUpper", Set.of( ChaiError.PASSWORD_TOO_MANY_UPPER ) ),
    PASSWORD_FIRST_IS_NUMERIC(
            4020, "Password_FirstIsNumeric", Set.of( ChaiError.PASSWORD_FIRST_IS_NUMERIC ) ),
    PASSWORD_LAST_IS_NUMERIC(
            4021, "Password_LastIsNumeric", Set.of( ChaiError.PASSWORD_LAST_IS_NUMERIC ) ),
    PASSWORD_FIRST_IS_SPECIAL(
            4022, "Password_FirstIsSpecial", Set.of( ChaiError.PASSWORD_FIRST_IS_SPECIAL ) ),
    PASSWORD_LAST_IS_SPECIAL(
            4023, "Password_LastIsSpecial", Set.of( ChaiError.PASSWORD_LAST_IS_SPECIAL ) ),
    PASSWORD_TOO_MANY_SPECIAL(
            4024, "Password_TooManyNonAlphaSpecial", Set.of( ChaiError.PASSWORD_TOO_MANY_SPECIAL, ChaiError.PASSWORD_NUMERIC_DISALLOWED ) ),
    PASSWORD_INVALID_CHAR(
            4025, "Password_InvalidChar", Set.of( ChaiError.PASSWORD_INVALID_CHAR ) ),
    PASSWORD_REQUIREDMISSING(
            4026, "Password_RequiredMissing", Collections.emptySet() ),
    PASSWORD_INWORDLIST(
            4027, "Password_InWordlist", Set.of( ChaiError.PASSWORD_INWORDLIST ) ),
    PASSWORD_SAMEASOLD(
            4028, "Password_SameAsOld", Collections.emptySet() ),
    PASSWORD_SAMEASATTR(
            4029, "Password_SameAsAttr", Set.of( ChaiError.PASSWORD_SAMEASATTR ) ),
    PASSWORD_MEETS_RULES(
            4030, "Password_MeetsRules", Collections.emptySet() ),
    PASSWORD_TOO_MANY_OLD_CHARS(
            4031, "Password_TooManyOldChars", Collections.emptySet() ),
    PASSWORD_HISTORY_FULL(
            4032, "Password_HistoryFull", Set.of( ChaiError.PASSWORD_HISTORY_FULL ) ),
    PASSWORD_TOO_SOON(
            4033, "Password_TooSoon", Set.of( ChaiError.PASSWORD_TOO_SOON ) ),
    PASSWORD_USING_DISALLOWED(
            4034, "Password_UsingDisallowedValue", Collections.emptySet() ),
    PASSWORD_TOO_WEAK(
            4035, "Password_TooWeak", Collections.emptySet() ),
    PASSWORD_TOO_MANY_NONALPHA(
            4036, "Password_TooManyNonAlpha", Collections.emptySet() ),
    PASSWORD_NOT_ENOUGH_NONALPHA(
            4037, "Password_NotEnoughNonAlpha", Collections.emptySet() ),
    PASSWORD_UNKNOWN_VALIDATION(
            4038, "Password_UnknownValidation", Collections.emptySet() ),
    PASSWORD_NEW_PASSWORD_REQUIRED(
            4039, "Password_NewPasswordRequired", Set.of( ChaiError.NEW_PASSWORD_REQUIRED ) ),
    PASSWORD_EXPIRED(
            4040, "Password_Expired", Set.of( ChaiError.PASSWORD_EXPIRED ) ),
    PASSWORD_CUSTOM_ERROR(
            4041, "Password_CustomError", Collections.emptySet() ),
    PASSWORD_NOT_ENOUGH_GROUPS(
            4042, "Password_NotEnoughGroups", Collections.emptySet() ),
    PASSWORD_TOO_MANY_CONSECUTIVE(
            4043, "Password_TooManyConsecutive", Collections.emptySet() ),

    ERROR_WRONGPASSWORD(
            5001, "Error_WrongPassword", Set.of( ChaiError.FAILED_AUTHENTICATION ) ),
    ERROR_INCORRECT_RESPONSE(
            5002, "Error_WrongResponse", Collections.emptySet() ),
    ERROR_USERAUTHENTICATED(
            5003, "Error_UserAuthenticated", Collections.emptySet() ),
    ERROR_AUTHENTICATION_REQUIRED(
            5004, "Error_AuthenticationRequired", Collections.emptySet() ),
    ERROR_RESPONSES_NORESPONSES(
            5006, "Error_Response_NoResponse", Collections.emptySet() ),
    ERROR_RESPONSE_WORDLIST(
            5007, "Error_Response_Wordlist", Collections.emptySet() ),
    ERROR_RESPONSE_TOO_SHORT(
            5008, "Error_Response_TooShort", Collections.emptySet() ),
    ERROR_RESPONSE_TOO_LONG(
            5009, "Error_Response_TooLong", Collections.emptySet() ),
    ERROR_RESPONSE_DUPLICATE(
            5010, "Error_Response_Duplicate", Collections.emptySet() ),
    ERROR_CHALLENGE_DUPLICATE(
            5011, "Error_Challenge_Duplicate", Collections.emptySet() ),
    ERROR_MISSING_CHALLENGE_TEXT(
            5012, "Error_Missing_Challenge_Text", Collections.emptySet() ),
    ERROR_MISSING_PARAMETER(
            5013, "Error_MissingParameter", Collections.emptySet() ),
    ERROR_INTERNAL(
            5015, "Error_Internal", null ),
    ERROR_CANT_MATCH_USER(
            5016, "Error_CantMatchUser", Collections.emptySet() ),
    ERROR_DIRECTORY_UNAVAILABLE(
            5017, "Error_DirectoryUnavailable", null ),
    ERROR_ACTIVATION_VALIDATIONFAIL(
            5018, "Error_ActivationValidationFailed", Collections.emptySet() ),
    ERROR_SERVICE_NOT_AVAILABLE(
            5019, "Error_ServiceNotAvailable", Collections.emptySet() ),
    ERROR_USER_MISMATCH(
            5020, "Error_UserMisMatch", Collections.emptySet() ),
    ERROR_ACTIVATE_NO_PERMISSION(
            5021, "Error_ActivateUserNoQueryMatch", Collections.emptySet() ),
    ERROR_NO_CHALLENGES(
            5022, "Error_NoChallenges", Collections.emptySet() ),
    ERROR_INTRUDER_USER(
            5023, "Error_UserIntruder", null, ErrorFlag.Permanent ),
    ERROR_INTRUDER_ADDRESS(
            5024, "Error_AddressIntruder", null, ErrorFlag.Permanent ),
    ERROR_INTRUDER_SESSION(
            5025, "Error_SessionIntruder", null, ErrorFlag.Permanent ),
    ERROR_BAD_SESSION_PASSWORD(
            5026, "Error_BadSessionPassword", Collections.emptySet() ),
    ERROR_UNAUTHORIZED(
            5027, "Error_Unauthorized", null, ErrorFlag.Permanent ),
    ERROR_BAD_SESSION(
            5028, "Error_BadSession", Collections.emptySet() ),
    ERROR_MISSING_REQUIRED_RESPONSE(
            5029, "Error_MissingRequiredResponse", Collections.emptySet() ),
    ERROR_MISSING_RANDOM_RESPONSE(
            5030, "Error_MissingRandomResponse", Collections.emptySet() ),
    ERROR_BAD_CAPTCHA_RESPONSE(
            5031, "Error_BadCaptchaResponse", Collections.emptySet() ),
    ERROR_CAPTCHA_API_ERROR(
            5032, "Error_CaptchaAPIError", Collections.emptySet() ),
    ERROR_INVALID_CONFIG(
            5033, "Error_InvalidConfig", Collections.emptySet() ),
    ERROR_INVALID_FORMID(
            5034, "Error_InvalidFormID", Collections.emptySet(), ErrorFlag.Trivial ),
    ERROR_INCORRECT_REQ_SEQUENCE(
            5035, "Error_IncorrectRequestSequence", Collections.emptySet() ),
    ERROR_TOKEN_MISSING_CONTACT(
            5036, "Error_TokenMissingContact", Collections.emptySet() ),
    ERROR_TOKEN_INCORRECT(
            5037, "Error_TokenIncorrect", Collections.emptySet() ),
    ERROR_BAD_CURRENT_PASSWORD(
            5038, "Error_BadCurrentPassword", Collections.emptySet() ),
    ERROR_CLOSING(
            5039, "Error_Closing", Collections.emptySet(), ErrorFlag.AuditIgnored ),
    ERROR_MISSING_GUID(
            5040, "Error_Missing_GUID", Collections.emptySet() ),
    ERROR_TOKEN_EXPIRED(
            5041, "Error_TokenExpired", Collections.emptySet() ),
    ERROR_MULTI_USERNAME(
            5042, "Error_Multi_Username", Collections.emptySet() ),
    ERROR_ORIG_ADMIN_ONLY(
            5043, "Error_Orig_Admin_Only", Collections.emptySet() ),
    ERROR_SECURE_REQUEST_REQUIRED(
            5044, "Error_SecureRequestRequired", Collections.emptySet() ),
    ERROR_WRITING_RESPONSES(
            5045, "Error_Writing_Responses", Collections.emptySet() ),
    ERROR_UNLOCK_FAILURE(
            5046, "Error_Unlock_Failure", Collections.emptySet() ),
    ERROR_UPDATE_ATTRS_FAILURE(
            5047, "Error_Update_Attrs_Failure", Collections.emptySet() ),
    ERROR_ACTIVATION_FAILURE(
            5048, "Error_Activation_Failure", Collections.emptySet() ),
    ERROR_NEW_USER_FAILURE(
            5049, "Error_NewUser_Failure", Collections.emptySet() ),
    ERROR_ACTIVATION(
            5050, "Error_Activation", Collections.emptySet() ),
    ERROR_DB_UNAVAILABLE(
            5051, "Error_DB_Unavailable", Collections.emptySet() ),
    ERROR_LOCALDB_UNAVAILABLE(
            5052, "Error_LocalDB_Unavailable", Collections.emptySet() ),
    ERROR_APP_UNAVAILABLE(
            5053, "Error_App_Unavailable", Collections.emptySet() ),
    ERROR_UNREACHABLE_CLOUD_SERVICE(
            5054, "Error_UnreachableCloudService", Collections.emptySet() ),
    ERROR_INVALID_SECURITY_KEY(
            5055, "Error_InvalidSecurityKey", Collections.emptySet() ),
    ERROR_CLEARING_RESPONSES(
            5056, "Error_Clearing_Responses", Collections.emptySet() ),
    ERROR_SERVICE_UNREACHABLE(
            5057, "Error_ServiceUnreachable", Collections.emptySet() ),
    ERROR_CHALLENGE_IN_RESPONSE(
            5058, "Error_ChallengeInResponse", Collections.emptySet() ),
    ERROR_CERTIFICATE_ERROR(
            5059, "Error_CertificateError", Collections.emptySet() ),
    ERROR_SYSLOG_WRITE_ERROR(
            5060, "Error_SyslogWriteError", Collections.emptySet() ),
    ERROR_TOO_MANY_THREADS(
            5061, "Error_TooManyThreads", Collections.emptySet() ),
    ERROR_PASSWORD_REQUIRED(
            5062, "Error_PasswordRequired", Collections.emptySet() ),
    ERROR_SECURITY_VIOLATION(
            5063, "Error_SecurityViolation", Collections.emptySet() ),
    ERROR_TRIAL_VIOLATION(
            5064, "Error_TrialViolation", Collections.emptySet() ),
    ERROR_ACCOUNT_DISABLED(
            5065, "Error_AccountDisabled", Set.of( ChaiError.ACCOUNT_DISABLED ) ),
    ERROR_ACCOUNT_EXPIRED(
            5066, "Error_AccountExpired", Set.of( ChaiError.ACCOUNT_EXPIRED ) ),
    ERROR_NO_OTP_CONFIGURATION(
            5087, "Error_NoOtpConfiguration", Collections.emptySet() ),
    ERROR_INCORRECT_OTP_TOKEN(
            5088, "Error_WrongOtpToken", Collections.emptySet() ),
    ERROR_WRITING_OTP_SECRET(
            5086, "Error_Writing_Otp_Secret", Collections.emptySet() ),
    ERROR_INTRUDER_ATTR_SEARCH(
            5067, "Error_AttrIntruder", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_AUDIT_WRITE(
            5068, "Error_AuditWrite", Collections.emptySet() ),
    ERROR_INTRUDER_LDAP(
            5069, "Error_LdapIntruder", Set.of( ChaiError.INTRUDER_LOCKOUT ), ErrorFlag.Permanent ),
    ERROR_NO_LDAP_CONNECTION(
            5070, "Error_NoLdapConnection", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_OAUTH_ERROR(
            5071, "Error_OAuthError", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_REPORTING_ERROR(
            5072, "Error_ReportingError", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_INTRUDER_TOKEN_DEST(
            5073, "Error_TokenDestIntruder", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_OTP_RECOVERY_USED(
            5074, "Error_OtpRecoveryUsed", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_REDIRECT_ILLEGAL(
            5075, "Error_RedirectIllegal", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_CRYPT_ERROR(
            5076, "Error_CryptError", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_SMS_SEND_ERROR(
            5078, "Error_SmsSendError", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_LDAP_DATA_ERROR(
            5079, "Error_LdapDataError", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_MACRO_PARSE_ERROR(
            5080, "Error_MacroParseError", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_NO_PROFILE_ASSIGNED(
            5081, "Error_NoProfileAssigned", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_STARTUP_ERROR(
            5082, "Error_StartupError", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_ENVIRONMENT_ERROR(
            5083, "Error_EnvironmentError", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_APPLICATION_NOT_RUNNING(
            5084, "Error_ApplicationNotRunning", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_EMAIL_SEND_FAILURE(
            5085, "Error_EmailSendFailure", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_PASSWORD_ONLY_BAD(
            5089, "Error_PasswordOnlyBad", Collections.emptySet() ),
    ERROR_RECOVERY_SEQUENCE_INCOMPLETE(
            5090, "Error_RecoverySequenceIncomplete", Collections.emptySet() ),
    ERROR_FILE_TYPE_INCORRECT(
            5091, "Error_FileTypeIncorrect", Collections.emptySet() ),
    ERROR_FILE_TOO_LARGE(
            5092, "Error_FileTooLarge", Collections.emptySet() ),
    ERROR_NODE_SERVICE_ERROR(
            5093, "Error_NodeServiceError", Collections.emptySet() ),
    ERROR_WORDLIST_IMPORT_ERROR(
            5094, "Error_WordlistImportError", Collections.emptySet() ),
    ERROR_PWNOTIFY_SERVICE_ERROR(
            5095, "Error_PwNotifyServiceError", Collections.emptySet() ),
    ERROR_TIMEOUT(
            5096, "Error_Timeout", Collections.emptySet() ),

    ERROR_REMOTE_ERROR_VALUE(
            6000, "Error_RemoteErrorValue", Collections.emptySet(), ErrorFlag.Permanent ),
    ERROR_TELEMETRY_SEND_ERROR(
            6001, "Error_TelemetrySendError", Collections.emptySet() ),
    ERROR_HTTP_CLIENT(
            6002, "Error_HttpError", Collections.emptySet() ),

    ERROR_FIELD_REQUIRED(
            5100, "Error_FieldRequired", Collections.emptySet() ),
    ERROR_FIELD_NOT_A_NUMBER(
            5101, "Error_FieldNotANumber", Collections.emptySet() ),
    ERROR_FIELD_INVALID_EMAIL(
            5102, "Error_FieldInvalidEmail", Collections.emptySet() ),
    ERROR_FIELD_TOO_SHORT(
            5103, "Error_FieldTooShort", Collections.emptySet() ),
    ERROR_FIELD_TOO_LONG(
            5104, "Error_FieldTooLong", Collections.emptySet() ),
    ERROR_FIELD_DUPLICATE(
            5105, "Error_FieldDuplicate", Collections.emptySet() ),
    ERROR_FIELD_BAD_CONFIRM(
            5106, "Error_FieldBadConfirm", Collections.emptySet() ),
    ERROR_FIELD_REGEX_NOMATCH(
            5107, "Error_FieldRegexNoMatch", Collections.emptySet() ),

    CONFIG_UPLOAD_SUCCESS(
            5200, "Error_ConfigUploadSuccess", Collections.emptySet() ),
    CONFIG_UPLOAD_FAILURE(
            5201, "Error_ConfigUploadFailure", Collections.emptySet() ),
    CONFIG_SAVE_SUCCESS(
            5202, "Error_ConfigSaveSuccess", Collections.emptySet() ),
    CONFIG_FORMAT_ERROR(
            5203, "Error_ConfigFormatError", Collections.emptySet() ),
    CONFIG_LDAP_FAILURE(
            5204, "Error_ConfigLdapFailure", Collections.emptySet() ),
    CONFIG_LDAP_SUCCESS(
            5205, "Error_ConfigLdapSuccess", Collections.emptySet() ),

    ERROR_HTTP_404(
            5300, "Error_HTTP_404", Collections.emptySet() ),

    ERROR_REST_INVOCATION_ERROR(
            7000, "Error_RestInvocationError", Collections.emptySet() ),
    ERROR_REST_PARAMETER_CONFLICT(
            7001, "Error_RestParameterConflict", Collections.emptySet() ),

    /* End of list*/;

    private enum ErrorFlag
    {
        Permanent,
        Trivial,
        AuditIgnored,
    }

    private static final Set<PwmError> AUDIT_IGNORED_ERRORS;

    static
    {
        final Set<PwmError> set = EnumSet.noneOf( PwmError.class );
        set.addAll( Arrays.stream( values() )
                .filter( PwmError::isAuditIgnored )
                .collect( Collectors.toSet() ) );
        AUDIT_IGNORED_ERRORS = Collections.unmodifiableSet( set );
    }

    private final int errorCode;
    private final String resourceKey;
    private final Set<ChaiError> chaiErrorCode;
    private final boolean errorIsPermanent;
    private final boolean trivial;
    private final boolean auditIgnored;

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
        this.trivial = JavaHelper.enumArrayContainsValue( errorFlags, ErrorFlag.Trivial );
        this.auditIgnored = JavaHelper.enumArrayContainsValue( errorFlags, ErrorFlag.AuditIgnored );
        this.chaiErrorCode = chaiErrorCode == null ? Collections.emptySet() : Set.copyOf( chaiErrorCode );
    }

    public String getLocalizedMessage( final Locale locale, final SettingReader config, final String... fieldValue )
    {
        return LocaleHelper.getLocalizedMessage( locale, this.getResourceKey(), config, password.pwm.i18n.Error.class, fieldValue );
    }

    public static Optional<PwmError> forChaiError( final ChaiError errorCode )
    {
        return JavaHelper.readEnumFromPredicate( PwmError.class,
                pwmError -> pwmError.chaiErrorCode.contains( errorCode ) );
    }

    public static Optional<PwmError> forErrorNumber( final int code )
    {
        return JavaHelper.readEnumFromPredicate( PwmError.class, pwmError -> pwmError.getErrorCode() == code );
    }

    public boolean isTrivial()
    {
        return trivial;
    }

    public boolean isErrorIsPermanent( )
    {
        return errorIsPermanent;
    }

    public boolean isAuditIgnored()
    {
        return auditIgnored;
    }

    public static Set<PwmError> auditIgnoredErrors()
    {
        return AUDIT_IGNORED_ERRORS;
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



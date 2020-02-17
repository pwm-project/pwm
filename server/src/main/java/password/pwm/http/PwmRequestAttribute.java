/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.http;

public enum PwmRequestAttribute
{
    PwmErrorInfo,
    SuccessMessage,
    PwmRequest,
    OriginalUri,
    AgreementText,
    CompleteText,
    AvailableAuthMethods,
    ConfigurationSummaryOutput,
    LdapPermissionItems,
    PageTitle,
    ModuleBean,
    ModuleBean_String,
    CspNonce,

    FormConfiguration,
    FormInitialValues,
    FormReadOnly,
    FormShowPasswordFields,
    FormData,
    FormMobileDevices,
    FormCustomLinks,

    AccountInfo,

    SetupResponses_ResponseInfo,
    SetupResponses_AllowSkip,

    SetupOtp_QrCodeValue,
    SetupOtp_AllowSkip,
    SetupOtp_UserRecord,

    HelpdeskDetail,
    HelpdeskObfuscatedDN,
    HelpdeskVerificationEnabled,

    ConfigFilename,
    ConfigLastModified,
    ConfigHasPassword,
    ConfigPasswordRememberTime,
    ConfigLoginHistory,
    ConfigEnablePersistentLogin,
    ApplicationPath,

    ConfigHasCertificates,

    CaptchaClientUrl,
    CaptchaIframeUrl,
    CaptchaPublicKey,

    ChangePassword_MaxWaitSeconds,
    ChangePassword_CheckIntervalSeconds,
    ChangePassword_PasswordPolicyChangeMessage,

    ForgottenPasswordChallengeSet,
    ForgottenPasswordOptionalPageView,
    ForgottenPasswordPrompts,
    ForgottenPasswordInstructions,
    ForgottenPasswordOtpRecord,
    ForgottenPasswordResendTokenEnabled,
    ForgottenPasswordInhibitPasswordReset,

    GuestCurrentExpirationDate,
    GuestMaximumExpirationDate,
    GuestMaximumValidDays,

    NewUser_FormShowBackButton,
    NewUser_VisibleProfiles,

    CookieBeanStorage,
    CookieNonce,

    ShortcutItems,
    NextUrl,

    UserDebugData,
    AppDashboardData,

    TokenDestItems,

    GoBackAction,;
}

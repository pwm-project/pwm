/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http;

public enum PwmRequestAttribute {
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

    SetupOtp_QrCodeValue,

    HelpdeskDetail,
    HelpdeskObfuscatedDN,
    HelpdeskUsername,
    HelpdeskVerificationEnabled,

    ConfigFilename,
    ConfigLastModified,
    ConfigHasPassword,
    ConfigPasswordRememberTime,
    ConfigLoginHistory,
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
    ForgottenPasswordTokenDestItems,

    GuestCurrentExpirationDate,
    GuestMaximumExpirationDate,
    GuestMaximumValidDays,

    NewUser_FormShowBackButton,
    NewUser_VisibleProfiles,

    CookieBeanStorage,

    ShortcutItems,
    NextUrl,

    UserDebugData,
    AppDashboardData,
}

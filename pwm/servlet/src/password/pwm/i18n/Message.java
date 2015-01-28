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

package password.pwm.i18n;

import password.pwm.config.Configuration;

import java.util.Locale;


/**
 * Utility class for managing messages returned by the servlet for inclusion in UI screens.
 * This class contains a set of constants that match a corresponding properties file which
 * follows ResourceBundle rules for structure and internationalization.
 *
 * @author Jason D. Rivard
 */
public enum Message implements PwmDisplayBundle {
    Success_PasswordChange(),
    Success_SetupResponse(),
    Success_ClearResponse(),
    Success_Unknown(),
    Success_CreateUser(),
    Success_NewUserForm(),
    Success_UpdateForm(),
    Success_CreateGuest(),
    Success_UpdateGuest(),
    Success_ActivateUser(),
    Success_UpdateProfile(),
    Success_ResponsesMeetRules(),
    Success_UnlockAccount(),
    Success_ForgottenUsername(),
    Success_ConfigFileUpload(),
    Success_PasswordReset(),
    Success_PasswordSend(),
    Success_Action(),
    Success_OtpSetup(),

    EventLog_Startup(),
    EventLog_Shutdown(),
    EventLog_FatalEvent(),
    EventLog_ModifyConfiguration(),
    EventLog_IntruderAttempt(),
    EventLog_IntruderLockout(),

    EventLog_Authenticate(),
    EventLog_AgreementPassed(),
    EventLog_ChangePassword(),
    EventLog_UnlockPassword(),
    EventLog_RecoverPassword(),
    EventLog_SetupResponses(),
    Eventlog_SetupOtpSecret(),
    EventLog_ActivateUser(),
    EventLog_CreateUser(),
    EventLog_UpdateProfile(),
    EventLog_IntruderUser(),
    EventLog_TokenIssued(),
    EventLog_TokenClaimed(),
    EventLog_ClearResponses(),
    EventLog_HelpdeskSetPassword(),
    EventLog_HelpdeskUnlockPassword(),
    EventLog_HelpdeskClearResponses(),
    EventLog_HelpdeskClearOtpSecret(),
    EventLog_HelpdeskAction(),
    EventLog_HelpdeskDeleteUser(),
    EventLog_HelpdeskViewDetail(),
    EventLog_HelpdeskVerifyOtp(),

    Requirement_MinLength(),
    Requirement_MinLengthPlural(),
    Requirement_MaxLength(),
    Requirement_MaxLengthPlural(),
    Requirement_MinAlpha(),
    Requirement_MinAlphaPlural(),
    Requirement_MaxAlpha(),
    Requirement_MaxAlphaPlural(),
    Requirement_AllowNumeric(),
    Requirement_MinNumeric(),
    Requirement_MinNumericPlural(),
    Requirement_MaxNumeric(),
    Requirement_MaxNumericPlural(),
    Requirement_FirstNumeric(),
    Requirement_LastNumeric(),
    Requirement_AllowSpecial(),
    Requirement_MinSpecial(),
    Requirement_MinSpecialPlural(),
    Requirement_MaxSpecial(),
    Requirement_MaxSpecialPlural(),
    Requirement_FirstSpecial(),
    Requirement_LastSpecial(),
    Requirement_MaxRepeat(),
    Requirement_MaxRepeatPlural(),
    Requirement_MaxSeqRepeat(),
    Requirement_MaxSeqRepeatPlural(),
    Requirement_MinLower(),
    Requirement_MinLowerPlural(),
    Requirement_MaxLower(),
    Requirement_MaxLowerPlural(),
    Requirement_MinUpper(),
    Requirement_MinUpperPlural(),
    Requirement_MaxUpper(),
    Requirement_MaxUpperPlural(),
    Requirement_MinUnique(),
    Requirement_MinUniquePlural(),
    Requirement_RequiredChars(),
    Requirement_DisAllowedValues(),
    Requirement_DisAllowedAttributes(),
    Requirement_WordList(),
    Requirement_OldChar(),
    Requirement_OldCharPlural(),
    Requirement_CaseSensitive(),
    Requirement_NotCaseSensitive(),
    Requirement_MinimumFrequency(),
    Requirement_ADComplexity(),
    Requirement_ADComplexity2008(),
    Requirement_UniqueRequired(),

    ;

    public static String getLocalizedMessage(final Locale locale, final Message message, final Configuration config, final String... fieldValue) {
        return LocaleHelper.getLocalizedMessage(locale, message.getKey(),config , Message.class, fieldValue);
    }

    Message() {
    }


    public String getLocalizedMessage(final Locale locale, final Configuration config, final String... fieldValue) {
        return Message.getLocalizedMessage(locale, this, config, fieldValue);
    }

    @Override
    public String getKey() {
        return this.toString();

    }
}

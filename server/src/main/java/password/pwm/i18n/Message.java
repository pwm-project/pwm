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

package password.pwm.i18n;

import password.pwm.config.Configuration;
import password.pwm.util.i18n.LocaleHelper;

import java.util.Locale;


/**
 * Utility class for managing messages returned by the servlet for inclusion in UI screens.
 * This class contains a set of constants that match a corresponding properties file which
 * follows ResourceBundle rules for structure and internationalization.
 *
 * @author Jason D. Rivard
 */
public enum Message implements PwmDisplayBundle
{
    Success_PasswordChange( null ),
    Success_ChangedHelpdeskPassword( null ),
    Success_SetupResponse( null ),
    Success_ClearResponse( null ),
    Success_Unknown( null ),
    Success_CreateUser( null ),
    Success_NewUserForm( null ),
    Success_UpdateForm( null ),
    Success_CreateGuest( null ),
    Success_UpdateGuest( null ),
    Success_ActivateUser( null ),
    Success_UpdateProfile( null ),
    Success_ResponsesMeetRules( null ),
    Success_UnlockAccount( null ),
    Success_ConfigFileUpload( null ),
    Success_PasswordReset( null ),
    Success_PasswordSend( null ),
    Success_Action( null ),
    Success_OtpSetup( null ),
    Success_TokenResend( null ),

    EventLog_Startup( null ),
    EventLog_Shutdown( null ),
    EventLog_FatalEvent( null ),
    EventLog_ModifyConfiguration( null ),
    EventLog_IntruderAttempt( null ),
    EventLog_IntruderLockout( null ),

    EventLog_Authenticate( null ),
    EventLog_AgreementPassed( null ),
    EventLog_ChangePassword( null ),
    EventLog_UnlockPassword( null ),
    EventLog_RecoverPassword( null ),
    EventLog_SetupResponses( null ),
    Eventlog_SetupOtpSecret( null ),
    EventLog_ActivateUser( null ),
    EventLog_CreateUser( null ),
    EventLog_UpdateProfile( null ),
    EventLog_DeleteAccount( null ),
    EventLog_IntruderUserLock( null ),
    EventLog_IntruderUserAttempt( null ),
    EventLog_TokenIssued( null ),
    EventLog_TokenClaimed( null ),
    EventLog_ClearResponses( null ),
    EventLog_HelpdeskSetPassword( null ),
    EventLog_HelpdeskUnlockPassword( null ),
    EventLog_HelpdeskClearResponses( null ),
    EventLog_HelpdeskClearOtpSecret( null ),
    EventLog_HelpdeskAction( null ),
    EventLog_HelpdeskDeleteUser( null ),
    EventLog_HelpdeskViewDetail( null ),
    EventLog_HelpdeskVerifyOtp( null ),
    EventLog_HelpdeskVerifyOtpIncorrect( null ),
    EventLog_HelpdeskVerifyToken( null ),
    EventLog_HelpdeskVerifyTokenIncorrect( null ),
    EventLog_HelpdeskVerifyAttributes( null ),
    EventLog_HelpdeskVerifyAttributesIncorrect( null ),

    Requirement_MinLengthPlural( null ),
    Requirement_MinLength( Requirement_MinLengthPlural ),

    Requirement_MaxLengthPlural( null ),
    Requirement_MaxLength( Requirement_MaxLengthPlural ),

    Requirement_MinAlphaPlural( null ),
    Requirement_MinAlpha( Requirement_MinAlphaPlural ),

    Requirement_MaxAlphaPlural( null ),
    Requirement_MaxAlpha( Requirement_MaxAlphaPlural ),

    Requirement_AllowNumeric( null ),

    Requirement_MinNumericPlural( null ),
    Requirement_MinNumeric( Requirement_MinNumericPlural ),

    Requirement_MaxNumericPlural( null ),
    Requirement_MaxNumeric( Requirement_MaxNumericPlural ),

    Requirement_FirstNumeric( null ),
    Requirement_LastNumeric( null ),
    Requirement_AllowSpecial( null ),

    Requirement_MinSpecialPlural( null ),
    Requirement_MinSpecial( Requirement_MinSpecialPlural ),

    Requirement_MaxSpecialPlural( null ),
    Requirement_MaxSpecial( Requirement_MaxSpecialPlural ),

    Requirement_LastSpecial( null ),
    Requirement_FirstSpecial( null ),

    Requirement_MaxRepeatPlural( null ),
    Requirement_MaxRepeat( Requirement_MaxRepeatPlural ),

    Requirement_MaxSeqRepeatPlural( null ),
    Requirement_MaxSeqRepeat( Requirement_MaxSeqRepeatPlural ),

    Requirement_MinLowerPlural( null ),
    Requirement_MinLower( Requirement_MinLowerPlural ),

    Requirement_MaxLowerPlural( null ),
    Requirement_MaxLower( Requirement_MaxLowerPlural ),

    Requirement_MinUpperPlural( null ),
    Requirement_MinUpper( Requirement_MinUpperPlural ),

    Requirement_MaxUpperPlural( null ),
    Requirement_MaxUpper( Requirement_MaxUpperPlural ),

    Requirement_MinUniquePlural( null ),
    Requirement_MinUnique( Requirement_MinUniquePlural ),

    Requirement_RequiredChars( null ),
    Requirement_DisAllowedValues( null ),
    Requirement_DisAllowedAttributes( null ),
    Requirement_WordList( null ),

    Requirement_OldCharPlural( null ),
    Requirement_OldChar( Requirement_OldCharPlural ),

    Requirement_CaseSensitive( null ),
    Requirement_NotCaseSensitive( null ),
    Requirement_MinimumFrequency( null ),
    Requirement_ADComplexity( null ),
    Requirement_ADComplexity2008( null ),
    Requirement_UniqueRequired( null ),;

    private final Message pluralMessage;

    public static String getLocalizedMessage( final Locale locale, final Message message, final Configuration config, final String... fieldValue )
    {
        return LocaleHelper.getLocalizedMessage( locale, message.getKey(), config, Message.class, fieldValue );
    }

    Message( final Message pluralMessage )
    {
        this.pluralMessage = pluralMessage;
    }

    public Message getPluralMessage( )
    {
        return pluralMessage;
    }

    public String getLocalizedMessage( final Locale locale, final Configuration config, final String... fieldValue )
    {
        return Message.getLocalizedMessage( locale, this, config, fieldValue );
    }

    @Override
    public String getKey( )
    {
        return this.toString();

    }
}

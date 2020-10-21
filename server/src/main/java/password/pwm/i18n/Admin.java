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

public enum Admin implements PwmDisplayBundle
{


    Notice_TrialRestrictConfig,

    EventLog_Narrative_Startup,
    EventLog_Narrative_Shutdown,
    EventLog_Narrative_FatalEvent,
    EventLog_Narrative_ModifyConfiguration,
    EventLog_Narrative_IntruderAttempt,
    EventLog_Narrative_IntruderLockout,

    EventLog_Narrative_Authenticate,
    EventLog_Narrative_AgreementPassed,
    EventLog_Narrative_ChangePassword,
    EventLog_Narrative_UnlockPassword,
    EventLog_Narrative_RecoverPassword,
    EventLog_Narrative_SetupResponses,
    Eventlog_Narrative_SetupOtpSecret,
    EventLog_Narrative_ActivateUser,
    EventLog_Narrative_CreateUser,
    EventLog_Narrative_UpdateProfile,
    EventLog_Narrative_DeleteAccount,
    EventLog_Narrative_IntruderUserLock,
    EventLog_Narrative_IntruderUserAttempt,
    EventLog_Narrative_TokenIssued,
    EventLog_Narrative_TokenClaimed,
    EventLog_Narrative_ClearResponses,
    EventLog_Narrative_HelpdeskSetPassword,
    EventLog_Narrative_HelpdeskUnlockPassword,
    EventLog_Narrative_HelpdeskClearResponses,
    EventLog_Narrative_HelpdeskClearOtpSecret,
    EventLog_Narrative_HelpdeskAction,
    EventLog_Narrative_HelpdeskDeleteUser,
    EventLog_Narrative_HelpdeskViewDetail,
    EventLog_Narrative_HelpdeskVerifyOtp,
    EventLog_Narrative_HelpdeskVerifyOtpIncorrect,
    EventLog_Narrative_HelpdeskVerifyToken,
    EventLog_Narrative_HelpdeskVerifyTokenIncorrect,
    EventLog_Narrative_HelpdeskVerifyAttributes,
    EventLog_Narrative_HelpdeskVerifyAttributesIncorrect,

    Field_Session_UserID,
    Field_Session_LdapProfile,
    Field_Session_UserDN,
    Field_Session_CreateTime,
    Field_Session_LastTime,
    Field_Session_Label,
    Field_Session_Idle,
    Field_Session_SrcAddress,
    Field_Session_Locale,
    Field_Session_SrcHost,
    Field_Session_LastURL,
    Field_Session_IntruderAttempts,;


    public static final String STATISTICS_LABEL_PREFIX = "Statistic_Label.";
    public static final String STATISTICS_DESCRIPTION_PREFIX = "Statistic_Description.";
    public static final String EPS_STATISTICS_LABEL_PREFIX = "EpsStatistic_Label.";

    @Override
    public String getKey( )
    {
        return this.toString();
    }
}

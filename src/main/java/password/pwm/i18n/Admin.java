/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

public enum Admin implements PwmDisplayBundle {


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

    ;

    @Override
    public String getKey() {
        return this.toString();
    }
}

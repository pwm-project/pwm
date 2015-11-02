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

package password.pwm.svc.event;

import password.pwm.config.Configuration;
import password.pwm.i18n.Message;

import java.util.Locale;

public enum AuditEvent {

    // system events
    STARTUP(Message.EventLog_Startup, Type.SYSTEM),
    SHUTDOWN(Message.EventLog_Shutdown, Type.SYSTEM),
    FATAL_EVENT(Message.EventLog_FatalEvent, Type.SYSTEM),
    MODIFY_CONFIGURATION(Message.EventLog_ModifyConfiguration, Type.SYSTEM),
    INTRUDER_LOCK(Message.EventLog_IntruderLockout, Type.SYSTEM),
    INTRUDER_ATTEMPT(Message.EventLog_IntruderAttempt, Type.SYSTEM),

    // user events not stored in user event history
    AUTHENTICATE(Message.EventLog_Authenticate, Type.USER),
    AGREEMENT_PASSED(Message.EventLog_AgreementPassed, Type.USER),
    TOKEN_ISSUED(Message.EventLog_TokenIssued, Type.USER),
    TOKEN_CLAIMED(Message.EventLog_TokenClaimed, Type.USER),
    CLEAR_RESPONSES(Message.EventLog_ClearResponses, Type.USER),

    // user events stored in user event history
    CHANGE_PASSWORD(Message.EventLog_ChangePassword, Type.USER),
    UNLOCK_PASSWORD(Message.EventLog_UnlockPassword, Type.USER),
    RECOVER_PASSWORD(Message.EventLog_RecoverPassword, Type.USER),
    SET_RESPONSES(Message.EventLog_SetupResponses, Type.USER),
    SET_OTP_SECRET(Message.Eventlog_SetupOtpSecret, Type.USER),
    ACTIVATE_USER(Message.EventLog_ActivateUser, Type.USER),
    CREATE_USER(Message.EventLog_CreateUser, Type.USER),
    UPDATE_PROFILE(Message.EventLog_UpdateProfile, Type.USER),
    INTRUDER_USER(Message.EventLog_IntruderUser, Type.USER),

    // helpdesk events
    HELPDESK_SET_PASSWORD(Message.EventLog_HelpdeskSetPassword, Type.HELPDESK),
    HELPDESK_UNLOCK_PASSWORD(Message.EventLog_HelpdeskUnlockPassword, Type.HELPDESK),
    HELPDESK_CLEAR_RESPONSES(Message.EventLog_HelpdeskClearResponses, Type.HELPDESK),
    HELPDESK_CLEAR_OTP_SECRET(Message.EventLog_HelpdeskClearOtpSecret, Type.HELPDESK),
    HELPDESK_ACTION(Message.EventLog_HelpdeskAction, Type.HELPDESK),
    HELPDESK_DELETE_USER(Message.EventLog_HelpdeskDeleteUser, Type.HELPDESK),
    HELPDESK_VIEW_DETAIL(Message.EventLog_HelpdeskViewDetail, Type.HELPDESK),
    HELPDESK_VERIFY_OTP(Message.EventLog_HelpdeskViewDetail, Type.HELPDESK),


    ;

    final private Message message;
    private Type type;

    AuditEvent(final Message message, final Type type) {
        this.message = message;
        this.type = type;
    }

    public Message getMessage() {
        return message;
    }

    public static AuditEvent forKey(final String key) {
        for (final AuditEvent loopEvent : AuditEvent.values()) {
            final Message message = loopEvent.getMessage();
            if (message != null) {
                final String resourceKey = message.getKey();
                if (resourceKey.equals(key)) {
                    return loopEvent;
                }
            }
        }

        return null;
    }

    public String getLocalizedString(final Configuration config, final Locale locale) {
        if (this.getMessage() == null) {
            return "[unknown event]";
        }
        return Message.getLocalizedMessage(locale,this.getMessage(),config);
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        USER,
        SYSTEM,
        HELPDESK,
    }
}

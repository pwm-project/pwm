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

package password.pwm.event;

import password.pwm.config.Configuration;
import password.pwm.i18n.Message;

import java.util.Locale;

public enum AuditEvent {

    // system events
    STARTUP(Message.EventLog_Startup, Type.SYSTEM, false),
    SHUTDOWN(Message.EventLog_Shutdown, Type.SYSTEM, false),
    FATAL_EVENT(Message.EventLog_FatalEvent, Type.SYSTEM, false),
    MODIFY_CONFIGURATION(Message.EventLog_ModifyConfiguration, Type.SYSTEM, false),
    INTRUDER_LOCK(Message.EventLog_IntruderLockout, Type.SYSTEM, true),
    INTRUDER_ATTEMPT(Message.EventLog_IntruderAttempt, Type.SYSTEM, false),

    // user events not stored in user event history
    AUTHENTICATE(Message.EventLog_Authenticate, Type.USER, false),
    AGREEMENT_PASSED(Message.EventLog_AgreementPassed, Type.USER, false),
    TOKEN_ISSUED(Message.EventLog_TokenIssued, Type.USER, false),
    TOKEN_CLAIMED(Message.EventLog_TokenClaimed, Type.USER, false),
    CLEAR_RESPONSES(Message.EventLog_ClearResponses, Type.USER, false),

    // user events stored in user event history
    CHANGE_PASSWORD(Message.EventLog_ChangePassword, Type.USER, true),
    UNLOCK_PASSWORD(Message.EventLog_UnlockPassword, Type.USER, true),
    RECOVER_PASSWORD(Message.EventLog_RecoverPassword, Type.USER, true),
    SET_RESPONSES(Message.EventLog_SetupResponses, Type.USER, true),
    SET_OTP_SECRET(Message.Eventlog_SetupOtpSecret, Type.USER, true),
    ACTIVATE_USER(Message.EventLog_ActivateUser, Type.USER, true),
    CREATE_USER(Message.EventLog_CreateUser, Type.USER, true),
    UPDATE_PROFILE(Message.EventLog_UpdateProfile, Type.USER, true),
    INTRUDER_USER(Message.EventLog_IntruderUser, Type.USER, true),

    // helpdesk events
    HELPDESK_SET_PASSWORD(Message.EventLog_HelpdeskSetPassword, Type.HELPDESK, true),
    HELPDESK_UNLOCK_PASSWORD(Message.EventLog_HelpdeskUnlockPassword, Type.HELPDESK, true),
    HELPDESK_CLEAR_RESPONSES(Message.EventLog_HelpdeskClearResponses, Type.HELPDESK, true),
    HELPDESK_CLEAR_OTP_SECRET(Message.EventLog_HelpdeskClearOtpSecret, Type.HELPDESK, true),
    HELPDESK_ACTION(Message.EventLog_HelpdeskAction, Type.HELPDESK, true),
    HELPDESK_DELETE_USER(Message.EventLog_HelpdeskDeleteUser, Type.HELPDESK, false),
    HELPDESK_VIEW_DETAIL(Message.EventLog_HelpdeskViewDetail, Type.HELPDESK, false),
    HELPDESK_VERIFY_OTP(Message.EventLog_HelpdeskViewDetail, Type.HELPDESK, false),


    ;

    final private Message message;
    final private boolean storeOnUser;
    private Type type;

    AuditEvent(final Message message, final Type type, boolean storeOnUser) {
        this.message = message;
        this.storeOnUser = storeOnUser;
        this.type = type;
    }

    public Message getMessage() {
        return message;
    }

    public boolean isStoreOnUser() {
        return storeOnUser;
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

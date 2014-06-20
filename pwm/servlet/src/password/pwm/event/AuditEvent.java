/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
    STARTUP(Message.EVENT_LOG_STARTUP, Type.SYSTEM, false),
    SHUTDOWN(Message.EVENT_LOG_SHUTDOWN, Type.SYSTEM, false),
    FATAL_EVENT(Message.EVENT_LOG_FATAL_EVENT, Type.SYSTEM, false),
    MODIFY_CONFIGURATION(Message.EVENT_LOG_MODIFY_CONFIGURATION, Type.SYSTEM, false),
    INTRUDER_LOCK(Message.EVENT_LOG_INTRUDER_LOCKOUT, Type.SYSTEM, true),
    INTRUDER_ATTEMPT(Message.EVENT_LOG_INTRUDER_ATTEMPT, Type.SYSTEM, false),

    // user events
    AUTHENTICATE(Message.EVENT_LOG_AUTHENTICATE, Type.USER, false),
    CHANGE_PASSWORD(Message.EVENT_LOG_CHANGE_PASSWORD, Type.USER, true),
    RECOVER_PASSWORD(Message.EVENT_LOG_RECOVER_PASSWORD, Type.USER, true),
    SET_RESPONSES(Message.EVENT_LOG_SETUP_RESPONSES, Type.USER, true),
    SET_OTP_SECRET(Message.EVENT_LOG_SETUP_OTP_SECRET, Type.USER, true),
    ACTIVATE_USER(Message.EVENT_LOG_ACTIVATE_USER, Type.USER, true),
    CREATE_USER(Message.EVENT_LOG_CREATE_USER, Type.USER, true),
    UPDATE_PROFILE(Message.EVENT_LOG_UPDATE_PROFILE, Type.USER, true),
    INTRUDER_USER(Message.EVENT_LOG_INTRUDER_USER, Type.USER, true),
    TOKEN_ISSUED(Message.EVENT_LOG_TOKEN_ISSUED, Type.USER, false),
    TOKEN_CLAIMED(Message.EVENT_LOG_TOKEN_CLAIMED, Type.USER, false),
    CLEAR_RESPONSES(Message.EVENT_LOG_TOKEN_CLAIMED, Type.USER, false),
    HELPDESK_SET_PASSWORD(Message.EVENT_LOG_HELPDESK_SET_PASSWORD, Type.USER, true),
    HELPDESK_UNLOCK_PASSWORD(Message.EVENT_LOG_HELPDESK_UNLOCK_PASSWORD, Type.USER, true),
    HELPDESK_CLEAR_RESPONSES(Message.EVENT_LOG_HELPDESK_CLEAR_RESPONSES, Type.USER, true),
    HELPDESK_CLEAR_OTP_SECRET(Message.EVENT_LOG_HELPDESK_CLEAR_OTP_SECRET, Type.USER, true),
    HELPDESK_ACTION(Message.EVENT_LOG_HELPDESK_ACTION, Type.USER, true),
    HELPDESK_DELETE_USER(Message.EVENT_LOG_HELPDESK_DELETE_USER, Type.USER, false),


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
                final String resourceKey = message.getResourceKey();
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
        SYSTEM
    }
}

/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
    AUTHENTICATE(Message.EVENT_LOG_AUTHENTICATE),
    CHANGE_PASSWORD(Message.EVENT_LOG_CHANGE_PASSWORD),
    RECOVER_PASSWORD(Message.EVENT_LOG_RECOVER_PASSWORD),
    SET_RESPONSES(Message.EVENT_LOG_SETUP_RESPONSES),
    ACTIVATE_USER(Message.EVENT_LOG_ACTIVATE_USER),
    CREATE_USER(Message.EVENT_LOG_CREATE_USER),
    UPDATE_PROFILE(Message.EVENT_LOG_UPDATE_PROFILE),
    INTRUDER_LOCK(Message.EVENT_LOG_INTRUDER_LOCKOUT),
    HELPDESK_SET_PASSWORD(Message.EVENT_LOG_HELPDESK_SET_PASSWORD),
    HELPDESK_UNLOCK_PASSWORD(Message.EVENT_LOG_HELPDESK_UNLOCK_PASSWORD),
    HELPDESK_CLEAR_RESPONSES(Message.EVENT_LOG_HELPDESK_CLEAR_RESPONSES),
    UNKNOWN(null);

    final private Message message;

    AuditEvent(final Message message) {
        this.message = message;
    }

    public Message getMessage() {
        return message;
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

        return UNKNOWN;
    }

    public String getLocalizedString(final Configuration config, final Locale locale) {
        if (this.getMessage() == null) {
            return "[unknown event]";
        }
        return Message.getLocalizedMessage(locale,this.getMessage(),config);
    }
}

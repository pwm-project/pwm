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

package password.pwm.svc.event;

import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;

import java.util.*;

class AuditSettings {
    private List<String> systemEmailAddresses = new ArrayList<>();
    private List<String> userEmailAddresses = new ArrayList<>();
    private String alertFromAddress = "";
    private Set<AuditEvent> userStoredEvents = new HashSet<>();
    private Set<AuditEvent> permittedEvents = new HashSet<>();

    AuditSettings(final Configuration configuration) {
        systemEmailAddresses = configuration.readSettingAsStringArray(PwmSetting.AUDIT_EMAIL_SYSTEM_TO);
        userEmailAddresses = configuration.readSettingAsStringArray(PwmSetting.AUDIT_EMAIL_USER_TO);
        alertFromAddress = configuration.readAppProperty(AppProperty.AUDIT_EVENTS_EMAILFROM);
        permittedEvents = figurePermittedEvents(configuration);
        userStoredEvents = figureUserStoredEvents(configuration);
    }

    List<String> getSystemEmailAddresses() {
        return systemEmailAddresses;
    }

    List<String> getUserEmailAddresses() {
        return userEmailAddresses;
    }

    Set<AuditEvent> getUserStoredEvents() {
        return userStoredEvents;
    }

    String getAlertFromAddress() {
        return alertFromAddress;
    }

    Set<AuditEvent> getPermittedEvents() {
        return permittedEvents;
    }

    private static Set<AuditEvent> figurePermittedEvents(final Configuration configuration) {
        final Set<AuditEvent> eventSet = new HashSet<>();
        eventSet.addAll(configuration.readSettingAsOptionList(PwmSetting.AUDIT_SYSTEM_EVENTS,AuditEvent.class));
        eventSet.addAll(configuration.readSettingAsOptionList(PwmSetting.AUDIT_USER_EVENTS,AuditEvent.class));
        return Collections.unmodifiableSet(eventSet);
    }

    private static Set<AuditEvent> figureUserStoredEvents(final Configuration configuration) {
        final Set<AuditEvent> eventSet = new HashSet<>();
        eventSet.addAll(configuration.readSettingAsOptionList(PwmSetting.EVENTS_USER_EVENT_TYPES,AuditEvent.class));
        return Collections.unmodifiableSet(eventSet);
    }
}

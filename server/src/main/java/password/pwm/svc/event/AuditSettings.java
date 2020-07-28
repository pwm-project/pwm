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

package password.pwm.svc.event;

import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Value
@Builder
class AuditSettings
{
    private List<String> systemEmailAddresses;
    private List<String> userEmailAddresses;
    private String alertFromAddress;
    private Set<AuditEvent> userStoredEvents;
    private Set<AuditEvent> permittedEvents;

    static AuditSettings fromConfig( final Configuration configuration )
    {
        return AuditSettings.builder()
                .systemEmailAddresses( configuration.readSettingAsStringArray( PwmSetting.AUDIT_EMAIL_SYSTEM_TO ) )
                .userEmailAddresses( configuration.readSettingAsStringArray( PwmSetting.AUDIT_EMAIL_USER_TO ) )
                .alertFromAddress( configuration.readAppProperty( AppProperty.AUDIT_EVENTS_EMAILFROM ) )
                .permittedEvents( figurePermittedEvents( configuration ) )
                .userStoredEvents( figureUserStoredEvents( configuration ) )
                .build();
    }

    private static Set<AuditEvent> figurePermittedEvents( final Configuration configuration )
    {
        final Set<AuditEvent> eventSet = EnumSet.noneOf( AuditEvent.class );
        eventSet.addAll( configuration.readSettingAsOptionList( PwmSetting.AUDIT_SYSTEM_EVENTS, AuditEvent.class ) );
        eventSet.addAll( configuration.readSettingAsOptionList( PwmSetting.AUDIT_USER_EVENTS, AuditEvent.class ) );
        return Collections.unmodifiableSet( eventSet );
    }

    private static Set<AuditEvent> figureUserStoredEvents( final Configuration configuration )
    {
        final Set<AuditEvent> eventSet = EnumSet.noneOf( AuditEvent.class );
        eventSet.addAll( configuration.readSettingAsOptionList( PwmSetting.EVENTS_USER_EVENT_TYPES, AuditEvent.class ) );
        return Collections.unmodifiableSet( eventSet );
    }
}

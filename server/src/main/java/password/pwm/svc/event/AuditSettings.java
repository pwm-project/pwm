/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.TimeDuration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

record AuditSettings(
        List<String> systemEmailAddresses,
        List<String> userEmailAddresses,
        String alertFromAddress,
        Set<AuditEvent> permittedEvents,
        TimeDuration maxRecordAge,
        long maxRecords
)
{
    AuditSettings(
            final List<String> systemEmailAddresses,
            final List<String> userEmailAddresses,
            final String alertFromAddress,
            final Set<AuditEvent> permittedEvents,
            final TimeDuration maxRecordAge,
            final long maxRecords
    )
    {
        this.systemEmailAddresses = CollectionUtil.stripNulls( systemEmailAddresses );
        this.userEmailAddresses =  CollectionUtil.stripNulls( userEmailAddresses );
        this.alertFromAddress = alertFromAddress;
        this.permittedEvents = CollectionUtil.stripNulls( permittedEvents );
        this.maxRecordAge = maxRecordAge;
        this.maxRecords = maxRecords;
    }

    static AuditSettings fromConfig( final AppConfig appConfig )
    {
        return new AuditSettings(
                appConfig.readSettingAsStringArray( PwmSetting.AUDIT_EMAIL_SYSTEM_TO ),
                appConfig.readSettingAsStringArray( PwmSetting.AUDIT_EMAIL_USER_TO ),
                appConfig.readAppProperty( AppProperty.AUDIT_EVENTS_EMAILFROM ),
                figurePermittedEvents( appConfig ),
                TimeDuration.of( appConfig.readSettingAsLong( PwmSetting.EVENTS_AUDIT_MAX_AGE ), TimeDuration.Unit.SECONDS ),
                appConfig.readSettingAsLong( PwmSetting.EVENTS_AUDIT_MAX_EVENTS ) );
    }

    private static Set<AuditEvent> figurePermittedEvents( final AppConfig appConfig )
    {
        final Set<AuditEvent> eventSet = EnumSet.noneOf( AuditEvent.class );
        eventSet.addAll( appConfig.readSettingAsOptionList( PwmSetting.AUDIT_SYSTEM_EVENTS, AuditEvent.class ) );
        eventSet.addAll( appConfig.readSettingAsOptionList( PwmSetting.AUDIT_USER_EVENTS, AuditEvent.class ) );
        return Collections.unmodifiableSet( eventSet );
    }
}

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

import lombok.AccessLevel;
import lombok.Builder;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;

import java.time.Instant;

public record AuditRecordData(
        AuditEventType type,
        AuditEvent eventCode,
        String guid,
        Instant timestamp,
        String message,
        String narrative,
        String xdasTaxonomy,
        String xdasOutcome,
        String instance,
        String perpetratorID,
        String perpetratorDN,
        ProfileID perpetratorLdapProfile,
        String sourceAddress,
        String sourceHost,
        String targetID,
        String targetDN,
        ProfileID targetLdapProfile,
        DomainID domain
)
        implements AuditRecord, SystemAuditRecord, UserAuditRecord, HelpdeskAuditRecord
{
    @Builder( access = AccessLevel.PACKAGE, toBuilder = true )
    public AuditRecordData
    {
    }
}

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

package password.pwm.ws.server.rest.bean;

import password.pwm.config.SettingReader;
import password.pwm.health.HealthStatus;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class PublicHealthRecord implements Serializable
{
    public HealthStatus status;
    public String topic;
    public String detail;
    public String domainID;

    public static PublicHealthRecord fromHealthRecord(
            final password.pwm.health.HealthRecord healthRecord,
            final Locale locale,
            final SettingReader config )
    {
        final PublicHealthRecord bean = new PublicHealthRecord();
        bean.status = healthRecord.getStatus();
        bean.topic = healthRecord.getTopic( locale, config );
        bean.detail = healthRecord.getDetail( locale, config );
        bean.domainID = healthRecord.getDomainID().stringValue();
        return bean;
    }

    public static List<PublicHealthRecord> fromHealthRecords(
            final List<password.pwm.health.HealthRecord> healthRecords,
            final Locale locale,
            final SettingReader config )
    {
        return healthRecords.stream()
                .map( record -> fromHealthRecord( record, locale, config ) )
                .collect( Collectors.toList() );
    }
}

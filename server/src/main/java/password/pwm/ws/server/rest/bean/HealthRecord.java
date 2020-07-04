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

package password.pwm.ws.server.rest.bean;

import password.pwm.config.Configuration;
import password.pwm.health.HealthStatus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HealthRecord implements Serializable
{
    public HealthStatus status;
    public String topic;
    public String detail;

    public static HealthRecord fromHealthRecord( final password.pwm.health.HealthRecord healthRecord, final Locale locale, final Configuration config )
    {
        final HealthRecord bean = new HealthRecord();
        bean.status = healthRecord.getStatus();
        bean.topic = healthRecord.getTopic( locale, config );
        bean.detail = healthRecord.getDetail( locale, config );
        return bean;
    }

    public static List<HealthRecord> fromHealthRecords( final List<password.pwm.health.HealthRecord> healthRecords, final Locale locale, final Configuration config )
    {
        final List<HealthRecord> beanList = new ArrayList<>();
        for ( final password.pwm.health.HealthRecord record : healthRecords )
        {
            if ( record != null )
            {
                beanList.add( fromHealthRecord( record, locale, config ) );
            }
        }
        return beanList;
    }
}

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

package password.pwm.ws.server.rest.bean;

import password.pwm.config.Configuration;
import password.pwm.health.HealthStatus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HealthRecord implements Serializable {
    public HealthStatus status;
    public String topic;
    public String detail;

    public static HealthRecord fromHealthRecord(password.pwm.health.HealthRecord healthRecord, Locale locale, final Configuration config) {
        final HealthRecord bean = new HealthRecord();
        bean.status = healthRecord.getStatus();
        bean.topic = healthRecord.getTopic(locale,config);
        bean.detail = healthRecord.getDetail(locale,config);
        return bean;
    }

    public static List<HealthRecord> fromHealthRecords(final List<password.pwm.health.HealthRecord> healthRecords, final Locale locale, final Configuration config) {
        final List<HealthRecord> beanList = new ArrayList<>();
        for (password.pwm.health.HealthRecord record : healthRecords) {
            if (record != null) {
                beanList.add(fromHealthRecord(record, locale, config));
            }
        }
        return beanList;
    }
}

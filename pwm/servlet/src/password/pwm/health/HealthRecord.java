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

package password.pwm.health;

import password.pwm.config.Configuration;
import password.pwm.i18n.Config;
import password.pwm.i18n.LocaleHelper;

import java.io.Serializable;
import java.util.Locale;

public class HealthRecord implements Serializable,Comparable<HealthRecord> {
    private final HealthStatus status;
    private final HealthTopic topicEnum;
    private final HealthMessage message;
    private final String topic;
    private final String detail;
    private final String[] fields;

    @Deprecated
    public HealthRecord(final HealthStatus status, final String topic, final String detail) {
        this.status = status;
        this.topic = topic;
        this.detail = detail;

        this.topicEnum = null;
        this.message = null;
        this.fields = null;
    }

    public HealthRecord(HealthStatus status, HealthTopic topicEnum, HealthMessage message, String[] fields) {
        this.status = status;
        this.topicEnum = topicEnum;
        this.message = message;
        this.fields = fields;

        this.topic = null;
        this.detail = null;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public String getTopic(final Locale locale, final Configuration config) {
        if (topic != null) {
            return topic;
        }
        return LocaleHelper.getLocalizedMessage(locale,"ConfigTopic_" + topicEnum.toString(),config,Config.class);
    }

    public String getDetail(final Locale locale, final Configuration config) {
        if (detail != null) {
            return detail;
        }
        return LocaleHelper.getLocalizedMessage(locale,"ConfigMessage_" + message.toString(),config,Config.class,fields);
    }

    public int compareTo(final HealthRecord otherHealthRecord) {
        final int statusCompare = status.compareTo(otherHealthRecord.status);
        if (statusCompare != 0) {
            return statusCompare;
        }

        final int topicCompare = topic.compareTo(otherHealthRecord.topic);
        if (topicCompare != 0) {
            return topicCompare;
        }

        return detail.compareTo(otherHealthRecord.detail);
    }
}

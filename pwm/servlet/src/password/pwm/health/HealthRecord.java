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

import java.io.Serializable;
import java.util.Locale;

public class HealthRecord implements Serializable,Comparable<HealthRecord> {
    private final HealthStatus status;

    // new fields
    private final HealthTopic topic;
    private final HealthMessage message;
    private final String[] fields;

    // old fields
    private final String old_topic;
    private final String old_detail;

    private boolean oldStyle = false;

    @Deprecated
    public HealthRecord(
            final HealthStatus status,
            final String topic,
            final String detail
    ) {
        this.oldStyle = true;

        if (status == null) {
            throw new NullPointerException("status cannot be null");
        }
        this.status = status;

        this.old_topic = topic;
        this.old_detail = detail;

        this.topic = null;
        this.message = null;
        this.fields = null;
    }

    public HealthRecord(
            final HealthStatus status,
            final HealthTopic topicEnum,
            final HealthMessage message,
            final String... fields
    ) {

        if (status == null) {
            throw new NullPointerException("status cannot be null");
        }
        this.status = status;

        this.topic = topicEnum;
        this.message = message;
        this.fields = fields;

        this.old_topic = null;
        this.old_detail = null;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public String getTopic(final Locale locale, final Configuration config) {
        if (oldStyle) {
            return old_topic;
        }
        return this.topic.getDescription(locale, config);
    }

    public String getDetail(final Locale locale, final Configuration config) {
        if (oldStyle) {
            return old_detail;
        }
        return this.message.getDescription(locale, config, fields);
    }

    public String toDebugString(final Locale locale, final Configuration config) {
        if (oldStyle) {
            return HealthRecord.class.getSimpleName() + " " + status + " " + old_topic + " " + old_detail;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(HealthRecord.class.getSimpleName());
        sb.append(" ");
        sb.append(status.getDescription(locale,config));
        sb.append(" ");
        sb.append(this.getTopic(locale, config));
        sb.append(" ");
        sb.append(this.getDetail(locale, config));
        return sb.toString();
    }

    public int compareTo(final HealthRecord otherHealthRecord) {
        final int statusCompare = status.compareTo(otherHealthRecord.status);
        if (statusCompare != 0) {
            return statusCompare;
        }

        final int topicCompare = this.getTopic(null,null).compareTo(otherHealthRecord.getTopic(null,null));
        if (topicCompare != 0) {
            return topicCompare;
        }

        final int detailCompare = this.getDetail(null,null).compareTo(otherHealthRecord.getDetail(null,null));
        if (detailCompare != 0) {
            return detailCompare;
        }

        return 0;
    }
}

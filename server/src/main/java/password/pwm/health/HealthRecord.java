/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import lombok.EqualsAndHashCode;
import password.pwm.config.Configuration;
import password.pwm.ws.server.rest.bean.HealthData;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@EqualsAndHashCode
public class HealthRecord implements Serializable, Comparable<HealthRecord>
{
    private final HealthStatus status;

    // new fields
    private final HealthTopic topic;
    private final HealthMessage message;
    private final String[] fields;

    // old fields
    private final String oldTopic;
    private final String oldDetail;

    public HealthRecord(
            final HealthStatus status,
            final String topic,
            final String detail
    )
    {
        if ( status == null )
        {
            throw new NullPointerException( "status cannot be null" );
        }
        this.status = status;

        this.oldTopic = topic;
        this.oldDetail = detail;

        this.topic = null;
        this.message = null;
        this.fields = null;
    }

    public HealthRecord(
            final HealthStatus status,
            final HealthTopic topic,
            final String detail
    )
    {
        if ( status == null )
        {
            throw new NullPointerException( "status cannot be null" );
        }
        this.status = status;

        this.oldTopic = null;
        this.oldDetail = detail;

        this.topic = topic;
        this.message = null;
        this.fields = null;
    }

    private HealthRecord(
            final HealthStatus status,
            final HealthTopic topicEnum,
            final HealthMessage message,
            final String[] fields
    )
    {

        if ( status == null )
        {
            throw new NullPointerException( "status cannot be null" );
        }
        this.status = status;

        this.topic = topicEnum;
        this.message = message;
        this.fields = fields;

        this.oldTopic = null;
        this.oldDetail = null;
    }

    public static HealthRecord forMessage( final HealthMessage message )
    {
        return new HealthRecord( message.getStatus(), message.getTopic(), message, null );
    }

    public static HealthRecord forMessage( final HealthMessage message, final String... fields )
    {
        return new HealthRecord( message.getStatus(), message.getTopic(), message, fields );
    }


    public HealthStatus getStatus( )
    {
        return status;
    }

    public String getTopic( final Locale locale, final Configuration config )
    {
        if ( oldTopic != null )
        {
            return oldTopic;
        }
        return this.topic.getDescription( locale, config );
    }

    public String getDetail( final Locale locale, final Configuration config )
    {
        if ( oldDetail != null )
        {
            return oldDetail;
        }
        return this.message.getDescription( locale, config, fields );
    }

    public String toDebugString( final Locale locale, final Configuration config )
    {
        return HealthRecord.class.getSimpleName() + " " + status.getDescription( locale, config ) + " " + this.getTopic(
                locale, config ) + " " + this.getDetail( locale, config );
    }

    public int compareTo( final HealthRecord otherHealthRecord )
    {
        final int statusCompare = status.compareTo( otherHealthRecord.status );
        if ( statusCompare != 0 )
        {
            return statusCompare;
        }

        final int topicCompare = this.getTopic( null, null ).compareTo( otherHealthRecord.getTopic( null, null ) );
        if ( topicCompare != 0 )
        {
            return topicCompare;
        }

        final int detailCompare = this.getDetail( null, null ).compareTo( otherHealthRecord.getDetail( null, null ) );
        if ( detailCompare != 0 )
        {
            return detailCompare;
        }

        return 0;
    }


    public List<HealthRecord> singletonList( )
    {
        return Collections.singletonList( this );
    }

    public static HealthData asHealthDataBean(
            final Configuration configuration,
            final Locale locale,
            final List<HealthRecord> profileRecords
    )
    {
        final List<password.pwm.ws.server.rest.bean.HealthRecord> healthRecordBeans = password.pwm.ws.server.rest.bean.HealthRecord.fromHealthRecords(
                profileRecords, locale, configuration );
        final HealthData healthData = new HealthData();
        healthData.timestamp = Instant.now();
        healthData.overall = HealthMonitor.getMostSevereHealthStatus( profileRecords ).toString();
        healthData.records = healthRecordBeans;
        return healthData;
    }
}

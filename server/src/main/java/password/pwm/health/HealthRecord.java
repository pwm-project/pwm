/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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
        return HealthData.builder()
                .timestamp( Instant.now() )
                .overall( HealthMonitor.getMostSevereHealthStatus( profileRecords ).toString() )
                .records( healthRecordBeans )
                .build();
    }
}

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

package password.pwm.health;

import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import password.pwm.config.Configuration;
import password.pwm.ws.server.rest.bean.HealthData;

import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@EqualsAndHashCode
public class HealthRecord implements Serializable, Comparable<HealthRecord>
{
    private final HealthStatus status;

    // new fields
    private final HealthTopic topic;
    private final HealthMessage message;
    private final List<String> fields;

    private static final Comparator<HealthRecord> COMPARATOR = Comparator.comparing(
            HealthRecord::getStatus,
            Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    healthRecord -> healthRecord.getTopic( null, null ),
                    Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    healthRecord -> healthRecord.getDetail( null, null ),
                    Comparator.nullsLast( Comparator.naturalOrder() ) );


    private HealthRecord(
            final HealthStatus status,
            final HealthTopic topicEnum,
            final HealthMessage message,
            final String... fields
    )
    {
        this.status = Objects.requireNonNull( status,  "status cannot be null" );
        this.topic = Objects.requireNonNull( topicEnum,  "topic cannot be null" );
        this.message = Objects.requireNonNull( message,  "message cannot be null" );
        this.fields = fields == null ? Collections.emptyList() : List.copyOf( Arrays.asList( fields ) );
    }

    public static HealthRecord forMessage( final HealthMessage message )
    {
        return new HealthRecord( message.getStatus(), message.getTopic(), message );
    }

    public static HealthRecord forMessage( final HealthMessage message, final String... fields )
    {
        return new HealthRecord( message.getStatus(), message.getTopic(), message, fields );
    }

    public static HealthRecord forMessage( final HealthMessage message, final HealthTopic healthTopic, final String... fields )
    {
        return new HealthRecord( message.getStatus(), healthTopic, message, fields );
    }

    public HealthStatus getStatus( )
    {
        return status;
    }

    public String getTopic( final Locale locale, final Configuration config )
    {
        if ( topic != null )
        {
            return this.topic.getDescription( locale, config );
        }
        return "";
    }

    public String getDetail( final Locale locale, final Configuration config )
    {
        if ( message != null )
        {
            return this.message.getDescription( locale, config, fields.toArray( new String[0] ) );
        }
        return "";
    }

    public String toDebugString( final Locale locale, final Configuration config )
    {
        return HealthRecord.class.getSimpleName() + " " + status.getDescription( locale, config ) + " " + this.getTopic(
                locale, config ) + " " + this.getDetail( locale, config );
    }

    @Override
    public int compareTo( @NotNull final HealthRecord otherHealthRecord )
    {
        return COMPARATOR.compare( this, otherHealthRecord );
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

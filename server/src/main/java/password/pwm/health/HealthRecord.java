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

package password.pwm.health;

import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import password.pwm.bean.DomainID;
import password.pwm.config.DomainConfig;
import password.pwm.config.SettingReader;
import password.pwm.ws.server.rest.bean.PublicHealthData;
import password.pwm.ws.server.rest.bean.PublicHealthRecord;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@EqualsAndHashCode
public class HealthRecord implements Comparable<HealthRecord>
{
    private final HealthStatus status;

    // new fields
    private final HealthTopic topic;
    private final DomainID domainID;
    private final HealthMessage message;
    private final List<String> fields;

    private static final Comparator<HealthRecord> COMPARATOR = Comparator.comparing(
            HealthRecord::getDomainID,
            Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    HealthRecord::getStatus,
                    Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    healthRecord -> healthRecord.getTopic( null, null ),
                    Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    healthRecord -> healthRecord.getDetail( null, null ),
                    Comparator.nullsLast( Comparator.naturalOrder() ) );


    private HealthRecord(
            final DomainID domainID,
            final HealthStatus status,
            final HealthTopic topicEnum,
            final HealthMessage message,
            final String... fields
    )
    {
        this.domainID = Objects.requireNonNull( domainID );
        this.status = Objects.requireNonNull( status,  "status cannot be null" );
        this.topic = Objects.requireNonNull( topicEnum,  "topic cannot be null" );
        this.message = Objects.requireNonNull( message,  "message cannot be null" );
        this.fields = fields == null ? Collections.emptyList() : List.copyOf( Arrays.asList( fields ) );
    }



    public static HealthRecord forMessage( final DomainID domainID, final HealthMessage message )
    {
        return new HealthRecord( domainID, message.getStatus(), message.getTopic(), message );
    }

    public static HealthRecord forMessage( final DomainID domainID, final HealthMessage message, final String... fields )
    {
        return new HealthRecord( domainID, message.getStatus(), message.getTopic(), message, fields );
    }

    public static HealthRecord forMessage( final DomainID domainID, final HealthMessage message, final HealthTopic healthTopic, final String... fields )
    {
        return new HealthRecord( domainID, message.getStatus(), healthTopic, message, fields );
    }

    public HealthStatus getStatus( )
    {
        return status;
    }

    public DomainID getDomainID()
    {
        return domainID;
    }

    public String getTopic( final Locale locale, final SettingReader config )
    {
        if ( topic != null )
        {
            return this.topic.getDescription( locale, config );
        }
        return "";
    }

    public String getDetail( final Locale locale, final SettingReader config )
    {
        if ( message != null )
        {
            return this.message.getDescription( locale, config, fields.toArray( new String[0] ) );
        }
        return "";
    }

    public String toDebugString( final Locale locale, final SettingReader config )
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

    public static PublicHealthData asHealthDataBean(
            final DomainConfig domainConfig,
            final Locale locale,
            final List<HealthRecord> profileRecords
    )
    {
        final List<PublicHealthRecord> healthRecordBeans = PublicHealthRecord.fromHealthRecords(
                profileRecords, locale, domainConfig );
        return PublicHealthData.builder()
                .timestamp( Instant.now() )
                .overall( HealthUtils.getMostSevereHealthStatus( profileRecords ).toString() )
                .records( healthRecordBeans )
                .build();
    }
}

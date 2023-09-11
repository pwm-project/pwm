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

import password.pwm.bean.DomainID;
import password.pwm.config.DomainConfig;
import password.pwm.config.SettingReader;
import password.pwm.util.java.CollectionUtil;
import password.pwm.ws.server.rest.bean.PublicHealthData;
import password.pwm.ws.server.rest.bean.PublicHealthRecord;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record HealthRecord(
        HealthStatus status,
        HealthTopic topic,
        DomainID domainID,
        HealthMessage message,
        List<String> fields

)
        implements Comparable<HealthRecord>
{
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


    public HealthRecord(
            final HealthStatus status,
            final HealthTopic topic,
            final DomainID domainID,
            final HealthMessage message,
            final List<String> fields
    )
    {
        this.domainID = Objects.requireNonNull( domainID );
        this.status = Objects.requireNonNull( status,  "status cannot be null" );
        this.topic = Objects.requireNonNull( topic,  "topic cannot be null" );
        this.message = Objects.requireNonNull( message,  "message cannot be null" );
        this.fields = CollectionUtil.stripNulls( fields );
    }

    public static HealthRecord forMessage( final DomainID domainID, final HealthMessage message )
    {
        return new HealthRecord( message.getStatus(), message.getTopic(), domainID,  message, List.of() );
    }

    public static HealthRecord forMessage( final DomainID domainID, final HealthMessage message, final String... fields )
    {
        final List<String> fieldList = CollectionUtil.arrayToList( fields );
        return new HealthRecord( message.getStatus(), message.getTopic(), domainID, message, fieldList );
    }

    public static HealthRecord forMessage( final DomainID domainID, final HealthMessage message, final HealthTopic healthTopic, final String... fields )
    {
        final List<String> fieldList = CollectionUtil.arrayToList( fields );
        return new HealthRecord( message.getStatus(), healthTopic, domainID,  message, fieldList );
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
            return this.message.getDescription( locale, config, CollectionUtil.listToArray( fields, String.class ) );
        }
        return "";
    }

    public String toDebugString( final Locale locale, final SettingReader config )
    {
        return HealthRecord.class.getSimpleName() + " " + status.getDescription( locale, config ) + " " + this.getTopic(
                locale, config ) + " " + this.getDetail( locale, config );
    }

    @Override
    public int compareTo( final HealthRecord otherHealthRecord )
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
        return new PublicHealthData(
                Instant.now(),
                HealthUtils.getMostSevereHealthStatus( profileRecords ).toString(),
                healthRecordBeans );

    }
}

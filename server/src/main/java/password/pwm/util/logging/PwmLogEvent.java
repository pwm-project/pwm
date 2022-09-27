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

package password.pwm.util.logging;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Value
@AllArgsConstructor( access = AccessLevel.PRIVATE )
@Builder( access = AccessLevel.PRIVATE, toBuilder = true )
@SuppressWarnings( "checkstyle:ParameterNumber" )
public class PwmLogEvent implements Serializable, Comparable<PwmLogEvent>
{
    private static final int MAX_MESSAGE_LENGTH = 10_000;
    private static final int MAX_FIELD_LENGTH = 256;

    private final Instant timestamp;
    private final String sessionID;
    private final String requestID;
    private final PwmLogLevel level;
    private final String topic;
    private final String message;
    private final LoggedThrowable loggedThrowable;
    private final String username;
    private final String domain;
    private final String sourceAddress;
    private final Duration duration;
    private final String threadName;

    private static final Comparator<PwmLogEvent> COMPARATOR = Comparator.comparing(
            PwmLogEvent::getTimestamp,
            Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    PwmLogEvent::getSessionID,
                    Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    PwmLogEvent::getRequestID,
                    Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing( PwmLogEvent::getLevel,
                    Comparator.nullsLast( Comparator.naturalOrder() ) );


    public static PwmLogEvent fromEncodedString( final String encodedString )
    {
        return JsonFactory.get().deserialize( encodedString, PwmLogEvent.class );
    }


    private PwmLogEvent(
            final Instant timestamp,
            final String topic,
            final String message,
            final SessionLabel sessionLabel,
            final LoggedThrowable loggedThrowable,
            final PwmLogLevel level,
            final Duration duration,
            final String threadName
    )
    {
        if ( timestamp == null )
        {
            throw new IllegalArgumentException( "date may not be null" );
        }

        if ( level == null )
        {
            throw new IllegalArgumentException( "level may not be null" );
        }

        this.timestamp = timestamp;
        this.topic = StringUtil.truncate( topic, MAX_FIELD_LENGTH );
        this.message = StringUtil.truncate( message, MAX_MESSAGE_LENGTH, " [truncated message]" );
        this.loggedThrowable = loggedThrowable;
        this.level = level;
        this.threadName = threadName == null ? "" : StringUtil.truncate( threadName, MAX_FIELD_LENGTH );

        this.sessionID = sessionLabel == null ? "" : StringUtil.truncate( sessionLabel.getSessionID(), MAX_FIELD_LENGTH );
        this.requestID = sessionLabel == null ? "" : StringUtil.truncate( sessionLabel.getRequestID(), MAX_FIELD_LENGTH );
        this.username = sessionLabel == null ? "" : StringUtil.truncate( sessionLabel.getUsername(), MAX_FIELD_LENGTH );
        this.domain = sessionLabel == null ? "" : StringUtil.truncate( sessionLabel.getDomain(), MAX_FIELD_LENGTH );
        this.sourceAddress = sessionLabel == null ? "" : StringUtil.truncate( sessionLabel.getSourceAddress(), MAX_FIELD_LENGTH );
        this.duration = duration == null ? Duration.ZERO : duration;

    }

    public static PwmLogEvent createPwmLogEvent(
            final Instant timestamp,
            final String topic,
            final String message,
            final SessionLabel sessionLabel,
            final Throwable throwable,
            final PwmLogLevel level,
            final Duration duration,
            final String threadName
    )
    {
        return new PwmLogEvent( timestamp, topic, message, sessionLabel, LoggedThrowable.fromThrowable( throwable ), level, duration, threadName );
    }

    String getEnhancedMessage( )
    {

        final SessionLabel sessionLabel = SessionLabel.fromPwmLogEvent( this );

        return PwmLogUtil.createEnhancedMessage(
                sessionLabel,
                message,
                loggedThrowable == null ? null : loggedThrowable.toThrowable(),
                duration == null ? null : TimeDuration.fromDuration( duration ) );
    }

    @Override
    public int compareTo( final PwmLogEvent o )
    {
        return COMPARATOR.compare( this, o );
    }

    String toEncodedString( )
    {
        return JsonFactory.get().serialize( this, PwmLogEvent.class );
    }

    public String toLogString( )
    {
        return toLogString( true );
    }

    public String toCsvLine( ) throws IOException
    {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final CSVPrinter csvPrinter = MiscUtil.makeCsvPrinter( byteArrayOutputStream );

        final String throwableMessage = ( getLoggedThrowable() == null || getLoggedThrowable().getMessage() == null ) ? "" : getLoggedThrowable().getMessage();

        final List<String> dataRow = new ArrayList<>();
        dataRow.add( StringUtil.toIsoDate( getTimestamp() ) );
        dataRow.add( getLevel().name() );
        dataRow.add( getSourceAddress( ) == null ? "" : getSourceAddress() );
        dataRow.add( getSessionID() == null ? "" : getSessionID() );
        dataRow.add( getUsername( ) );
        dataRow.add( getTopic() );
        dataRow.add( getMessage() );
        dataRow.add( throwableMessage );

        csvPrinter.printRecord( dataRow );
        csvPrinter.flush();

        return byteArrayOutputStream.toString( PwmConstants.DEFAULT_CHARSET.name() );
    }

    public String toLogString( final boolean includeTimeStamp )
    {
        final StringBuilder sb = new StringBuilder();
        if ( includeTimeStamp )
        {
            sb.append( this.getTimestamp() );
            sb.append( ", " );
        }
        sb.append( StringUtil.padRight( getLevel().toString(), 5, ' ' ) );
        sb.append( ", " );
        sb.append( shortenTopic( this.topic ) );
        sb.append( ", " );

        sb.append( this.getEnhancedMessage() );

        return sb.toString();
    }

    @Override
    public String toString( )
    {
        return "PwmLogEvent=" + JsonFactory.get().serialize( this );
    }

    private static String shortenTopic( final String input )
    {
        if ( input == null || input.isEmpty() )
        {
            return input;
        }

        final int keepParts = 2;
        final String[] parts = input.split( "\\." );
        final StringBuilder output = new StringBuilder();
        int partsAdded = 0;
        for ( int i = parts.length; i > 0 && partsAdded < keepParts; i-- )
        {
            output.insert( 0, parts[ i - 1 ] );
            partsAdded++;
            if ( i > 0 && partsAdded < keepParts )
            {
                output.insert( 0, "." );
            }
        }
        return output.toString();
    }

    PwmLogEvent stripThrowable()
    {
        return this.toBuilder().loggedThrowable( null ).build();
    }
}

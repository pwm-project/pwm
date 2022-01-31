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

import lombok.Value;
import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Value
public class PwmLogEvent implements Serializable, Comparable<PwmLogEvent>
{
    private static final int MAX_MESSAGE_LENGTH = 50_000;

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
            throws ClassNotFoundException, IOException
    {
        return JsonFactory.get().deserialize( encodedString, PwmLogEvent.class );
    }


    private PwmLogEvent(
            final Instant timestamp,
            final String topic,
            final String message,
            final SessionLabel sessionLabel,
            final LoggedThrowable loggedThrowable,
            final PwmLogLevel level
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

        // safety truncate
        final int maxFieldLength = 256;

        this.timestamp = timestamp;
        this.topic = StringUtil.truncate( topic, maxFieldLength );
        this.message = StringUtil.truncate( message, MAX_MESSAGE_LENGTH, " [truncated message]" );
        this.loggedThrowable = loggedThrowable;
        this.level = level;

        this.sessionID = sessionLabel == null ? "" : StringUtil.truncate( sessionLabel.getSessionID(), 256 );
        this.requestID = sessionLabel == null ? "" : StringUtil.truncate( sessionLabel.getRequestID(), 256 );
        this.username = sessionLabel == null ? "" : StringUtil.truncate( sessionLabel.getUsername(), 256 );
        this.domain = sessionLabel == null ? "" : StringUtil.truncate( sessionLabel.getDomain(), 256 );
        this.sourceAddress = sessionLabel == null ? "" : StringUtil.truncate( sessionLabel.getSourceAddress(), 256 );
    }

    public static PwmLogEvent createPwmLogEvent(
            final Instant timestamp,
            final String topic,
            final String message,
            final SessionLabel sessionLabel,
            final Throwable throwable,
            final PwmLogLevel level
    )
    {
        return new PwmLogEvent( timestamp, topic, message, sessionLabel, LoggedThrowable.fromThrowable( throwable ), level );
    }

    String getEnhancedMessage( )
    {
        final StringBuilder output = new StringBuilder();
        output.append( getDebugLabel() );
        output.append( message );

        final String srcAddrString = getSourceAddress();
        if ( StringUtil.notEmpty( srcAddrString ) )
        {
            final String srcStr = " [" + srcAddrString + "]";

            final int firstCR = output.indexOf( "\n" );
            if ( firstCR == -1 )
            {
                output.append( srcStr );
            }
            else
            {
                output.insert( firstCR, srcStr );
            }
        }

        if ( this.getLoggedThrowable() != null && this.getLoggedThrowable().getStackTrace() != null )
        {
            output.append( JavaHelper.throwableToString( getLoggedThrowable().toThrowable() ) );
        }

        return output.toString();
    }

    @Override
    public int compareTo( final PwmLogEvent o )
    {
        return COMPARATOR.compare( this, o );
    }

    String toEncodedString( )
            throws IOException
    {
        return JsonFactory.get().serialize( this, PwmLogEvent.class );
    }

    Throwable getThrowable()
    {
        return getLoggedThrowable() == null
                ? null
                : getLoggedThrowable().toThrowable();
    }

    private String getDebugLabel( )
    {
        final StringBuilder sb = new StringBuilder();
        final String sessionID = getSessionID();
        final String username = getUsername();

        if ( StringUtil.notEmpty( sessionID ) )
        {
            sb.append( sessionID );
        }
        if ( StringUtil.notEmpty( domain ) )
        {
            if ( sb.length() > 0 )
            {
                sb.append( ',' );
            }
            sb.append( domain );
        }
        if ( StringUtil.notEmpty( username ) )
        {
            if ( sb.length() > 0 )
            {
                sb.append( ',' );
            }
            sb.append( username );
        }

        if ( sb.length() > 0 )
        {
            sb.insert( 0, "{" );
            sb.append( "} " );
        }

        return sb.toString();
    }

    public String toLogString( )
    {
        return toLogString( true );
    }

    public String toCsvLine( ) throws IOException
    {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( byteArrayOutputStream );
        final List<String> dataRow = new ArrayList<>();
        dataRow.add( JavaHelper.toIsoDate( getTimestamp() ) );
        dataRow.add( getLevel().name() );
        dataRow.add( getSourceAddress( ) == null ? "" : getSourceAddress() );
        dataRow.add( getSessionID() == null ? "" : getSessionID() );
        dataRow.add( getUsername( ) );
        dataRow.add( getTopic() );
        dataRow.add( getMessage() );
        dataRow.add( getLoggedThrowable() == null && getLoggedThrowable().getMessage() != null ? "" : getLoggedThrowable().getMessage() );
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

}

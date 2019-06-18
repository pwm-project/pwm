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

package password.pwm.util.logging;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Value
public class PwmLogEvent implements Serializable, Comparable
{

    private static final int MAX_MESSAGE_LENGTH = 50_000;

    @SerializedName( "l" )
    private final PwmLogLevel level;

    @SerializedName( "t" )
    private final String topic;

    @SerializedName( "m" )
    private final String message;

    //aka network address/host
    @SerializedName( "s" )
    private final String source;

    //aka principal
    @SerializedName( "a" )
    private final String actor;

    //aka session id
    @SerializedName( "b" )
    private final String label;

    @SerializedName( "e" )
    private final Throwable throwable;

    @SerializedName( "d" )
    private final Instant date;

    public static PwmLogEvent fromEncodedString( final String encodedString )
            throws ClassNotFoundException, IOException
    {
        return JsonUtil.deserialize( encodedString, PwmLogEvent.class );
    }


    @SuppressWarnings( "checkstyle:ParameterNumber" )
    private PwmLogEvent(
            final Instant date,
            final String topic,
            final String message,
            final String source,
            final String actor,
            final String label,
            final Throwable throwable,
            final PwmLogLevel level
    )
    {
        if ( date == null )
        {
            throw new IllegalArgumentException( "date may not be null" );
        }

        if ( level == null )
        {
            throw new IllegalArgumentException( "level may not be null" );
        }

        final String retainedMessage = message != null && message.length() > MAX_MESSAGE_LENGTH
                ? message.substring( 0, MAX_MESSAGE_LENGTH ) + " [truncated message]"
                : message;

        this.date = date;
        this.topic = topic;
        this.message = retainedMessage;
        this.source = source;
        this.actor = actor;
        this.label = label;
        this.throwable = throwable;
        this.level = level;
    }

    private static String makeSrcString( final SessionLabel sessionLabel )
    {
        try
        {
            final StringBuilder from = new StringBuilder();
            {
                final String srcAddress = sessionLabel.getSrcAddress();
                final String srcHostname = sessionLabel.getSrcHostname();

                if ( srcAddress != null )
                {
                    from.append( srcAddress );
                    if ( !srcAddress.equals( srcHostname ) )
                    {
                        from.append( "/" );
                        from.append( srcHostname );
                    }
                }
            }
            return from.toString();
        }
        catch ( NullPointerException e )
        {
            return "";
        }
    }

    private static String makeActorString( final SessionLabel sessionLabel )
    {
        final StringBuilder sb = new StringBuilder();
        if ( sessionLabel != null )
        {
            if ( sessionLabel.getUsername() != null )
            {
                sb.append( sessionLabel.getUsername() );
            }
            else if ( sessionLabel.getUserIdentity() != null )
            {
                sb.append( sessionLabel.getUserIdentity().toDelimitedKey() );
            }
        }
        return sb.toString();
    }

    @SuppressWarnings( "checkstyle:ParameterNumber" )
    public static PwmLogEvent createPwmLogEvent(
            final Instant date,
            final String topic,
            final String message,
            final String source,
            final String actor,
            final String label,
            final Throwable throwable,
            final PwmLogLevel level
    )
    {
        return new PwmLogEvent( date, topic, message, source, actor, label, throwable, level );
    }

    public static PwmLogEvent createPwmLogEvent(
            final Instant date,
            final String topic,
            final String message,
            final SessionLabel sessionLabel,
            final Throwable throwable,
            final PwmLogLevel level
    )
    {
        final String source = makeSrcString( sessionLabel );
        final String actor = makeActorString( sessionLabel );
        final String label = sessionLabel != null ? sessionLabel.getSessionID() : null;
        return new PwmLogEvent( date, topic, message, source, actor, label, throwable, level );
    }


    public String getTopTopic( )
    {
        if ( topic == null )
        {
            return null;
        }

        final int lastDot = topic.lastIndexOf( "." );
        return lastDot != -1 ? topic.substring( lastDot + 1, topic.length() ) : topic;
    }

    String getEnhancedMessage( )
    {
        final StringBuilder output = new StringBuilder();
        output.append( getDebugLabel() );
        output.append( message );

        final String srcAddrString = this.getSource();
        if ( srcAddrString != null && !srcAddrString.isEmpty() )
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

        if ( this.getThrowable() != null )
        {
            output.append( " (stacktrace follows)\n" );
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter( sw );
            this.getThrowable().printStackTrace( pw );
            pw.flush();
            output.append( sw.toString() );
        }

        return output.toString();
    }

    public int compareTo( final Object o )
    {
        if ( !( o instanceof PwmLogEvent ) )
        {
            throw new IllegalArgumentException( "cannot compare with non PwmLogEvent" );
        }
        return this.getDate().compareTo( ( ( PwmLogEvent ) o ).getDate() );
    }

    String toEncodedString( )
            throws IOException
    {
        return JsonUtil.serialize( this );
    }

    private String getDebugLabel( )
    {
        final StringBuilder sb = new StringBuilder();
        if ( ( getActor() != null && !getActor().isEmpty() ) || ( ( getLabel() != null && !getLabel().isEmpty() ) ) )
        {
            sb.append( "{" );
            if ( getLabel() != null && !getLabel().isEmpty() )
            {
                sb.append( this.getLabel() );
            }
            if ( getActor() != null && !getActor().isEmpty() )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( "," );
                }
                sb.append( this.getActor() );
            }
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
        dataRow.add( JavaHelper.toIsoDate( getDate() ) );
        dataRow.add( getLevel().name() );
        dataRow.add( getSource() );
        dataRow.add( getLabel() );
        dataRow.add( getActor() );
        dataRow.add( getTopic() );
        dataRow.add( getMessage() );
        dataRow.add( getThrowable() == null ? "" : JavaHelper.readHostileExceptionMessage( getThrowable() ) );
        csvPrinter.printRecord( dataRow );
        csvPrinter.flush();
        return byteArrayOutputStream.toString( PwmConstants.DEFAULT_CHARSET.name() );

    }

    public String toLogString( final boolean includeTimeStamp )
    {
        final StringBuilder sb = new StringBuilder();
        if ( includeTimeStamp )
        {
            sb.append( this.getDate().toString() );
            sb.append( ", " );
        }
        sb.append( StringUtil.padEndToLength( getLevel().toString(), 5, ' ' ) );
        sb.append( ", " );
        sb.append( shortenTopic( this.topic ) );
        sb.append( ", " );

        sb.append( this.getEnhancedMessage() );

        return sb.toString();
    }

    @Override
    public String toString( )
    {
        return "PwmLogEvent=" + JsonUtil.serialize( this );
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

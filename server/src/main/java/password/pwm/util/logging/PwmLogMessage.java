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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import password.pwm.bean.SessionLabel;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.TimeDuration;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * In memory non-intended-for-serialization logging event.  See {@link PwmLogEvent} for a stored version;
 */
@Value
@AllArgsConstructor( access = AccessLevel.PRIVATE )
@SuppressWarnings( "checkstyle:ParameterNumber" )
public class PwmLogMessage
{
    private Instant timestamp;
    private String topic;
    private PwmLogLevel level;
    private SessionLabel sessionLabel;
    private Supplier<? extends CharSequence> message;
    private TimeDuration duration;
    private Throwable throwable;
    private String threadName;

    private final Supplier<String> enhancedMessage = LazySupplier.create( () -> PwmLogMessage.createEnhancedMessage( this ) );

    public static PwmLogMessage create(
            final Instant timestamp,
            final String topic,
            final PwmLogLevel level,
            final SessionLabel sessionLabel,
            final Supplier<? extends CharSequence> message,
            final TimeDuration duration,
            final Throwable throwable,
            final String threadName
    )
    {
        return new PwmLogMessage( timestamp, topic, level, sessionLabel, message, duration, throwable, threadName );
    }

    String messageToString()
    {
        return message == null || message.get() == null ? "" : message.get().toString();
    }

    PwmLogEvent toLogEvent()
    {
        return PwmLogEvent.createPwmLogEvent(
                timestamp,
                topic,
                messageToString(),
                sessionLabel,
                throwable,
                level,
                duration == null ? null : duration.asDuration(),
                threadName );
    }

    String getEnhancedMessage()
    {
        return enhancedMessage.get();
    }

    private static String createEnhancedMessage( final PwmLogMessage pwmLogMessage )
    {
        return PwmLogUtil.createEnhancedMessage(
                pwmLogMessage.getSessionLabel(),
                pwmLogMessage.messageToString(),
                pwmLogMessage.getThrowable(),
                pwmLogMessage.getDuration() );
    }

    static Throwable throwableFromILoggingEvent( final ILoggingEvent event )
    {
        return event.getThrowableProxy() instanceof ThrowableProxy
                ? ( ( ThrowableProxy ) event.getThrowableProxy() ).getThrowable()
                : null;
    }

    static PwmLogMessage fromLogbackEvent( final ILoggingEvent event  )
    {
        final Supplier<? extends CharSequence> message = ( Supplier<CharSequence> ) event::getMessage;
        final Throwable throwableInformation = throwableFromILoggingEvent( event );
        final PwmLogLevel level = PwmLogLevel.fromLogbackLevel( event.getLevel() );
        final Instant timeStamp = Instant.ofEpochMilli( event.getTimeStamp() );
        final String sourceLogger = event.getLoggerName();
        final String threadName = event.getThreadName();

        final SessionLabel sessionLabel = PwmLogManager.getThreadSessionData();

        return PwmLogMessage.create(
                timeStamp,
                sourceLogger,
                level,
                sessionLabel,
                message,
                null,
                throwableInformation,
                threadName );
    }
}

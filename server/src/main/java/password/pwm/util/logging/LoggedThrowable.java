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
import password.pwm.util.java.JavaHelper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Value
public class LoggedThrowable
{
    private final String message;
    private final List<LoggedStackTraceElement> stackTrace;
    private final LoggedThrowable cause;
    private final List<LoggedThrowable> suppressedExceptions;

    public Throwable toThrowable()
    {
        return new DeserializedThrowable( this );
    }

    public static LoggedThrowable fromThrowable( final Throwable t )
    {
        if ( t == null )
        {
            return null;
        }

        return new LoggedThrowable(
                JavaHelper.readHostileExceptionMessage( t ),
                t.getStackTrace() == null ? null : Arrays.stream( t.getStackTrace() )
                        .map( LoggedStackTraceElement::fromStackTraceElement )
                        .collect( Collectors.toUnmodifiableList() ),
                t.getCause() == null ? null : fromThrowable( t.getCause() ),
                t.getSuppressed() == null ? null : Arrays.stream( t.getSuppressed() )
                        .map( LoggedThrowable::fromThrowable )
                        .collect( Collectors.toUnmodifiableList() ) );
    }

    @Value
    static class LoggedStackTraceElement
    {
        private final String declaringClass;
        private final String methodName;
        private final String fileName;
        private final int lineNumber;

        static LoggedStackTraceElement fromStackTraceElement( final StackTraceElement stackTraceElement )
        {
            return new LoggedStackTraceElement(
                    stackTraceElement.getClassName(),
                    stackTraceElement.getMethodName(),
                    stackTraceElement.getFileName(),
                    stackTraceElement.getLineNumber() );
        }

        public StackTraceElement toStackTraceElement()
        {
            return new StackTraceElement( getDeclaringClass(), getMethodName(), getFileName(), getLineNumber() );
        }
    }

    static class DeserializedThrowable extends Throwable
    {
        private final LoggedThrowable loggedThrowable;

        DeserializedThrowable( final LoggedThrowable loggedThrowable )
        {
            this.loggedThrowable = loggedThrowable;
            super.setStackTrace( getStackTrace() );
            if ( loggedThrowable.getSuppressedExceptions() != null )
            {
                loggedThrowable.getSuppressedExceptions().forEach( e -> this.addSuppressed( e.toThrowable() ) );
            }
        }

        @Override
        public String getMessage()
        {
            return loggedThrowable.getMessage();
        }

        @Override
        public String getLocalizedMessage()
        {
            return getMessage();
        }

        @Override
        public synchronized Throwable getCause()
        {
            return loggedThrowable.getCause() == null
                    ? null
                    : new DeserializedThrowable( loggedThrowable.getCause() );
        }

        @Override
        public StackTraceElement[] getStackTrace()
        {
            if ( loggedThrowable.getStackTrace() == null )
            {
                return null;
            }
            return this.loggedThrowable.getStackTrace().stream()
                    .map( LoggedStackTraceElement::toStackTraceElement )
                    .toArray( StackTraceElement[]::new );
        }
    }
}

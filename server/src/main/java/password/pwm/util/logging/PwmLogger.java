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

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * @author Jason D. Rivard
 */
public class PwmLogger
{
    private final String name;
    private final Logger logbackLogger;
    private final boolean localDBDisabled;

    public static PwmLogger forClass( final Class<?> className )
    {
        return new PwmLogger( className.getName(), false );
    }

    public static PwmLogger getLogger( final String name )
    {
        return new PwmLogger( name, false );
    }

    public static PwmLogger forClass(
            final Class<?> className,
            final boolean localDBDisabled
    )
    {
        return new PwmLogger( className.getName(), localDBDisabled );
    }

    public static PwmLogger getLogger( final String name, final boolean localDBDisabled )
    {
        return new PwmLogger( name, localDBDisabled );
    }

    PwmLogger( final String name, final boolean localDBDisabled )
    {
        this.name = name;
        this.localDBDisabled = localDBDisabled;

        final ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        logbackLogger = loggerFactory.getLogger( name );
    }

    public String getName( )
    {
        return name;
    }

    public void log( final PwmLogLevel level, final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message )
    {
        doLogEvent( level, sessionLabel, message, null, null );
    }

    public void log(
            final PwmLogLevel level,
            final SessionLabel sessionLabel,
            final Supplier<? extends CharSequence> message,
            final Throwable throwable,
            final TimeDuration timeDuration
    )
    {
        doLogEvent( level, sessionLabel, message, throwable, timeDuration );
    }

    public void trace( final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.TRACE, null, message, null, null );
    }

    public void trace( final Supplier<? extends CharSequence> message, final TimeDuration timeDuration )
    {
        doLogEvent( PwmLogLevel.TRACE, null, message, null, timeDuration );
    }

    public void trace( final PwmRequest pwmRequest, final Supplier<? extends CharSequence> message )
    {
        doPwmRequestLogEvent( PwmLogLevel.TRACE, pwmRequest, message, null, null );
    }

    public void trace( final PwmRequest pwmRequest, final Supplier<? extends CharSequence> message, final TimeDuration timeDuration )
    {
        doPwmRequestLogEvent( PwmLogLevel.TRACE, pwmRequest, message, null, timeDuration );
    }

    public void trace( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.TRACE, sessionLabel, message, null, null );
    }

    public void traceDevDebug( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message )
    {
        final PwmApplication pwmApplication = PwmLogManager.getPwmApplication();
        if ( pwmApplication == null || !pwmApplication.getConfig().isDevDebugMode() )
        {
            return;
        }

        doLogEvent( PwmLogLevel.TRACE, sessionLabel, message, null, null );
    }

    public void trace( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message, final TimeDuration timeDuration )
    {
        doLogEvent( PwmLogLevel.TRACE, sessionLabel, message, null, timeDuration );
    }

    public void trace( final Supplier<? extends CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.TRACE, null, message, exception, null );
    }

    public void debug( final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.DEBUG, null, message, null, null );
    }

    public void debug( final Supplier<? extends CharSequence> message, final TimeDuration timeDuration )
    {
        doLogEvent( PwmLogLevel.DEBUG, null, message, null, timeDuration );
    }

    public void debug( final PwmRequest pwmRequest, final Supplier<? extends CharSequence> message )
    {
        doPwmRequestLogEvent( PwmLogLevel.DEBUG, pwmRequest, message, null, null );
    }

    public void debug( final PwmRequest pwmRequest, final ErrorInformation errorInformation )
    {
        doPwmRequestLogEvent( PwmLogLevel.DEBUG, pwmRequest, convertErrorInformation( errorInformation ), null, null );
    }

    public void debug( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.DEBUG, sessionLabel, message, null, null );
    }

    public void debug( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message, final TimeDuration timeDuration )
    {
        doLogEvent( PwmLogLevel.DEBUG, sessionLabel, message, null, timeDuration );
    }

    public void debug( final SessionLabel sessionLabel, final ErrorInformation errorInformation )
    {
        doLogEvent( PwmLogLevel.DEBUG, sessionLabel, convertErrorInformation( errorInformation ), null, null );
    }

    public void info( final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.INFO, null, message, null, null );
    }

    public void info( final Supplier<? extends CharSequence> message, final TimeDuration timeDuration )
    {
        doLogEvent( PwmLogLevel.INFO, null, message, null, timeDuration );
    }

    public void info( final PwmRequest pwmRequest, final Supplier<? extends CharSequence> message )
    {
        doPwmRequestLogEvent( PwmLogLevel.INFO, pwmRequest, message, null, null );
    }

    public void info( final PwmRequest pwmRequest, final ErrorInformation errorInformation )
    {
        doPwmRequestLogEvent( PwmLogLevel.INFO, pwmRequest, convertErrorInformation( errorInformation ), null, null );
    }

    public void info( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.INFO, sessionLabel, message, null, null );
    }

    public void info( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message, final TimeDuration timeDuration )
    {
        doLogEvent( PwmLogLevel.INFO, sessionLabel, message, null, timeDuration );
    }

    public void info( final Supplier<? extends CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.INFO, null, message, exception, null );
    }

    public void error( final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.ERROR, null, message, null, null );
    }

    public void error( final PwmRequest pwmRequest, final Supplier<? extends CharSequence> message, final Throwable exception )
    {
        doPwmRequestLogEvent( PwmLogLevel.ERROR, pwmRequest, message, exception, null );
    }

    public void error( final PwmRequest pwmRequest, final Supplier<? extends CharSequence> message )
    {
        doPwmRequestLogEvent( PwmLogLevel.ERROR, pwmRequest, message, null, null );
    }

    public void error( final PwmRequest pwmRequest, final ErrorInformation errorInformation )
    {
        doPwmRequestLogEvent( PwmLogLevel.ERROR, pwmRequest, convertErrorInformation( errorInformation ), null, null );
    }

    public void error( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, message, null, null );
    }

    public void error( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message, final TimeDuration timeDurationSupplier )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, message, null, timeDurationSupplier );
    }

    public void error( final SessionLabel sessionLabel, final ErrorInformation errorInformation )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, convertErrorInformation( errorInformation ), null, null );
    }

    public void error( final SessionLabel sessionLabel, final ErrorInformation errorInformation, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, convertErrorInformation( errorInformation ), exception, null );
    }

    public void error( final Supplier<? extends CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.ERROR, null, message, exception, null );
    }

    public void error( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, message, exception, null );
    }

    public void warn( final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.WARN, null, message, null, null );
    }

    public void warn( final PwmRequest pwmRequest, final Supplier<? extends CharSequence> message )
    {
        doPwmRequestLogEvent( PwmLogLevel.WARN, pwmRequest, message, null, null );
    }

    public void warn( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.WARN, sessionLabel, message, exception, null );
    }

    public void warn( final PwmRequest pwmRequest, final Supplier<? extends CharSequence> message, final Throwable exception )
    {
        doPwmRequestLogEvent( PwmLogLevel.WARN, pwmRequest, message, exception, null );
    }

    public void warn( final SessionLabel sessionLabel, final ErrorInformation errorInformation, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, convertErrorInformation( errorInformation ), exception, null );
    }

    public void warn( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.WARN, sessionLabel, message, null, null );
    }

    public void warn( final Supplier<? extends CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.WARN, null, message, exception, null );
    }

    public void fatal( final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.FATAL, null, message, null, null );
    }

    public void fatal( final SessionLabel sessionLabel, final Supplier<? extends CharSequence> message )
    {
        doLogEvent( PwmLogLevel.FATAL, sessionLabel, message, null, null );
    }

    public void fatal( final Supplier<? extends CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.FATAL, null, message, exception, null );
    }

    public Appendable asAppendable( final PwmLogLevel pwmLogLevel, final SessionLabel sessionLabel )
    {
        return new PwmLoggerAppendable( this, pwmLogLevel, sessionLabel );
    }

    private void doPwmRequestLogEvent(
            final PwmLogLevel level,
            final PwmRequest pwmRequest,
            final Supplier<? extends CharSequence> message,
            final Throwable e,
            final TimeDuration timeDuration
    )
    {
        if ( !isInterestingLevel( level ) )
        {
            return;
        }

        final SessionLabel sessionLabel = pwmRequest != null ? pwmRequest.getLabel() : null;

        Supplier<? extends CharSequence> cleanedMessage = message;

        if ( pwmRequest != null && message != null )
        {
            try
            {
                final String cleanedString = PwmLogUtil.removeUserDataFromString( pwmRequest.getPwmSession().getLoginInfoBean(), message.get().toString() );
                final String printableString = StringUtil.cleanNonPrintableCharacters( cleanedString ).toString();
                cleanedMessage = () -> printableString;
            }
            catch ( final PwmUnrecoverableException e1 )
            {
                /* can't be logged */
            }
        }

        doLogEvent( level, sessionLabel, cleanedMessage, e, timeDuration );
    }

    private void doLogEvent(
            final PwmLogLevel level,
            final SessionLabel sessionLabel,
            final Supplier<? extends CharSequence> message,
            final Throwable e,
            final TimeDuration timeDuration
    )
    {
        final String threadName = Thread.currentThread().getName();

        final PwmLogMessage pwmLogMessage = PwmLogMessage.create(
                Instant.now(),
                logbackLogger.getName(),
                level,
                sessionLabel,
                message,
                timeDuration,
                e,
                threadName );

        doLogEvent( pwmLogMessage );
    }

    void doLogEvent( final PwmLogMessage pwmLogMessage )
    {
        if ( PwmLogUtil.ignorableLogEvent( pwmLogMessage ) )
        {
            return;
        }

        PwmLogUtil.pushMessageToSlf4j( logbackLogger, pwmLogMessage );

        try
        {
            final LocalDBLogger localDBLogger = PwmLogManager.getLocalDbLogger();
            if ( !localDBDisabled && localDBLogger != null )
            {
                localDBLogger.writeEvent( pwmLogMessage );
            }

            final PwmApplication pwmApplication = PwmLogManager.getPwmApplication();
            PwmLogUtil.captureFilteredLogEventsToAudit( pwmApplication, pwmLogMessage );
        }
        catch ( final Exception e2 )
        {
            //nothing can be done about it now;
        }
    }


    private static Supplier<? extends CharSequence> convertErrorInformation( final ErrorInformation info )
    {
        return info::toDebugStr;
    }

    public boolean isInterestingLevel( final PwmLogLevel pwmLogLevel )
    {
        final PwmLogLevel lowestLevelConfigured = PwmLogManager.getLowestLogLevelConfigured();
        return pwmLogLevel.isGreaterOrSameAs( lowestLevelConfigured );
    }
}


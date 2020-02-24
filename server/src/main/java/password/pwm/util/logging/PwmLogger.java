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

import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.varia.NullAppender;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Jason D. Rivard
 */
public class PwmLogger
{
    private static LocalDBLogger localDBLogger;
    private static PwmLogLevel minimumDbLogLevel;
    private static PwmApplication pwmApplication;
    private static RollingFileAppender fileAppender;
    private static boolean initialized;

    private final String name;
    private final org.apache.log4j.Logger log4jLogger;
    private final boolean localDBDisabled;

    public static void markInitialized( )
    {
        initialized = true;
    }

    static void setPwmApplication( final PwmApplication pwmApplication )
    {
        PwmLogger.pwmApplication = pwmApplication;
        if ( pwmApplication != null )
        {
            initialized = true;
        }
    }

    static void setLocalDBLogger( final PwmLogLevel minimumDbLogLevel, final LocalDBLogger localDBLogger )
    {
        PwmLogger.minimumDbLogLevel = minimumDbLogLevel;
        PwmLogger.localDBLogger = localDBLogger;
    }

    static void setFileAppender( final RollingFileAppender rollingFileAppender )
    {
        PwmLogger.fileAppender = rollingFileAppender;
    }

    public static PwmLogger forClass( final Class className )
    {
        return new PwmLogger( className.getName(), false );
    }

    public static PwmLogger getLogger( final String name )
    {
        return new PwmLogger( name, false );
    }

    public static PwmLogger forClass(
            final Class className,
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
        log4jLogger = org.apache.log4j.Logger.getLogger( name );
    }

    public String getName( )
    {
        return name;
    }

    private void doPwmRequestLogEvent( final PwmLogLevel level, final PwmRequest pwmRequest, final Supplier<CharSequence> message, final Throwable e )
    {
        if ( !isEnabled( level ) )
        {
            return;
        }

        final SessionLabel sessionLabel = pwmRequest != null ? pwmRequest.getLabel() : null;

        Supplier<CharSequence> cleanedMessage = message;

        if ( pwmRequest != null && message != null )
        {
            try
            {
                final CharSequence cleanedString = PwmLogger.removeUserDataFromString( pwmRequest.getPwmSession().getLoginInfoBean(), message.get() );
                cleanedMessage = () -> cleanedString;
            }
            catch ( final PwmUnrecoverableException e1 )
            {
                /* can't be logged */
            }
        }

        doLogEvent( level, sessionLabel, cleanedMessage, e );
    }

    private void doLogEvent( final PwmLogLevel level, final SessionLabel sessionLabel, final CharSequence message, final Throwable e )
    {
        doLogEvent( level, sessionLabel, () -> message == null ? "" : message, e );
    }

    private void doLogEvent( final PwmLogLevel level, final SessionLabel sessionLabel, final Supplier<CharSequence> message, final Throwable e )
    {
        if ( !isEnabled( level ) )
        {
            return;
        }

        final PwmLogLevel effectiveLevel = level == null ? PwmLogLevel.TRACE : level;
        final String topic = log4jLogger.getName();
        final CharSequence effectiveMessage = message == null ? "" : message.get();
        final PwmLogEvent logEvent = PwmLogEvent.createPwmLogEvent( Instant.now(), topic, effectiveMessage.toString(), sessionLabel,
                e, effectiveLevel );
        doLogEvent( logEvent );
    }

    private void doLogEvent( final PwmLogEvent logEvent )
    {
        pushMessageToLog4j( logEvent );

        try
        {

            if ( !localDBDisabled && localDBLogger != null && minimumDbLogLevel != null )
            {
                if ( logEvent.getLevel().compareTo( minimumDbLogLevel ) >= 0 )
                {
                    localDBLogger.writeEvent( logEvent );
                }
            }

            if ( logEvent.getLevel() == PwmLogLevel.FATAL )
            {
                if ( !logEvent.getMessage().contains( "5039" ) )
                {
                    final Map<String, String> messageInfo = new HashMap<>();
                    messageInfo.put( "level", logEvent.getLevel() == null ? "null" : logEvent.getLevel().toString() );
                    messageInfo.put( "actor", logEvent.getUsername() );
                    messageInfo.put( "source", logEvent.getSourceAddress() );
                    messageInfo.put( "topic", logEvent.getTopic() );
                    messageInfo.put( "errorMessage", logEvent.getMessage() );

                    final String messageInfoStr = JsonUtil.serializeMap( messageInfo );
                    final AuditRecord auditRecord = new AuditRecordFactory( pwmApplication ).createSystemAuditRecord(
                            AuditEvent.FATAL_EVENT,
                            messageInfoStr
                    );
                    pwmApplication.getAuditManager().submit( auditRecord );
                }
            }
        }
        catch ( final Exception e2 )
        {
            //nothing can be done about it now;
        }
    }

    private void pushMessageToLog4j( final PwmLogEvent logEvent )
    {
        final String wrappedMessage = logEvent.getEnhancedMessage();
        final Throwable throwable = logEvent.getThrowable();
        final PwmLogLevel level = logEvent.getLevel();

        if ( initialized )
        {
            switch ( level )
            {
                case DEBUG:
                    log4jLogger.debug( wrappedMessage, throwable );
                    break;
                case ERROR:
                    log4jLogger.error( wrappedMessage, throwable );
                    break;
                case INFO:
                    log4jLogger.info( wrappedMessage, throwable );
                    break;
                case TRACE:
                    log4jLogger.trace( wrappedMessage, throwable );
                    break;
                case WARN:
                    log4jLogger.warn( wrappedMessage, throwable );
                    break;
                case FATAL:
                    log4jLogger.fatal( wrappedMessage, throwable );
                    break;

                default:
                    JavaHelper.unhandledSwitchStatement( level );
            }
        }
        else
        {
            System.err.println( logEvent.toLogString() );
        }
    }


    private static String convertErrorInformation( final ErrorInformation info )
    {
        return info.toDebugStr();
    }

    public void log( final PwmLogLevel level, final SessionLabel sessionLabel, final CharSequence message )
    {
        doLogEvent( level, sessionLabel, message, null );
    }

    public void log( final PwmLogLevel level, final SessionLabel sessionLabel, final Supplier<CharSequence> message )
    {
        doLogEvent( level, sessionLabel, message, null );
    }

    public void trace( final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.TRACE, null, message, null );
    }

    public void trace( final PwmRequest pwmRequest, final Supplier<CharSequence> message )
    {
        doPwmRequestLogEvent( PwmLogLevel.TRACE, pwmRequest, message, null );
    }

    public void trace( final SessionLabel sessionLabel, final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.TRACE, sessionLabel, message, null );
    }

    public void trace( final Supplier<CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.TRACE, null, message, exception );
    }

    public void debug( final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.DEBUG, null, message, null );
    }

    public void debug( final PwmRequest pwmRequest, final Supplier<CharSequence> message )
    {
        doPwmRequestLogEvent( PwmLogLevel.DEBUG, pwmRequest, message, null );
    }

    public void debug( final PwmRequest pwmRequest, final ErrorInformation errorInformation )
    {
        doPwmRequestLogEvent( PwmLogLevel.DEBUG, pwmRequest, () -> convertErrorInformation( errorInformation ), null );
    }

    public void debug( final SessionLabel sessionLabel, final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.DEBUG, sessionLabel, message, null );
    }

    public void debug( final SessionLabel sessionLabel, final ErrorInformation errorInformation )
    {
        doLogEvent( PwmLogLevel.DEBUG, sessionLabel, convertErrorInformation( errorInformation ), null );
    }

    public void debug( final Supplier<CharSequence> message, final Throwable exception )
    {
        doPwmRequestLogEvent( PwmLogLevel.DEBUG, null, message, exception );
    }

    public void info( final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.INFO, null, message, null );
    }

    public void info( final PwmRequest pwmRequest, final Supplier<CharSequence> message )
    {
        doPwmRequestLogEvent( PwmLogLevel.INFO, pwmRequest, message, null );
    }

    public void info( final PwmRequest pwmRequest, final ErrorInformation errorInformation )
    {
        doPwmRequestLogEvent( PwmLogLevel.INFO, pwmRequest, () -> convertErrorInformation( errorInformation ), null );
    }

    public void info( final SessionLabel sessionLabel, final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.INFO, sessionLabel, message, null );
    }

    public void info( final Supplier<CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.INFO, null, message, exception );
    }

    public void error( final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.ERROR, null, message, null );
    }

    public void error( final PwmRequest pwmRequest, final Supplier<CharSequence> message, final Throwable exception )
    {
        doPwmRequestLogEvent( PwmLogLevel.ERROR, pwmRequest, message, exception );
    }

    public void error( final PwmRequest pwmRequest, final Supplier<CharSequence> message )
    {
        doPwmRequestLogEvent( PwmLogLevel.ERROR, pwmRequest, message, null );
    }

    public void error( final PwmRequest pwmRequest, final ErrorInformation errorInformation )
    {
        doPwmRequestLogEvent( PwmLogLevel.ERROR, pwmRequest, () -> convertErrorInformation( errorInformation ), null );
    }

    public void error( final ErrorInformation errorInformation )
    {
        doPwmRequestLogEvent( PwmLogLevel.ERROR, null, () -> convertErrorInformation( errorInformation ), null );
    }

    public void error( final SessionLabel sessionLabel, final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, message, null );
    }

    public void error( final SessionLabel sessionLabel, final ErrorInformation errorInformation )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, convertErrorInformation( errorInformation ), null );
    }

    public void error( final SessionLabel sessionLabel, final ErrorInformation errorInformation, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, convertErrorInformation( errorInformation ), exception );
    }

    public void error( final Supplier<CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.ERROR, null, message, exception );
    }

    public void error( final SessionLabel sessionLabel, final Supplier<CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, message, exception );
    }

    public void warn( final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.WARN, null, message, null );
    }

    public void warn( final PwmRequest pwmRequest, final Supplier<CharSequence> message )
    {
        doPwmRequestLogEvent( PwmLogLevel.WARN, pwmRequest, message, null );
    }

    public void warn( final PwmRequest pwmRequest, final Supplier<CharSequence> message, final Throwable exception )
    {
        doPwmRequestLogEvent( PwmLogLevel.WARN, pwmRequest, message, exception );
    }

    public void warn( final SessionLabel sessionLabel, final ErrorInformation errorInformation, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.ERROR, sessionLabel, convertErrorInformation( errorInformation ), exception );
    }

    public void warn( final SessionLabel sessionLabel, final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.WARN, sessionLabel, message, null );
    }

    public void warn( final Supplier<CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.WARN, null, message, exception );
    }

    public void fatal( final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.FATAL, null, message, null );
    }

    public void fatal( final SessionLabel sessionLabel, final Supplier<CharSequence> message )
    {
        doLogEvent( PwmLogLevel.FATAL, sessionLabel, message, null );
    }

    public void fatal( final Supplier<CharSequence> message, final Throwable exception )
    {
        doLogEvent( PwmLogLevel.FATAL, null, message, exception );
    }

    public Appendable asAppendable( final PwmLogLevel pwmLogLevel, final SessionLabel sessionLabel )
    {
        return new PwmLoggerAppendable( pwmLogLevel, sessionLabel );
    }

    private class PwmLoggerAppendable implements Appendable
    {
        private final PwmLogLevel logLevel;
        private final SessionLabel sessionLabel;

        private StringBuilder buffer = new StringBuilder();

        private PwmLoggerAppendable(
                final PwmLogLevel logLevel,
                final SessionLabel sessionLabel
        )
        {
            this.logLevel = logLevel;
            this.sessionLabel = sessionLabel;
        }

        @Override
        public Appendable append( final CharSequence csq )
                throws IOException
        {

            doAppend( csq );
            return this;
        }

        @Override
        public Appendable append(
                final CharSequence csq,
                final int start,
                final int end
        )
                throws IOException
        {
            doAppend( csq.subSequence( start, end ) );
            return this;
        }

        @Override
        public Appendable append( final char c )
                throws IOException
        {
            doAppend( String.valueOf( c ) );
            return this;
        }

        private synchronized void doAppend( final CharSequence charSequence )
        {
            buffer.append( charSequence );

            int length = buffer.indexOf( "\n" );
            while ( length > 0 )
            {
                final String line = buffer.substring( 0, length );
                buffer.delete( 0, +length + 1 );
                doLogEvent( logLevel, sessionLabel, line, null );
                length = buffer.indexOf( "\n" );
            }
        }
    }

    public static CharSequence removeUserDataFromString( final LoginInfoBean loginInfoBean, final CharSequence input )
            throws PwmUnrecoverableException
    {
        if ( input == null || loginInfoBean == null )
        {
            return input;
        }

        String returnString = input.toString();
        if ( loginInfoBean.getUserCurrentPassword() != null )
        {
            final String pwdStringValue = loginInfoBean.getUserCurrentPassword().getStringValue();
            if ( pwdStringValue != null && !pwdStringValue.isEmpty() && returnString.contains( pwdStringValue ) )
            {
                returnString = returnString.replace( pwdStringValue, PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT );
            }
        }

        return returnString;
    }

    public boolean isEnabled( final PwmLogLevel pwmLogLevel )
    {
        /*
        if ( minimumDbLogLevel != null )
        {
            final int compareValue = minimumDbLogLevel.compareTo( pwmLogLevel );
            final boolean dbLogLevelHigher =  compareValue <= 0;
            System.out.println( " dbLogLevelHigher=" + dbLogLevelHigher
                    + ", compareValue=" + compareValue
                    + " pwmLogLevel=" + pwmLogLevel
                    + ", minDbLevel=" + minimumDbLogLevel );
        }
        */

        return (
                log4jLogger != null
                        && log4jLogger.isEnabledFor( pwmLogLevel.getLog4jLevel() )
        )
                ||
                (
                        localDBLogger != null
                                && minimumDbLogLevel != null
                                && minimumDbLogLevel.compareTo( pwmLogLevel ) <= 0
                );
    }

    public static void disableAllLogging()
    {
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender( new NullAppender() );
        PwmLogger.markInitialized();
    }
}


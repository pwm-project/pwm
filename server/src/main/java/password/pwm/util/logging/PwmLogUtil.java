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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import lombok.Builder;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;

import java.io.File;
import java.io.Serializable;
import java.util.function.Function;
import java.util.function.Supplier;

class PwmLogUtil
{
    static void pushMessageToSlf4j( final Logger slf4jLogger, final PwmLogMessage logMessage )
    {
        if ( !slf4jLogger.isEnabledForLevel( logMessage.getLevel().getSlf4jLevel() ) )
        {
            return;
        }

        final PwmLogSettings.LogOutputMode logOutputMode = PwmLogManager.getPwmLogSettings().getLogOutputMode();
        final Supplier<String> messageSupplier = () -> logOutputMode.getMessageSupplier().apply( logMessage );

        slf4jLogger.makeLoggingEventBuilder( logMessage.getLevel().getSlf4jLevel() )
                .setCause( logMessage.getThrowable() )
                .addMarker( PwmLogbackMarker.singleton() )
                .log( messageSupplier );
    }

    static void captureFilteredLogEventsToAudit(
            final PwmApplication pwmApplication,
            final PwmLogMessage logEvent
    )
    {
        if ( logEvent.getLevel() != PwmLogLevel.FATAL )
        {
            return;
        }

        final boolean ignoreEvent = PwmError.auditIgnoredErrors().stream()
                .anyMatch( error -> logEvent.getEnhancedMessage().contains( String.valueOf( error.getErrorCode() ) ) );

        if ( !ignoreEvent )
        {
            final LogToAuditMessageInfo.LogToAuditMessageInfoBuilder messageInfoBuilder = LogToAuditMessageInfo.builder()
                    .level( logEvent.getLevel().toString() )
                    .errorMsg( logEvent.getEnhancedMessage() )
                    .topic( logEvent.getTopic() );

            final SessionLabel sessionLabel = logEvent.getSessionLabel();
            if ( sessionLabel != null )
            {

                messageInfoBuilder.actor( sessionLabel.getUsername() );
                messageInfoBuilder.source( sessionLabel.getSourceAddress() );
            }

            final LogToAuditMessageInfo messageInfo = messageInfoBuilder.build();
            final String messageInfoStr = JsonFactory.get().serialize( messageInfo );
            AuditServiceClient.submitSystemEvent( pwmApplication, SessionLabel.SYSTEM_LABEL, AuditEvent.FATAL_EVENT, messageInfoStr );
        }
    }

    /**
     * Init logback system directly from a known XML file.
     *
     * @param file file to load XML config from
     * @return true if successfully loaded and initialized loggers
     * @see <a href=" https://logback.qos.ch/manual/configuration.html#joranDirectly">Logback Docs</a>
     */
    public static boolean initLogbackFromXmlFile( final File file )
    {
        if ( !file.exists() )
        {
            return false;
        }

        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        try
        {
            final JoranConfigurator configurator = new JoranConfigurator();

            configurator.setContext( context );

            context.reset();
            configurator.doConfigure( file );

            return true;
        }
        catch ( final JoranException je )
        {
            context.reset();
            // StatusPrinter will handle this
        }

        StatusPrinter.printInCaseOfErrorsOrWarnings( context );

        return false;
    }

    static boolean ignorableLogEvent( final PwmLogMessage pwmLogMessage )
    {
        final SessionLabel sessionLabel = pwmLogMessage.getSessionLabel();

        if ( sessionLabel != null )
        {
            if ( sessionLabel.isRuntime() )
            {
                return !PwmLogManager.getPwmLogSettings().isRuntimeLoggingEnabled();
            }

            if ( sessionLabel.isHealth() )
            {
                return !PwmLogManager.getPwmLogSettings().isHealthLoggingEnabled();
            }
        }

        return false;
    }

    public static String removeUserDataFromString( final LoginInfoBean loginInfoBean, final String input )
            throws PwmUnrecoverableException
    {
        if ( input == null || loginInfoBean == null )
        {
            return input;
        }

        String returnString = input;
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

    static String createEnhancedMessage(
            final SessionLabel sessionLabel,
            final String message,
            final Throwable throwable,
            final TimeDuration duration
    )
    {
        final StringBuilder output = new StringBuilder();
        if ( sessionLabel != null )
        {
            output.append( sessionLabel.toDebugLabel() );
        }

        output.append( message );

        final String srcAddrString = sessionLabel == null ? null : sessionLabel.getSourceAddress();
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

        if ( throwable != null )
        {
            output.append( JavaHelper.throwableToString( throwable ) );
        }

        if ( duration != null )
        {
            output.append( " (" ).append( duration.asCompactString() ).append( ")" );
        }

        return output.toString();
    }


    @Value
    @Builder
    private static class LogToAuditMessageInfo implements Serializable
    {
        private final String level;
        private final String actor;
        private final String source;
        private final String topic;
        private final String errorMsg;
    }

    static class TraditionalMsgFunction implements Function<PwmLogMessage, String>
    {
        @Override
        public String apply( final PwmLogMessage pwmLogMessage )
        {
            return pwmLogMessage.getEnhancedMessage();
        }
    }

    static class JsonMsgFunction implements Function<PwmLogMessage, String>
    {
        @Override
        public String apply( final PwmLogMessage pwmLogMessage )
        {
            return pwmLogMessage.toLogEvent().toEncodedString();
        }
    }

}

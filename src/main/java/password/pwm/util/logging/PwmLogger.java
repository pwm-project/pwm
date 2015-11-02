/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.util.logging;

import org.apache.log4j.RollingFileAppender;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.LoginInfoBean;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.SystemAuditRecord;
import password.pwm.util.JsonUtil;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class PwmLogger {
// ------------------------------ FIELDS ------------------------------

    private static LocalDBLogger localDBLogger;
    private static PwmLogLevel minimumDbLogLevel;
    private static PwmApplication pwmApplication;
    private static RollingFileAppender fileAppender;
    private static boolean initialized;

    private final String name;
    private final org.apache.log4j.Logger log4jLogger;
    private final boolean localDBDisabled;

    public static void markInitialized() {
        initialized = true;
    }

    static void setPwmApplication(final PwmApplication pwmApplication) {
        PwmLogger.pwmApplication = pwmApplication;
        if (pwmApplication != null) {
            initialized = true;
        }
    }

    static void setLocalDBLogger(final PwmLogLevel minimumDbLogLevel, final LocalDBLogger localDBLogger) {
        PwmLogger.minimumDbLogLevel = minimumDbLogLevel;
        PwmLogger.localDBLogger = localDBLogger;
    }

    static void setFileAppender(final RollingFileAppender rollingFileAppender) {
        PwmLogger.fileAppender = rollingFileAppender;
    }

    public static PwmLogger forClass(final Class className) {
        return new PwmLogger(className.getName(), false);
    }

    public static PwmLogger getLogger(final String name) {
        return new PwmLogger(name, false);
    }

    public static PwmLogger forClass(
            final Class className,
            final boolean localDBDisabled
    ) {
        return new PwmLogger(className.getName(), localDBDisabled);
    }

    public static PwmLogger getLogger(final String name, final boolean localDBDisabled) {
        return new PwmLogger(name, localDBDisabled);
    }



// --------------------------- CONSTRUCTORS ---------------------------

    PwmLogger(final String name, final boolean localDBDisabled) {
        this.name = name;
        this.localDBDisabled = localDBDisabled;
        log4jLogger = org.apache.log4j.Logger.getLogger(name);
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getName() {
        return name;
    }

// -------------------------- OTHER METHODS --------------------------


    private void doPwmRequestLogEvent(final PwmLogLevel level, final PwmRequest pwmRequest, final Object message, final Throwable e)
    {
        final PwmSession pwmSession = pwmRequest != null ? pwmRequest.getPwmSession() : null;
        doPwmSessionLogEvent(level, pwmSession, message, e);
    }

    private void doPwmSessionLogEvent(final PwmLogLevel level, final PwmSession pwmSession, final Object message, final Throwable e)
    {
        final SessionLabel sessionLabel = pwmSession != null ? pwmSession.getLabel() : null;
        Object cleanedMessage = message;
        if (pwmSession != null && message != null) {
            try {
                cleanedMessage = PwmLogger.removeUserDataFromString(pwmSession.getLoginInfoBean(), message.toString());
            } catch (PwmUnrecoverableException e1) {
                /* can't be logged */
            }
        };
        doLogEvent(level, sessionLabel, cleanedMessage, e);
    }

    private void doLogEvent(final PwmLogLevel level, final SessionLabel sessionLabel, final Object message, final Throwable e)
    {
        final String topic = log4jLogger.getName();
        final PwmLogEvent logEvent = PwmLogEvent.createPwmLogEvent(new Date(), topic, message.toString(), sessionLabel,
                e, level);
        doLogEvent(logEvent);
    }

    private void doLogEvent(final PwmLogEvent logEvent)
    {
        pushMessageToLog4j(logEvent);

        try {

            if (!localDBDisabled && localDBLogger != null && minimumDbLogLevel != null) {
                if (logEvent.getLevel().compareTo(minimumDbLogLevel) >= 0) {
                    localDBLogger.writeEvent(logEvent);
                }
            }

            if (logEvent.getLevel() == PwmLogLevel.FATAL) {
                if (!logEvent.getMessage().contains("5039")) {
                    final Map<String,String> messageInfo = new HashMap<>();
                    messageInfo.put("level",logEvent.getLevel().toString());
                    messageInfo.put("actor",logEvent.getActor());
                    messageInfo.put("source",logEvent.getSource());
                    messageInfo.put("topic",logEvent.getTopic());
                    messageInfo.put("errorMessage",logEvent.getMessage());

                    final String messageInfoStr = JsonUtil.serializeMap(messageInfo);
                    pwmApplication.getAuditManager().submit(SystemAuditRecord.create(
                            AuditEvent.FATAL_EVENT,
                            messageInfoStr,
                            pwmApplication.getInstanceID()
                    ));
                }
            }
        } catch (Exception e2) {
            //nothing can be done about it now;
        }
    }

    private void pushMessageToLog4j(final PwmLogEvent logEvent) {
        final String wrappedMessage = logEvent.getEnhancedMessage();
        final Throwable throwable = logEvent.getThrowable();
        final PwmLogLevel level = logEvent.getLevel();

        if (initialized) {
            switch (level) {
                case DEBUG:
                    log4jLogger.debug(wrappedMessage, throwable);
                    break;
                case ERROR:
                    log4jLogger.error(wrappedMessage, throwable);
                    break;
                case INFO:
                    log4jLogger.info(wrappedMessage, throwable);
                    break;
                case TRACE:
                    log4jLogger.trace(wrappedMessage, throwable);
                    break;
                case WARN:
                    log4jLogger.warn(wrappedMessage, throwable);
                    break;
                case FATAL:
                    log4jLogger.fatal(wrappedMessage, throwable);
                    break;
            }
        } else {
            System.err.println(logEvent.toLogString());
        }
    }



    private static String convertErrorInformation(final ErrorInformation info) {
        return info.toDebugStr();
    }

    public void log(final PwmLogLevel level, final SessionLabel sessionLabel, final CharSequence message) {
        doLogEvent(level, sessionLabel, message, null);
    }

    public void trace(final CharSequence message) {
        doLogEvent(PwmLogLevel.TRACE, null, message, null);
    }

    public void trace(final PwmSession pwmSession, final CharSequence message) {
        doPwmSessionLogEvent(PwmLogLevel.TRACE, pwmSession, message, null);
    }

    public void trace(final PwmRequest pwmRequest, final CharSequence message) {
        doPwmRequestLogEvent(PwmLogLevel.TRACE, pwmRequest, message, null);
    }

    public void trace(final SessionLabel sessionLabel, final CharSequence message) {
        doLogEvent(PwmLogLevel.TRACE, sessionLabel, message, null);
    }

    public void trace(final CharSequence message, final Throwable exception) {
        doLogEvent(PwmLogLevel.TRACE, null, message, exception);
    }

    public void debug(final CharSequence message) {
        doLogEvent(PwmLogLevel.DEBUG, null, message, null);
    }

    public void debug(final PwmSession pwmSession, final CharSequence message) {
        doPwmSessionLogEvent(PwmLogLevel.DEBUG, pwmSession, message, null);
    }

    public void debug(final PwmSession pwmSession, final ErrorInformation errorInformation) {
        doPwmSessionLogEvent(PwmLogLevel.DEBUG, pwmSession, convertErrorInformation(errorInformation), null);
    }

    public void debug(final PwmRequest pwmRequest, final CharSequence message) {
        doPwmRequestLogEvent(PwmLogLevel.DEBUG, pwmRequest, message, null);
    }

    public void debug(final PwmRequest pwmRequest, final ErrorInformation errorInformation) {
        doPwmRequestLogEvent(PwmLogLevel.DEBUG, pwmRequest, convertErrorInformation(errorInformation), null);
    }

    public void debug(final SessionLabel sessionLabel, final CharSequence message) {
        doLogEvent(PwmLogLevel.DEBUG, sessionLabel, message, null);
    }

    public void debug(final SessionLabel sessionLabel, final ErrorInformation errorInformation) {
        doLogEvent(PwmLogLevel.DEBUG, sessionLabel, convertErrorInformation(errorInformation), null);
    }

    public void debug(final CharSequence message, final Throwable exception) {
        doPwmSessionLogEvent(PwmLogLevel.DEBUG, null, message, exception);
    }

    public void debug(final PwmSession pwmSession, final CharSequence message, final Throwable e) {
        doPwmSessionLogEvent(PwmLogLevel.DEBUG, pwmSession, message, e);
    }

    public void info(final CharSequence message) {
        doLogEvent(PwmLogLevel.INFO, null, message, null);
    }

    public void info(final PwmSession pwmSession, final CharSequence message) {
        doPwmSessionLogEvent(PwmLogLevel.INFO, pwmSession, message, null);
    }

    public void info(final PwmRequest pwmRequest, final CharSequence message) {
        doPwmRequestLogEvent(PwmLogLevel.INFO, pwmRequest, message, null);
    }

    public void info(final PwmRequest pwmRequest, final ErrorInformation errorInformation) {
        doPwmRequestLogEvent(PwmLogLevel.INFO, pwmRequest, convertErrorInformation(errorInformation), null);
    }

    public void info(final SessionLabel sessionLabel, final CharSequence message) {
        doLogEvent(PwmLogLevel.INFO, sessionLabel, message, null);
    }

    public void info(final CharSequence message, final Throwable exception) {
        doLogEvent(PwmLogLevel.INFO, null, message, exception);
    }

    public void error(final CharSequence message) {
        doLogEvent(PwmLogLevel.ERROR, null, message, null);
    }

    public void error(final PwmSession pwmSession, final CharSequence message) {
        doPwmSessionLogEvent(PwmLogLevel.ERROR, pwmSession, message, null);
    }

    public void error(final PwmSession pwmSession, final ErrorInformation errorInformation) {
        doPwmSessionLogEvent(PwmLogLevel.ERROR, pwmSession, convertErrorInformation(errorInformation), null);
    }

    public void error(final PwmRequest pwmRequest, final CharSequence message, final Throwable exception) {
        doPwmRequestLogEvent(PwmLogLevel.ERROR, pwmRequest, message, exception);
    }

    public void error(final PwmRequest pwmRequest, final CharSequence message) {
        doPwmRequestLogEvent(PwmLogLevel.ERROR, pwmRequest, message, null);
    }

    public void error(final PwmRequest pwmRequest, final ErrorInformation errorInformation) {
        doPwmRequestLogEvent(PwmLogLevel.ERROR, pwmRequest, convertErrorInformation(errorInformation), null);
    }

    public void error(final ErrorInformation errorInformation) {
        doPwmRequestLogEvent(PwmLogLevel.ERROR, null, convertErrorInformation(errorInformation), null);
    }

    public void error(final SessionLabel sessionLabel, final CharSequence message) {
        doLogEvent(PwmLogLevel.ERROR, sessionLabel, message, null);
    }

    public void error(final SessionLabel sessionLabel, final ErrorInformation errorInformation) {
        doLogEvent(PwmLogLevel.ERROR, sessionLabel, convertErrorInformation(errorInformation), null);
    }

    public void error(final CharSequence message, final Throwable exception) {
        doLogEvent(PwmLogLevel.ERROR, null, message, exception);
    }

    public void error(final SessionLabel sessionLabel, final CharSequence message, final Throwable exception) {
        doLogEvent(PwmLogLevel.ERROR, sessionLabel, message, exception);
    }

    public void error(final PwmSession pwmSession, final CharSequence message, final Throwable exception) {
        doPwmSessionLogEvent(PwmLogLevel.ERROR, pwmSession, message, exception);
    }

    public void warn(final CharSequence message) {
        doLogEvent(PwmLogLevel.WARN, null, message, null);
    }

    public void warn(final PwmSession pwmSession, final CharSequence message) {
        doPwmSessionLogEvent(PwmLogLevel.WARN, pwmSession, message, null);
    }

    public void warn(final PwmRequest pwmRequest, final CharSequence message) {
        doPwmRequestLogEvent(PwmLogLevel.WARN, pwmRequest, message, null);
    }

    public void warn(final SessionLabel sessionLabel, final CharSequence message) {
        doLogEvent(PwmLogLevel.WARN, sessionLabel, message, null);
    }

    public void warn(final PwmSession pwmSession, final ErrorInformation message) {
        doPwmSessionLogEvent(PwmLogLevel.WARN, pwmSession, convertErrorInformation(message), null);
    }

    public void warn(final CharSequence message, final Throwable exception) {
        doLogEvent(PwmLogLevel.WARN, null, message, exception);
    }

    public void warn(final PwmSession pwmSession, final CharSequence message, final Throwable exception) {
        doPwmSessionLogEvent(PwmLogLevel.WARN, pwmSession, message, exception);
    }

    public void warn(final PwmSession pwmSession, final ErrorInformation errorInformation, final Throwable exception) {
        doPwmSessionLogEvent(PwmLogLevel.WARN, pwmSession, convertErrorInformation(errorInformation), exception);
    }

    public void fatal(final CharSequence message) {
        doLogEvent(PwmLogLevel.FATAL, null, message, null);
    }

    public void fatal(final PwmSession pwmSession, final CharSequence message) {
        doPwmSessionLogEvent(PwmLogLevel.FATAL, pwmSession, message, null);
    }

    public void fatal(final SessionLabel sessionLabel, final CharSequence message) {
        doLogEvent(PwmLogLevel.FATAL, sessionLabel, message, null);
    }

    public void fatal(final Object message, final Throwable exception) {
        doLogEvent(PwmLogLevel.FATAL, null, message, exception);
    }

    public Appendable asAppendable(final PwmLogLevel pwmLogLevel, final SessionLabel sessionLabel) {
        return new PwmLoggerAppendable(pwmLogLevel, sessionLabel);
    }

    private class PwmLoggerAppendable implements Appendable {
        private final PwmLogLevel logLevel;
        private final SessionLabel sessionLabel;

        private StringBuilder buffer = new StringBuilder();

        private PwmLoggerAppendable(
                PwmLogLevel logLevel,
                SessionLabel sessionLabel
        )
        {
            this.logLevel = logLevel;
            this.sessionLabel = sessionLabel;
        }

        @Override
        public Appendable append(CharSequence csq)
                throws IOException
        {

            doAppend(csq);
            return this;
        }

        @Override
        public Appendable append(
                CharSequence csq,
                int start,
                int end
        )
                throws IOException
        {
            doAppend(csq.subSequence(start,end));
            return this;
        }

        @Override
        public Appendable append(char c)
                throws IOException
        {
            doAppend(String.valueOf(c));
            return this;
        }

        private synchronized void doAppend(CharSequence charSequence) {
            buffer.append(charSequence);

            int length = buffer.indexOf("\n");
            while (length > 0) {
                final String line = buffer.substring(0,length);
                buffer.delete(0, + length + 1);
                doLogEvent(logLevel, sessionLabel, line, null);
                length = buffer.indexOf("\n");
            }
        }
    }

    public static String removeUserDataFromString(final LoginInfoBean loginInfoBean, final String input)
            throws PwmUnrecoverableException
    {
        if (input == null || loginInfoBean == null) {
            return input;
        }

        String returnString = input;
        if (loginInfoBean.getUserCurrentPassword() != null) {
            final String pwdStringValue = loginInfoBean.getUserCurrentPassword().getStringValue();
            if (pwdStringValue != null && !pwdStringValue.isEmpty() && returnString.contains(pwdStringValue)) {
                returnString = returnString.replace(pwdStringValue, PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT);
            }
        }

        return returnString;
    }
}


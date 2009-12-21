/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.util;

import password.pwm.PwmSession;
import password.pwm.error.ErrorInformation;
import password.pwm.util.db.PwmDB;

import java.util.Date;

/**
 * @author Jason D. Rivard
 */
public class PwmLogger {
// ------------------------------ FIELDS ------------------------------

    private static PwmDBLogger pwmDBLogger;
    private final String name;
    private final org.apache.log4j.Logger log4jLogger;

// -------------------------- STATIC METHODS --------------------------

    public static PwmLogger getLogger(final Class className)
    {
        return new PwmLogger(className.getName());
    }

    public static PwmLogger getLogger(final String name)
    {
        return new PwmLogger(name);
    }

    public static PwmDBLogger initContextManager(final PwmDB pwmDB, final int maxEvents, final int maxAge) {
        PwmLogger.pwmDBLogger = new PwmDBLogger(pwmDB, maxEvents, maxAge);
        return PwmLogger.pwmDBLogger;
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public PwmLogger(final String name)
    {
        this.name = name;
        log4jLogger = org.apache.log4j.Logger.getLogger(name);
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getName()
    {
        return name;
    }

// -------------------------- OTHER METHODS --------------------------

    private static String wrapWithSessionInfo(final PwmSession pwmSession, final String message)
    {
        if (pwmSession == null) {
            return message;
        }

        final StringBuilder output = new StringBuilder();
        output.append("{");
        output.append(pwmSession.getSessionLabel());
        output.append("} ");
        output.append(message);

        final StringBuilder srcStr = new StringBuilder();
        srcStr.append(" [");
        srcStr.append(makeSrcString(pwmSession));
        srcStr.append("]");

        final int firstCR = output.indexOf("\n");
        if (firstCR == -1) {
            output.append(srcStr);
        } else {
            output.insert(firstCR, srcStr);
        }

        return output.toString();
    }

    private static String makeSrcString(final PwmSession pwmSession)
    {
        try {
            final StringBuilder from = new StringBuilder();
            {
                final String srcAddress = pwmSession.getSessionStateBean().getSrcAddress();
                final String srcHostname = pwmSession.getSessionStateBean().getSrcHostname();

                if (srcAddress != null) {
                    from.append(srcAddress);
                    if (!srcAddress.equals(srcHostname)) {
                        from.append("/");
                        from.append(srcHostname);
                    }
                }
            }
            return from.toString();
        } catch (NullPointerException e) {
            return "";
        }
    }

    private static String makeActorString(final PwmSession pwmSession) {
        if (pwmSession != null) {
            if (pwmSession.getSessionStateBean().isAuthenticated()) {
                final String userID = pwmSession.getUserInfoBean().getUserID();
                if (userID != null && userID.length() > 0) {
                    return userID;
                }
            }
        }
        return "";
    }

    public void debug(final Object message)
    {
        doLogEvent(PwmLogLevel.DEBUG, null, message, null);
    }

    public void debug(final PwmSession pwmSession, final Object message)
    {
        doLogEvent(PwmLogLevel.DEBUG, pwmSession, message, null);
    }

    public void debug(final Object message, final Throwable exception)
    {
        doLogEvent(PwmLogLevel.DEBUG, null, message, exception);
    }

    public void debug(final PwmSession pwmSession, final Object message, final Throwable e)
    {
        doLogEvent(PwmLogLevel.DEBUG, pwmSession, message, e);
    }

    private void doLogEvent(final PwmLogLevel level, final PwmSession pwmSession, final Object message, final Throwable e) {
        switch (level) {
            case DEBUG:
                log4jLogger.debug(wrapWithSessionInfo(pwmSession, message.toString()), e);
                break;
            case ERROR:
                log4jLogger.error(wrapWithSessionInfo(pwmSession, message.toString()), e);
                break;
            case INFO:
                log4jLogger.info(wrapWithSessionInfo(pwmSession, message.toString()), e);
                break;
            case TRACE:
                log4jLogger.trace(wrapWithSessionInfo(pwmSession, message.toString()), e);
                break;
            case WARN:
                log4jLogger.warn(wrapWithSessionInfo(pwmSession, message.toString()), e);
                break;
            case FATAL:
                log4jLogger.fatal(wrapWithSessionInfo(pwmSession, message.toString()), e);
                break;
        }

        if (pwmDBLogger != null) {
            final PwmLogEvent logEvent = new PwmLogEvent(
                    new Date(),
                    this.getName(),
                    message.toString(),
                    makeSrcString(pwmSession),
                    makeActorString(pwmSession),
                    e,
                    level
            );
            pwmDBLogger.writeEvent(logEvent);
        }
    }


    public void error(final Object message)
    {
        doLogEvent(PwmLogLevel.ERROR, null, message, null);
    }

    public void error(final PwmSession pwmSession, final Object message)
    {
        doLogEvent(PwmLogLevel.ERROR, pwmSession, message, null);
    }

    public void error(final Object message, final Throwable exception)
    {
        doLogEvent(PwmLogLevel.ERROR, null, message, exception);
    }

    public void error(final PwmSession pwmSession, final Object message, final Throwable exception)
    {
        doLogEvent(PwmLogLevel.ERROR, pwmSession, message, exception);
    }

    public void fatal(final Object message)
    {
        doLogEvent(PwmLogLevel.FATAL, null, message, null);
    }

    public void fatal(final PwmSession pwmSession, final Object message)
    {
        doLogEvent(PwmLogLevel.FATAL, pwmSession, message, null);
    }

    public void fatal(final Object message, final Throwable exception)
    {
        doLogEvent(PwmLogLevel.FATAL, null, message, exception);
    }

    public void info(final Object message)
    {
        doLogEvent(PwmLogLevel.INFO, null, message, null);
    }

    public void info(final PwmSession pwmSession, final Object message)
    {
        doLogEvent(PwmLogLevel.INFO, pwmSession, message, null);
    }

    public void info(final Object message, final Throwable exception)
    {
        doLogEvent(PwmLogLevel.INFO, null, message, exception);
    }

    public void trace(final String message)
    {
        doLogEvent(PwmLogLevel.TRACE, null, message, null);
    }

    public void trace(final PwmSession pwmSession, final Object message)
    {
        doLogEvent(PwmLogLevel.TRACE, pwmSession, message, null);
    }

    public void trace(final String message, final Throwable exception)
    {
        doLogEvent(PwmLogLevel.TRACE, null, message, exception);
    }

    public void warn(final Object message)
    {
        doLogEvent(PwmLogLevel.WARN, null, message, null);
    }

    public void warn(final PwmSession pwmSession, final Object message)
    {
        doLogEvent(PwmLogLevel.WARN, pwmSession, message, null);
    }

    public void warn(final PwmSession pwmSession, final ErrorInformation message)
    {
        doLogEvent(PwmLogLevel.WARN, pwmSession, convertErrorInformation(message), null);
    }

    private static String convertErrorInformation(final ErrorInformation info) {
        return info.toDebugStr();
    }

    public void warn(final Object message, final Throwable exception)
    {
        doLogEvent(PwmLogLevel.WARN, null, message, exception);
    }

    public void warn(final PwmSession pwmSession, final Object message, final Throwable exception)
    {
        doLogEvent(PwmLogLevel.WARN, pwmSession, message, exception);
    }

    public void warn(final PwmSession pwmSession, final ErrorInformation errorInformation, final Throwable exception)
    {
        doLogEvent(PwmLogLevel.WARN, pwmSession, convertErrorInformation(errorInformation), exception);
    }

// -------------------------- INNER CLASSES --------------------------

}


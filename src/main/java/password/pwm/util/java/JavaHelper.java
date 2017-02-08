/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.util.java;

import org.apache.commons.io.IOUtils;
import password.pwm.PwmConstants;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class JavaHelper {

    private static final PwmLogger LOGGER = PwmLogger.forClass(JavaHelper.class);

    private JavaHelper() {
    }


    /**
     * Convert a byte[] array to readable string format. This makes the "hex" readable
     *
     * @param in byte[] buffer to convert to string format
     * @return result String buffer in String format
     */
    public static String byteArrayToHexString(final byte[] in) {
        final String[] pseudo = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};

        if (in == null || in.length <= 0) {
            return "";
        }

        final StringBuilder out = new StringBuilder(in.length * 2);

        for (final byte b : in) {
            byte ch = (byte) (b & 0xF0);    // strip off high nibble
            ch = (byte) (ch >>> 4);         // shift the bits down
            ch = (byte) (ch & 0x0F);        // must do this is high order bit is on!
            out.append(pseudo[(int) ch]);   // convert the nibble to a String Character
            ch = (byte) (b & 0x0F);         // strip off low nibble
            out.append(pseudo[(int) ch]);   // convert the nibble to a String Character
        }

        return out.toString();
    }

    /**
     * Pause the calling thread the specified amount of time.
     *
     * @param sleepTimeMS - a time duration in milliseconds
     * @return time actually spent sleeping
     */
    public static long pause(final long sleepTimeMS) {
        final long startTime = System.currentTimeMillis();
        do {
            try {
                final long sleepTime = sleepTimeMS - (System.currentTimeMillis() - startTime);
                Thread.sleep(sleepTime > 0 ? sleepTime : 5);
            } catch (InterruptedException e) {
                //who cares
            }
        } while ((System.currentTimeMillis() - startTime) < sleepTimeMS);

        return System.currentTimeMillis() - startTime;
    }

    public static long pause(
            final long sleepTimeMS,
            final long predicateCheckIntervalMS,
            final Predicate predicate
    ) {
        final long startTime = System.currentTimeMillis();
        final long pauseTime = Math.max(sleepTimeMS, predicateCheckIntervalMS);
        while ((System.currentTimeMillis() - startTime) < sleepTimeMS) {
            JavaHelper.pause(pauseTime);
            if (predicate.test(null)) {
                break;
            }
        }

        return System.currentTimeMillis() - startTime;
    }

    public static String binaryArrayToHex(final byte[] buf) {
        final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();
        final char[] chars = new char[2 * buf.length];
        for (int i = 0; i < buf.length; ++i) {
            chars[2 * i] = HEX_CHARS[(buf[i] & 0xF0) >>> 4];
            chars[2 * i + 1] = HEX_CHARS[buf[i] & 0x0F];
        }
        return new String(chars);
    }

    public static Date nextZuluZeroTime() {
        final Calendar nextZuluMidnight = GregorianCalendar.getInstance(TimeZone.getTimeZone("Zulu"));
        nextZuluMidnight.set(Calendar.HOUR_OF_DAY,0);
        nextZuluMidnight.set(Calendar.MINUTE,0);
        nextZuluMidnight.set(Calendar.SECOND, 0);
        nextZuluMidnight.add(Calendar.HOUR, 24);
        return nextZuluMidnight.getTime();
    }

    public static <E extends Enum<E>> List<E> readEnumListFromStringCollection(final Class<E> enumClass, final Collection<String> inputs ) {
        final List<E> returnList = new ArrayList<E>();
        for (final String input : inputs) {
            final E item = readEnumFromString(enumClass, null, input);
            if (item != null) {
                returnList.add(item);
            }
        }
        return Collections.unmodifiableList(returnList);
    }

    public static <E extends Enum<E>> E readEnumFromString(final Class<E> enumClass, final E defaultValue, final String input) {
        if (input == null) {
            return defaultValue;
        }

        try {
            final Method valueOfMethod = enumClass.getMethod("valueOf", String.class);
            try {
                final Object result = valueOfMethod.invoke(null, input);
                return (E) result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        } catch (IllegalArgumentException e) {
            /* noop */
            //LOGGER.trace("input=" + input + " does not exist in enumClass=" + enumClass.getSimpleName());
        } catch (Throwable e) {
            LOGGER.warn("unexpected error translating input=" + input + " to enumClass=" + enumClass.getSimpleName() + ", error: " + e.getMessage());
        }

        return defaultValue;
    }

    public static String throwableToString(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /**
     * Converts an exception to a string message.  Handles cases where the message in the exception is null
     * and/or there are multiple nested cause exceptions.
     * @param e The exception to convert to a string
     * @return A string containing any meaningful extractable cause information, suitable for debugging.
     */
    public static String readHostileExceptionMessage(final Throwable e) {
        String errorMsg = e.getClass().getName();
        if (e.getMessage() != null) {
            errorMsg += ": " + e.getMessage();
        }

        Throwable cause = e.getCause();
        int safetyCounter = 0;
        while (cause != null && safetyCounter < 10) {
            safetyCounter++;
            errorMsg += ", cause:" + cause.getClass().getName();
            if (cause.getMessage() != null) {
                errorMsg += ": " + cause.getMessage();
            }
            cause = cause.getCause();
        }

        return errorMsg;
    }

    public static <E extends Enum<E>> boolean enumArrayContainsValue(final E[] enumArray, final E enumValue) {
        return !(enumArray == null || enumArray.length == 0) && Arrays.asList(enumArray).contains(enumValue);
    }

    public static void unhandledSwitchStatement(final Object switchParameter) {
        final String className = switchParameter == null
                ? "unknown - see stack trace"
                : switchParameter.getClass().getName();

        final String paramValue = switchParameter == null
                ? "unknown"
                : switchParameter.toString();

        final String errorMsg = "unhandled switch statement on parameter class=" + className + ", value=" + paramValue;
        final UnsupportedOperationException exception = new UnsupportedOperationException(errorMsg);
        LOGGER.warn(errorMsg, exception);
        throw exception;
    }

    public static long copyWhilePredicate(final InputStream input, final OutputStream output, final Predicate predicate)
            throws IOException
    {
        final int bufferSize = 4 * 1024;
        final byte[] buffer = new byte[bufferSize];
        long bytesCopied;
        long totalCopied = 0;
        do {
            bytesCopied = IOUtils.copyLarge(input, output, 0 , bufferSize, buffer);
            if (bytesCopied > 0) {
                totalCopied += bytesCopied;
            }
            if (!predicate.test(null)) {
                return totalCopied;
            }
        } while (bytesCopied > 0);
        return totalCopied;
    }

    public static String toIsoDate(final Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    public static String toIsoDate(final Date date) {
        return date == null ? "" : PwmConstants.DEFAULT_DATETIME_FORMAT.format(date);
    }

    public static boolean closeAndWaitExecutor(final ExecutorService executor, final TimeDuration timeDuration)
    {
        if (executor == null) {
            return true;
        }

        executor.shutdown();
        try {
            return executor.awaitTermination(timeDuration.getTotalMilliseconds(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("unexpected error shutting down executor service " + executor.getClass().toString() + " error: " + e.getMessage());
        }
        return false;
    }

}

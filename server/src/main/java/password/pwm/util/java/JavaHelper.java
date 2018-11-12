/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.IOUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.http.ContextManager;
import password.pwm.util.logging.PwmLogger;

import javax.annotation.CheckReturnValue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class JavaHelper
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( JavaHelper.class );

    private JavaHelper( )
    {
    }

    /**
     * Convert a byte[] array to readable string format. This makes the "hex" readable.
     *
     * @param in byte[] buffer to convert to string format
     * @return result String buffer in String format
     */
    @SuppressFBWarnings( "ICAST_QUESTIONABLE_UNSIGNED_RIGHT_SHIFT" )
    public static String byteArrayToHexString( final byte[] in )
    {
        final String[] pseudo =
                {
                        "0",
                        "1",
                        "2",
                        "3",
                        "4",
                        "5",
                        "6",
                        "7",
                        "8",
                        "9",
                        "A",
                        "B",
                        "C",
                        "D",
                        "E",
                        "F",
                };

        if ( in == null || in.length <= 0 )
        {
            return "";
        }

        final StringBuilder out = new StringBuilder( in.length * 2 );

        for ( final byte b : in )
        {
            // strip off high nibble
            byte ch = ( byte ) ( b & 0xF0 );

            // shift the bits down
            ch = ( byte ) ( ch >>> 4 );

            // must do this is high order bit is on!
            ch = ( byte ) ( ch & 0x0F );

            // convert the nibble to a String Character
            out.append( pseudo[ ( int ) ch ] );

            // strip off low nibble
            ch = ( byte ) ( b & 0x0F );

            // convert the nibble to a String Character
            out.append( pseudo[ ( int ) ch ] );
        }

        return out.toString();
    }

    /**
     * Pause the calling thread the specified amount of time.
     *
     * @param sleepTimeMS - a time duration in milliseconds
     * @return time actually spent sleeping
     */
    @CheckReturnValue( when = javax.annotation.meta.When.NEVER )
    public static long pause( final long sleepTimeMS )
    {
        final long startTime = System.currentTimeMillis();
        final long sliceTime = Math.max( 5, sleepTimeMS / 10 );
        do
        {
            try
            {
                final long sleepTime = sleepTimeMS - ( System.currentTimeMillis() - startTime );
                Thread.sleep( Math.min( sleepTime, sliceTime ) );
            }
            catch ( InterruptedException e )
            {
                // ignore
            }
        }
        while ( ( System.currentTimeMillis() - startTime ) < sleepTimeMS );

        return System.currentTimeMillis() - startTime;
    }

    public static long pause(
            final long sleepTimeMS,
            final long predicateCheckIntervalMS,
            final Predicate predicate
    )
    {
        final long startTime = System.currentTimeMillis();
        final long pauseTime = Math.min( sleepTimeMS, predicateCheckIntervalMS );
        while ( ( System.currentTimeMillis() - startTime ) < sleepTimeMS )
        {
            JavaHelper.pause( pauseTime );
            if ( predicate.test( null ) )
            {
                break;
            }
        }

        return System.currentTimeMillis() - startTime;
    }

    public static String binaryArrayToHex( final byte[] buf )
    {
        final char[] hexChars = "0123456789ABCDEF".toCharArray();
        final char[] chars = new char[ 2 * buf.length ];
        for ( int i = 0; i < buf.length; ++i )
        {
            chars[ 2 * i ] = hexChars[ ( buf[ i ] & 0xF0 ) >>> 4 ];
            chars[ 2 * i + 1 ] = hexChars[ buf[ i ] & 0x0F ];
        }
        return new String( chars );
    }

    public static Instant nextZuluZeroTime( )
    {
        final Calendar nextZuluMidnight = GregorianCalendar.getInstance( TimeZone.getTimeZone( "Zulu" ) );
        nextZuluMidnight.set( Calendar.HOUR_OF_DAY, 0 );
        nextZuluMidnight.set( Calendar.MINUTE, 0 );
        nextZuluMidnight.set( Calendar.SECOND, 0 );
        nextZuluMidnight.add( Calendar.HOUR, 24 );
        return nextZuluMidnight.getTime().toInstant();
    }

    public static <E extends Enum<E>> List<E> readEnumListFromStringCollection( final Class<E> enumClass, final Collection<String> inputs )
    {
        final List<E> returnList = new ArrayList<E>();
        for ( final String input : inputs )
        {
            final E item = readEnumFromString( enumClass, null, input );
            if ( item != null )
            {
                returnList.add( item );
            }
        }
        return Collections.unmodifiableList( returnList );
    }

    public static <E extends Enum<E>> E readEnumFromString( final Class<E> enumClass, final E defaultValue, final String input )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return defaultValue;
        }

        if ( enumClass == null || !enumClass.isEnum() )
        {
            return defaultValue;
        }

        try
        {
            return Enum.valueOf( enumClass, input );
        }
        catch ( IllegalArgumentException e )
        {
            /* noop */
            //LOGGER.trace("input=" + input + " does not exist in enumClass=" + enumClass.getSimpleName());
        }
        catch ( Throwable e )
        {
            LOGGER.warn( "unexpected error translating input=" + input + " to enumClass=" + enumClass.getSimpleName() + ", error: " + e.getMessage() );
        }

        return defaultValue;
    }

    public static String throwableToString( final Throwable throwable )
    {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter( sw );
        throwable.printStackTrace( pw );
        pw.flush();
        return sw.toString();
    }

    /**
     * Converts an exception to a string message.  Handles cases where the message in the exception is null
     * and/or there are multiple nested cause exceptions.
     *
     * @param e The exception to convert to a string
     * @return A string containing any meaningful extractable cause information, suitable for debugging.
     */
    public static String readHostileExceptionMessage( final Throwable e )
    {
        final StringBuilder errorMsg = new StringBuilder();
        errorMsg.append( e.getClass().getName() );
        if ( e.getMessage() != null )
        {
            errorMsg.append( ": " ).append( e.getMessage() );
        }

        Throwable cause = e.getCause();
        int safetyCounter = 0;
        while ( cause != null && safetyCounter < 10 )
        {
            safetyCounter++;
            errorMsg.append( ", cause:" ).append( cause.getClass().getName() );
            if ( cause.getMessage() != null )
            {
                errorMsg.append( ": " ).append( cause.getMessage() );
            }
            cause = cause.getCause();
        }

        return errorMsg.toString();
    }

    public static <E extends Enum<E>> boolean enumArrayContainsValue( final E[] enumArray, final E enumValue )
    {
        return !( enumArray == null || enumArray.length == 0 ) && Arrays.asList( enumArray ).contains( enumValue );
    }

    public static void unhandledSwitchStatement( final Object switchParameter )
    {
        final String className = switchParameter == null
                ? "unknown - see stack trace"
                : switchParameter.getClass().getName();

        final String paramValue = switchParameter == null
                ? "unknown"
                : switchParameter.toString();

        final String errorMsg = "unhandled switch statement on parameter class=" + className + ", value=" + paramValue;
        final UnsupportedOperationException exception = new UnsupportedOperationException( errorMsg );
        LOGGER.warn( errorMsg, exception );
        throw exception;
    }

    public static long copy( final InputStream input, final OutputStream output )
            throws IOException
    {
        final int bufferSize = 4 * 1024;
        final byte[] buffer = new byte[ bufferSize ];
        return IOUtils.copyLarge( input, output, 0, -1, buffer );
    }

    public static long copyWhilePredicate(
            final InputStream input,
            final OutputStream output,
            final Predicate<Long> predicate
    )
            throws IOException
    {
        return copyWhilePredicate( input, output, 4 * 1024, predicate, null );
    }

    public static long copyWhilePredicate(
            final InputStream input,
            final OutputStream output,
            final int bufferSize,
            final Predicate<Long> predicate,
            final ConditionalTaskExecutor condtionalTaskExecutor
    )
            throws IOException
    {
        final byte[] buffer = new byte[ bufferSize ];
        long bytesCopied;
        long totalCopied = 0;
        do
        {
            bytesCopied = IOUtils.copyLarge( input, output, 0, bufferSize, buffer );
            if ( bytesCopied > 0 )
            {
                totalCopied += bytesCopied;
            }
            if ( condtionalTaskExecutor != null )
            {
                condtionalTaskExecutor.conditionallyExecuteTask();
            }
            if ( !predicate.test( bytesCopied ) )
            {
                return totalCopied;
            }
        }
        while ( bytesCopied > 0 );
        return totalCopied;
    }

    public static String toIsoDate( final Instant instant )
    {
        return instant == null ? "" : instant.truncatedTo( ChronoUnit.SECONDS ).toString();
    }

    public static String toIsoDate( final Date date )
    {
        if ( date == null )
        {
            return "";
        }

        final DateFormat dateFormat = new SimpleDateFormat(
                PwmConstants.DEFAULT_DATETIME_FORMAT_STR,
                PwmConstants.DEFAULT_LOCALE
        );

        dateFormat.setTimeZone( PwmConstants.DEFAULT_TIMEZONE );

        return dateFormat.format( date );
    }

    public static Instant parseIsoToInstant( final String input )
    {
        return Instant.parse( input );
    }

    @CheckReturnValue( when = javax.annotation.meta.When.NEVER )
    public static boolean closeAndWaitExecutor( final ExecutorService executor, final TimeDuration timeDuration )
    {
        if ( executor == null )
        {
            return true;
        }

        executor.shutdown();
        try
        {
            return executor.awaitTermination( timeDuration.asMillis(), TimeUnit.MILLISECONDS );
        }
        catch ( InterruptedException e )
        {
            LOGGER.warn( "unexpected error shutting down executor service " + executor.getClass().toString() + " error: " + e.getMessage() );
        }
        return false;
    }

    public static String makeThreadName( final PwmApplication pwmApplication, final Class theClass )
    {
        String instanceName = "-";
        if ( pwmApplication != null && pwmApplication.getInstanceID() != null )
        {
            instanceName = pwmApplication.getInstanceID();
        }

        return PwmConstants.PWM_APP_NAME + "-" + instanceName + "-" + theClass.getSimpleName();
    }

    public static Properties newSortedProperties( )
    {
        return new Properties()
        {
            public synchronized Enumeration<Object> keys( )
            {
                return Collections.enumeration( new TreeSet<>( super.keySet() ) );
            }
        };
    }

    public static ThreadFactory makePwmThreadFactory( final String namePrefix, final boolean daemon )
    {
        return new ThreadFactory()
        {
            private final ThreadFactory realThreadFactory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread( final Runnable r )
            {
                final Thread t = realThreadFactory.newThread( r );
                t.setDaemon( daemon );
                if ( namePrefix != null )
                {
                    final String newName = namePrefix + t.getName();
                    t.setName( newName );
                }
                return t;
            }
        };
    }

    public static Collection<Method> getAllMethodsForClass( final Class clazz )
    {
        final LinkedHashSet<Method> methods = new LinkedHashSet<>();

        // add local methods;
        methods.addAll( Arrays.asList( clazz.getDeclaredMethods() ) );

        final Class superClass = clazz.getSuperclass();
        if ( superClass != null )
        {
            methods.addAll( getAllMethodsForClass( superClass ) );
        }

        return Collections.unmodifiableSet( methods );
    }

    public static CSVPrinter makeCsvPrinter( final OutputStream outputStream )
            throws IOException
    {
        return new CSVPrinter( new OutputStreamWriter( outputStream, PwmConstants.DEFAULT_CHARSET ), PwmConstants.DEFAULT_CSV_FORMAT );
    }

    public static ScheduledExecutorService makeSingleThreadExecutorService(
            final PwmApplication pwmApplication,
            final Class clazz
    )
    {
        return Executors.newSingleThreadScheduledExecutor(
                makePwmThreadFactory(
                        JavaHelper.makeThreadName( pwmApplication, clazz ) + "-",
                        true
                ) );
    }

    public static ExecutorService makeBackgroundExecutor(
            final PwmApplication pwmApplication,
            final Class clazz
    )
    {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                JavaHelper.makePwmThreadFactory(
                        JavaHelper.makeThreadName( pwmApplication, clazz ) + "-",
                        true
                ) );
        executor.allowCoreThreadTimeOut( true );
        return executor;
    }
    
    /**
     * Copy of {@link ThreadInfo#toString()} but with the MAX_FRAMES changed from 8 to 256.
     * @param threadInfo thread information
     * @return a stacktrace string with newline formatting
     */
    public static String threadInfoToString( final ThreadInfo threadInfo )
    {
        final int maxFrames = 256;
        final StringBuilder sb = new StringBuilder( "\"" + threadInfo.getThreadName() + "\""
                + " Id=" + threadInfo.getThreadId() + " "
                + threadInfo.getThreadState() );
        if ( threadInfo.getLockName() != null )
        {
            sb.append( " on " + threadInfo.getLockName() );
        }
        if ( threadInfo.getLockOwnerName() != null )
        {
            sb.append( " owned by \"" + threadInfo.getLockOwnerName()
                    + "\" Id=" + threadInfo.getLockOwnerId() );
        }
        if ( threadInfo.isSuspended() )
        {
            sb.append( " (suspended)" );
        }
        if ( threadInfo.isInNative() )
        {
            sb.append( " (in native)" );
        }
        sb.append( '\n' );

        int counter = 0;

        for (; counter < threadInfo.getStackTrace().length && counter < maxFrames; counter++ )
        {
            final StackTraceElement ste = threadInfo.getStackTrace()[ counter ];
            sb.append( "\tat " ).append( ste.toString() );
            sb.append( '\n' );
            if ( counter == 0 && threadInfo.getLockInfo() != null )
            {
                final Thread.State ts = threadInfo.getThreadState();
                switch ( ts )
                {
                    case BLOCKED:
                        sb.append( "\t-  blocked on " + threadInfo.getLockInfo() );
                        sb.append( '\n' );
                        break;
                    case WAITING:
                        sb.append( "\t-  waiting on " + threadInfo.getLockInfo() );
                        sb.append( '\n' );
                        break;
                    case TIMED_WAITING:
                        sb.append( "\t-  waiting on " + threadInfo.getLockInfo() );
                        sb.append( '\n' );
                        break;
                    default:
                }
            }

            for ( MonitorInfo mi : threadInfo.getLockedMonitors() )
            {
                if ( mi.getLockedStackDepth() == counter )
                {
                    sb.append( "\t-  locked " + mi );
                    sb.append( '\n' );
                }
            }
        }
        if ( counter < threadInfo.getStackTrace().length )
        {
            sb.append( "\t..." );
            sb.append( '\n' );
        }

        final LockInfo[] locks = threadInfo.getLockedSynchronizers();
        if ( locks.length > 0 )
        {
            sb.append( "\n\tNumber of locked synchronizers = " + locks.length );
            sb.append( '\n' );
            for ( LockInfo li : locks )
            {
                sb.append( "\t- " + li );
                sb.append( '\n' );
            }
        }
        sb.append( '\n' );
        return sb.toString();
    }

    public static String readEulaText( final ContextManager contextManager, final String filename )
            throws IOException
    {
        final String path = PwmConstants.URL_PREFIX_PUBLIC + "/resources/text/" + filename;
        final InputStream inputStream = contextManager.getResourceAsStream( path );
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        copyWhilePredicate( inputStream, byteArrayOutputStream, o -> true );
        return byteArrayOutputStream.toString( PwmConstants.DEFAULT_CHARSET.name() );
    }

    public static boolean isEmpty( final Collection collection )
    {
        return collection == null ? true : collection.isEmpty();
    }

    public static boolean isEmpty( final Map map )
    {
        return map == null || map.isEmpty();
    }

    public static int rangeCheck( final int min, final int max, final int value )
    {
        return (int) rangeCheck( (long) min, (long) max, (long) value );
    }

    public static long rangeCheck( final long min, final long max, final long value )
    {
        if ( min > max )
        {
            throw new IllegalArgumentException( "min range is greater than max range" );
        }
        if ( max < min )
        {
            throw new IllegalArgumentException( "max range is less than min range" );
        }
        long returnValue = value;
        if ( value < min )
        {
            returnValue = min;
        }
        if ( value > max )
        {
            returnValue = max;
        }
        return returnValue;
    }

    public static <T> Optional<T> extractNestedExceptionType( final Exception inputException, final Class<T> exceptionType )
    {
        if ( inputException == null )
        {
            return Optional.empty();
        }

        if ( inputException.getClass().isInstance( exceptionType ) )
        {
            return Optional.of( ( T ) inputException );
        }

        Throwable nextException = inputException.getCause();
        while ( nextException != null )
        {
            if ( nextException.getClass().isInstance( exceptionType ) )
            {
                return Optional.of( ( T ) inputException );
            }

            nextException = nextException.getCause();
        }

        return Optional.empty();
    }

    /**
     * Very naive implementation to get a rough order estimate of object memory size, used for debug
     * purposes only.
     * @param object object to be analyzed
     * @return size of object (very rough estimate)
     */
    public static long sizeof( final Serializable object )
    {
        try ( ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream() )
        {
            final ObjectOutputStream out = new ObjectOutputStream( byteArrayOutputStream );
            out.writeObject( object );
            out.flush();
            return byteArrayOutputStream.toByteArray().length;
        }
        catch ( IOException e )
        {
            LOGGER.debug( "exception while estimating session size: " + e.getMessage() );
            return 0;
        }
    }
}

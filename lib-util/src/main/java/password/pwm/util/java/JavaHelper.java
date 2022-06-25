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

package password.pwm.util.java;

import org.apache.commons.io.IOUtils;
import password.pwm.data.ImmutableByteArray;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class JavaHelper
{
    private static final char[] HEX_CHAR_ARRAY = "0123456789ABCDEF".toCharArray();

    private JavaHelper( )
    {
    }

    public static String binaryArrayToHex( final byte[] buf )
    {
        final char[] chars = new char[2 * buf.length];
        for ( int i = 0; i < buf.length; ++i )
        {
            chars[2 * i] = HEX_CHAR_ARRAY[( buf[i] & 0xF0 ) >>> 4];
            chars[2 * i + 1] = HEX_CHAR_ARRAY[buf[i] & 0x0F];
        }
        return new String( chars );
    }

    public static <E extends Enum<E>> E readEnumFromString( final Class<E> enumClass, final E defaultValue, final String input )
    {
        return readEnumFromString( enumClass, input ).orElse( defaultValue );
    }

    public static <E extends Enum<E>> Optional<E> readEnumFromCaseIgnoreString( final Class<E> enumClass, final String input )
    {
        return JavaHelper.readEnumFromPredicate( enumClass, loopValue -> loopValue.name().equalsIgnoreCase( input ) );
    }

    public static <E extends Enum<E>> Optional<E> readEnumFromPredicate( final Class<E> enumClass, final Predicate<E> match )
    {
        if ( match == null )
        {
            return Optional.empty();
        }

        if ( enumClass == null || !enumClass.isEnum() )
        {
            return Optional.empty();
        }

        return EnumSet.allOf( enumClass ).stream().filter( match ).findFirst();
    }

    public static <E extends Enum<E>> Set<E> readEnumsFromPredicate( final Class<E> enumClass, final Predicate<E> match )
    {
        if ( match == null )
        {
            return Collections.emptySet();
        }

        if ( enumClass == null || !enumClass.isEnum() )
        {
            return Collections.emptySet();
        }

        return EnumSet.allOf( enumClass ).stream().filter( match ).collect( Collectors.toUnmodifiableSet() );
    }

    public static <E extends Enum<E>> Optional<E> readEnumFromString( final Class<E> enumClass, final String input )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return Optional.empty();
        }

        if ( enumClass == null || !enumClass.isEnum() )
        {
            return Optional.empty();
        }

        try
        {
            return Optional.of( Enum.valueOf( enumClass, input ) );
        }
        catch ( final IllegalArgumentException e )
        {
            /* noop */
        }

        return Optional.empty();
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
        if ( enumArray == null || enumArray.length == 0 )
        {
            return false;
        }

        for ( final E loopValue : enumArray )
        {
            if ( loopValue == enumValue )
            {
                return true;
            }
        }

        return false;
    }

    public static long copy( final InputStream input, final OutputStream output )
            throws IOException
    {
        return IOUtils.copyLarge( input, output, 0, -1 );
    }


    public static Optional<String> copyToString( final InputStream input, final Charset charset, final int maximumLength )
            throws IOException
    {
        if ( input == null )
        {
            return Optional.empty();
        }
        final StringWriter stringWriter = new StringWriter();
        final InputStreamReader reader = new InputStreamReader( input, charset );
        IOUtils.copyLarge( reader, stringWriter, 0, maximumLength );
        final String value = stringWriter.toString();
        return ( value.length() > 0 )
                ? Optional.of( value )
                : Optional.empty();
    }

    public static void closeQuietly( final Closeable closable )
    {
        IOUtils.closeQuietly( closable );
    }

    public static ImmutableByteArray copyToBytes( final InputStream inputStream, final int maxLength )
            throws IOException
    {
        try ( InputStream limitedInputStream = new LengthLimitedInputStream( inputStream, maxLength ) )
        {
            final byte[] bytes = IOUtils.toByteArray( limitedInputStream );
            return ImmutableByteArray.of( bytes );
        }
    }

    public static void copy( final String input, final OutputStream output, final Charset charset )
            throws IOException
    {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream( input.getBytes( charset ) );
        JavaHelper.copy( byteArrayInputStream, output );
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
            final ConditionalTaskExecutor conditionalTaskExecutor
    )
            throws IOException
    {
        final byte[] buffer = new byte[bufferSize];
        int bytesCopied;
        long totalCopied = 0;
        do
        {
            bytesCopied = input.read( buffer );
            if ( bytesCopied > 0 )
            {
                output.write( buffer, 0, bytesCopied );
                totalCopied += bytesCopied;
            }
            if ( conditionalTaskExecutor != null )
            {
                conditionalTaskExecutor.conditionallyExecuteTask();
            }
            if ( !predicate.test( totalCopied ) )
            {
                return totalCopied;
            }
        }
        while ( bytesCopied > 0 );
        return totalCopied;
    }

    public static Instant parseIsoToInstant( final String input )
    {
        return Instant.parse( input );
    }

    public static Collection<Method> getAllMethodsForClass( final Class<?> clazz )
    {
        final LinkedHashSet<Method> methods = new LinkedHashSet<>( Arrays.asList( clazz.getDeclaredMethods() ) );

        final Class<?> superClass = clazz.getSuperclass();
        if ( superClass != null )
        {
            methods.addAll( getAllMethodsForClass( superClass ) );
        }

        return Collections.unmodifiableSet( methods );
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
            sb.append( " on " ).append( threadInfo.getLockName() );
        }
        if ( threadInfo.getLockOwnerName() != null )
        {
            sb.append( " owned by \"" ).append( threadInfo.getLockOwnerName() ).append( "\" Id=" ).append( threadInfo.getLockOwnerId() );
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
            sb.append( "\tat " ).append( ste );
            sb.append( '\n' );
            if ( counter == 0 && threadInfo.getLockInfo() != null )
            {
                final Thread.State ts = threadInfo.getThreadState();
                switch ( ts )
                {
                    case BLOCKED:
                        sb.append( "\t-  blocked on " ).append( threadInfo.getLockInfo() );
                        sb.append( '\n' );
                        break;
                    case WAITING:
                        sb.append( "\t-  waiting on " ).append( threadInfo.getLockInfo() );
                        sb.append( '\n' );
                        break;
                    case TIMED_WAITING:
                        sb.append( "\t-  waiting on " ).append( threadInfo.getLockInfo() );
                        sb.append( '\n' );
                        break;
                    default:
                }
            }

            for ( final MonitorInfo mi : threadInfo.getLockedMonitors() )
            {
                if ( mi.getLockedStackDepth() == counter )
                {
                    sb.append( "\t-  locked " ).append( mi );
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
            sb.append( "\n\tNumber of locked synchronizers = " ).append( locks.length );
            sb.append( '\n' );
            for ( final LockInfo li : locks )
            {
                sb.append( "\t- " ).append( li );
                sb.append( '\n' );
            }
        }
        sb.append( '\n' );
        return sb.toString();
    }

    public static int rangeCheck( final int min, final int max, final int value )
    {
        return ( int ) rangeCheck( ( long ) min, max, value );
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

        if ( inputException.getClass().isAssignableFrom( exceptionType ) )
        {
            return Optional.of( ( T ) inputException );
        }

        Throwable nextException = inputException.getCause();
        while ( nextException != null )
        {
            if ( nextException.getClass().isAssignableFrom( exceptionType ) )
            {
                return Optional.of( ( T ) inputException );
            }

            nextException = nextException.getCause();
        }

        return Optional.empty();
    }

    public static Map<String, String> propertiesToStringMap( final Properties properties )
    {
        Objects.requireNonNull( properties );
        final Map<String, String> returnMap = new LinkedHashMap<>( properties.size() );
        properties.forEach( ( key, value ) -> returnMap.put( ( String ) key, ( String ) value ) );
        return returnMap;
    }

    public static LongAccumulator newAbsLongAccumulator()
    {
        return new LongAccumulator( ( left, right ) ->
        {
            final long newValue = left + right;
            return newValue < 0 ? 0 : newValue;
        }, 0L );
    }

    public static int silentParseInt( final String input, final int defaultValue )
    {
        try
        {
            return Integer.parseInt( input );
        }
        catch ( final NumberFormatException e )
        {
            return defaultValue;
        }
    }

    public static long silentParseLong( final String input, final long defaultValue )
    {
        try
        {
            return Long.parseLong( input );
        }
        catch ( final NumberFormatException e )
        {
            return defaultValue;
        }
    }

    public static boolean doubleContainsLongValue( final Double input )
    {
        return input.equals( Math.floor( input ) )
                && !Double.isInfinite( input )
                && !Double.isNaN( input )
                && input <= Long.MAX_VALUE
                && input >= Long.MIN_VALUE;
    }

    public static byte[] longToBytes( final long input )
    {
        return ByteBuffer.allocate( 8 ).putLong( input ).array();
    }

    public static String requireNonEmpty( final String input )
    {
        return requireNonEmpty( input, "non-empty string value required" );
    }

    public static String requireNonEmpty( final String input, final String message )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            throw new NullPointerException( message );
        }
        return input;
    }

    public static byte[] gunzip( final byte[] bytes )
            throws IOException
    {
        try (  GZIPInputStream inputGzipStream = new GZIPInputStream( new ByteArrayInputStream( bytes ) ) )
        {
            return inputGzipStream.readAllBytes();
        }
    }

    public static byte[] gzip( final byte[] bytes )
            throws IOException
    {
        try ( ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
              GZIPOutputStream gzipOutputStream = new GZIPOutputStream( byteArrayOutputStream ) )
        {
            gzipOutputStream.write( bytes );
            gzipOutputStream.close();
            final byte[] byteOutput = byteArrayOutputStream.toByteArray();
            if ( byteOutput.length > 9 )
            {
                // revert fix for https://bugs.openjdk.java.net/browse/JDK-8244706 in JDK16+.
                // this is incorrect behavior, but effectively harmless and preserves backwards xpat.
                byteOutput[9] = 0;
            }
            return byteOutput;
        }
    }

    /**
     * Append multiple byte array values into a single array.
     * @param byteArrays two or more byte arrays.
     * @return A new array with the contents of all byteArrays appended
     */
    public static byte[] concatByteArrays( final byte[]... byteArrays )
    {
        if ( byteArrays == null || byteArrays.length < 1 )
        {
            return new byte[0];
        }

        int totalLength = 0;
        for ( final byte[] array : byteArrays )
        {
            totalLength += array.length;
        }

        final byte[] newByteArray = new byte[ totalLength ];
        int nextIndex = 0;
        for ( final byte[] array : byteArrays )
        {
            System.arraycopy( array, 0, newByteArray, nextIndex, array.length );
            nextIndex += array.length;
        }

        return newByteArray;
    }

    /**
     * Close executor and wait up to the specified TimeDuration for all executor jobs to terminate.  There is no guarantee that either all jobs will
     * terminate or the entire duration will be waited for, though the duration should not be exceeded.
     * @param executor Executor close
     * @param timeDuration TimeDuration to wait for
     */
    public static void closeAndWaitExecutor( final ExecutorService executor, final TimeDuration timeDuration )
    {
        if ( executor == null )
        {
            return;
        }

        executor.shutdown();
        try
        {
            executor.awaitTermination( timeDuration.asMillis(), TimeUnit.MILLISECONDS );
        }
        catch ( final InterruptedException e )
        {
            /* ignore */
        }
    }

    public static String stackTraceToString( final Throwable e )
    {
        final Writer stackTraceOutput = new StringWriter();
        e.printStackTrace( new PrintWriter( stackTraceOutput ) );
        return stackTraceOutput.toString();

    }
}

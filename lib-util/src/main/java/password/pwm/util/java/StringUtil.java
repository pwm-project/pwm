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

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class StringUtil
{
    private static final Charset STRING_UTIL_CHARSET = StandardCharsets.UTF_8;

    private static final Base64.Decoder B64_MIME_DECODER = Base64.getMimeDecoder();
    private static final Base64.Decoder B64_URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder B64_MIME_ENCODER = Base64.getMimeEncoder( 0, new byte[0] );
    private static final Base64.Encoder B64_URL_ENCODER = Base64.getUrlEncoder();

    /**
     * Based on http://www.owasp.org/index.php/Preventing_LDAP_Injection_in_Java.
     *
     * @param input string to have escaped
     * @return ldap escaped script
     */
    public static String escapeLdapFilter( final String input )
    {
        final StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < input.length(); i++ )
        {
            final char curChar = input.charAt( i );
            switch ( curChar )
            {
                case '\\':
                    sb.append( "\\5c" );
                    break;
                case '*':
                    sb.append( "\\2a" );
                    break;
                case '(':
                    sb.append( "\\28" );
                    break;
                case ')':
                    sb.append( "\\29" );
                    break;
                case '\u0000':
                    sb.append( "\\00" );
                    break;
                default:
                    sb.append( curChar );
            }
        }
        return sb.toString();
    }

    /**
     * Based on http://www.owasp.org/index.php/Preventing_LDAP_Injection_in_Java.
     *
     * @param input string to have escaped
     * @return ldap escaped script
     */
    public static String escapeLdapDN( final String input )
    {
        final StringBuilder sb = new StringBuilder();
        if ( ( input.length() > 0 ) && ( ( input.charAt( 0 ) == ' ' ) || ( input.charAt( 0 ) == '#' ) ) )
        {
            // add the leading backslash if needed
            sb.append( '\\' );
        }
        for ( int i = 0; i < input.length(); i++ )
        {
            final char curChar = input.charAt( i );
            switch ( curChar )
            {
                case '\\':
                    sb.append( "\\\\" );
                    break;
                case ',':
                    sb.append( "\\," );
                    break;
                case '+':
                    sb.append( "\\+" );
                    break;
                case '"':
                    sb.append( "\\\"" );
                    break;
                case '<':
                    sb.append( "\\<" );
                    break;
                case '>':
                    sb.append( "\\>" );
                    break;
                case ';':
                    sb.append( "\\;" );
                    break;
                default:
                    sb.append( curChar );
            }
        }
        if ( ( input.length() > 1 ) && ( input.charAt( input.length() - 1 ) == ' ' ) )
        {
            // add the trailing backslash if needed
            sb.insert( sb.length() - 1, '\\' );
        }
        return sb.toString();
    }

    public static Map<String, String> convertStringListToNameValuePair( final Collection<String> input, final String separator )
    {
        if ( input == null || input.isEmpty() )
        {
            return Collections.emptyMap();
        }

        final Map<String, String> returnMap = new LinkedHashMap<>();
        for ( final String loopStr : input )
        {
            if ( loopStr != null && separator != null && loopStr.contains( separator ) )
            {
                final int separatorLocation = loopStr.indexOf( separator );
                final String key = loopStr.substring( 0, separatorLocation );
                if ( !key.trim().isEmpty() )
                {
                    final String value = loopStr.substring( separatorLocation + separator.length() );
                    returnMap.put( key, value );
                }
            }
            else
            {
                if ( loopStr != null && !loopStr.trim().isEmpty() )
                {
                    returnMap.put( loopStr, "" );
                }
            }
        }

        return returnMap;
    }

    public static String join( final Object[] inputs, final String separator )
    {
        return StringUtils.join( inputs, separator );
    }

    public static <V> String join( final Collection<V> inputs, final String separator )
    {
        if ( inputs != null )
        {
            return StringUtils.join( inputs, separator );
        }
        return "";
    }

    public static String formatDiskSizeforDebug( final long diskSize )
    {
        return diskSize == 0
                ? "0"
                : PwmNumberFormat.forLocale( Locale.getDefault() ).format( diskSize ) + " (" + formatDiskSize( diskSize ) + ")";
    }

    public static String formatDiskSize( final long diskSize )
    {
        final float count = 1000;
        if ( diskSize < 0 )
        {
            return "n/a";
        }

        if ( diskSize == 0 )
        {
            return "0";
        }

        final NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits( 2 );

        if ( diskSize > count * count * count )
        {
            return nf.format( diskSize / count / count / count ) + " GB";
        }

        if ( diskSize > count * count )
        {
            return nf.format( diskSize / count / count ) + " MB";
        }

        return PwmNumberFormat.forLocale( Locale.getDefault() ).format( diskSize ) + " bytes";
    }

    public static boolean nullSafeEqualsIgnoreCase( final String value1, final String value2 )
    {
        final String compare1 = value1 == null ? "" : value1;
        final String compare2 = value2 == null ? "" : value2;
        return Objects.equals( compare1.toLowerCase(), compare2.toLowerCase() );
    }

    public static boolean nullSafeEquals( final String value1, final String value2 )
    {
        return Objects.equals( value1, value2 );
    }

    public static String toIsoDate( final Instant instant )
    {
        return instant == null ? "" : instant.truncatedTo( ChronoUnit.SECONDS ).toString();
    }

    public enum Base64Options
    {
        GZIP,
        URL_SAFE,
    }

    public static String escapeJS( final String input )
    {
        return StringEscapeUtils.escapeEcmaScript( input );
    }

    public static String escapeHtml( final String input )
    {
        return StringEscapeUtils.escapeHtml4( input );
    }

    public static String escapeCsv( final String input )
    {
        return StringEscapeUtils.escapeCsv( input );
    }

    public static String escapeJava( final String input )
    {
        return StringEscapeUtils.escapeJava( input );
    }

    public static String escapeXml( final String input )
    {
        return StringEscapeUtils.escapeXml11( input );
    }

    public static String urlEncode( final String input )
    {
        try
        {
            return URLEncoder.encode( input, STRING_UTIL_CHARSET.toString() );
        }
        catch ( final UnsupportedEncodingException e )
        {
            throw new IllegalStateException( "unexpected error during url encoding: " + e.getMessage(), e );
        }
    }

    public static String urlDecode( final String input )
            throws IOException
    {
        try
        {
            return URLDecoder.decode( input, STRING_UTIL_CHARSET.toString() );
        }
        catch ( final UnsupportedEncodingException e )
        {
            throw new IOException( "unexpected error during url decoding: " + e.getMessage(), e );
        }
    }

    public static String base32Encode( final byte[] input )
            throws IOException
    {
        final Base32 base32 = new Base32();
        return new String( base32.encode( input ), STRING_UTIL_CHARSET.toString() );
    }

    public static byte[] base64Decode( final CharSequence input, final StringUtil.Base64Options... options )
            throws IOException
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return new byte[0];
        }

        final byte[] decodedBytes;
        if ( JavaHelper.enumArrayContainsValue( options, Base64Options.URL_SAFE ) )
        {
            decodedBytes = B64_URL_DECODER.decode( input.toString() );
        }
        else
        {
            decodedBytes = B64_MIME_DECODER.decode( input.toString() );
        }

        if ( JavaHelper.enumArrayContainsValue( options, Base64Options.GZIP ) )
        {
            return JavaHelper.gunzip( decodedBytes );
        }
        else
        {
            return decodedBytes;
        }
    }

    public static String base64Encode( final byte[] input, final StringUtil.Base64Options... options )
    {
        final byte[] compressedBytes;
        if ( JavaHelper.enumArrayContainsValue( options, Base64Options.GZIP ) )
        {
            try
            {
                compressedBytes = JavaHelper.gzip( input );
            }
            catch ( final IOException e )
            {
                throw new IllegalStateException( "unexpected error during base64 decoding: " + e, e );
            }
        }
        else
        {
            compressedBytes = input;
        }

        if ( JavaHelper.enumArrayContainsValue( options, Base64Options.URL_SAFE ) )
        {
            return B64_URL_ENCODER.encodeToString( compressedBytes );
        }
        else
        {
            return B64_MIME_ENCODER.encodeToString( compressedBytes );
        }
    }

    public static String padRight( final String input, final int length, final char appendChar )
    {
        return padImpl( input, length, appendChar, true );
    }

    public static String padLeft( final String input, final int length, final char appendChar )
    {
        return padImpl( input, length, appendChar, false );
    }

    private static String padImpl( final String input, final int length, final char appendChar, final boolean right )
    {
        if ( input == null )
        {
            return "";
        }

        if ( input.length() >= length )
        {
            return input;
        }

        final char[] charArray = new char[ length - input.length() ];
        Arrays.fill( charArray, appendChar );
        final String paddingString = new String( charArray );

        return right
                ? input + paddingString
                : paddingString + input;
    }

    public static List<String> splitAndTrim( final String input, final String separator )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return Collections.emptyList();
        }

        final String[] splitValues = StringUtils.split( input, separator );

        return Arrays.stream( splitValues )
                .map( String::trim )
                .collect( Collectors.toList() );
    }

    public static Collection<String> whitespaceSplit( final String input )
    {
        if ( input == null )
        {
            return Collections.emptyList();
        }

        final String[] splitValues = input.trim().split( "\\s+" );
        return Arrays.asList( splitValues );
    }

    public static String[] createStringChunks( final String str, final int size )
    {
        if ( size <= 0 || str == null || str.length() <= size )
        {
            return new String[]
                    {
                            str,
                    };
        }

        final int numOfChunks = str.length() - size + 1;
        final Set<String> chunks = new HashSet<>( numOfChunks );

        for ( int i = 0; i < numOfChunks; i++ )
        {
            chunks.add( StringUtils.substring( str, i, i + size ) );
        }

        return chunks.toArray( new String[ numOfChunks ] );
    }

    public static <V> String collectionToString( final Collection<V> collection )
    {
        return collectionToString( collection, "," );
    }

    public static <V> String collectionToString( final Collection<V> collection, final String recordSeparator )
    {
        final StringBuilder sb = new StringBuilder();
        if ( collection != null )
        {
            for ( final Iterator<V> iterator = collection.iterator(); iterator.hasNext(); )
            {
                final Object obj = iterator.next();
                if ( obj != null )
                {
                    sb.append( obj );
                    if ( iterator.hasNext() )
                    {
                        sb.append( recordSeparator );
                    }
                }
            }
        }
        return sb.toString();
    }

    public static <K, V> String mapToString( final Map<K, V> map )
    {
        return mapToString( map, "=", ", " );
    }

    public static <K, V> String mapToString( final Map<K, V> map, final String keyValueSeparator, final String recordSeparator )
    {
        final StringBuilder sb = new StringBuilder();
        for ( final Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator(); iterator.hasNext(); )
        {
            final Map.Entry<K, V> entry = iterator.next();
            if ( entry.getKey() != null && entry.getValue() != null )
            {
                final String key = entry.getKey().toString().trim();
                final String value = entry.getValue().toString().trim();

                if ( !key.isEmpty() && !value.isEmpty() )
                {
                    sb.append( key );
                    sb.append( keyValueSeparator );
                    sb.append( value );

                    if ( iterator.hasNext() )
                    {
                        sb.append( recordSeparator );
                    }
                }
            }
        }
        return sb.toString();
    }

    public static int[] toCodePointArray( final String str )
    {
        if ( str != null )
        {
            final int len = str.length();
            final int[] acp = new int[ str.codePointCount( 0, len ) ];

            for ( int i = 0, j = 0; i < len; i = str.offsetByCodePoints( i, 1 ) )
            {
                acp[ j++ ] = str.codePointAt( i );
            }

            return acp;
        }

        return new int[ 0 ];
    }

    public static boolean isEmpty( final CharSequence input )
    {
        return StringUtils.isEmpty( input );
    }

    public static boolean notEmpty( final CharSequence input )
    {
        return !StringUtils.isEmpty( input );
    }

    public static boolean isTrimEmpty( final String input )
    {
        return isEmpty( input ) || input.trim().length() == 0;
    }

    public static boolean notTrimEmpty( final String input )
    {
        return input != null && !input.trim().isEmpty();
    }

    public static String defaultString( final String input, final String defaultStr )
    {
        return StringUtils.defaultString( input, defaultStr );
    }

    public static boolean equals( final String input1, final String input2 )
    {
        return StringUtils.equals( input1, input2 );
    }

    public static String truncate( final String input, final int length )
    {
        return truncate( input, length, null );
    }

    public static String truncate( final String input, final int length, final String appendIfTruncated )
    {
        if ( input == null )
        {
            return "";
        }

        if ( input.length() > length )
        {
            return input.substring( 0, length ) + ( appendIfTruncated == null ? "" : appendIfTruncated );
        }

        return input;
    }

    public static int convertStrToInt( final String string, final int defaultValue )
    {
        if ( string == null )
        {
            return defaultValue;
        }

        try
        {
            return Integer.parseInt( string );
        }
        catch ( final NumberFormatException e )
        {
            return defaultValue;
        }
    }

    public static String stripAllWhitespace( final CharSequence input )
    {
        return stripAllChars( input, Character::isWhitespace );
    }

    public static CharSequence cleanNonPrintableCharacters( final CharSequence input )
    {
        final Predicate<Character> nonPrintableCharPredicate = character ->
                ( Character.isISOControl( character ) && !Character.isWhitespace( character ) )
                        || !Character.isDefined( character );

        return replaceAllChars( input,
                character -> nonPrintableCharPredicate.test( character ) ? Optional.of( "?" ) : Optional.empty() );
    }

    public static String stripAllChars( final CharSequence input, final Predicate<Character> characterPredicate )
    {
        return replaceAllChars( input,
                character -> ( characterPredicate.test( character ) ) ? Optional.of( "" ) : Optional.empty() );
    }

    public static String replaceAllChars( final CharSequence input, final Function<Character, Optional<String>> replacementFunction )
    {
        if ( isEmpty( input ) )
        {
            return "";
        }

        if ( replacementFunction == null )
        {
            return input.toString();
        }

        final int inputLength = input.length();

        // count of valid output chars
        int index = 0;
        {
            // loop through input chars and stop if replacement char is needed ( but no actual copying yet )
            while ( index < inputLength )
            {
                final Character indexChar = input.charAt( index );
                final Optional<String> replacementStr = replacementFunction.apply( indexChar );
                if ( replacementStr.isEmpty() )
                {
                    index++;
                }
                else
                {
                    break;
                }
            }

            // return input string if we made it through input without detecting replacement char
            if ( index >= inputLength )
            {
                return input.toString();
            }
        }

        // create the destination builder
        final StringBuilder sb = new StringBuilder( input.subSequence( 0, index ) );

        // loop through remaining chars and copy one by one
        while ( index < inputLength )
        {
            final char loopChar = input.charAt( index );
            final Optional<String> replacementStr = replacementFunction.apply( loopChar );
            if ( replacementStr.isPresent() )
            {
                sb.append( replacementStr.get() );
            }
            else
            {
                sb.append( loopChar );
            }
            index++;
        }

        return sb.toString();
    }

    public static String insertRepeatedLineBreaks( final String input, final int periodicity )
    {
        final String lineSeparator = System.lineSeparator();
        return repeatedInsert( input, periodicity, lineSeparator );
    }

    public static String repeatedInsert( final String input, final int periodicity, final String insertValue )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return "";
        }

        if ( StringUtil.isEmpty( insertValue ) )
        {
            return input;
        }

        final int inputLength = input.length();
        final StringBuilder output = new StringBuilder( inputLength + ( periodicity * insertValue.length() ) );

        int index = 0;
        while ( index < inputLength )
        {
            final int endIndex = index + periodicity;
            output.append( input, index, Math.min( endIndex, inputLength ) );
            if ( endIndex < inputLength )
            {
                output.append( insertValue );
            }
            index += periodicity;
        }
        return output.toString();
    }

    public static boolean caseIgnoreContains( final Collection<String> collection, final String value )
    {
        if ( value == null || collection == null )
        {
            return false;
        }

        if ( collection.contains( value ) )
        {
            return true;
        }

        final String lCaseValue = value.toLowerCase();
        return collection.stream()
                .map( String::toLowerCase )
                .anyMatch( lCaseValue::equals );
    }


    private static final Map<Character, String> URL_PATH_ENCODING_REPLACEMENTS = Map.ofEntries(
            Map.entry( ' ', "%20" ),
            Map.entry( '!', "%21" ),
            Map.entry( '#', "%23" ),
            Map.entry( '$', "%24" ),
            Map.entry( '&', "%26" ),
            Map.entry( '\'', "%27" ),
            Map.entry( '(', "%28" ),
            Map.entry( ')', "%29" ),
            Map.entry( '*', "%2A" ),
            Map.entry( '+', "%2B" ),
            Map.entry( ',', "%2C" ),
            Map.entry( '/', "%2F" ),
            Map.entry( ':', "%3A" ),
            Map.entry( ';', "%3B" ),
            Map.entry( '=', "%3D" ),
            Map.entry( '?', "%3F" ),
            Map.entry( '@', "%40" ),
            Map.entry( '[', "%5B" ),
            Map.entry( ']', "%5D" )
    );

    public static CharSequence urlPathEncode( final CharSequence input )
    {
        return replaceAllChars( input,
                character -> Optional.ofNullable( URL_PATH_ENCODING_REPLACEMENTS.getOrDefault( character, null ) ) );
    }

    /**
     * Convert a string value to a boolean.  If the value is a common positive string
     * value such as "1", "true", "y" or "yes" then TRUE is returned.  For any other
     * value or null, FALSE is returned.
     *
     * @param string value to test
     * @return true if the string resolves to a positive value.
     */
    public static boolean convertStrToBoolean( final String string )
    {
        if ( StringUtil.isEmpty( string ) )
        {
            return false;
        }

        return "true".equalsIgnoreCase( string )
                || "1".equalsIgnoreCase( string )
                || "yes".equalsIgnoreCase( string )
                || "y".equalsIgnoreCase( string );
    }

    public static List<String> tokenizeString(
            final String inputString,
            final String separator
    )
    {
        if ( StringUtil.isEmpty( inputString ) )
        {
            return Collections.emptyList();
        }

        final List<String> values = new ArrayList<>( Arrays.asList( inputString.split( separator ) ) );
        return Collections.unmodifiableList( values );
    }
}

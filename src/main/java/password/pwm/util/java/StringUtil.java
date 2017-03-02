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

import net.iharder.Base64;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import password.pwm.PwmConstants;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public abstract class StringUtil {
    private static final PwmLogger LOGGER = PwmLogger.forClass(StringUtil.class);

    /**
     * Based on http://www.owasp.org/index.php/Preventing_LDAP_Injection_in_Java.
     *
     * @param input string to have escaped
     * @return ldap escaped script
     *
     */
    public static String escapeLdapFilter(final String input) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            final char curChar = input.charAt(i);
            switch (curChar) {
                case '\\':
                    sb.append("\\5c");
                    break;
                case '*':
                    sb.append("\\2a");
                    break;
                case '(':
                    sb.append("\\28");
                    break;
                case ')':
                    sb.append("\\29");
                    break;
                case '\u0000':
                    sb.append("\\00");
                    break;
                default:
                    sb.append(curChar);
            }
        }
        return sb.toString();
    }

    /**
     * Based on http://www.owasp.org/index.php/Preventing_LDAP_Injection_in_Java.
     *
     * @param input string to have escaped
     * @return ldap escaped script
     *
     */
    public static String escapeLdapDN(final String input) {
        final StringBuilder sb = new StringBuilder(); // If using JDK >= 1.5 consider using StringBuilder
        if ((input.length() > 0) && ((input.charAt(0) == ' ') || (input.charAt(0) == '#'))) {
            sb.append('\\'); // add the leading backslash if needed
        }
        for (int i = 0; i < input.length(); i++) {
            final char curChar = input.charAt(i);
            switch (curChar) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case ',':
                    sb.append("\\,");
                    break;
                case '+':
                    sb.append("\\+");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '<':
                    sb.append("\\<");
                    break;
                case '>':
                    sb.append("\\>");
                    break;
                case ';':
                    sb.append("\\;");
                    break;
                default:
                    sb.append(curChar);
            }
        }
        if ((input.length() > 1) && (input.charAt(input.length() - 1) == ' ')) {
            sb.insert(sb.length() - 1, '\\'); // add the trailing backslash if needed
        }
        return sb.toString();
    }

    public static Map<String, String> convertStringListToNameValuePair(final Collection<String> input, final String separator) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<String, String> returnMap = new LinkedHashMap<>();
        for (final String loopStr : input) {
            if (loopStr != null && separator != null && loopStr.contains(separator)) {
                final int separatorLocation = loopStr.indexOf(separator);
                final String key = loopStr.substring(0, separatorLocation);
                if (!key.trim().isEmpty()) {
                    final String value = loopStr.substring(separatorLocation + separator.length(), loopStr.length());
                    returnMap.put(key, value);
                }
            } else {
                if (loopStr != null && !loopStr.trim().isEmpty()) {
                    returnMap.put(loopStr, "");
                }
            }
        }

        return returnMap;
    }

    public static String join(final Object[] inputs, final String separator) {
        return StringUtils.join(inputs, separator);
    }

    public static String join(final Collection inputs, final String separator) {
        return StringUtils.join(inputs == null ? new String[]{} : inputs.toArray(), separator);
    }

    public static String formatDiskSize(final long diskSize) {
        final float COUNT = 1000;
        if (diskSize < 1) {
            return "n/a";
        }

        if (diskSize == 0) {
            return "0";
        }

        final NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);

        if (diskSize > COUNT * COUNT * COUNT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(nf.format(diskSize / COUNT / COUNT / COUNT));
            sb.append(" GB");
            return sb.toString();
        }

        if (diskSize > COUNT * COUNT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(nf.format(diskSize / COUNT / COUNT));
            sb.append(" MB");
            return sb.toString();
        }

        return NumberFormat.getInstance().format(diskSize) + " bytes";
    }

    public enum Base64Options {
        GZIP,
        URL_SAFE,
        ;

        private static int asBase64UtilOptions(final Base64Options... options) {
            int b64UtilOptions = 0;

            if (JavaHelper.enumArrayContainsValue(options, Base64Options.GZIP)) {
                b64UtilOptions = b64UtilOptions | Base64.GZIP;
            }
            if (JavaHelper.enumArrayContainsValue(options, Base64Options.URL_SAFE)) {
                b64UtilOptions = b64UtilOptions | Base64.URL_SAFE;
            }
            return b64UtilOptions;
        }
    }

    public static String escapeJS(final String input) {
        return StringEscapeUtils.escapeEcmaScript(input);
    }

    public static String escapeHtml(final String input)
    {
        return StringEscapeUtils.escapeHtml4(input);
    }

    public static String escapeCsv(final String input)
    {
        return StringEscapeUtils.escapeCsv(input);
    }

    public static String escapeJava(final String input)
    {
        return StringEscapeUtils.escapeJava(input);
    }

    public static String escapeXml(final String input)
    {
        return StringEscapeUtils.escapeXml11(input);
    }

    public static String urlEncode(final String input) {
        try {
            return URLEncoder.encode(input, PwmConstants.DEFAULT_CHARSET.toString());
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("unexpected error during url encoding: " + e.getMessage());
            return input;
        }
    }

    public static String urlDecode(final String input) {
        try {
            return URLDecoder.decode(input, PwmConstants.DEFAULT_CHARSET.toString());
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("unexpected error during url decoding: " + e.getMessage());
            return input;
        }
    }

    public static byte[] base64Decode(final String input)
            throws IOException
    {
        return Base64.decode(input);
    }

    public static String base32Encode(final byte[] input)
            throws IOException
    {
        final Base32 base32 = new Base32();
        return new String(base32.encode(input));
    }

    public static byte[] base64Decode(final String input, final StringUtil.Base64Options... options)
            throws IOException
    {
        final int b64UtilOptions = Base64Options.asBase64UtilOptions(options);

        return Base64.decode(input, b64UtilOptions);
    }

    public static String base64Encode(final byte[] input)
    {
        return Base64.encodeBytes(input);
    }

    public static String base64Encode(final byte[] input, final StringUtil.Base64Options... options)
            throws IOException
    {
        final int b64UtilOptions = Base64Options.asBase64UtilOptions(options);

        if (b64UtilOptions > 0) {
            return Base64.encodeBytes(input, b64UtilOptions);
        } else {
            return Base64.encodeBytes(input);
        }
    }

    public static String padEndToLength(final String input, final int length, final char appendChar) {
        if (input == null) {
            return null;
        }

        if (input.length() >= length) {
            return input;
        }

        final StringBuilder sb = new StringBuilder(input);
        while (sb.length() < length) {
            sb.append(appendChar);
        }

        return sb.toString();
    }

    public static Collection<String> whitespaceSplit(final String input) {
        if (input == null) {
            return Collections.emptyList();
        }

        final String[] splitValues = input.trim().split("\\s+");
        return Arrays.asList(splitValues);
    }

    public static String[] createStringChunks(final String str, final int size) {
        if (size <= 0 || str == null || str.length() <= size) {
            return new String[] { str };
        }

        final int numOfChunks = str.length() - size + 1;
        final Set<String> chunks = new HashSet<>(numOfChunks);

        for (int i=0; i<numOfChunks; i++) {
            chunks.add(StringUtils.substring(str, i, i+size));
        }

        return chunks.toArray(new String[numOfChunks]);
    }

    public static String collectionToString(final Collection collection, final String recordSeparator) {
        final StringBuilder sb = new StringBuilder();
        if (collection != null) {
            for (final Iterator iterator = collection.iterator(); iterator.hasNext(); ) {
                final Object obj = iterator.next();
                if (obj != null) {
                    sb.append(obj.toString());
                    if (iterator.hasNext()) {
                        sb.append(recordSeparator);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String mapToString(final Map map) {
        return mapToString(map, "=", ",");
    }

    public static String mapToString(final Map map, final String keyValueSeparator, final String recordSeparator) {
        final StringBuilder sb = new StringBuilder();
        for (final Iterator iterator = map.keySet().iterator(); iterator.hasNext(); ) {
            final String key = iterator.next().toString();
            final String value = map.get(key) == null ? "" : map.get(key).toString();
            if (key != null && value != null && !key.trim().isEmpty() && !value.trim().isEmpty()) {
                sb.append(key.trim());
                sb.append(keyValueSeparator);
                sb.append(value.trim());
                if (iterator.hasNext()) {
                    sb.append(recordSeparator);
                }
            }
        }
        return sb.toString();
    }

    public static int[] toCodePointArray(final String str) {
        if (str != null) {
            final int len = str.length();
            final int[] acp = new int[str.codePointCount(0, len)];

            for (int i = 0, j = 0; i < len; i = str.offsetByCodePoints(i, 1)) {
                acp[j++] = str.codePointAt(i);
            }

            return acp;
        }

        return new int[0];
    }

    public static boolean isEmpty(final CharSequence input) {
        return StringUtils.isEmpty(input);
    }

    public static String defaultString(final String input, final String defaultStr) {
        return StringUtils.defaultString(input, defaultStr);
    }

    public static boolean equals(final String input1, final String input2) {
        return StringUtils.equals(input1, input2);
    }
}

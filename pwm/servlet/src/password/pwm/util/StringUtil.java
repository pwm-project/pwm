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

package password.pwm.util;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringEscapeUtils;
import password.pwm.PwmConstants;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

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
            char curChar = input.charAt(i);
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
        StringBuilder sb = new StringBuilder(); // If using JDK >= 1.5 consider using StringBuilder
        if ((input.length() > 0) && ((input.charAt(0) == ' ') || (input.charAt(0) == '#'))) {
            sb.append('\\'); // add the leading backslash if needed
        }
        for (int i = 0; i < input.length(); i++) {
            char curChar = input.charAt(i);
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
        if (input == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> returnMap = new LinkedHashMap<>();
        for (final String loopStr : input) {
            if (loopStr != null && separator != null && loopStr.contains(separator)) {
                final int separatorLocation = loopStr.indexOf(separator);
                final String key = loopStr.substring(0, separatorLocation);
                final String value = loopStr.substring(separatorLocation + separator.length(), loopStr.length());
                returnMap.put(key, value);
            } else {
                returnMap.put(loopStr, "");
            }
        }

        return returnMap;
    }

    public enum Base64Options {
        GZIP,
        URL_SAFE,
        ;

        private static int asBase64UtilOptions(Base64Options[] options) {
            int b64UtilOptions = 0;
            Set<Base64Options> optionsEnum = EnumSet.noneOf(Base64Options.class);
            optionsEnum.addAll(Arrays.asList(options));

            if (optionsEnum.contains(Base64Options.GZIP)) {
                b64UtilOptions = b64UtilOptions | Base64Util.GZIP;
            }
            if (optionsEnum.contains(Base64Options.URL_SAFE)) {
                b64UtilOptions = b64UtilOptions | Base64Util.URL_SAFE;
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
        return Base64Util.decode(input);
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

        return Base64Util.decode(input, b64UtilOptions);
    }

    public static String base64Encode(final byte[] input)
    {
        return Base64Util.encodeBytes(input);
    }

    public static String base64Encode(final byte[] input, final StringUtil.Base64Options... options)
            throws IOException
    {
        final int b64UtilOptions = Base64Options.asBase64UtilOptions(options);

        if (b64UtilOptions > 0) {
            return Base64Util.encodeBytes(input, b64UtilOptions);
        } else {
            return Base64Util.encodeBytes(input);
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
}

/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import com.novell.ldapchai.exception.ChaiException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserInfoBean;
import password.pwm.util.operations.UserDataReader;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacroMachine {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(MacroMachine.class);

    private static final List<MacroImplementation> MACROS = new ArrayList<MacroImplementation>();
    static {
        MACROS.add(new LdapMacro());
        MACROS.add(new UserPwExpirationTimeMacro());
        MACROS.add(new UserPwExpirationTimeDefaultMacro());
        MACROS.add(new UserDaysUntilPwExpireMacro());
        MACROS.add(new UserIDMacro());
        MACROS.add(new UserPasswordMacro());
        MACROS.add(new PwmInstanceIDMacro());
        MACROS.add(new PwmCurrentTimeMacro());
        MACROS.add(new PwmCurrentTimeDefaultMacro());
        MACROS.add(new PwmSiteURLMacro());
        MACROS.add(new PwmSiteHostMacro());
    }

    private MacroMachine() {
    }

    private interface MacroImplementation {
        public Pattern getRegExPattern();
        public String replaceValue(final String input, final PwmApplication pwmApplication, final UserInfoBean uiBean, final UserDataReader userDataReader);
    }

    private static class LdapMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@LDAP:.*?@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean userInfoBean, UserDataReader dataReader) {
            if (dataReader == null) {
                return "";
            }

            final String ldapAttr = matchValue.substring(6,matchValue.length() -1);

            if (ldapAttr.equalsIgnoreCase("dn")) {
                return dataReader.getUserDN();
            }

            final String ldapValue;
            try {
                ldapValue = dataReader.readStringAttribute(ldapAttr);
            } catch (ChaiException e) {
                LOGGER.trace("could not replace value for '" + matchValue + "', ldap error: " + e.getMessage());
                return "";
            }

            if (ldapValue == null || ldapValue.length() < 1) {
                LOGGER.trace("could not replace value for '" + matchValue + "', user does not have value for '" + ldapAttr + "'");
                return "";
            }

            return ldapValue;
        }
    }

    private static class PwmInstanceIDMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@InstanceID@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean userInfoBean, UserDataReader dataReader) {
            if (pwmApplication == null) {
                LOGGER.error("could not replace value for '" + matchValue + "', pwmApplication is null");
                return "";
            }

            return pwmApplication.getInstanceID();
        }
    }

    private static class PwmCurrentTimeMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@CurrentTime:.*?@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean userInfoBean, UserDataReader dataReader) {
            final String datePattern = matchValue.substring(17,matchValue.length() -1);

            if (datePattern != null && datePattern.length() > 0) {
                try {
                    final DateFormat dateFormat = new SimpleDateFormat(datePattern);
                    return dateFormat.format(new Date());
                } catch (IllegalArgumentException e) {
                    LOGGER.error("invalid macro expression: " + matchValue + ", invalid SimpleDateFormat pattern: " + e.getMessage());
                }
            } else {
                LOGGER.error("invalid macro expression: " + matchValue + ", SimpleDatePattern <pattern> expected, using default instead.");
            }

            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date());
        }
    }

    private static class PwmCurrentTimeDefaultMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@CurrentTime@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean userInfoBean, UserDataReader dataReader) {
            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date());
        }
    }

    private static class UserPwExpirationTimeMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@User:PwExpireTime:.*?@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean, UserDataReader dataReader) {
            if (uiBean == null) {
                return "";
            }

            final Date pwdExpirationTime = uiBean.getPasswordExpirationTime();
            if (pwdExpirationTime == null) {
                return "";
            }

            final String datePattern = matchValue.substring(19,matchValue.length() -1);
            if (datePattern != null && datePattern.length() > 0) {
                try {
                    final DateFormat dateFormat = new SimpleDateFormat(datePattern);
                    return dateFormat.format(pwdExpirationTime);
                } catch (IllegalArgumentException e) {
                    LOGGER.error("invalid macro expression: " + matchValue + ", invalid SimpleDateFormat pattern: " + e.getMessage());
                }
            } else {
                LOGGER.error("invalid macro expression: " + matchValue + ", SimpleDatePattern <pattern> expected, using default instead.");
            }

            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(pwdExpirationTime);
        }
    }

    private static class UserPwExpirationTimeDefaultMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@User:PwExpireTime@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean, UserDataReader dataReader) {
            if (uiBean == null) {
                return "";
            }

            final Date pwdExpirationTime = uiBean.getPasswordExpirationTime();
            if (pwdExpirationTime == null) {
                return "";
            }

            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(pwdExpirationTime);
        }
    }

    private static class UserDaysUntilPwExpireMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@User:DaysUntilPwExpire@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean, UserDataReader dataReader) {
            if (uiBean == null) {
                LOGGER.error("could not replace value for '" + matchValue + "', userInfoBean is null");
                return "";
            }

            final Date pwdExpirationTime = uiBean.getPasswordExpirationTime();
            final TimeDuration timeUntilExpiration = TimeDuration.fromCurrent(pwdExpirationTime);
            final long daysUntilExpiration = timeUntilExpiration.getDays();


            return String.valueOf(daysUntilExpiration);
        }
    }

    private static class UserIDMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@User:ID@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean, UserDataReader dataReader) {
            if (uiBean == null || uiBean.getUserID() == null) {
                return "";
            }

            return uiBean.getUserID();
        }
    }

    private static class UserPasswordMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@User:Password@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean, UserDataReader dataReader) {
            if (uiBean == null || uiBean.getUserCurrentPassword() == null) {
                return "";
            }

            return uiBean.getUserCurrentPassword();
        }
    }

    private static class PwmSiteURLMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@SiteURL@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean userInfoBean, UserDataReader dataReader) {
            return pwmApplication.getSiteURL();
        }
    }

    private static class PwmSiteHostMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@SiteHost@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean userInfoBean, UserDataReader dataReader) {
            try {
                final URL url = new URL(pwmApplication.getSiteURL());
                return url.getHost();
            } catch (MalformedURLException e) {
                LOGGER.error("unable to parse configured/detected site URL: " + e.getMessage());
            }
            return "";
        }
    }

    public static String expandMacros(
            final String input,
            final PwmApplication pwmApplication,
            final UserInfoBean uiBean,
            final UserDataReader userDataReader
    ) {
        return expandMacros(input, pwmApplication, uiBean, userDataReader, null);
    }

    public static String expandMacros(
            final String input,
            final PwmApplication pwmApplication,
            final UserInfoBean uiBean,
            final UserDataReader userDataReader,
            final StringReplacer stringReplacer
    )
    {
        if (input == null) {
            return null;
        }

        if (input.length() < 1) {
            return input;
        }

        String workingString = input;

        for (MacroImplementation pwmMacro : MACROS) {
            boolean matched = true;
            while (matched) {
                final Pattern pattern = pwmMacro.getRegExPattern();
                final Matcher matcher = pattern.matcher(workingString);
                if (matcher.find()) {
                    workingString = doReplace(workingString, pwmMacro, matcher, pwmApplication, uiBean, userDataReader, stringReplacer);
                } else {
                    matched = false;
                }
            }
        }

        return workingString;
    }

    private static String doReplace(
            final String input,
            final MacroImplementation configVar,
            final Matcher matcher,
            final PwmApplication pwmApplication,
            final UserInfoBean uiBean,
            final UserDataReader userDataReader,
            final StringReplacer stringReplacer
    ) {
        final String matchedStr = matcher.group();
        final int startPos = matcher.start();
        final int endPos = matcher.end();
        String replaceStr = "";
        try {
            replaceStr = configVar.replaceValue(matchedStr,pwmApplication,uiBean,userDataReader);
        }  catch (Exception e) {
            LOGGER.error("error while replacing macro '" + matchedStr + "', error: " + e.getMessage());
        }

        if (replaceStr == null) {
            return input;
        }

        if (stringReplacer != null) {
            try {
                replaceStr = stringReplacer.replace(matchedStr, replaceStr);
            }  catch (Exception e) {
                LOGGER.error("error while executing '" + matchedStr + "' during StringReplacer.replace(), error: " + e.getMessage());
            }
        }

        if (replaceStr != null && replaceStr.length() > 0) {
            LOGGER.trace("replaced Macro " + matchedStr + " with value: " + replaceStr);
        }
        return new StringBuilder(input).replace(startPos, endPos, replaceStr).toString();
    }

    public static interface StringReplacer {
        public String replace(final String matchedMacro, final String newValue);
    }

    public static class URLEncoderReplacer implements StringReplacer {
        public String replace(String matchedMacro, String newValue) {
            try {
                return URLEncoder.encode(newValue, "UTF8"); // make sure replacement values are properly encoded
            } catch (UnsupportedEncodingException e) {
                LOGGER.error("unexpected error attempting to url-encode macro values: " + e.getMessage(),e);
            }
            return newValue;
        }
    }
}
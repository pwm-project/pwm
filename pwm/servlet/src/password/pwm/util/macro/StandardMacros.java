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

package password.pwm.util.macro;

import com.novell.ldapchai.exception.ChaiException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.LoginInfoBean;
import password.pwm.ldap.UserDataReader;
import password.pwm.util.PwmRandom;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public abstract class StandardMacros {
    private static final PwmLogger LOGGER = PwmLogger.forClass(StandardMacros.class);

    static final String PATTERN_OPTIONAL_PARAMETER_MATCH = "(:(?:/@|[^@])*)*"; // match param values inside @ and include @ if preceded by /
    static final String PATTERN_PARAMETER_SPLIT = "(?<![/]):"; //split on : except if preceded by /


    public static final List<Class<? extends MacroImplementation>> STANDARD_MACROS;
    static {
        final List<Class<? extends MacroImplementation>> defaultMacros = new ArrayList<>();
        defaultMacros.add(LdapMacro.class);
        defaultMacros.add(UserPwExpirationTimeMacro.class);
        defaultMacros.add(UserPwExpirationTimeDefaultMacro.class);
        defaultMacros.add(UserDaysUntilPwExpireMacro.class);
        defaultMacros.add(UserIDMacro.class);
        defaultMacros.add(UserEmailMacro.class);
        defaultMacros.add(UserPasswordMacro.class);
        defaultMacros.add(InstanceIDMacro.class);
        defaultMacros.add(CurrentTimeMacro.class);
        defaultMacros.add(DefaultEmailFromAddressMacro.class);
        defaultMacros.add(SiteURLMacro.class);
        defaultMacros.add(SiteHostMacro.class);
        defaultMacros.add(RandomCharMacro.class);
        defaultMacros.add(UUIDMacro.class);
        STANDARD_MACROS = Collections.unmodifiableList(defaultMacros);
    }

    static String stripMacroDelimiters(final String input) {
        return input.replaceAll("^@|@$",""); // strip leading / trailing @
    }

    static String unescapeParamValue(final String input) {
        String result = input;
        result = result.replace("/:", ":");
        result = result.replace("/@","@");
        return result;
    }

    static List<String> splitMacroParameters(final String input, String... ignoreValues) {
        final String strippedInput = stripMacroDelimiters(input);
        final String[] splitInput = strippedInput.split(PATTERN_PARAMETER_SPLIT);
        final List<String> returnObj = new ArrayList<>();
        final List<String> ignoreValueList = Arrays.asList(ignoreValues);
        for (final String value : splitInput) {
            if (!ignoreValueList.contains(value)) {
                returnObj.add(unescapeParamValue(value));
                ignoreValueList.remove(value);
            }
        }
        return returnObj;
    }

    public static class LdapMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@LDAP" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) throws MacroParseException {
            final UserDataReader userDataReader = macroRequestInfo.getUserDataReader();

            if (userDataReader == null) {
                return "";
            }

            final List<String> parameters = splitMacroParameters(matchValue,"LDAP");

            final String ldapAttr;
            if (parameters.size() > 0 && !parameters.get(0).isEmpty()) {
                ldapAttr = parameters.get(0);
            } else {
                throw new MacroParseException("required attribute name parameter is missing");
            }

            final int length;
            if (parameters.size() > 1 && !parameters.get(1).isEmpty()) {
                try {
                    length = Integer.parseInt(parameters.get(1));
                } catch (NumberFormatException e) {
                    throw new MacroParseException("error parsing length parameter: " + e.getMessage());
                }

                int maxLengthPermitted = Integer.parseInt(macroRequestInfo.getPwmApplication().getConfig().readAppProperty(AppProperty.MACRO_LDAP_ATTR_CHAR_MAX_LENGTH));
                if (length > maxLengthPermitted) {
                    throw new MacroParseException("maximum permitted length of LDAP attribute (" + maxLengthPermitted + ") exceeded");
                } else if (length <= 0) {
                    throw new MacroParseException("length parameter must be greater than zero");
                }
            } else {
                length = 0;
            }

            final String paddingChar;
            if (parameters.size() > 2 && !parameters.get(2).isEmpty()) {
                paddingChar = parameters.get(2);
            } else {
                paddingChar = "";
            }

            if (parameters.size() > 3) {
                throw new MacroParseException("too many parameters");
            }

            final String ldapValue;
            if (ldapAttr.equalsIgnoreCase("dn")) {
                ldapValue = userDataReader.getUserDN();
            } else {
                try {
                    ldapValue = userDataReader.readStringAttribute(ldapAttr);
                } catch (ChaiException e) {
                    LOGGER.trace("could not replace value for '" + matchValue + "', ldap error: " + e.getMessage());
                    return "";
                }

                if (ldapValue == null || ldapValue.length() < 1) {
                    LOGGER.trace("could not replace value for '" + matchValue + "', user does not have value for '" + ldapAttr + "'");
                    return "";
                }
            }

            String returnValue = ldapValue == null ? "" : ldapValue;
            if (length > 0 && length < returnValue.length()) {
                returnValue = returnValue.substring(0, length);
            }
            if (length > 0 && paddingChar.length() > 0) {
                while (returnValue.length() < length) {
                    returnValue += paddingChar;
                }
            }

            return returnValue;
        }
    }

    public static class InstanceIDMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@InstanceID@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            final PwmApplication pwmApplication = macroRequestInfo.getPwmApplication();

            if (pwmApplication == null) {
                LOGGER.error("could not replace value for '" + matchValue + "', pwmApplication is null");
                return "";
            }

            return pwmApplication.getInstanceID();
        }
    }

    public static class CurrentTimeMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@CurrentTime" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo

        ) throws MacroParseException {
            final List<String> parameters = splitMacroParameters(matchValue,"CurrentTime");

            final DateFormat dateFormat;
            if (parameters.size() > 0 && !parameters.get(0).isEmpty()) {
                try {
                    dateFormat = new SimpleDateFormat(parameters.get(0));
                } catch (IllegalArgumentException e) {
                    throw new MacroParseException(e.getMessage());
                }
            } else {
                dateFormat = new SimpleDateFormat(PwmConstants.DEFAULT_DATETIME_FORMAT_STR);
            }

            final TimeZone tz;
            if (parameters.size() > 1 && !parameters.get(1).isEmpty()) {
                final String desiredTz = parameters.get(1);
                final List<String> avalibleIDs = Arrays.asList(TimeZone.getAvailableIDs());
                if (!avalibleIDs.contains(desiredTz)) {
                    throw new MacroParseException("unknown timezone");
                }
                tz = TimeZone.getTimeZone(desiredTz);
            } else {
                tz = PwmConstants.DEFAULT_TIMEZONE;
            }

            if (parameters.size() > 2) {
                throw new MacroParseException("too many parameters");
            }

            dateFormat.setTimeZone(tz);
            return dateFormat.format(new Date());
        }
    }

    public static class UserPwExpirationTimeMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@User:PwExpireTime" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo

        ) throws MacroParseException {
            final UserInfoBean userInfoBean = macroRequestInfo.getUserInfoBean();

            if (userInfoBean == null) {
                return "";
            }

            final Date pwdExpirationTime = userInfoBean.getPasswordExpirationTime();
            if (pwdExpirationTime == null) {
                return "";
            }

            final String datePattern = matchValue.substring(19, matchValue.length() - 1);
            if (datePattern.length() > 0) {
                try {
                    final DateFormat dateFormat = new SimpleDateFormat(datePattern);
                    return dateFormat.format(pwdExpirationTime);
                } catch (IllegalArgumentException e) {
                    throw new MacroParseException(e.getMessage());
                }
            }

            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(pwdExpirationTime);
        }
    }

    public static class UserPwExpirationTimeDefaultMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@User:PwExpireTime@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            final UserInfoBean userInfoBean = macroRequestInfo.getUserInfoBean();

            if (userInfoBean == null) {
                return "";
            }

            final Date pwdExpirationTime = userInfoBean.getPasswordExpirationTime();
            if (pwdExpirationTime == null) {
                return "";
            }

            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(pwdExpirationTime);
        }
    }

    public static class UserDaysUntilPwExpireMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@User:DaysUntilPwExpire@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            final UserInfoBean userInfoBean = macroRequestInfo.getUserInfoBean();

            if (userInfoBean == null) {
                LOGGER.error("could not replace value for '" + matchValue + "', userInfoBean is null");
                return "";
            }

            final Date pwdExpirationTime = userInfoBean.getPasswordExpirationTime();
            final TimeDuration timeUntilExpiration = TimeDuration.fromCurrent(pwdExpirationTime);
            final long daysUntilExpiration = timeUntilExpiration.getDays();


            return String.valueOf(daysUntilExpiration);
        }
    }

    public static class UserIDMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@User:ID@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            final UserInfoBean userInfoBean = macroRequestInfo.getUserInfoBean();

            if (userInfoBean == null || userInfoBean.getUsername() == null) {
                return "";
            }

            return userInfoBean.getUsername();
        }
    }

    public static class UserEmailMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@User:Email@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            final UserInfoBean userInfoBean = macroRequestInfo.getUserInfoBean();

            if (userInfoBean == null || userInfoBean.getUserEmailAddress() == null) {
                return "";
            }

            return userInfoBean.getUserEmailAddress();
        }
    }

    public static class UserPasswordMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@User:Password@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            final LoginInfoBean loginInfoBean = macroRequestInfo.getLoginInfoBean();

            try {
                if (loginInfoBean == null || loginInfoBean.getUserCurrentPassword() == null) {
                    return "";
                }

                return loginInfoBean.getUserCurrentPassword().getStringValue();
            } catch (PwmUnrecoverableException e) {
                LOGGER.error("error decrypting in memory password during macro replacement: " + e.getMessage());
                return "";
            }
        }
    }

    public static class SiteURLMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@SiteURL@|@Site:URL@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            return macroRequestInfo.getPwmApplication().getConfig().readSettingAsString(PwmSetting.PWM_SITE_URL);
        }
    }

    public static class DefaultEmailFromAddressMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@DefaultEmailFromAddress@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            return macroRequestInfo.getPwmApplication().getConfig().readSettingAsString(PwmSetting.EMAIL_DEFAULT_FROM_ADDRESS);
        }
    }

    public static class SiteHostMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@SiteHost@|@Site:Host@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            try {
                final String siteUrl = macroRequestInfo.getPwmApplication().getConfig().readSettingAsString(PwmSetting.PWM_SITE_URL);
                final URL url = new URL(siteUrl);
                return url.getHost();
            } catch (MalformedURLException e) {
                LOGGER.error("unable to parse configured/detected site URL: " + e.getMessage());
            }
            return "";
        }
    }

    public static class RandomCharMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@RandomChar(:[^@]*)?@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        )
                throws MacroParseException
        {
            if (matchValue == null || matchValue.length() < 1) {
                return "";
            }

            final List<String> parameters = splitMacroParameters(matchValue,"RandomChar");
            int length = 1;
            if (parameters.size() > 0 && !parameters.get(0).isEmpty()) {
                int maxLengthPermitted = Integer.parseInt(macroRequestInfo.getPwmApplication().getConfig().readAppProperty(AppProperty.MACRO_RANDOM_CHAR_MAX_LENGTH));
                try {
                    length = Integer.parseInt(parameters.get(0));
                    if (length > maxLengthPermitted) {
                        throw new MacroParseException("maximum permitted length of RandomChar (" + maxLengthPermitted + ") exceeded");
                    } else if (length <= 0) {
                        throw new MacroParseException("length of RandomChar (" + maxLengthPermitted + ") must be greater than zero");
                    }
                } catch (NumberFormatException e) {
                    throw new MacroParseException("error parsing length parameter of RandomChar: " + e.getMessage());
                }
            }

            if (parameters.size() > 1 && !parameters.get(1).isEmpty()) {
                final String chars = parameters.get(1);
                return PwmRandom.getInstance().alphaNumericString(chars,length);
            } else {
                return PwmRandom.getInstance().alphaNumericString(length);
            }
        }
    }

    public static class UUIDMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@UUID@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            return PwmRandom.getInstance().randomUUID().toString();
        }
    }

}

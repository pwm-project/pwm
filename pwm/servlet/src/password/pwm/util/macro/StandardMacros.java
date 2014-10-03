/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public abstract class StandardMacros {
    private static final PwmLogger LOGGER = PwmLogger.forClass(StandardMacros.class);

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
        defaultMacros.add(CurrentTimeDefaultMacro.class);
        defaultMacros.add(SiteURLMacro.class);
        defaultMacros.add(SiteHostMacro.class);
        defaultMacros.add(RandomCharMacro.class);
        STANDARD_MACROS = Collections.unmodifiableList(defaultMacros);
    }

    public static class LdapMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@LDAP:.*?@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            final UserDataReader userDataReader = macroRequestInfo.getUserDataReader();

            if (userDataReader == null) {
                return "";
            }

            final String ldapAttr = matchValue.substring(6,matchValue.length() -1);

            if (ldapAttr.equalsIgnoreCase("dn")) {
                return userDataReader.getUserDN();
            }

            final String ldapValue;
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

            return ldapValue;
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
        private static final Pattern PATTERN = Pattern.compile("@CurrentTime:.*?@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo

        ) {
            final String datePattern = matchValue.substring(13,matchValue.length() -1);

            if (datePattern.length() > 0) {
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

    public static class CurrentTimeDefaultMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@CurrentTime@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date());
        }
    }

    public static class UserPwExpirationTimeMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@User:PwExpireTime:.*?@");

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

            final String datePattern = matchValue.substring(19,matchValue.length() -1);
            if (datePattern.length() > 0) {
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
        private static final Pattern PATTERN = Pattern.compile("@SiteURL@");

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

    public static class SiteHostMacro extends AbstractMacro {
        private static final Pattern PATTERN = Pattern.compile("@SiteHost@");

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
        private static final Pattern PATTERN = Pattern.compile("@RandomChar:[0-9]+(:.+)?@");

        public Pattern getRegExPattern() {
            return PATTERN;
        }

        public String replaceValue(
                final String matchValue,
                final MacroRequestInfo macroRequestInfo
        ) {
            if (matchValue == null || matchValue.length() < 1) {
                return "";
            }

            final String cleanedMatchValue = matchValue.replaceAll("^@|@$",""); // strip leading / trailing @

            final String[] splitString = cleanedMatchValue.split(":");
            int length = 1;
            if (splitString.length > 1) {
                try {
                    length = Integer.parseInt(splitString[1]);
                } catch (NumberFormatException e) {
                    return "[macro error: unable to parse character quantity from value '" +splitString[1]+ "', error=" + e.getMessage() + "]";
                }
            }

            if (splitString.length > 2) {
                String chars = splitString[2];
                return PwmRandom.getInstance().alphaNumericString(chars,length);
            } else {
                return PwmRandom.getInstance().alphaNumericString(length);
            }
        }
    }
}

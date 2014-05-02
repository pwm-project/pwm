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
import password.pwm.ldap.UserDataReader;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.TimeDuration;

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
    private static final PwmLogger LOGGER = PwmLogger.getLogger(StandardMacros.class);

    public static final List<Class> STANDARD_MACROS;
    static {
        final List<Class> defaultMacros = new ArrayList<Class>();
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
        public UserDataReader userDataReader;

        public LdapMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean, UserDataReader userDataReader)
        {
            this.userDataReader = userDataReader;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@LDAP:.*?@");
        }

        public String replaceValue(
                String matchValue
        ) {
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
        public PwmApplication pwmApplication;

        public InstanceIDMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
            this.pwmApplication = pwmApplication;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@InstanceID@");
        }

        public String replaceValue(
                String matchValue
        ) {
            if (pwmApplication == null) {
                LOGGER.error("could not replace value for '" + matchValue + "', pwmApplication is null");
                return "";
            }

            return pwmApplication.getInstanceID();
        }
    }

    public static class CurrentTimeMacro extends AbstractMacro {
        public PwmApplication pwmApplication;

        public CurrentTimeMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
            this.pwmApplication = pwmApplication;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@CurrentTime:.*?@");
        }

        public String replaceValue(
                String matchValue
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
        public CurrentTimeDefaultMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@CurrentTime@");
        }

        public String replaceValue(
                String matchValue
        ) {
            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date());
        }
    }

    public static class UserPwExpirationTimeMacro extends AbstractMacro {
        public UserInfoBean userInfoBean;

        public UserPwExpirationTimeMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
            this.userInfoBean = userInfoBean;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@User:PwExpireTime:.*?@");
        }

        public String replaceValue(
                String matchValue
        ) {
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
        public UserInfoBean userInfoBean;

        public UserPwExpirationTimeDefaultMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
            this.userInfoBean = userInfoBean;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@User:PwExpireTime@");
        }

        public String replaceValue(
                String matchValue
        ) {
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
        public UserInfoBean userInfoBean;

        public UserDaysUntilPwExpireMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
            this.userInfoBean = userInfoBean;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@User:DaysUntilPwExpire@");
        }

        public String replaceValue(
                String matchValue
        ) {
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
        public UserInfoBean userInfoBean;

        public UserIDMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
            this.userInfoBean = userInfoBean;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@User:ID@");
        }

        public String replaceValue(
                String matchValue
        ) {
            if (userInfoBean == null || userInfoBean.getUsername() == null) {
                return "";
            }

            return userInfoBean.getUsername();
        }
    }

    public static class UserEmailMacro extends AbstractMacro {
        public UserInfoBean userInfoBean;

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
            this.userInfoBean = userInfoBean;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@User:Email@");
        }

        public String replaceValue(
                String matchValue
        ) {
            if (userInfoBean == null || userInfoBean.getUserEmailAddress() == null) {
                return "";
            }

            return userInfoBean.getUserEmailAddress();
        }
    }

    public static class UserPasswordMacro extends AbstractMacro {
        public UserInfoBean userInfoBean;

        public UserPasswordMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
            this.userInfoBean = userInfoBean;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@User:Password@");
        }

        public String replaceValue(
                String matchValue
        ) {
            if (userInfoBean == null || userInfoBean.getUserCurrentPassword() == null) {
                return "";
            }

            return userInfoBean.getUserCurrentPassword();
        }
    }

    public static class SiteURLMacro extends AbstractMacro {
        public PwmApplication pwmApplication;

        public SiteURLMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {

            this.pwmApplication = pwmApplication;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@SiteURL@");
        }

        public String replaceValue(
                String matchValue
        ) {
            return pwmApplication.getSiteURL();
        }
    }

    public static class SiteHostMacro extends AbstractMacro {
        public PwmApplication pwmApplication;

        public SiteHostMacro() {
        }

        public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean,UserDataReader userDataReader) {
            this.pwmApplication = pwmApplication;
        }

        public Pattern getRegExPattern() {
            return Pattern.compile("@SiteHost@");
        }

        public String replaceValue(
                String matchValue
        ) {
            try {
                final URL url = new URL(pwmApplication.getSiteURL());
                return url.getHost();
            } catch (MalformedURLException e) {
                LOGGER.error("unable to parse configured/detected site URL: " + e.getMessage());
            }
            return "";
        }
    }

    public static class RandomCharMacro extends AbstractMacro {

        public Pattern getRegExPattern()
        {
            return Pattern.compile("@RandomChar:[0-9]+(:.+)?@");
        }

        public String replaceValue(String matchValue)
        {
            if (matchValue == null || matchValue.length() < 1) {
                return "";
            }

            matchValue = matchValue.replaceAll("^@|@$",""); // strip leading / trailing @

            final String[] splitString = matchValue.split(":");
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

        public void init(
                PwmApplication pwmApplication,
                UserInfoBean userInfoBean,
                UserDataReader userDataReader
        )
        {

        }
    }

}

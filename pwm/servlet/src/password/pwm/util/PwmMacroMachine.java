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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserInfoBean;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PwmMacroMachine {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmMacroMachine.class);

    private static final List<MacroImplementation> MACROS = new ArrayList<MacroImplementation>();
    static {
        MACROS.add(new LdapMacro());
        MACROS.add(new InstanceIDMacro());
        MACROS.add(new CurrentDateTimeMacro());
        MACROS.add(new CurrentDateMacro());
        MACROS.add(new CurrentTimeMacro());
        MACROS.add(new PwExpirationDateTimeMacro());
        MACROS.add(new PwExpirationDateMacro());
        MACROS.add(new PwExpirationTimeMacro());
        MACROS.add(new DaysUntilPwExpireMacro());
        MACROS.add(new SiteURLMacro());
        MACROS.add(new SiteHostMacro());
    }

    private PwmMacroMachine() {
    }

    private interface MacroImplementation {
        public Pattern getRegExPattern();
        public String replaceValue(final String input, final PwmApplication pwmApplication, final UserInfoBean uiBean);
    }

    private static class LdapMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@LDAP:.*@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            if (uiBean == null) {
                LOGGER.error("could not replace value for '" + matchValue + "', userInfoBean is null");
                return "";
            }

            final String ldapAttr = matchValue.substring(6,matchValue.length() -1);
            final String ldapValue = uiBean.getAllUserAttributes().get(ldapAttr);
            if (ldapValue == null || ldapValue.length() < 1) {
                LOGGER.debug("could not replace value for '" + matchValue + "', user does not have value for " + ldapAttr );
                return "";
            } else {
                LOGGER.debug("replaced value for '" + matchValue + "', with ldap attribute value: " + ldapValue);
                return ldapValue;
            }
        }
    }

    private static class InstanceIDMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:InstanceID@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            if (pwmApplication == null) {
                LOGGER.error("could not replace value for '" + matchValue + "', pwmApplication is null");
                return "";
            }

            return pwmApplication.getInstanceID();
        }
    }

    private static class CurrentDateTimeMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:CurrentDateTime@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date());
        }
    }

    private static class CurrentDateMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:CurrentDate@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            return PwmConstants.DEFAULT_DATE_FORMAT.format(new Date());
        }
    }

    private static class CurrentTimeMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:CurrentTime@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            return PwmConstants.DEFAULT_TIME_FORMAT.format(new Date());
        }
    }

    private static class PwExpirationDateTimeMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:PwExpireDateTime@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            if (uiBean == null) {
                LOGGER.error("could not replace value for '" + matchValue + "', userInfoBean is null");
                return "";
            }

            final Date pwdExpirationTime = uiBean.getPasswordExpirationTime();
            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(pwdExpirationTime);
        }
    }

    private static class PwExpirationDateMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:PwExpireDate@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            if (uiBean == null) {
                LOGGER.error("could not replace value for '" + matchValue + "', userInfoBean is null");
                return "";
            }

            final Date pwdExpirationTime = uiBean.getPasswordExpirationTime();
            return PwmConstants.DEFAULT_DATE_FORMAT.format(pwdExpirationTime);
        }
    }

    private static class PwExpirationTimeMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:PwExpireTime@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            if (uiBean == null) {
                LOGGER.error("could not replace value for '" + matchValue + "', userInfoBean is null");
                return "";
            }

            final Date pwdExpirationTime = uiBean.getPasswordExpirationTime();
            return PwmConstants.DEFAULT_TIME_FORMAT.format(pwdExpirationTime);
        }
    }

    private static class DaysUntilPwExpireMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:DaysUntilPwExpire@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
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

    private static class SiteURLMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:SiteURL@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            return pwmApplication.getSiteURL();
        }
    }

    private static class SiteHostMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:SiteHost@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            try {
                final URL url = new URL(pwmApplication.getSiteURL());
                return url.getHost();
            } catch (MalformedURLException e) {
                LOGGER.error("unable to parse configured/detected site URL: " + e.getMessage());
            }
            return "";
        }
    }

    public static String expandMacros(final String input, final PwmApplication pwmApplication, final UserInfoBean uiBean) {
        if (input == null) {
            return null;
        }

        if (input.length() < 1) {
            return input;
        }

        final StringBuilder sb = new StringBuilder(input);

        for (MacroImplementation configVar : MACROS) {
            final Pattern pattern = configVar.getRegExPattern();
            final Matcher matcher = pattern.matcher(sb.toString());
            while (matcher.find()) {
                final String replaceString = doReplace(sb.toString(), configVar, matcher, pwmApplication, uiBean);
                sb.delete(0, sb.length());
                sb.append(replaceString);
            }
        }

        return sb.toString();
    }

    private static String doReplace(final String input, MacroImplementation configVar, Matcher matcher, PwmApplication pwmApplication, UserInfoBean uiBean) {
        final String matchedStr = matcher.group();
        final int startPos = matcher.start();
        final int endPos = matcher.end();
        String replaceStr = "";
        try {
            replaceStr = configVar.replaceValue(matchedStr,pwmApplication,uiBean);
        }  catch (Exception e) {
            LOGGER.error("error while replacing PwmVariable '" + matchedStr + "', error: " + e.getMessage());
        }
        return new StringBuilder(input).replace(startPos, endPos, replaceStr).toString();
    }
}

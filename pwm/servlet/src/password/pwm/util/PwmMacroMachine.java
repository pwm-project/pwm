package password.pwm.util;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserInfoBean;

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
        MACROS.add(new CurrentDateTimeMacro());
        MACROS.add(new CurrentDateMacro());
        MACROS.add(new CurrentTimeMacro());
        MACROS.add(new PwExpirationDateTimeMacro());
        MACROS.add(new PwExpirationDateMacro());
        MACROS.add(new PwExpirationTimeMacro());
        MACROS.add(new DaysUntilPwExpireMacro());
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
            return Pattern.compile("@PWM:CurrentDateTime@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            return PwmConstants.DEFAULT_DATE_FORMAT.format(new Date());
        }
    }

    private static class CurrentTimeMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:CurrentDateTime@");
        }

        public String replaceValue(String matchValue, PwmApplication pwmApplication, UserInfoBean uiBean) {
            return PwmConstants.DEFAULT_TIME_FORMAT.format(new Date());
        }
    }

    private static class PwExpirationDateTimeMacro implements MacroImplementation {
        public Pattern getRegExPattern() {
            return Pattern.compile("@PWM:DaysUntilPwExpire@");
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
            return Pattern.compile("@PWM:DaysUntilPwExpire@");
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
            return Pattern.compile("@PWM:DaysUntilPwExpire@");
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

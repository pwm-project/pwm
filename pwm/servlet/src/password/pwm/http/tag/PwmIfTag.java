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

package password.pwm.http.tag;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmSession;
import password.pwm.util.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.util.ArrayList;
import java.util.List;

public class PwmIfTag extends BodyTagSupport {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmIfTag.class);

    private String test;
    private String arg1;

    public void setTest(String test)
    {
        this.test = test;
    }

    public void setArg1(String arg1)
    {
        this.arg1 = arg1;
    }

    @Override
    public int doStartTag()
            throws JspException
    {
        boolean showBody = false;
        if (test != null) {
            try {
                final HttpServletRequest req = (HttpServletRequest)pageContext.getRequest();
                final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
                final PwmSession pwmSession = PwmSession.getPwmSession(req);

                boolean validTestName = false;
                for (TESTS testEnum : TESTS.values()) {
                    validTestName = true;
                    if (testEnum.toString().equals(test)) {
                        try {
                            showBody = testEnum.getTest().test(pwmApplication,pwmSession, this.readArgs());
                        } catch (ChaiUnavailableException e) {
                            LOGGER.error("error testing jsp if '" + testEnum.toString() + "', error: " + e.getMessage());
                        }
                    }
                }
                if (!validTestName) {
                    final String errorMsg = "unknown test name '" + test + "' in pwm:If jsp tag!";
                    LOGGER.warn(pwmSession,errorMsg);
                }
            } catch (PwmUnrecoverableException e) {
                e.printStackTrace();
            }

        }

        return showBody ? EVAL_BODY_INCLUDE : SKIP_BODY;
    }

    private String[] readArgs() {
        final List<String> argsList = new ArrayList<>();
        if (arg1 != null) {
            argsList.add(arg1);
        }

        return argsList.isEmpty() ? null : argsList.toArray(new String[argsList.size()]);
    }

    enum TESTS {
        showIcons(new BooleanAppPropertyTest(AppProperty.CLIENT_JSP_SHOW_ICONS)),
        showCancel(new BooleanPwmSettingTest(PwmSetting.DISPLAY_CANCEL_BUTTON)),
        showReset(new BooleanPwmSettingTest(PwmSetting.DISPLAY_RESET_BUTTON)),
        showHome(new BooleanPwmSettingTest(PwmSetting.DISPLAY_HOME_BUTTON)),
        showLoginOptions(new BooleanPwmSettingTest(PwmSetting.DISPLAY_LOGIN_PAGE_OPTIONS)),
        showStrengthMeter(new BooleanPwmSettingTest(PwmSetting.PASSWORD_SHOW_STRENGTH_METER)),
        showRandomPasswordGenerator(new BooleanPwmSettingTest(PwmSetting.PASSWORD_SHOW_AUTOGEN)),
        permission(new BooleanPermissionTest()),
        otpEnabled(new BooleanPwmSettingTest(PwmSetting.OTP_ENABLED)),
        booleanSetting(new BooleanPwmSettingTest(null)),
        stripInlineJavascript(new BooleanAppPropertyTest(AppProperty.SECURITY_STRIP_INLINE_JAVASCRIPT)),

        ;
        private Test test;

        TESTS(Test test)
        {
            this.test = test;
        }

        public Test getTest()
        {
            return test;
        }
    }

    interface Test {
        boolean test(
                final PwmApplication pwmApplication,
                final PwmSession pwmSession,
                final String... args
        )
                throws ChaiUnavailableException, PwmUnrecoverableException;
    }

    private static class BooleanAppPropertyTest implements Test {
        private AppProperty appProperty;

        private BooleanAppPropertyTest(AppProperty appProperty)
        {
            this.appProperty = appProperty;
        }

        public boolean test(
                PwmApplication pwmApplication,
                PwmSession pwmSession,
                String... args
        )
        {
            if (pwmApplication != null && pwmApplication.getConfig() != null) {
                final String strValue = pwmApplication.getConfig().readAppProperty(appProperty);
                return Boolean.parseBoolean(strValue);
            }
            return false;
        }
    }

    private static class BooleanPwmSettingTest implements Test {
        private PwmSetting pwmSetting;

        private BooleanPwmSettingTest(PwmSetting pwmSetting)
        {
            this.pwmSetting = pwmSetting;
        }

        public boolean test(
                PwmApplication pwmApplication,
                PwmSession pwmSession,
                String... args
        )
        {
            return pwmApplication != null && pwmApplication.getConfig() != null &&
                    pwmApplication.getConfig().readSettingAsBoolean(pwmSetting);
        }
    }

    private static class BooleanPermissionTest implements Test {
        public boolean test(
                PwmApplication pwmApplication,
                PwmSession pwmSession,
                String... args
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if (args == null || args.length < 1) {
                return false;
            }

            final String permissionName = args[0];
            Permission permission = null;
            for (Permission loopPerm : Permission.values()) {
                if (loopPerm.toString().equals(permissionName)) {
                    permission = loopPerm;
                }
            }

            return permission != null && pwmSession != null &&
                    pwmSession.getSessionManager().checkPermission(pwmApplication, permission);
        }
    }
}

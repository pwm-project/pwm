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

package password.pwm.http.tag;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.util.Helper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.util.ArrayList;
import java.util.List;

public class PwmIfTag extends BodyTagSupport {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmIfTag.class);

    private String test;
    private String arg1;
    private boolean negate;

    public void setTest(String test)
    {
        this.test = test;
    }

    public void setArg1(String arg1)
    {
        this.arg1 = arg1;
    }

    public void setNegate(boolean negate)
    {
        this.negate = negate;
    }

    @Override
    public int doStartTag()
            throws JspException
    {
        boolean showBody = false;
        if (test != null) {
            try {

                final PwmRequest pwmRequest = PwmRequest.forRequest((HttpServletRequest) pageContext.getRequest(),
                        (HttpServletResponse) pageContext.getResponse());
                final PwmSession pwmSession = pwmRequest.getPwmSession();

                boolean validTestName = false;
                for (TESTS testEnum : TESTS.values()) {
                    validTestName = true;
                    if (testEnum.toString().equals(test)) {
                        try {
                            showBody = testEnum.getTest().test(pwmRequest, this.readArgs());
                        } catch (ChaiUnavailableException e) {
                            LOGGER.error(
                                    "error testing jsp if '" + testEnum.toString() + "', error: " + e.getMessage());
                        }
                    }
                }
                if (!validTestName) {
                    final String errorMsg = "unknown test name '" + test + "' in pwm:If jsp tag!";
                    LOGGER.warn(pwmSession, errorMsg);
                }
            } catch (PwmUnrecoverableException e) {
                LOGGER.error("error executing PwmIfTag for test '" + test + "', error: " + e.getMessage());
            }
        }

        if (negate) {
            showBody = !showBody;
        }

        return showBody ? EVAL_BODY_INCLUDE : SKIP_BODY;
    }

    private String[] readArgs()
    {
        final List<String> argsList = new ArrayList<>();
        if (arg1 != null) {
            argsList.add(arg1);
        }

        return argsList.isEmpty() ? null : argsList.toArray(new String[argsList.size()]);
    }

    public enum TESTS {
        authenticated(new AuthenticatedTest()),
        configurationOpen(new ConfigurationOpen()),
        showIcons(new BooleanAppPropertyTest(AppProperty.CLIENT_JSP_SHOW_ICONS)),
        showCancel(new BooleanPwmSettingTest(PwmSetting.DISPLAY_CANCEL_BUTTON)),
        showHome(new BooleanPwmSettingTest(PwmSetting.DISPLAY_HOME_BUTTON)),
        showLogout(new BooleanPwmSettingTest(PwmSetting.DISPLAY_LOGOUT_BUTTON)),
        showLoginOptions(new BooleanPwmSettingTest(PwmSetting.DISPLAY_LOGIN_PAGE_OPTIONS)),
        showStrengthMeter(new BooleanPwmSettingTest(PwmSetting.PASSWORD_SHOW_STRENGTH_METER)),
        showRandomPasswordGenerator(new BooleanPwmSettingTest(PwmSetting.PASSWORD_SHOW_AUTOGEN)),
        permission(new BooleanPermissionTest()),
        otpEnabled(new BooleanPwmSettingTest(PwmSetting.OTP_ENABLED)),
        hasStoredOtpTimestamp(new HasStoredOtpTimestamp()),
        setupChallengeEnabled(new BooleanPwmSettingTest(PwmSetting.CHALLENGE_ENABLE)),
        updateProfileEnabled(new BooleanPwmSettingTest(PwmSetting.UPDATE_PROFILE_ENABLE)),
        shortcutsEnabled(new BooleanPwmSettingTest(PwmSetting.SHORTCUT_ENABLE)),
        peopleSearchEnabled(new BooleanPwmSettingTest(PwmSetting.PEOPLE_SEARCH_ENABLE)),
        accountInfoEnabled(new BooleanPwmSettingTest(PwmSetting.ACCOUNT_INFORMATION_ENABLED)),

        forgottenPasswordEnabled(new BooleanPwmSettingTest(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)),
        forgottenUsernameEnabled(new BooleanPwmSettingTest(PwmSetting.FORGOTTEN_USERNAME_ENABLE)),
        activateUserEnabled(new BooleanPwmSettingTest(PwmSetting.ACTIVATE_USER_ENABLE)),
        newUserRegistrationEnabled(new BooleanPwmSettingTest(PwmSetting.NEWUSER_ENABLE)),

        booleanSetting(new BooleanPwmSettingTest(null)),
        stripInlineJavascript(new BooleanAppPropertyTest(AppProperty.SECURITY_STRIP_INLINE_JAVASCRIPT)),
        forcedPageView(new ForcedPageViewTest()),
        showErrorDetail(new ShowErrorDetailTest()),
        forwardUrlDefined(new ForwardUrlDefinedTest()),

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
                final PwmRequest pwmRequest,
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
                PwmRequest pwmRequest,
                String... args
        )
        {
            if (pwmRequest.getPwmApplication() != null && pwmRequest.getConfig() != null) {
                final String strValue = pwmRequest.getConfig().readAppProperty(appProperty);
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
                PwmRequest pwmRequest,
                String... args
        )
        {
            return pwmRequest != null && pwmRequest.getConfig() != null &&
                    pwmRequest.getConfig().readSettingAsBoolean(pwmSetting);
        }
    }

    private static class BooleanPermissionTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
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

            return permission != null && pwmRequest != null &&
                    pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(),
                            permission);
        }
    }

    private static class AuthenticatedTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                String... args
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.isAuthenticated();
        }
    }

    private static class ForcedPageViewTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                String... args
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.isForcedPageView();
        }
    }

    private static class ConfigurationOpen implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                String... args
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getPwmApplication().getApplicationMode() == PwmApplication.MODE.CONFIGURATION;
        }
    }

    private static class HasStoredOtpTimestamp implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                String... args
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if (!pwmRequest.isAuthenticated()) {
                return false;
            }
            if (pwmRequest.getPwmSession().getUserInfoBean().getOtpUserRecord() != null) {
                if (pwmRequest.getPwmSession().getUserInfoBean().getOtpUserRecord().getTimestamp() != null) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class ShowErrorDetailTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                String... args
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return Helper.determineIfDetailErrorMsgShown(pwmRequest.getPwmApplication());
        }
    }

    private static class ForwardUrlDefinedTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                String... args
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.hasForwardUrl();
        }
    }
}


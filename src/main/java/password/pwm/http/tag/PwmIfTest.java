package password.pwm.http.tag;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.health.HealthStatus;
import password.pwm.http.PwmRequest;
import password.pwm.svc.PwmService;
import password.pwm.util.Helper;

public enum PwmIfTest {
    authenticated(new AuthenticatedTest()),
    configurationOpen(new ConfigurationOpen()),
    showIcons(new BooleanAppPropertyTest(AppProperty.CLIENT_JSP_SHOW_ICONS)),
    showCancel(new BooleanPwmSettingTest(PwmSetting.DISPLAY_CANCEL_BUTTON)),
    showHome(new BooleanPwmSettingTest(PwmSetting.DISPLAY_HOME_BUTTON)),
    showLogout(new BooleanPwmSettingTest(PwmSetting.DISPLAY_LOGOUT_BUTTON)),
    showLoginOptions(new BooleanPwmSettingTest(PwmSetting.DISPLAY_LOGIN_PAGE_OPTIONS)),
    showStrengthMeter(new BooleanPwmSettingTest(PwmSetting.PASSWORD_SHOW_STRENGTH_METER)),
    showRandomPasswordGenerator(new BooleanPwmSettingTest(PwmSetting.PASSWORD_SHOW_AUTOGEN)),
    showHeaderMenu(new ShowHeaderMenuTest()),
    permission(new BooleanPermissionTest()),
    otpEnabled(new BooleanPwmSettingTest(PwmSetting.OTP_ENABLED)),
    hasStoredOtpTimestamp(new HasStoredOtpTimestamp()),
    setupChallengeEnabled(new BooleanPwmSettingTest(PwmSetting.CHALLENGE_ENABLE)),
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

    trialMode(new TrialModeTest()),
    configMode(new ConfigModeTest()),

    healthWarningsPresent(new HealthWarningsPresentTest()),
    usernameHasValue(new UsernameHasValueTest()),

    headerMenuIsVisible(new HeaderMenuIsVisibleTest()),

    ;



    private Test test;

    PwmIfTest(Test test)
    {
        this.test = test;
    }

    public Test getTest()
    {
        return test;
    }



    interface Test {
        boolean test(
                final PwmRequest pwmRequest,
                final Options options
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
                Options options
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
                Options options
        )
        {
            return pwmRequest != null && pwmRequest.getConfig() != null &&
                    pwmRequest.getConfig().readSettingAsBoolean(pwmSetting);
        }
    }

    private static class ShowHeaderMenuTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, Options options) throws ChaiUnavailableException, PwmUnrecoverableException {
            final PwmApplication.MODE applicationMode = pwmRequest.getPwmApplication().getApplicationMode();
            boolean configMode = applicationMode == PwmApplication.MODE.CONFIGURATION;
            boolean adminUser = pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN);
            if (Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.CLIENT_WARNING_HEADER_SHOW))) {
                if (!pwmRequest.getURL().isConfigManagerURL()) {
                    if (configMode || PwmConstants.TRIAL_MODE) {
                        return true;
                    } else if (pwmRequest.isAuthenticated()) {
                        if (adminUser && !pwmRequest.isForcedPageView()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private static class BooleanPermissionTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                Options options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final Permission permission = options.getPermission();

            if (permission == null) {
                return false;
            }

            return pwmRequest != null &&
                    pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(),
                            permission);
        }
    }

    private static class AuthenticatedTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                Options options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.isAuthenticated();
        }
    }

    private static class ForcedPageViewTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                Options options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.isForcedPageView();
        }
    }

    private static class ConfigurationOpen implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                Options options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getPwmApplication().getApplicationMode() == PwmApplication.MODE.CONFIGURATION;
        }
    }

    private static class HasStoredOtpTimestamp implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                Options options
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

    private static class  ShowErrorDetailTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                Options options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return Helper.determineIfDetailErrorMsgShown(pwmRequest.getPwmApplication());
        }
    }

    private static class ForwardUrlDefinedTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                Options options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.hasForwardUrl();
        }
    }

    private static class TrialModeTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, Options options) throws ChaiUnavailableException, PwmUnrecoverableException {
            return PwmConstants.TRIAL_MODE;
        }
    }

    private static class ConfigModeTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, Options options) throws ChaiUnavailableException, PwmUnrecoverableException {
            final PwmApplication.MODE applicationMode = pwmRequest.getPwmApplication().getApplicationMode();
            return applicationMode == PwmApplication.MODE.CONFIGURATION;
        }
    }

    private static class HealthWarningsPresentTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, Options options) throws ChaiUnavailableException, PwmUnrecoverableException {
            final HealthMonitor healthMonitor = pwmRequest.getPwmApplication().getHealthMonitor();
            if (healthMonitor != null && healthMonitor.status() == PwmService.STATUS.OPEN) {
                if (healthMonitor.getMostSevereHealthStatus() == HealthStatus.WARN) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class UsernameHasValueTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, Options options) throws ChaiUnavailableException, PwmUnrecoverableException {
            final String usernameValue = PwmValue.username.getValueOutput().valueOutput(pwmRequest, null);
            return usernameValue != null && !usernameValue.isEmpty();
        }
    }


    private static class HeaderMenuIsVisibleTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, Options options) throws ChaiUnavailableException, PwmUnrecoverableException {
            if (PwmConstants.TRIAL_MODE) {
                return true;
            }

            if (pwmRequest.getPwmApplication().getApplicationMode() != PwmApplication.MODE.RUNNING) {
                return true;
            }

            if (pwmRequest.isAuthenticated()) {
                if (pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN)) {
                    return true;
                }
            }

            return false;
        }
    }


    static class Options {
        private boolean negate;
        private Permission permission;

        public Options(boolean negate, Permission permission) {
            this.negate = negate;
            this.permission = permission;
        }

        public boolean isNegate() {
            return negate;
        }

        public Permission getPermission() {
            return permission;
        }
    }
}

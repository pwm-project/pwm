/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.http.tag.conditional;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ProfileType;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.health.HealthStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestFlag;
import password.pwm.svc.PwmService;
import password.pwm.util.Helper;

public enum PwmIfTest {
    authenticated(new AuthenticatedTest()),
    configurationOpen(new ConfigurationOpen()),
    endUserFunctionalityAvaiable(new EndUserFunctionalityTest()),
    showIcons(new BooleanAppPropertyTest(AppProperty.CLIENT_JSP_SHOW_ICONS)),
    showCancel(new BooleanPwmSettingTest(PwmSetting.DISPLAY_CANCEL_BUTTON)),
    showHome(new BooleanPwmSettingTest(PwmSetting.DISPLAY_HOME_BUTTON)),
    showLogout(new BooleanPwmSettingTest(PwmSetting.DISPLAY_LOGOUT_BUTTON)),
    showLoginOptions(new BooleanPwmSettingTest(PwmSetting.DISPLAY_LOGIN_PAGE_OPTIONS)),
    showStrengthMeter(new BooleanPwmSettingTest(PwmSetting.PASSWORD_SHOW_STRENGTH_METER)),
    showRandomPasswordGenerator(new BooleanPwmSettingTest(PwmSetting.PASSWORD_SHOW_AUTOGEN)),
    showHeaderMenu(new ShowHeaderMenuTest()),
    showVersionHeader(new BooleanAppPropertyTest(AppProperty.HTTP_HEADER_SEND_XVERSION)),
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

    updateProfileAvailable(new BooleanPwmSettingTest(PwmSetting.UPDATE_PROFILE_ENABLE), new ActorHasProfileTest(ProfileType.UpdateAttributes)),
    helpdeskAvailable(new BooleanPwmSettingTest(PwmSetting.HELPDESK_ENABLE), new ActorHasProfileTest(ProfileType.Helpdesk)),
    guestRegistrationAvailable(new BooleanPwmSettingTest(PwmSetting.GUEST_ENABLE), new BooleanPermissionTest(Permission.GUEST_REGISTRATION)),

    booleanSetting(new BooleanPwmSettingTest(null)),
    stripInlineJavascript(new BooleanAppPropertyTest(AppProperty.SECURITY_STRIP_INLINE_JAVASCRIPT)),
    forcedPageView(new ForcedPageViewTest()),
    showErrorDetail(new ShowErrorDetailTest()),
    forwardUrlDefined(new ForwardUrlDefinedTest()),

    trialMode(new TrialModeTest()),
    appliance(new EnvironmentFlagTest(PwmEnvironment.ApplicationFlag.Appliance)),

    healthWarningsVisible(new HealthWarningsVisibileTest()),

    headerMenuIsVisible(new HeaderMenuIsVisibleTest()),

    requestFlag(new RequestFlagTest()),

    ;



    private Test[] tests;

    PwmIfTest(final Test... test)
    {
        this.tests = test;
    }

    public Test[] getTests()
    {
        return tests;
    }

    public boolean passed(final PwmRequest pwmRequest, final PwmIfOptions options)
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        for (final PwmIfTest.Test loopTest : getTests()) {
            if (!loopTest.test(pwmRequest, options)) {
                return false;
            }
        }
        return true;
    }



    interface Test {
        boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException;
    }

    private static class BooleanAppPropertyTest implements Test {
        private final AppProperty appProperty;

        private BooleanAppPropertyTest(AppProperty appProperty)
        {
            this.appProperty = appProperty;
        }

        public boolean test(
                PwmRequest pwmRequest,
                PwmIfOptions options
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
        private final PwmSetting pwmSetting;

        private BooleanPwmSettingTest(PwmSetting pwmSetting)
        {
            this.pwmSetting = pwmSetting;
        }

        public boolean test(
                PwmRequest pwmRequest,
                PwmIfOptions options
        )
        {
            final PwmSetting setting = options != null && options.getPwmSetting() != null
                    ? options.getPwmSetting()
                    : this.pwmSetting;

            if (setting == null) {
                return false;
            }

            return pwmRequest != null && pwmRequest.getConfig() != null &&
                    pwmRequest.getConfig().readSettingAsBoolean(setting);
        }
    }

    private static class ShowHeaderMenuTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, PwmIfOptions options) throws ChaiUnavailableException, PwmUnrecoverableException {
            final PwmApplicationMode applicationMode = pwmRequest.getPwmApplication().getApplicationMode();
            boolean configMode = applicationMode == PwmApplicationMode.CONFIGURATION;
            boolean adminUser = pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN);
            if (Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.CLIENT_WARNING_HEADER_SHOW))) {
                if (configMode || PwmConstants.TRIAL_MODE) {
                    return true;
                } else if (pwmRequest.isAuthenticated()) {
                    if (adminUser && !pwmRequest.isForcedPageView()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static class BooleanPermissionTest implements Test {

        private final Permission constructorPermission;

        public BooleanPermissionTest(Permission constructorPermission) {
            this.constructorPermission = constructorPermission;
        }

        public BooleanPermissionTest() {
            this.constructorPermission = null;
        }

        public boolean test(
                PwmRequest pwmRequest,
                PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final Permission permission = constructorPermission != null ? constructorPermission : options.getPermission();

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
                PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.isAuthenticated();
        }
    }

    private static class ForcedPageViewTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.isForcedPageView();
        }
    }


    private static class HasStoredOtpTimestamp implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                PwmIfOptions options
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
                PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return Helper.determineIfDetailErrorMsgShown(pwmRequest.getPwmApplication());
        }
    }

    private static class ForwardUrlDefinedTest implements Test {
        public boolean test(
                PwmRequest pwmRequest,
                PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.hasForwardUrl();
        }
    }

    private static class TrialModeTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, PwmIfOptions options) throws ChaiUnavailableException, PwmUnrecoverableException {
            return PwmConstants.TRIAL_MODE;
        }
    }

    private static class ConfigurationOpen implements Test {
        @Override
        public boolean test(
                PwmRequest pwmRequest,
                PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getPwmApplication().getApplicationMode() == PwmApplicationMode.CONFIGURATION;
        }
    }


    private static class HealthWarningsVisibileTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, PwmIfOptions options) throws ChaiUnavailableException, PwmUnrecoverableException {
            if (pwmRequest.isFlag(PwmRequestFlag.HIDE_HEADER_WARNINGS)) {
                return false;
            }

            final PwmApplicationMode mode = pwmRequest.getPwmApplication().getApplicationMode();

            if (mode == PwmApplicationMode.CONFIGURATION) {
                return true;
            }

            final boolean adminUser = pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN);
            if (adminUser) {
                final HealthMonitor healthMonitor = pwmRequest.getPwmApplication().getHealthMonitor();
                if (healthMonitor != null && healthMonitor.status() == PwmService.STATUS.OPEN) {
                    if (healthMonitor.getMostSevereHealthStatus(HealthMonitor.CheckTimeliness.NeverBlock) == HealthStatus.WARN) {
                        return true;
                    }
                }
            }
            return false;
        }
    }


    private static class HeaderMenuIsVisibleTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, PwmIfOptions options) throws ChaiUnavailableException, PwmUnrecoverableException {
            if (PwmConstants.TRIAL_MODE) {
                return true;
            }

            if (pwmRequest.isFlag(PwmRequestFlag.HIDE_HEADER_WARNINGS)) {
                return false;
            }

            if (pwmRequest.getPwmApplication().getApplicationMode() != PwmApplicationMode.RUNNING) {
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

    private static class ActorHasProfileTest implements Test {

        private final ProfileType profileType;

        public ActorHasProfileTest(ProfileType profileType) {
            this.profileType = profileType;
        }

        @Override
        public boolean test(PwmRequest pwmRequest, PwmIfOptions options) throws ChaiUnavailableException, PwmUnrecoverableException {
            return pwmRequest.getPwmSession().getSessionManager().getProfile(pwmRequest.getPwmApplication(), profileType) != null;
        }
    }

    private static class RequestFlagTest implements Test {

        @Override
        public boolean test(PwmRequest pwmRequest, PwmIfOptions options) throws ChaiUnavailableException, PwmUnrecoverableException {
            if (options.getRequestFlag() == null) {
                return false;
            }
            return pwmRequest.isFlag(options.getRequestFlag());
        }
    }

    private static class EnvironmentFlagTest implements Test {
        private final PwmEnvironment.ApplicationFlag flag;

        public EnvironmentFlagTest(PwmEnvironment.ApplicationFlag flag) {
            this.flag = flag;
        }

        @Override
        public boolean test(PwmRequest pwmRequest, PwmIfOptions options) throws ChaiUnavailableException, PwmUnrecoverableException {
            return pwmRequest.getPwmApplication().getPwmEnvironment().getFlags().contains(flag);
        }
    }

    private static class EndUserFunctionalityTest implements Test {
        @Override
        public boolean test(PwmRequest pwmRequest, PwmIfOptions options) throws ChaiUnavailableException, PwmUnrecoverableException {
            return pwmRequest.endUserFunctionalityAvailable();
        }

    }

}

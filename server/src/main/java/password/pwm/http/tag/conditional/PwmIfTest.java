/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http.tag.conditional;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.bean.PasswordStatus;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.PeopleSearchProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.SetupOtpProfile;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.health.HealthStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestFlag;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.PwmService;
import password.pwm.util.java.StringUtil;

import java.util.Arrays;
import java.util.Optional;

public enum PwmIfTest
{
    authenticated( new AuthenticatedTest() ),
    configurationOpen( new ConfigurationOpen() ),
    endUserFunctionalityAvailable( new EndUserFunctionalityTest() ),
    showIcons( new BooleanAppPropertyTest( AppProperty.CLIENT_JSP_SHOW_ICONS ) ),
    showCancel( new BooleanPwmSettingTest( PwmSetting.DISPLAY_CANCEL_BUTTON ) ),
    maskTokenInput( new BooleanPwmSettingTest( PwmSetting.DISPLAY_MASK_TOKEN_FIELDS ) ),
    showHome( new BooleanPwmSettingTest( PwmSetting.DISPLAY_HOME_BUTTON ) ),
    showLogout( new BooleanPwmSettingTest( PwmSetting.DISPLAY_LOGOUT_BUTTON ) ),
    showLoginOptions( new BooleanPwmSettingTest( PwmSetting.DISPLAY_LOGIN_PAGE_OPTIONS ) ),
    showStrengthMeter( new BooleanPwmSettingTest( PwmSetting.PASSWORD_SHOW_STRENGTH_METER ) ),
    showRandomPasswordGenerator( new BooleanPwmSettingTest( PwmSetting.PASSWORD_SHOW_AUTOGEN ) ),
    showHeaderMenu( new ShowHeaderMenuTest() ),
    showVersionHeader( new BooleanAppPropertyTest( AppProperty.HTTP_HEADER_SEND_XVERSION ) ),
    permission( new BooleanPermissionTest() ),
    otpSetupEnabled( new SetupOTPEnabled() ),
    hasStoredOtpTimestamp( new HasStoredOtpTimestamp() ),
    hasCustomJavascript( new HasCustomJavascript() ),
    setupChallengeEnabled( new BooleanPwmSettingTest( PwmSetting.CHALLENGE_ENABLE ) ),
    shortcutsEnabled( new BooleanPwmSettingTest( PwmSetting.SHORTCUT_ENABLE ) ),
    peopleSearchAvailable(  new BooleanPwmSettingTest( PwmSetting.PEOPLE_SEARCH_ENABLE ), new ActorHasProfileTest( ProfileDefinition.PeopleSearch )  ),
    orgChartEnabled( new OrgChartEnabled() ),
    passwordExpired( new PasswordExpired() ),
    showMaskedTokenSelection( new BooleanAppPropertyTest( AppProperty.TOKEN_MASK_SHOW_SELECTION ) ),
    clientFormShowRegexEnabled( new BooleanAppPropertyTest( AppProperty.CLIENT_FORM_CLIENT_REGEX_ENABLED ) ),

    accountInfoEnabled( new BooleanPwmSettingTest( PwmSetting.ACCOUNT_INFORMATION_ENABLED ) ),

    forgottenPasswordEnabled( new BooleanPwmSettingTest( PwmSetting.FORGOTTEN_PASSWORD_ENABLE ) ),
    forgottenUsernameEnabled( new BooleanPwmSettingTest( PwmSetting.FORGOTTEN_USERNAME_ENABLE ) ),
    activateUserEnabled( new BooleanPwmSettingTest( PwmSetting.ACTIVATE_USER_ENABLE ) ),
    newUserRegistrationEnabled( new BooleanPwmSettingTest( PwmSetting.NEWUSER_ENABLE ) ),

    updateProfileAvailable( new BooleanPwmSettingTest( PwmSetting.UPDATE_PROFILE_ENABLE ), new ActorHasProfileTest( ProfileDefinition.UpdateAttributes ) ),
    helpdeskAvailable( new BooleanPwmSettingTest( PwmSetting.HELPDESK_ENABLE ), new ActorHasProfileTest( ProfileDefinition.Helpdesk ) ),
    DeleteAccountAvailable( new BooleanPwmSettingTest( PwmSetting.DELETE_ACCOUNT_ENABLE ), new ActorHasProfileTest( ProfileDefinition.DeleteAccount ) ),
    guestRegistrationAvailable( new BooleanPwmSettingTest( PwmSetting.GUEST_ENABLE ), new BooleanPermissionTest( Permission.GUEST_REGISTRATION ) ),

    booleanSetting( new BooleanPwmSettingTest( null ) ),
    stripInlineJavascript( new BooleanAppPropertyTest( AppProperty.SECURITY_STRIP_INLINE_JAVASCRIPT ) ),
    forcedPageView( new ForcedPageViewTest() ),
    showErrorDetail( new ShowErrorDetailTest() ),
    forwardUrlDefined( new ForwardUrlDefinedTest() ),

    trialMode( new TrialModeTest() ),
    appliance( new EnvironmentFlagTest( PwmEnvironment.ApplicationFlag.Appliance ) ),

    healthWarningsVisible( new HealthWarningsVisibleTest() ),

    headerMenuIsVisible( new HeaderMenuIsVisibleTest() ),

    requestFlag( new RequestFlagTest() ),;


    private Test[] tests;

    PwmIfTest( final Test... test )
    {
        this.tests = test;
    }

    public Test[] getTests( )
    {
        return tests == null ? null : Arrays.copyOf( tests, tests.length );
    }

    public boolean passed( final PwmRequest pwmRequest, final PwmIfOptions options )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        for ( final PwmIfTest.Test loopTest : getTests() )
        {
            if ( !loopTest.test( pwmRequest, options ) )
            {
                return false;
            }
        }
        return true;
    }


    interface Test
    {
        boolean test(
                PwmRequest pwmRequest,
                PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException;
    }

    private static class BooleanAppPropertyTest implements Test
    {
        private final AppProperty appProperty;

        private BooleanAppPropertyTest( final AppProperty appProperty )
        {
            this.appProperty = appProperty;
        }

        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
        {
            if ( pwmRequest.getPwmApplication() != null && pwmRequest.getConfig() != null )
            {
                final String strValue = pwmRequest.getConfig().readAppProperty( appProperty );
                return Boolean.parseBoolean( strValue );
            }
            return false;
        }
    }

    private static class BooleanPwmSettingTest implements Test
    {
        private final PwmSetting pwmSetting;

        private BooleanPwmSettingTest( final PwmSetting pwmSetting )
        {
            this.pwmSetting = pwmSetting;
        }

        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
        {
            final PwmSetting setting = options != null && options.getPwmSetting() != null
                    ? options.getPwmSetting()
                    : this.pwmSetting;

            if ( setting == null )
            {
                return false;
            }

            return pwmRequest != null && pwmRequest.getConfig() != null
                    && pwmRequest.getConfig().readSettingAsBoolean( setting );
        }
    }

    private static class ShowHeaderMenuTest implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final PwmApplicationMode applicationMode = pwmRequest.getPwmApplication().getApplicationMode();
            final boolean configMode = applicationMode == PwmApplicationMode.CONFIGURATION;
            final boolean adminUser = pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmApplication(), Permission.PWMADMIN );
            if ( Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.CLIENT_WARNING_HEADER_SHOW ) ) )
            {
                if ( configMode || PwmConstants.TRIAL_MODE )
                {
                    return true;
                }
                else if ( pwmRequest.isAuthenticated() )
                {
                    if ( adminUser && !pwmRequest.isForcedPageView() )
                    {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static class BooleanPermissionTest implements Test
    {

        private final Permission constructorPermission;

        BooleanPermissionTest( final Permission constructorPermission )
        {
            this.constructorPermission = constructorPermission;
        }

        BooleanPermissionTest( )
        {
            this.constructorPermission = null;
        }

        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final Permission permission = constructorPermission != null ? constructorPermission : options.getPermission();

            if ( permission == null )
            {
                return false;
            }

            return pwmRequest != null
                    && pwmRequest.getPwmSession().getSessionManager().checkPermission(
                    pwmRequest.getPwmApplication(),
                    permission );
        }
    }

    private static class AuthenticatedTest implements Test
    {
        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.isAuthenticated();
        }
    }

    private static class ForcedPageViewTest implements Test
    {
        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.isForcedPageView();
        }
    }


    private static class HasStoredOtpTimestamp implements Test
    {
        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if ( !pwmRequest.isAuthenticated() )
            {
                return false;
            }
            if ( pwmRequest.getPwmSession().getUserInfo().getOtpUserRecord() != null )
            {
                if ( pwmRequest.getPwmSession().getUserInfo().getOtpUserRecord().getTimestamp() != null )
                {
                    return true;
                }
            }
            return false;
        }
    }

    private static class ShowErrorDetailTest implements Test
    {
        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getPwmApplication().determineIfDetailErrorMsgShown();
        }
    }

    private static class ForwardUrlDefinedTest implements Test
    {
        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.hasForwardUrl();
        }
    }

    private static class TrialModeTest implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return PwmConstants.TRIAL_MODE;
        }
    }

    private static class ConfigurationOpen implements Test
    {
        @Override
        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getPwmApplication().getApplicationMode() == PwmApplicationMode.CONFIGURATION;
        }
    }


    private static class HealthWarningsVisibleTest implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if ( pwmRequest.isFlag( PwmRequestFlag.HIDE_HEADER_WARNINGS ) )
            {
                return false;
            }

            final PwmApplicationMode mode = pwmRequest.getPwmApplication().getApplicationMode();

            if ( mode == PwmApplicationMode.CONFIGURATION )
            {
                return true;
            }

            final boolean adminUser = pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmApplication(), Permission.PWMADMIN );
            if ( adminUser )
            {
                final HealthMonitor healthMonitor = pwmRequest.getPwmApplication().getHealthMonitor();
                if ( healthMonitor != null && healthMonitor.status() == PwmService.STATUS.OPEN )
                {
                    if ( healthMonitor.getMostSevereHealthStatus() == HealthStatus.WARN )
                    {
                        return true;
                    }
                }
            }
            return false;
        }
    }


    private static class HeaderMenuIsVisibleTest implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if ( PwmConstants.TRIAL_MODE )
            {
                return true;
            }

            if ( pwmRequest.isFlag( PwmRequestFlag.HIDE_HEADER_WARNINGS ) )
            {
                return false;
            }

            if ( pwmRequest.getPwmApplication().getApplicationMode() != PwmApplicationMode.RUNNING )
            {
                return true;
            }

            if ( pwmRequest.isForcedPageView() )
            {
                return false;
            }

            if ( pwmRequest.isAuthenticated() )
            {
                if ( pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmApplication(), Permission.PWMADMIN ) )
                {
                    return true;
                }
            }

            return false;
        }
    }

    private static class ActorHasProfileTest implements Test
    {

        private final ProfileDefinition profileDefinition;

        ActorHasProfileTest( final ProfileDefinition profileDefinition )
        {
            this.profileDefinition = profileDefinition;
        }

        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getPwmSession().getSessionManager().getProfile( pwmRequest.getPwmApplication(), profileDefinition ) != null;
        }
    }

    private static class RequestFlagTest implements Test
    {

        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if ( options.getRequestFlag() == null )
            {
                return false;
            }
            return pwmRequest.isFlag( options.getRequestFlag() );
        }
    }

    private static class EnvironmentFlagTest implements Test
    {
        private final PwmEnvironment.ApplicationFlag flag;

        EnvironmentFlagTest( final PwmEnvironment.ApplicationFlag flag )
        {
            this.flag = flag;
        }

        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getPwmApplication().getPwmEnvironment().getFlags().contains( flag );
        }
    }

    private static class EndUserFunctionalityTest implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.endUserFunctionalityAvailable();
        }

    }

    private static class OrgChartEnabled implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if ( pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE ) )
            {
                final Optional<PeopleSearchProfile> peopleSearchProfile = pwmRequest.isAuthenticated()
                        ? Optional.ofNullable( pwmRequest.getPwmSession().getSessionManager().getPeopleSearchProfile( ) )
                        : pwmRequest.getConfig().getPublicPeopleSearchProfile();

                if ( peopleSearchProfile.isPresent() )
                {
                    return peopleSearchProfile.get().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_ORGCHART );
                }
            }

            return false;
        }
    }

    private static class PasswordExpired implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if ( !pwmRequest.isAuthenticated() )
            {
                return false;
            }

            final UserInfo userInfoBean = pwmRequest.getPwmSession().getUserInfo();
            final PasswordStatus passwordStatus = userInfoBean.getPasswordStatus();
            return passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isViolatesPolicy();
        }
    }

    private static class SetupOTPEnabled implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if ( !pwmRequest.isAuthenticated() )
            {
                return false;
            }

            final SetupOtpProfile setupOtpProfile = pwmRequest.getPwmSession().getSessionManager().getSetupOTPProfile( );
            return setupOtpProfile != null && setupOtpProfile.readSettingAsBoolean( PwmSetting.OTP_ALLOW_SETUP );
        }
    }

    private static class HasCustomJavascript implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options )
        {
            final String customJs = pwmRequest.getConfig().readSettingAsString( PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT );
            return !StringUtil.isEmpty( customJs );
        }
    }

}

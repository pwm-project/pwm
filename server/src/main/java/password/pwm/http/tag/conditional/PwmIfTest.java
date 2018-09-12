/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.bean.PasswordStatus;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.SetupOtpProfile;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.health.HealthStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestFlag;
import password.pwm.http.servlet.peoplesearch.PeopleSearchConfiguration;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.PwmService;

import java.util.Arrays;

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
    setupChallengeEnabled( new BooleanPwmSettingTest( PwmSetting.CHALLENGE_ENABLE ) ),
    shortcutsEnabled( new BooleanPwmSettingTest( PwmSetting.SHORTCUT_ENABLE ) ),
    peopleSearchEnabled( new BooleanPwmSettingTest( PwmSetting.PEOPLE_SEARCH_ENABLE ) ),
    orgChartEnabled( new OrgChartEnabled() ),
    passwordExpired( new PasswordExpired() ),
    showMaskedTokenSelection( new BooleanAppPropertyTest( AppProperty.TOKEN_MASK_SHOW_SELECTION ) ),
    clientFormShowRegexEnabled( new BooleanAppPropertyTest( AppProperty.CLIENT_FORM_CLIENT_REGEX_ENABLED ) ),

    accountInfoEnabled( new BooleanPwmSettingTest( PwmSetting.ACCOUNT_INFORMATION_ENABLED ) ),

    forgottenPasswordEnabled( new BooleanPwmSettingTest( PwmSetting.FORGOTTEN_PASSWORD_ENABLE ) ),
    forgottenUsernameEnabled( new BooleanPwmSettingTest( PwmSetting.FORGOTTEN_USERNAME_ENABLE ) ),
    activateUserEnabled( new BooleanPwmSettingTest( PwmSetting.ACTIVATE_USER_ENABLE ) ),
    newUserRegistrationEnabled( new BooleanPwmSettingTest( PwmSetting.NEWUSER_ENABLE ) ),

    updateProfileAvailable( new BooleanPwmSettingTest( PwmSetting.UPDATE_PROFILE_ENABLE ), new ActorHasProfileTest( ProfileType.UpdateAttributes ) ),
    helpdeskAvailable( new BooleanPwmSettingTest( PwmSetting.HELPDESK_ENABLE ), new ActorHasProfileTest( ProfileType.Helpdesk ) ),
    DeleteAccountAvailable( new BooleanPwmSettingTest( PwmSetting.DELETE_ACCOUNT_ENABLE ), new ActorHasProfileTest( ProfileType.DeleteAccount ) ),
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
                    if ( healthMonitor.getMostSevereHealthStatus( HealthMonitor.CheckTimeliness.NeverBlock ) == HealthStatus.WARN )
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

        private final ProfileType profileType;

        ActorHasProfileTest( final ProfileType profileType )
        {
            this.profileType = profileType;
        }

        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getPwmSession().getSessionManager().getProfile( pwmRequest.getPwmApplication(), profileType ) != null;
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
            if ( !pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE ) )
            {
                return false;
            }

            return PeopleSearchConfiguration.forRequest( pwmRequest ).isOrgChartEnabled();
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

            final SetupOtpProfile setupOtpProfile = pwmRequest.getPwmSession().getSessionManager().getSetupOTPProfile( pwmRequest.getPwmApplication() );
            return setupOtpProfile != null && setupOtpProfile.readSettingAsBoolean( PwmSetting.OTP_ALLOW_SETUP );
        }
    }
}

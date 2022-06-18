/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import password.pwm.config.profile.ChangePasswordProfile;
import password.pwm.config.profile.PeopleSearchProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthService;
import password.pwm.health.HealthStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestFlag;
import password.pwm.user.UserInfo;
import password.pwm.svc.PwmService;
import password.pwm.svc.otp.OTPUserRecord;
import password.pwm.util.java.StringUtil;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    showRandomPasswordGenerator( new ShowRandomPasswordGeneratorTest() ),
    showHeaderMenu( new ShowHeaderMenuTest() ),
    showVersionHeader( new BooleanAppPropertyTest( AppProperty.HTTP_HEADER_SEND_XVERSION ) ),
    permission( new BooleanPermissionTest() ),
    otpSetupEnabled( new ProfileEnabled( ProfileDefinition.SetupOTPProfile ) ),
    hasStoredOtpTimestamp( new HasStoredOtpTimestamp() ),
    hasCustomJavascript( new HasCustomJavascript() ),
    setupResponsesEnabled( new ProfileEnabled( ProfileDefinition.SetupResponsesProfile ) ),
    shortcutsEnabled( new BooleanPwmSettingTest( PwmSetting.SHORTCUT_ENABLE ) ),
    peopleSearchAvailable( new BooleanPwmSettingTest( PwmSetting.PEOPLE_SEARCH_ENABLE ), new ActorHasProfileTest( ProfileDefinition.PeopleSearch ) ),
    orgChartEnabled( new OrgChartEnabled() ),
    passwordExpired( new PasswordExpired() ),
    showMaskedTokenSelection( new BooleanAppPropertyTest( AppProperty.TOKEN_MASK_SHOW_SELECTION ) ),
    clientFormShowRegexEnabled( new BooleanAppPropertyTest( AppProperty.CLIENT_FORM_CLIENT_REGEX_ENABLED ) ),
    multiDomain( new MultiDomainTest() ),

    forgottenPasswordEnabled( new BooleanPwmSettingTest( PwmSetting.FORGOTTEN_PASSWORD_ENABLE ) ),
    forgottenUsernameEnabled( new BooleanPwmSettingTest( PwmSetting.FORGOTTEN_USERNAME_ENABLE ) ),
    activateUserEnabled( new BooleanPwmSettingTest( PwmSetting.ACTIVATE_USER_ENABLE ) ),
    newUserRegistrationEnabled( new BooleanPwmSettingTest( PwmSetting.NEWUSER_ENABLE ) ),

    accountInfoEnabled( new BooleanPwmSettingTest( PwmSetting.ACCOUNT_INFORMATION_ENABLED ), new ActorHasProfileTest( ProfileDefinition.AccountInformation ) ),
    changePasswordAvailable( new BooleanPwmSettingTest( PwmSetting.CHANGE_PASSWORD_ENABLE ), new ActorHasProfileTest( ProfileDefinition.ChangePassword ) ),
    updateProfileAvailable( new BooleanPwmSettingTest( PwmSetting.UPDATE_PROFILE_ENABLE ), new ActorHasProfileTest( ProfileDefinition.UpdateAttributes ) ),
    helpdeskAvailable( new BooleanPwmSettingTest( PwmSetting.HELPDESK_ENABLE ), new ActorHasProfileTest( ProfileDefinition.Helpdesk ) ),
    deleteAccountAvailable( new BooleanPwmSettingTest( PwmSetting.DELETE_ACCOUNT_ENABLE ), new ActorHasProfileTest( ProfileDefinition.DeleteAccount ) ),
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


    private final Set<Test> tests;

    PwmIfTest( final Test... test )
    {
        tests = test == null ? Collections.emptySet() : Set.of( test );
    }

    private Set<Test> getTests()
    {
        return tests;
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

    private interface Test
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

        @Override
        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
        {
            if ( pwmRequest.getPwmDomain() != null && pwmRequest.getDomainConfig() != null )
            {
                final String strValue = pwmRequest.getDomainConfig().readAppProperty( appProperty );
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

        @Override
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

            return pwmRequest != null && pwmRequest.getDomainConfig() != null
                    && pwmRequest.getDomainConfig().readSettingAsBoolean( setting );
        }
    }

    private static class ShowHeaderMenuTest implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final PwmApplicationMode applicationMode = pwmRequest.getPwmDomain().getApplicationMode();
            final boolean configMode = applicationMode == PwmApplicationMode.CONFIGURATION;
            final boolean adminUser = pwmRequest.checkPermission( Permission.PWMADMIN );
            if ( Boolean.parseBoolean( pwmRequest.getDomainConfig().readAppProperty( AppProperty.CLIENT_WARNING_HEADER_SHOW ) ) )
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

        BooleanPermissionTest()
        {
            this.constructorPermission = null;
        }

        @Override
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

            return pwmRequest != null && pwmRequest.checkPermission( permission );
        }
    }

    private static class AuthenticatedTest implements Test
    {
        @Override
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
        @Override
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
        @Override
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

            final OTPUserRecord otpUserRecord = pwmRequest.getPwmSession().getUserInfo().getOtpUserRecord();
            return otpUserRecord != null && otpUserRecord.getTimestamp() != null;
        }
    }

    private static class ShowErrorDetailTest implements Test
    {
        @Override
        public boolean test(
                final PwmRequest pwmRequest,
                final PwmIfOptions options
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            return pwmRequest.getPwmDomain().determineIfDetailErrorMsgShown();
        }
    }

    private static class ForwardUrlDefinedTest implements Test
    {
        @Override
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
            return pwmRequest.getPwmDomain().getApplicationMode() == PwmApplicationMode.CONFIGURATION;
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

            final PwmApplicationMode mode = pwmRequest.getPwmDomain().getApplicationMode();

            if ( mode == PwmApplicationMode.CONFIGURATION )
            {
                return true;
            }

            final boolean adminUser = pwmRequest.checkPermission( Permission.PWMADMIN );
            if ( adminUser )
            {
                final HealthService healthService = pwmRequest.getPwmApplication().getHealthMonitor();
                if ( healthService != null && healthService.status() == PwmService.STATUS.OPEN )
                {
                    if ( healthService.getMostSevereHealthStatus() == HealthStatus.WARN )
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

            if ( pwmRequest.getPwmDomain().getApplicationMode() != PwmApplicationMode.RUNNING )
            {
                return true;
            }

            if ( pwmRequest.isForcedPageView() )
            {
                return false;
            }

            if ( pwmRequest.isAuthenticated() )
            {
                if ( pwmRequest.checkPermission( Permission.PWMADMIN ) )
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
            final String profileID = pwmRequest.getPwmSession().getUserInfo().getProfileIDs().get( profileDefinition );
            return StringUtil.notEmpty( profileID );
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
            if ( pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE ) )
            {
                final Optional<PeopleSearchProfile> peopleSearchProfile = pwmRequest.isAuthenticated()
                        ? Optional.ofNullable( pwmRequest.getPeopleSearchProfile() )
                        : pwmRequest.getDomainConfig().getPublicPeopleSearchProfile();

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

    private static class ProfileEnabled implements Test
    {
        private final ProfileDefinition profileDefinition;

        ProfileEnabled( final ProfileDefinition profileDefinition )
        {
            this.profileDefinition = Objects.requireNonNull( profileDefinition );
        }

        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if ( !pwmRequest.isAuthenticated() )
            {
                return false;
            }

            if ( profileDefinition.getEnabledSetting().isPresent() )
            {
                if ( !pwmRequest.getDomainConfig().readSettingAsBoolean( profileDefinition.getEnabledSetting().get() ) )
                {
                    return false;
                }
            }

            final String profileID = pwmRequest.getPwmSession().getUserInfo().getProfileIDs().get( profileDefinition );
            if ( StringUtil.isEmpty( profileID ) )
            {
                return false;
            }

            return true;
        }
    }

    private static class HasCustomJavascript implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options )
        {
            final String customJs = pwmRequest.getDomainConfig().readSettingAsString( PwmSetting.DISPLAY_CUSTOM_JAVASCRIPT );
            return StringUtil.notEmpty( customJs );
        }
    }

    private static class ShowRandomPasswordGeneratorTest implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws PwmUnrecoverableException
        {
            final ChangePasswordProfile changePasswordProfile = pwmRequest.getChangePasswordProfile();
            return changePasswordProfile.readSettingAsBoolean( PwmSetting.PASSWORD_SHOW_AUTOGEN );
        }
    }


    private static class MultiDomainTest implements Test
    {
        @Override
        public boolean test( final PwmRequest pwmRequest, final PwmIfOptions options ) throws PwmUnrecoverableException
        {
            return pwmRequest.getPwmApplication().isMultiDomain();
        }
    }
}


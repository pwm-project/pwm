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

package password.pwm.http.servlet.changepw;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.PasswordStatus;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.RequireCurrentPasswordMode;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ChangePasswordBean;
import password.pwm.http.servlet.forgottenpw.ForgottenPasswordUtil;
import password.pwm.ldap.PasswordChangeProgressChecker;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.event.AuditEvent;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordUtility;

import java.util.Locale;
import java.util.Map;

public class ChangePasswordServletUtil
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ChangePasswordServletUtil.class );

    static boolean determineIfCurrentPasswordRequired(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final RequireCurrentPasswordMode currentSetting = pwmRequest.getConfig().readSettingAsEnum( PwmSetting.PASSWORD_REQUIRE_CURRENT, RequireCurrentPasswordMode.class );

        if ( currentSetting == RequireCurrentPasswordMode.FALSE )
        {
            return false;
        }

        final LoginInfoBean loginInfoBean = pwmRequest.getPwmSession().getLoginInfoBean();
        if ( loginInfoBean.getType() == AuthenticationType.AUTH_FROM_PUBLIC_MODULE )
        {
            LOGGER.debug( pwmRequest, () -> "skipping user current password requirement, authentication type is " + AuthenticationType.AUTH_FROM_PUBLIC_MODULE );
            return false;
        }

        {
            final PasswordData currentPassword = loginInfoBean.getUserCurrentPassword();
            if ( currentPassword == null )
            {
                LOGGER.debug( pwmRequest, () -> "skipping user current password requirement, current password is not known to application" );
                return false;
            }
        }

        if ( currentSetting == RequireCurrentPasswordMode.TRUE )
        {
            return true;
        }

        final UserInfo userInfo = pwmRequest.getPwmSession().getUserInfo();
        final PasswordStatus passwordStatus = userInfo.getPasswordStatus();
        return currentSetting == RequireCurrentPasswordMode.NOTEXPIRED
                && !passwordStatus.isExpired()
                && !passwordStatus.isPreExpired()
                && !passwordStatus.isViolatesPolicy()
                && !userInfo.isRequiresNewPassword();

    }

    static void validateParamsAgainstLDAP(
            final Map<FormConfiguration, String> formValues,
            final PwmRequest pwmRequest,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmDataValidationException
    {
        for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
        {
            final FormConfiguration formItem = entry.getKey();
            final String attrName = formItem.getName();
            final String value = entry.getValue();
            try
            {
                if ( !theUser.compareStringAttribute( attrName, value ) )
                {
                    final String errorMsg = "incorrect value for '" + attrName + "'";
                    final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INCORRECT_RESPONSE, errorMsg, new String[]
                            {
                                    attrName,
                            }
                    );
                    LOGGER.debug( pwmRequest, errorInfo );
                    throw new PwmDataValidationException( errorInfo );
                }
                LOGGER.trace( pwmRequest, () -> "successful validation of ldap value for '" + attrName + "'" );
            }
            catch ( final ChaiOperationException e )
            {
                LOGGER.error( pwmRequest, () -> "error during param validation of '" + attrName + "', error: " + e.getMessage() );
                throw new PwmDataValidationException( new ErrorInformation(
                        PwmError.ERROR_INCORRECT_RESPONSE,
                        "ldap error testing value for '" + attrName + "'",
                        new String[]
                                {
                                        attrName,
                                }
                ) );
            }
        }
    }

    static void sendChangePasswordEmailNotice(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final Locale locale = pwmRequest.getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_CHANGEPASSWORD, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequest, () -> "skipping change password email for '" + pwmRequest.getUserInfoIfLoggedIn() + "' no email configured" );
            return;
        }

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmRequest.getPwmSession().getUserInfo(),

                pwmRequest.getPwmSession().getSessionManager().getMacroMachine( ) );
    }

    static void checkMinimumLifetime(
            final PwmRequest pwmRequest,
            final ChangePasswordBean changePasswordBean,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        if ( changePasswordBean.isNextAllowedTimePassed() )
        {
            return;
        }

        if ( userInfo.isWithinPasswordMinimumLifetime() )
        {
            boolean allowChange = false;
            if ( pwmRequest.getPwmSession().getLoginInfoBean().getAuthFlags().contains( AuthenticationType.AUTH_FROM_PUBLIC_MODULE ) )
            {
                allowChange = ForgottenPasswordUtil.permitPwChangeDuringMinLifetime(
                        pwmRequest.getPwmApplication(),
                        pwmRequest.getLabel(),
                        userInfo.getUserIdentity()
                );

            }

            if ( allowChange )
            {
                LOGGER.debug( pwmRequest, () -> "current password is too young, but skipping enforcement of minimum lifetime check due to setting "
                        + PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS.toMenuLocationDebug( null, pwmRequest.getLocale() ) );
            }
            else
            {
                PasswordUtility.throwPasswordTooSoonException( userInfo, pwmRequest.getLabel() );
            }
        }

        changePasswordBean.setNextAllowedTimePassed( true );
    }

    static void executeChangePassword(
            final PwmRequest pwmRequest,
            final PasswordData newPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        // password accepted, setup change password
        final ChangePasswordBean cpb = pwmApplication.getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );

        // change password
        PasswordUtility.setActorPassword( pwmRequest, pwmApplication, newPassword );

        //init values for progress screen
        {
            final PasswordChangeProgressChecker.ProgressTracker tracker = new PasswordChangeProgressChecker.ProgressTracker();
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(
                    pwmApplication,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getLabel(),
                    pwmRequest.getLocale()
            );
            cpb.setChangeProgressTracker( tracker );
            cpb.setChangePasswordMaxCompletion( checker.maxCompletionTime( tracker ) );
        }

        // send user an email confirmation
        ChangePasswordServletUtil.sendChangePasswordEmailNotice( pwmRequest );

        // send audit event
        pwmApplication.getAuditManager().submit( AuditEvent.CHANGE_PASSWORD, pwmSession.getUserInfo(), pwmSession );
    }

    static boolean warnPageShouldBeShown(
            final PwmRequest pwmRequest,
            final ChangePasswordBean changePasswordBean
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if ( !pwmSession.getUserInfo().getPasswordStatus().isWarnPeriod() )
        {
            return false;
        }

        if ( pwmRequest.getPwmSession().getLoginInfoBean().isLoginFlag( LoginInfoBean.LoginFlag.skipNewPw ) )
        {
            return false;
        }

        if ( changePasswordBean.isWarnPassed() )
        {
            return false;
        }

        if ( pwmRequest.getPwmSession().getLoginInfoBean().getAuthFlags().contains( AuthenticationType.AUTH_FROM_PUBLIC_MODULE ) )
        {
            return false;
        }

        if ( pwmRequest.getPwmSession().getLoginInfoBean().getType() == AuthenticationType.AUTH_FROM_PUBLIC_MODULE )
        {
            return false;
        }

        return true;
    }
}

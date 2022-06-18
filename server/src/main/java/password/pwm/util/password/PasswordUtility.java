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

package password.pwm.util.password;

import com.novell.ldapchai.ChaiPasswordPolicy;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.impl.oracleds.entry.OracleDSEntries;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.provider.DirectoryVendor;
import com.novell.ldapchai.util.ChaiUtility;
import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;
import password.pwm.AppProperty;
import password.pwm.PwmDomain;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.HelpdeskClearResponseMode;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.StrengthMeterType;
import password.pwm.config.profile.AbstractProfile;
import password.pwm.config.profile.ChangePasswordProfile;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestContext;
import password.pwm.http.PwmSession;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.user.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.svc.cache.CacheKey;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.svc.cache.CacheService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.sms.SmsQueueService;
import password.pwm.svc.stats.AvgStatistic;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.PasswordData;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.operations.ActionExecutor;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Jason D. Rivard
 */
public class PasswordUtility
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordUtility.class );
    private static final String NEGATIVE_CACHE_HIT = "NEGATIVE_CACHE_HIT";

    public static String sendNewPassword(
            final UserInfo userInfo,
            final PwmDomain pwmDomain,
            final PasswordData newPassword,
            final Locale userLocale,
            final MessageSendMethod messageSendMethod
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String emailAddress = userInfo.getUserEmailAddress();
        final String smsNumber = userInfo.getUserSmsNumber();
        String returnToAddress = emailAddress;

        final MacroRequest macroRequest;
        {
            final LoginInfoBean loginInfoBean = new LoginInfoBean();
            loginInfoBean.setUserCurrentPassword( newPassword );
            loginInfoBean.setUserIdentity( userInfo.getUserIdentity() );
            macroRequest = MacroRequest.forUser( pwmDomain.getPwmApplication(), null, userInfo, loginInfoBean );
        }


        final ErrorInformation error;
        switch ( messageSendMethod )
        {
            case SMSONLY:
                // Only try SMS
                error = sendNewPasswordSms( userInfo, pwmDomain, macroRequest, newPassword, smsNumber, userLocale );
                returnToAddress = smsNumber;
                break;
            case EMAILONLY:
            default:
                // Only try email
                error = sendNewPasswordEmail( userInfo, pwmDomain, macroRequest, newPassword, emailAddress, userLocale );
                break;
        }
        if ( error != null )
        {
            throw new PwmOperationalException( error );
        }
        return returnToAddress;
    }

    private static ErrorInformation sendNewPasswordSms(
            final UserInfo userInfo,
            final PwmDomain pwmDomain,
            final MacroRequest macroRequest,
            final PasswordData newPassword,
            final String toNumber,
            final Locale userLocale
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final DomainConfig config = pwmDomain.getConfig();
        String message = config.readSettingAsLocalizedString( PwmSetting.SMS_CHALLENGE_NEW_PASSWORD_TEXT, userLocale );

        if ( toNumber == null || toNumber.length() < 1 )
        {
            final String errorMsg = String.format( "unable to send new password email for '%s'; no SMS number available in ldap", userInfo.getUserIdentity() );
            return new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
        }

        message = message.replace( "%TOKEN%", newPassword.getStringValue() );

        SmsQueueService.sendSmsUsingQueue( pwmDomain.getPwmApplication(), toNumber, message, null, macroRequest );
        LOGGER.debug( () -> String.format( "password SMS added to send queue for %s", toNumber ) );
        return null;
    }

    private static ErrorInformation sendNewPasswordEmail(
            final UserInfo userInfo,
            final PwmDomain pwmDomain,
            final MacroRequest macroRequest,
            final PasswordData newPassword,
            final String toAddress,
            final Locale userLocale
    )
            throws PwmUnrecoverableException
    {
        final DomainConfig config = pwmDomain.getConfig();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_SENDPASSWORD, userLocale );

        if ( configuredEmailSetting == null )
        {
            final String errorMsg = "send password email contents are not configured";
            return new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
        }

        final EmailItemBean emailItemBean = configuredEmailSetting.applyBodyReplacement(
                "%TOKEN%",
                newPassword.getStringValue() );

        pwmDomain.getPwmApplication().getEmailQueue().submitEmail(
                emailItemBean,
                userInfo,
                macroRequest );


        LOGGER.debug( () -> "new password email to " + userInfo.getUserIdentity() + " added to send queue for " + toAddress );
        return null;
    }


    enum PasswordPolicySource
    {
        MERGE,
        LDAP,
        PWM,
    }

    private PasswordUtility( )
    {
    }

    /**
     * <p>This is the entry point under which all password changes are managed.
     * The following is the general procedure when this method is invoked.</p>
     * <ul>
     * <li> password is checked against application password policy</li>
     * <li> ldap password set is attempted</li>
     * </ul>
     * if successful:
     * <ul>
     * <li> uiBean is updated with old and new passwords </li>
     * <li> uiBean's password expire flag is set to false </li>
     * <li> any configured external methods are invoked </li>
     * <li> user email notification is sent </li>
     * <li> return true </li>
     * </ul>
     * if unsuccessful
     * <ul>
     * <li> ssBean is updated with appropriate error </li>
     * <li> return false </li>
     * </ul>
     *
     * @param newPassword the new password that is being set.
     * @param pwmRequest Request used to issue change
     * @param pwmDomain the application reference
     * @throws ChaiUnavailableException if the ldap directory is not unavailable
     * @throws PwmUnrecoverableException  if user is not authenticated
     * @throws PwmOperationalException if operation fails
     */
    public static void setActorPassword(
            final PwmRequest pwmRequest,
            final PwmDomain pwmDomain,
            final PasswordData newPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final UserInfo userInfo = pwmSession.getUserInfo();

        if ( !pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.CHANGE_PASSWORD_ENABLE ) )
        {
            final String errorMsg = "attempt to setActorPassword, but user does not have password change permission";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }

        if ( pwmRequest.getChangePasswordProfile() == null )
        {
            final String errorMsg = "attempt to setActorPassword, but user does not have change password profile assigned";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }

        // double check to make sure password meets PWM rule requirements.  This should
        // have been done before setActorPassword() is invoked, so it should be redundant
        // but we do it just in case.
        try
        {
            final PwmPasswordRuleValidator pwmPasswordRuleValidator = PwmPasswordRuleValidator.create( pwmRequest.getLabel(), pwmDomain, userInfo.getPasswordPolicy() );
            pwmPasswordRuleValidator.testPassword( pwmSession.getLabel(), newPassword, null, userInfo, pwmRequest.getClientConnectionHolder().getActor( ) );
        }
        catch ( final PwmDataValidationException e )
        {
            final String errorMsg = "attempt to setActorPassword, but password does not pass local policy validator";
            final ErrorInformation errorInformation = new ErrorInformation( e.getErrorInformation().getError(), errorMsg );
            throw new PwmOperationalException( errorInformation );
        }

        // retrieve the user's old password from the userInfoBean in the session
        final PasswordData oldPassword = pwmSession.getLoginInfoBean().getUserCurrentPassword();

        boolean setPasswordWithoutOld = false;
        if ( oldPassword == null )
        {
            if ( pwmRequest.getClientConnectionHolder().getActor( ).getChaiProvider().getDirectoryVendor() == DirectoryVendor.ACTIVE_DIRECTORY )
            {
                setPasswordWithoutOld = true;
            }
        }

        if ( !setPasswordWithoutOld )
        {
            // Check to make sure we actually have an old password
            if ( oldPassword == null )
            {
                final String errorMsg = "cannot set password for user, old password is not available";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_WRONGPASSWORD, errorMsg );
                throw new PwmOperationalException( errorInformation );
            }
        }

        final ChaiProvider provider = pwmRequest.getClientConnectionHolder().getActorChaiProvider();

        setPassword( pwmDomain, pwmRequest.getLabel(), provider, userInfo, setPasswordWithoutOld ? null : oldPassword, newPassword );

        // update the session state bean's password modified flag
        pwmSession.getSessionStateBean().setPasswordModified( true );

        // update the login info bean with the user's new password
        pwmSession.getLoginInfoBean().setUserCurrentPassword( newPassword );

        //close any outstanding ldap connections (since they cache the old password)
        pwmRequest.getClientConnectionHolder().updateUserLdapPassword( userInfo.getUserIdentity(), newPassword );

        // clear the "requires new password flag"
        pwmSession.getLoginInfoBean().getLoginFlags().remove( LoginInfoBean.LoginFlag.forcePwChange );

        // mark the auth type as authenticatePd now that we have the user's natural password.
        pwmSession.getLoginInfoBean().setType( AuthenticationType.AUTHENTICATED );

        // update the uibean's "password expired flag".
        pwmSession.reloadUserInfoBean( pwmRequest );

        // create a proxy user object for pwm to update/read the user.
        final ChaiUser proxiedUser = pwmRequest.getClientConnectionHolder().getActor();

        // update statistics
        {
            StatisticsClient.incrementStat( pwmRequest, Statistic.PASSWORD_CHANGES );
        }

        {
            // execute configured actions
            LOGGER.debug( pwmRequest, () -> "executing configured actions to user " + proxiedUser.getEntryDN() );
            final ChangePasswordProfile changePasswordProfile = pwmRequest.getChangePasswordProfile();
            final List<ActionConfiguration> actionConfigurations = changePasswordProfile.readSettingAsAction( PwmSetting.CHANGE_PASSWORD_WRITE_ATTRIBUTES );
            if ( !CollectionUtil.isEmpty( actionConfigurations ) )
            {
                final LoginInfoBean clonedLoginInfoBean = JsonFactory.get().cloneUsingJson( pwmSession.getLoginInfoBean(), LoginInfoBean.class );
                clonedLoginInfoBean.setUserCurrentPassword( newPassword );

                final MacroRequest macroRequest = MacroRequest.forUser(
                        pwmDomain.getPwmApplication(),
                        pwmRequest.getLabel(),
                        pwmSession.getUserInfo(),
                        clonedLoginInfoBean
                );

                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmDomain, userInfo.getUserIdentity() )
                        .setMacroMachine( macroRequest )
                        .setExpandPwmMacros( true )
                        .createActionExecutor();
                actionExecutor.executeActions( actionConfigurations, pwmRequest.getLabel() );
            }
        }

        // invoke post password change actions
        invokePostChangePasswordActions( pwmRequest );

        //update the current last password update field in ldap
        LdapOperationsHelper.updateLastPasswordUpdateAttribute( pwmDomain, pwmRequest.getLabel(), userInfo.getUserIdentity() );
    }

    public static void setPassword(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final ChaiProvider chaiProvider,
            final UserInfo userInfo,
            final PasswordData oldPassword,
            final PasswordData newPassword
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final UserIdentity userIdentity = userInfo.getUserIdentity();
        final Instant startTime = Instant.now();
        final boolean bindIsSelf;
        final String bindDN;

        try
        {

            final ChaiUser theUser = chaiProvider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );

            final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                    pwmDomain,
                    sessionLabel,
                    userIdentity,
                    theUser
            );

            final PwmPasswordRuleValidator pwmPasswordRuleValidator = PwmPasswordRuleValidator.create(
                    sessionLabel,
                    pwmDomain,
                    passwordPolicy,
                    PwmPasswordRuleValidator.Flag.BypassLdapRuleCheck
            );

            pwmPasswordRuleValidator.testPassword( sessionLabel, newPassword, null, userInfo, theUser );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( final PwmException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation() );
        }


        try
        {
            final ChaiUser theUser = chaiProvider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );
            bindDN = chaiProvider.getChaiConfiguration().getSetting( ChaiSetting.BIND_DN );
            bindIsSelf = userIdentity.canonicalEquals( sessionLabel, UserIdentity.create(
                    bindDN,
                    userIdentity.getLdapProfileID(),
                    pwmDomain.getDomainID() ),
                    pwmDomain.getPwmApplication() );

            LOGGER.trace( sessionLabel, () -> "preparing to setActorPassword for '" + theUser.getEntryDN() + "', using bind DN: " + bindDN );

            final boolean settingEnableChange = Boolean.parseBoolean( pwmDomain.getConfig().readAppProperty( AppProperty.LDAP_PASSWORD_CHANGE_SELF_ENABLE ) );
            if ( settingEnableChange )
            {
                if ( oldPassword == null )
                {
                    theUser.setPassword( newPassword.getStringValue(), true );
                }
                else
                {
                    theUser.changePassword( oldPassword.getStringValue(), newPassword.getStringValue() );
                }
            }
            else
            {
                LOGGER.debug( sessionLabel, () -> "skipping actual ldap password change operation due to app property "
                        + AppProperty.LDAP_PASSWORD_CHANGE_SELF_ENABLE.getKey() + "=false" );
            }
        }
        catch ( final ChaiPasswordPolicyException e )
        {
            final String errorMsg = "error setting password for user '" + userIdentity.toDisplayString() + "'' " + e;
            final Optional<PwmError> pwmError = PwmError.forChaiError( e.getErrorCode() );
            final ErrorInformation error = new ErrorInformation( pwmError.orElse( PwmError.PASSWORD_UNKNOWN_VALIDATION ), errorMsg );
            throw new PwmOperationalException( error );
        }
        catch ( final ChaiOperationException e )
        {
            final String errorMsg = "error setting password for user '" + userIdentity.toDisplayString() + "'' " + e.getMessage();
            final PwmError pwmError = PwmError.forChaiError( e.getErrorCode() ).orElse( PwmError.ERROR_INTERNAL );
            final ErrorInformation error = new ErrorInformation( pwmError, errorMsg );
            throw new PwmOperationalException( error );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }

        // add the old password to the global history list (if the old password is known)
        if ( oldPassword != null && pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.PASSWORD_SHAREDHISTORY_ENABLE ) )
        {
            pwmDomain.getSharedHistoryManager().addWord( sessionLabel, oldPassword.getStringValue() );
        }

        // update stats
        pwmDomain.getStatisticsManager().updateEps( EpsStatistic.PASSWORD_CHANGES, 1 );

        final int passwordStrength = PasswordUtility.judgePasswordStrength( pwmDomain.getConfig(), newPassword.getStringValue() );
        pwmDomain.getStatisticsManager().updateAverageValue( AvgStatistic.AVG_PASSWORD_STRENGTH, passwordStrength );

        // at this point the password has been changed, so log it.
        final String msg = ( bindIsSelf
                ? "user " + userIdentity.toDisplayString() + " has changed own password"
                : "password for user '" + userIdentity.toDisplayString() + "' has been changed by " + bindDN )
                + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")";

        LOGGER.info( sessionLabel, () -> msg );
    }

    public static void helpdeskSetUserPassword(
            final PwmRequest pwmRequest,
            final ChaiUser chaiUser,
            final UserInfo userInfo,
            final PwmDomain pwmDomain,
            final PasswordData newPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final SessionLabel sessionLabel = pwmRequest.getLabel();
        final UserIdentity userIdentity = userInfo.getUserIdentity();

        final String changePasswordProfileID = userInfo.getProfileIDs().get( ProfileDefinition.ChangePassword );
        final ChangePasswordProfile changePasswordProfile = pwmRequest.getDomainConfig().getChangePasswordProfile().get( changePasswordProfileID );

        if ( changePasswordProfile == null )
        {
            final String errorMsg = "attempt to helpdeskSetUserPassword, but user does not have a configured change password profile";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }

        if ( !pwmRequest.isAuthenticated() )
        {
            final String errorMsg = "attempt to helpdeskSetUserPassword, but user is not authenticated";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }

        final HelpdeskProfile helpdeskProfile = pwmRequest.getHelpdeskProfile( );
        if ( helpdeskProfile == null )
        {
            final String errorMsg = "attempt to helpdeskSetUserPassword, but user does not have helpdesk permission";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }

        setPassword( pwmDomain, pwmRequest.getLabel(), chaiUser.getChaiProvider(), userInfo, null, newPassword );

        // create a proxy user object for pwm to update/read the user.
        final ChaiUser proxiedUser = pwmDomain.getProxiedChaiUser( sessionLabel, userIdentity );

        // mark the event log
        final LocalSessionStateBean sessionStateBean = pwmRequest.getPwmSession().getSessionStateBean();
        {
            final HelpdeskAuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_SET_PASSWORD,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    null,
                    userIdentity,
                    sessionStateBean.getSrcAddress(),
                    sessionStateBean.getSrcHostname()
            );
            AuditServiceClient.submit( pwmRequest, auditRecord );
        }

        // update statistics
        StatisticsClient.incrementStat( pwmRequest, Statistic.HELPDESK_PASSWORD_SET );

        {
            // execute configured actions
            LOGGER.debug( pwmRequest, () -> "executing changePassword and helpdesk post password change writeAttributes to user " + userIdentity );

            final List<ActionConfiguration> actions = new ArrayList<>();
            actions.addAll( changePasswordProfile.readSettingAsAction( PwmSetting.CHANGE_PASSWORD_WRITE_ATTRIBUTES ) );
            actions.addAll( helpdeskProfile.readSettingAsAction( PwmSetting.HELPDESK_POST_SET_PASSWORD_WRITE_ATTRIBUTES ) );
            if ( !actions.isEmpty() )
            {

                final LoginInfoBean loginInfoBean = new LoginInfoBean();
                loginInfoBean.setUserCurrentPassword( newPassword );

                final MacroRequest macroRequest = MacroRequest.forUser(
                        pwmDomain.getPwmApplication(),
                        sessionLabel,
                        userInfo,
                        loginInfoBean
                );

                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmDomain, userIdentity )
                        .setMacroMachine( macroRequest )
                        .setExpandPwmMacros( true )
                        .createActionExecutor();

                actionExecutor.executeActions( actions, pwmRequest.getLabel() );
            }
        }

        final HelpdeskClearResponseMode settingClearResponses = HelpdeskClearResponseMode.valueOf(
                helpdeskProfile.readSettingAsString( PwmSetting.HELPDESK_CLEAR_RESPONSES )
        );

        if ( settingClearResponses == HelpdeskClearResponseMode.yes )
        {
            final String userGUID = LdapOperationsHelper.readLdapGuidValue( pwmDomain, sessionLabel, userIdentity, false );
            pwmDomain.getCrService().clearResponses( pwmRequest.getLabel(), userIdentity, proxiedUser, userGUID );

            // mark the event log
            final HelpdeskAuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_CLEAR_RESPONSES,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    null,
                    userIdentity,
                    sessionStateBean.getSrcAddress(),
                    sessionStateBean.getSrcHostname()
            );
            AuditServiceClient.submit( pwmRequest, auditRecord );
        }

        // send email notification
        sendChangePasswordHelpdeskEmailNotice( pwmRequest, pwmDomain, userInfo );

        // expire if so configured
        if ( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_FORCE_PW_EXPIRATION ) )
        {
            LOGGER.trace( pwmRequest, () -> "preparing to expire password for user " + userIdentity.toDisplayString() );
            try
            {
                proxiedUser.expirePassword();
            }
            catch ( final ChaiOperationException e )
            {
                LOGGER.warn( pwmRequest, () -> "error while forcing password expiration for user " + userIdentity.toDisplayString() + ", error: " + e.getMessage() );
            }
        }

        // send password
        final boolean sendPassword = helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_SEND_PASSWORD );
        if ( sendPassword )
        {
            final Optional<String> profileID = ProfileUtility.discoverProfileIDForUser( pwmDomain, sessionLabel, userIdentity, ProfileDefinition.ForgottenPassword );
            if ( profileID.isPresent() )
            {
                final ForgottenPasswordProfile forgottenPasswordProfile = pwmDomain.getConfig().getForgottenPasswordProfiles().get( profileID.get() );
                final MessageSendMethod messageSendMethod = forgottenPasswordProfile.readSettingAsEnum( PwmSetting.RECOVERY_SENDNEWPW_METHOD, MessageSendMethod.class );

                PasswordUtility.sendNewPassword(
                        userInfo,
                        pwmDomain,
                        newPassword,
                        pwmRequest.getLocale(),
                        messageSendMethod
                );
            }
        }
    }

    public static Map<String, Instant> readIndividualReplicaLastPasswordTimes(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Map<String, Instant> returnValue = new LinkedHashMap<>();
        final ChaiProvider chaiProvider = pwmDomain.getProxyChaiProvider( sessionLabel, userIdentity.getLdapProfileID() );
        final Collection<ChaiConfiguration> perReplicaConfigs = ChaiUtility.splitConfigurationPerReplica(
                chaiProvider.getChaiConfiguration(),
                Collections.singletonMap( ChaiSetting.FAILOVER_CONNECT_RETRIES, "1" )
        );
        for ( final ChaiConfiguration loopConfiguration : perReplicaConfigs )
        {
            final String loopReplicaUrl = loopConfiguration.getSetting( ChaiSetting.BIND_DN );
            ChaiProvider loopProvider = null;
            try
            {
                loopProvider = pwmDomain.getLdapConnectionService().getChaiProviderFactory().newProvider( loopConfiguration );
                final Instant lastModifiedDate = determinePwdLastModified( pwmDomain, sessionLabel, userIdentity );
                returnValue.put( loopReplicaUrl, lastModifiedDate );
            }
            catch ( final ChaiUnavailableException e )
            {
                LOGGER.error( sessionLabel, () -> "unreachable server during replica password sync check" );
            }
            finally
            {
                if ( loopProvider != null )
                {
                    try
                    {
                        loopProvider.close();
                    }
                    catch ( final Exception e )
                    {
                        final String errorMsg = "error closing loopProvider to " + loopReplicaUrl + " while checking individual password sync status";
                        LOGGER.error( sessionLabel, () -> errorMsg );
                    }
                }
            }
        }
        return returnValue;
    }

    private static void invokePostChangePasswordActions( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PwmAuthenticationSource authenticationSource = pwmRequest.getPwmSession().getLoginInfoBean().getAuthSource();

        if ( authenticationSource == PwmAuthenticationSource.USER_ACTIVATION )
        {
            LOGGER.debug( pwmRequest, () -> "executing post-activate configured actions " );
            executePostActionMethods(  pwmRequest, ProfileDefinition.ActivateUser, PwmSetting.ACTIVATE_USER_POST_WRITE_ATTRIBUTES );
        }
        else if ( authenticationSource == PwmAuthenticationSource.FORGOTTEN_PASSWORD )
        {
            LOGGER.debug( pwmRequest, () -> "executing post-forgotten password configured actions" );
            executePostActionMethods(  pwmRequest, ProfileDefinition.ForgottenPassword, PwmSetting.RECOVERY_POST_ACTIONS );
        }
        else
        {
            LOGGER.trace( pwmRequest, () -> "no post change password actions required for authentication source: " + authenticationSource );
        }
    }

    private static void executePostActionMethods(
            final PwmRequest pwmRequest,
            final ProfileDefinition profileDefinition,
            final PwmSetting pwmSetting
    )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final UserIdentity userIdentity = pwmRequest.getUserInfoIfLoggedIn();
        final AbstractProfile activateUserProfile = ProfileUtility.profileForUser(
                pwmRequest.getPwmRequestContext(),
                userIdentity,
                profileDefinition,
                AbstractProfile.class );

        try
        {
            {
                final List<ActionConfiguration> configValues = activateUserProfile.readSettingAsAction( pwmSetting );

                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmDomain, userIdentity )
                        .setExpandPwmMacros( true )
                        .setMacroMachine( pwmRequest.getMacroMachine() )
                        .createActionExecutor();
                actionExecutor.executeActions( configValues, pwmRequest.getLabel() );
            }
        }
        catch ( final PwmException e )
        {
            final ErrorInformation info = new ErrorInformation(
                    PwmError.ERROR_SERVICE_UNREACHABLE,
                    e.getErrorInformation().getDetailedErrorMsg(), e.getErrorInformation().getFieldValues()
            );
            throw new PwmUnrecoverableException( info, e );
        }
    }


    /*
    static Map<String, ReplicationStatus> checkIfPasswordIsReplicated(final ChaiUser user, final PwmSession pwmSession)
            throws ChaiUnavailableException
    {
        final Map<String, ReplicationStatus> repStatusMap = new HashMap<String, ReplicationStatus>();

        {
            final ReplicationStatus repStatus = checkDirectoryReplicationStatus(user, pwmSession);
            repStatusMap.put("ReplicationSync", repStatus);
        }

        if (ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY == user.getChaiProvider().getDirectoryVendor()) {

        }

        return repStatusMap;
    }

    public static Map<String, ReplicationStatus> checkNovellIDMReplicationStatus(final ChaiUser chaiUser)
            throws ChaiUnavailableException, ChaiOperationException
    {
        final Map<String,ReplicationStatus> repStatuses = new HashMap<String,ReplicationStatus>();

        final Set<String> values = chaiUser.readMultiStringAttribute("DirXML-PasswordSyncStatus");
        if (values != null) {
            for (final String value : values) {
                if (value != null && value.length() >= 62 ) {
                    final String guid = value.substring(0,32);
                    final String timestamp = value.substring(32,46);
                    final String status = value.substring(46,62);
                    final String descr = value.substring(61, value.length());

                    final Date dateValue = EdirEntries.convertZuluToDate(timestamp + "Z");

                    System.out.println("guid=" + guid + ", timestamp=" + dateValue.toString() + ", status=" + status + ", descr=" + descr);
                }
            }
        }

        return repStatuses;
    }

    private static ReplicationStatus checkDirectoryReplicationStatus(final ChaiUser user, final PwmSession pwmSession)
            throws ChaiUnavailableException
    {
        boolean isReplicated = false;
        try {
            isReplicated = ChaiUtility.testAttributeReplication(user, pwmSession.getConfig().readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE), null);
            Helper.pause(PwmConstants.PASSWORD_UPDATE_CYCLE_DELAY_MS);
        } catch (ChaiOperationException e) {
            //oh well, give up.
            LOGGER.trace(pwmSession, "error during password sync check: " + e.getMessage());
        }
        return isReplicated ? ReplicationStatus.COMPLETE : ReplicationStatus.IN_PROGRESS;
    }

    enum ReplicationStatus {
        IN_PROGRESS,
        COMPLETE
    }

    */

    public static int judgePasswordStrength(
            final DomainConfig domainConfig,
            final String password
    )
            throws PwmUnrecoverableException
    {
        final StrengthMeterType strengthMeterType = domainConfig.getAppConfig().readSettingAsEnum( PwmSetting.PASSWORD_STRENGTH_METER_TYPE, StrengthMeterType.class );
        switch ( strengthMeterType )
        {
            case ZXCVBN:
                return judgePasswordStrengthUsingZxcvbnAlgorithm( domainConfig, password );

            case PWM:
                return judgePasswordStrengthUsingTraditionalAlgorithm( password );

            default:
                MiscUtil.unhandledSwitchStatement( strengthMeterType );
        }

        return -1;
    }

    public static int judgePasswordStrengthUsingZxcvbnAlgorithm(
            final DomainConfig domainConfig,
            final String password
    )
    {
        final int maxTestLength = 100;

        if ( StringUtil.isEmpty( password ) )
        {
            return Integer.parseInt( domainConfig.readAppProperty( AppProperty.PASSWORD_STRENGTH_THRESHOLD_VERY_WEAK ) );
        }

        final String testPassword = StringUtil.truncate( password, maxTestLength );

        final Zxcvbn zxcvbn = new Zxcvbn();
        final Strength strength = zxcvbn.measure( testPassword );

        final int zxcvbnScore = strength.getScore();

        // zxcvbn returns a score of 0-4 (see: https://github.com/nulab/zxcvbn4j)
        switch ( zxcvbnScore )
        {
            case 4:
                return Integer.parseInt( domainConfig.readAppProperty( AppProperty.PASSWORD_STRENGTH_THRESHOLD_VERY_STRONG ) );
            case 3:
                return Integer.parseInt( domainConfig.readAppProperty( AppProperty.PASSWORD_STRENGTH_THRESHOLD_STRONG ) );
            case 2:
                return Integer.parseInt( domainConfig.readAppProperty( AppProperty.PASSWORD_STRENGTH_THRESHOLD_GOOD ) );
            case 1:
                return Integer.parseInt( domainConfig.readAppProperty( AppProperty.PASSWORD_STRENGTH_THRESHOLD_WEAK ) );
            default:
                return Integer.parseInt( domainConfig.readAppProperty( AppProperty.PASSWORD_STRENGTH_THRESHOLD_VERY_WEAK ) );
        }
    }

    public static int judgePasswordStrengthUsingTraditionalAlgorithm(
            final String password
    )
    {
        if ( StringUtil.isEmpty( password ) )
        {
            return 0;
        }

        int score = 0;
        final PasswordCharCounter charCounter = new PasswordCharCounter( password );

        // -- Additions --
        // amount of unique chars
        if ( charCounter.getUniqueChars() > 7 )
        {
            score = score + 10;
        }
        score = score + ( ( charCounter.getUniqueChars() ) * 3 );

        // Numbers
        if ( charCounter.getNumericCharCount() > 0 )
        {
            score = score + 8;
            score = score + ( charCounter.getNumericCharCount() ) * 4;
        }

        // specials
        if ( charCounter.getSpecialCharsCount() > 0 )
        {
            score = score + 14;
            score = score + ( charCounter.getSpecialCharsCount() ) * 5;
        }

        // mixed case
        if ( ( charCounter.getAlphaChars().length() != charCounter.getUpperChars().length() )
                && ( charCounter.getAlphaChars().length() != charCounter.getLowerChars().length() ) )
        {
            score = score + 10;
        }

        // -- Deductions --

        // sequential numbers
        if ( charCounter.getSequentialNumericChars() > 2 )
        {
            score = score - ( charCounter.getSequentialNumericChars() - 1 ) * 4;
        }

        // sequential chars
        if ( charCounter.getSequentialRepeatedChars() > 1 )
        {
            score = score - ( charCounter.getSequentialRepeatedChars() ) * 5;
        }

        return score > 100 ? 100 : score < 0 ? 0 : score;
    }


    public static PwmPasswordPolicy readPasswordPolicyForUser(
            final PwmDomain pwmDomain,
            final SessionLabel pwmSession,
            final UserIdentity userIdentity,
            final ChaiUser theUser
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final PasswordPolicySource ppSource = PasswordPolicySource.valueOf( pwmDomain.getConfig().readSettingAsString( PwmSetting.PASSWORD_POLICY_SOURCE ) );

        final PwmPasswordPolicy returnPolicy;
        switch ( ppSource )
        {
            case MERGE:
                final PwmPasswordPolicy pwmPolicy = determineConfiguredPolicyProfileForUser( pwmDomain, pwmSession, userIdentity );
                final PwmPasswordPolicy userPolicy = readLdapPasswordPolicy( pwmDomain, theUser );
                LOGGER.trace( pwmSession, () -> "read user policy for '" + theUser.getEntryDN() + "', policy: " + userPolicy.toString() );
                returnPolicy = pwmPolicy.merge( userPolicy );
                LOGGER.debug( pwmSession, () -> "merged user password policy of '" + theUser.getEntryDN() + "' with PWM configured policy: " + returnPolicy.toString() );
                break;

            case LDAP:
                returnPolicy = readLdapPasswordPolicy( pwmDomain, theUser );
                LOGGER.debug( pwmSession, () -> "discovered assigned password policy for " + theUser.getEntryDN() + " " + returnPolicy.toString() );
                break;

            case PWM:
                returnPolicy = determineConfiguredPolicyProfileForUser( pwmDomain, pwmSession, userIdentity );
                break;

            default:
                throw new IllegalStateException( "unknown policy source defined: " + ppSource.name() );
        }

        LOGGER.trace( pwmSession, () -> "readPasswordPolicyForUser completed", TimeDuration.fromCurrent( startTime ) );
        return returnPolicy;
    }

    public static PwmPasswordPolicy determineConfiguredPolicyProfileForUser(
            final PwmDomain pwmDomain,
            final SessionLabel pwmSession,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final List<String> profiles = pwmDomain.getConfig().getPasswordProfileIDs();
        if ( profiles.isEmpty() )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED, "no password profiles are configured" ) );
        }

        for ( final String profile : profiles )
        {
            final PwmPasswordPolicy loopPolicy = pwmDomain.getConfig().getPasswordPolicy( profile );
            final List<UserPermission> userPermissions = loopPolicy.getUserPermissions();
            LOGGER.debug( pwmSession, () -> "testing password policy profile '" + profile + "'" );
            try
            {
                final boolean match = UserPermissionUtility.testUserPermission( pwmDomain, pwmSession, userIdentity, userPermissions );
                if ( match )
                {
                    return loopPolicy;
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( pwmSession, () -> "unexpected error while testing password policy profile '" + profile + "', error: " + e.getMessage() );
            }
        }

        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED, "no password profile is assigned to user" ) );
    }


    public static PwmPasswordPolicy readLdapPasswordPolicy(
            final PwmDomain pwmDomain,
            final ChaiUser theUser )
            throws PwmUnrecoverableException
    {
        try
        {
            final Map<String, String> ruleMap = new HashMap<>();
            final ChaiPasswordPolicy chaiPolicy;
            try
            {
                chaiPolicy = theUser.getPasswordPolicy();
            }
            catch ( final ChaiUnavailableException e )
            {
                throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ).orElse( PwmError.ERROR_INTERNAL ) );
            }
            if ( chaiPolicy != null )
            {
                for ( final String key : chaiPolicy.getKeys() )
                {
                    ruleMap.put( key, chaiPolicy.getValue( key ) );
                }

                if ( !"read".equals( pwmDomain.getConfig().readSettingAsString( PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY ) ) )
                {
                    ruleMap.put(
                            PwmPasswordRule.CaseSensitive.getKey(),
                            pwmDomain.getConfig().readSettingAsString( PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY )
                    );
                }

                return PwmPasswordPolicy.createPwmPasswordPolicy( pwmDomain.getDomainID(), ruleMap, chaiPolicy );
            }
        }
        catch ( final ChaiOperationException e )
        {
            LOGGER.warn( () -> "error reading password policy for user " + theUser.getEntryDN() + ", error: " + e.getMessage() );
        }
        return PwmPasswordPolicy.defaultPolicy();
    }

    public static PasswordCheckInfo checkEnteredPassword(
            final PwmRequestContext pwmRequestContext,
            final ChaiUser user,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean,
            final PasswordData password,
            final PasswordData confirmPassword
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        Objects.requireNonNull( userInfo );

        final PwmDomain pwmDomain = pwmRequestContext.getPwmDomain();
        final Locale locale = pwmRequestContext.getLocale();
        final SessionLabel sessionLabel = pwmRequestContext.getSessionLabel();

        boolean pass = false;
        String userMessage = "";
        int errorCode = 0;

        final boolean passwordIsCaseSensitive = userInfo.getPasswordPolicy() == null
                || userInfo.getPasswordPolicy().getRuleHelper().readBooleanValue( PwmPasswordRule.CaseSensitive );

        final CachePolicy cachePolicy;
        {
            final long cacheLifetimeMS = Long.parseLong( pwmDomain.getConfig().readAppProperty( AppProperty.CACHE_PWRULECHECK_LIFETIME_MS ) );
            cachePolicy = CachePolicy.makePolicyWithExpirationMS( cacheLifetimeMS );
        }

        if ( password == null )
        {
            userMessage = new ErrorInformation( PwmError.PASSWORD_MISSING ).toUserStr( locale, pwmDomain.getConfig() );
        }
        else
        {
            final CacheService cacheService = pwmDomain.getCacheService();
            final CacheKey cacheKey = user != null && userInfo.getUserIdentity() != null
                    ? CacheKey.newKey(
                    PasswordUtility.class,
                    userInfo.getUserIdentity(),
                    user.getEntryDN() + ":" + password.hash() )
                    : null;
            if ( pwmDomain.getPwmApplication().getConfig().isDevDebugMode() )
            {
                LOGGER.trace( () -> "generated cacheKey for password check request: " + cacheKey );
            }
            try
            {
                if ( cacheService != null && cacheKey != null )
                {
                    final String cachedValue = cacheService.get( cacheKey, String.class );
                    if ( cachedValue != null )
                    {
                        if ( NEGATIVE_CACHE_HIT.equals( cachedValue ) )
                        {
                            pass = true;
                        }
                        else
                        {
                            LOGGER.trace( () -> "cache hit!" );
                            final ErrorInformation errorInformation = JsonFactory.get().deserialize( cachedValue, ErrorInformation.class );
                            throw new PwmDataValidationException( errorInformation );
                        }
                    }
                }
                if ( !pass )
                {
                    final PwmPasswordRuleValidator pwmPasswordRuleValidator = PwmPasswordRuleValidator.create( sessionLabel, pwmDomain, userInfo.getPasswordPolicy(), locale );
                    final PasswordData oldPassword = loginInfoBean == null ? null : loginInfoBean.getUserCurrentPassword();
                    pwmPasswordRuleValidator.testPassword( pwmRequestContext.getSessionLabel(), password, oldPassword, userInfo, user );
                    pass = true;
                    if ( cacheService != null && cacheKey != null )
                    {
                        cacheService.put( cacheKey, cachePolicy, NEGATIVE_CACHE_HIT );
                    }
                }
            }
            catch ( final PwmDataValidationException e )
            {
                errorCode = e.getError().getErrorCode();
                userMessage = e.getErrorInformation().toUserStr( locale, pwmDomain.getConfig() );
                pass = false;
                if ( cacheService != null && cacheKey != null )
                {
                    final String jsonPayload = JsonFactory.get().serialize( e.getErrorInformation() );
                    cacheService.put( cacheKey, cachePolicy, jsonPayload );
                }
            }
        }

        final PasswordCheckInfo.MatchStatus matchStatus = figureMatchStatus( passwordIsCaseSensitive, password, confirmPassword );
        if ( pass )
        {
            switch ( matchStatus )
            {
                case EMPTY:
                    userMessage = new ErrorInformation( PwmError.PASSWORD_MISSING_CONFIRM ).toUserStr( locale, pwmDomain.getConfig() );
                    break;
                case MATCH:
                    userMessage = new ErrorInformation( PwmError.PASSWORD_MEETS_RULES ).toUserStr( locale, pwmDomain.getConfig() );
                    break;
                case NO_MATCH:
                    userMessage = new ErrorInformation( PwmError.PASSWORD_DOESNOTMATCH ).toUserStr( locale, pwmDomain.getConfig() );
                    break;
                default:
                    userMessage = "";
            }
        }

        final int strength = judgePasswordStrength( pwmDomain.getConfig(), password == null ? null : password.getStringValue() );
        return new PasswordCheckInfo( userMessage, pass, strength, matchStatus, errorCode );
    }


    public static PasswordCheckInfo.MatchStatus figureMatchStatus(
            final boolean caseSensitive,
            final PasswordData password1,
            final PasswordData password2
    )
    {
        final PasswordCheckInfo.MatchStatus matchStatus;
        if ( password2 == null )
        {
            matchStatus = PasswordCheckInfo.MatchStatus.EMPTY;
        }
        else if ( password1 == null )
        {
            matchStatus = PasswordCheckInfo.MatchStatus.NO_MATCH;
        }
        else
        {
            if ( caseSensitive )
            {
                matchStatus = password1.equals( password2 ) ? PasswordCheckInfo.MatchStatus.MATCH : PasswordCheckInfo.MatchStatus.NO_MATCH;
            }
            else
            {
                matchStatus = password1.equalsIgnoreCase( password2 ) ? PasswordCheckInfo.MatchStatus.MATCH : PasswordCheckInfo.MatchStatus.NO_MATCH;
            }
        }

        return matchStatus;
    }


    public static class PasswordCheckInfo implements Serializable
    {
        private final String message;
        private final boolean passed;
        private final int strength;
        private final MatchStatus match;
        private final int errorCode;

        public enum MatchStatus
        {
            MATCH, NO_MATCH, EMPTY
        }

        public PasswordCheckInfo( final String message, final boolean passed, final int strength, final MatchStatus match, final int errorCode )
        {
            this.message = message;
            this.passed = passed;
            this.strength = strength;
            this.match = match;
            this.errorCode = errorCode;
        }

        public String getMessage( )
        {
            return message;
        }

        public boolean isPassed( )
        {
            return passed;
        }

        public int getStrength( )
        {
            return strength;
        }

        public MatchStatus getMatch( )
        {
            return match;
        }

        public int getErrorCode( )
        {
            return errorCode;
        }
    }

    private static void sendChangePasswordHelpdeskEmailNotice(
            final PwmRequest pwmRequest,
            final PwmDomain pwmDomain,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final DomainConfig config = pwmDomain.getConfig();
        final Locale locale = pwmRequest.getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_CHANGEPASSWORD_HELPDESK, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequest, () -> "skipping send change password email for '" + pwmRequest.getUserInfoIfLoggedIn() + "' no email configured" );
            return;
        }

        final MacroRequest macroRequest = userInfo == null
                ? null
                : MacroRequest.forUser(
                        pwmDomain.getPwmApplication(),
                        pwmRequest.getLabel(),
                        userInfo,
                        null
                );

        pwmDomain.getPwmApplication().getEmailQueue().submitEmail( configuredEmailSetting, userInfo, macroRequest );
    }

    public static Instant determinePwdLastModified(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final ChaiUser theUser = pwmDomain.getProxiedChaiUser( sessionLabel, userIdentity );
        return determinePwdLastModified( pwmDomain, sessionLabel, theUser, userIdentity );
    }

    private static Instant determinePwdLastModified(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final ChaiUser theUser,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        // fetch last password modification time from pwm last update attribute operation
        try
        {
            final Instant chaiReadDate = theUser.readPasswordModificationDate();
            if ( chaiReadDate != null )
            {
                LOGGER.trace( sessionLabel, () -> "read last user password change timestamp (via chai) as: " + StringUtil.toIsoDate( chaiReadDate ) );
                return chaiReadDate;
            }
        }
        catch ( final ChaiOperationException e )
        {
            LOGGER.error( sessionLabel, () -> "unexpected error reading password last modified timestamp: " + e.getMessage() );
        }

        final LdapProfile ldapProfile = pwmDomain.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() );
        final String pwmLastSetAttr = ldapProfile.readSettingAsString( PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE );
        if ( pwmLastSetAttr != null && pwmLastSetAttr.length() > 0 )
        {
            try
            {
                final Instant pwmPwdLastModified = theUser.readDateAttribute( pwmLastSetAttr );
                LOGGER.trace( sessionLabel, () -> "read pwmPasswordChangeTime as: " + ( pwmPwdLastModified == null ? "n/a" : StringUtil.toIsoDate( pwmPwdLastModified ) ) );
                return pwmPwdLastModified;
            }
            catch ( final ChaiOperationException e )
            {
                LOGGER.error( sessionLabel, () -> "error parsing password last modified PWM password value for user " + theUser.getEntryDN() + "; error: " + e.getMessage() );
            }
        }

        LOGGER.debug( sessionLabel, () -> "unable to determine time of user's last password modification" );
        return null;
    }

    public static void throwPasswordTooSoonException(
            final UserInfo userInfo,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        if ( !userInfo.isWithinPasswordMinimumLifetime() )
        {
            return;
        }

        final Instant lastModified = userInfo.getPasswordLastModifiedTime();
        final TimeDuration minimumLifetime;
        {
            final int minimumLifetimeSeconds = userInfo.getPasswordPolicy().getRuleHelper().readIntValue( PwmPasswordRule.MinimumLifetime );
            if ( minimumLifetimeSeconds < 1 )
            {
                return;
            }

            if ( userInfo.getPasswordPolicy() == null )
            {
                LOGGER.debug( sessionLabel, () -> "skipping minimum lifetime check, password last set time is unknown" );
                return;
            }

            minimumLifetime = TimeDuration.of( minimumLifetimeSeconds, TimeDuration.Unit.SECONDS );

        }
        final Instant allowedChangeDate = Instant.ofEpochMilli( lastModified.toEpochMilli() + minimumLifetime.asMillis() );
        final TimeDuration passwordAge = TimeDuration.fromCurrent( lastModified );
        final String msg = "last password change was at "
                + StringUtil.toIsoDate( lastModified )
                + " and is too recent (" + passwordAge.asCompactString()
                + " ago), password cannot be changed within minimum lifetime of "
                + minimumLifetime.asCompactString()
                + ", next eligible time to change is after " + StringUtil.toIsoDate( allowedChangeDate );
        throw PwmUnrecoverableException.newException( PwmError.PASSWORD_TOO_SOON, msg );

    }

    public static boolean isPasswordWithinMinimumLifetimeImpl(
            final ChaiUser chaiUser,
            final SessionLabel sessionLabel,
            final PwmPasswordPolicy passwordPolicy,
            final Instant lastModified,
            final PasswordStatus passwordStatus
    )
            throws PwmUnrecoverableException
    {

        // for oracle DS; this check is also handled in UserAuthenticator.
        try
        {
            if ( DirectoryVendor.ORACLE_DS == chaiUser.getChaiProvider().getDirectoryVendor() )
            {
                final String oracleDSPrePasswordAllowChangeTime = chaiUser.readStringAttribute( "passwordAllowChangeTime" );
                if ( oracleDSPrePasswordAllowChangeTime != null && !oracleDSPrePasswordAllowChangeTime.isEmpty() )
                {
                    final Instant date = OracleDSEntries.convertZuluToDate( oracleDSPrePasswordAllowChangeTime );
                    if ( Instant.now().isBefore( date ) )
                    {
                        LOGGER.debug( () -> "discovered oracleds allowed change time is set to: " + StringUtil.toIsoDate( date ) + ", won't permit password change" );
                        final String errorMsg = "change not permitted until " + StringUtil.toIsoDate( date );
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.PASSWORD_TOO_SOON, errorMsg );
                        throw new PwmUnrecoverableException( errorInformation );
                    }
                }
                return false;
            }
        }
        catch ( final ChaiException e )
        {
            LOGGER.debug( sessionLabel, () -> "unexpected error reading OracleDS password allow modification time: " + e.getMessage() );
        }

        final TimeDuration minimumLifetime;
        {
            final int minimumLifetimeSeconds = passwordPolicy.getRuleHelper().readIntValue( PwmPasswordRule.MinimumLifetime );
            if ( minimumLifetimeSeconds < 1 )
            {
                return false;
            }

            if ( lastModified == null )
            {
                LOGGER.debug( sessionLabel, () -> "skipping minimum lifetime check, password last set time is unknown" );
                return false;
            }

            minimumLifetime = TimeDuration.of( minimumLifetimeSeconds, TimeDuration.Unit.SECONDS );
        }

        final TimeDuration passwordAge = TimeDuration.fromCurrent( lastModified );
        LOGGER.trace( sessionLabel, () -> "beginning check for minimum lifetime, lastModified="
                + StringUtil.toIsoDate( lastModified )
                + ", minimumLifetimeSeconds=" + minimumLifetime.asCompactString()
                + ", passwordAge=" + passwordAge.asCompactString() );


        if ( lastModified.isAfter( Instant.now() ) )
        {
            LOGGER.debug( sessionLabel, () -> "skipping minimum lifetime check, password lastModified time is in the future" );
            return false;
        }

        final boolean passwordTooSoon = passwordAge.isShorterThan( minimumLifetime );
        if ( !passwordTooSoon )
        {
            LOGGER.trace( sessionLabel, () -> "minimum lifetime check passed, password age " );
            return false;
        }

        if ( passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isWarnPeriod() )
        {
            LOGGER.debug( sessionLabel, () -> "current password is too young, but skipping enforcement of minimum lifetime check because current password is expired" );
            return false;
        }

        return true;
    }
}

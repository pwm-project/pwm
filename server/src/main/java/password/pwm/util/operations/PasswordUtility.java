/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.util.operations;

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
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.HelpdeskClearResponseMode;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.StrengthMeterType;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.cache.CacheKey;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.svc.cache.CacheService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.PasswordCharCounter;
import password.pwm.util.PasswordData;
import password.pwm.util.PostChangePasswordAction;
import password.pwm.util.PwmPasswordRuleValidator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

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
import java.util.concurrent.TimeUnit;

/**
 * @author Jason D. Rivard
 */
public class PasswordUtility {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PasswordUtility.class);
    private static final String NEGATIVE_CACHE_HIT = "NEGATIVE_CACHE_HIT";

    public static String sendNewPassword(
            final UserInfo userInfo,
            final PwmApplication pwmApplication,
            final PasswordData newPassword,
            final Locale userLocale,
            final MessageSendMethod messageSendMethod
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String emailAddress = userInfo.getUserEmailAddress();
        final String smsNumber = userInfo.getUserSmsNumber();
        String returnToAddress = emailAddress;

        final MacroMachine macroMachine;
        {
            final LoginInfoBean loginInfoBean = new LoginInfoBean();
            loginInfoBean.setUserCurrentPassword(newPassword);
            loginInfoBean.setUserIdentity(userInfo.getUserIdentity());
            macroMachine = MacroMachine.forUser(pwmApplication, null, userInfo, loginInfoBean);
        }


        final ErrorInformation error;
        switch (messageSendMethod) {
            case SMSONLY:
                // Only try SMS
                error = sendNewPasswordSms(userInfo, pwmApplication, macroMachine, newPassword, smsNumber, userLocale);
                returnToAddress = smsNumber;
                break;
            case EMAILONLY:
            default:
                // Only try email
                error = sendNewPasswordEmail(userInfo, pwmApplication, macroMachine, newPassword, emailAddress, userLocale);
                break;
        }
        if (error != null) {
            throw new PwmOperationalException(error);
        }
        return returnToAddress;
    }

    private static ErrorInformation sendNewPasswordSms(
            final UserInfo userInfo,
            final PwmApplication pwmApplication,
            final MacroMachine macroMachine,
            final PasswordData newPassword,
            final String toNumber,
            final Locale userLocale
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        String message = config.readSettingAsLocalizedString(PwmSetting.SMS_CHALLENGE_NEW_PASSWORD_TEXT, userLocale);

        if (toNumber == null || toNumber.length() < 1) {
            final String errorMsg = String.format("unable to send new password email for '%s'; no SMS number available in ldap", userInfo.getUserIdentity());
            return new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
        }

        message = message.replace("%TOKEN%", newPassword.getStringValue());

        pwmApplication.sendSmsUsingQueue(toNumber, message, null, macroMachine);
        LOGGER.debug(String.format("password SMS added to send queue for %s", toNumber));
        return null;
    }

    private static ErrorInformation sendNewPasswordEmail(
            final UserInfo userInfo,
            final PwmApplication pwmApplication,
            final MacroMachine macroMachine,
            final PasswordData newPassword,
            final String toAddress,
            final Locale userLocale
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_SENDPASSWORD, userLocale);

        if (configuredEmailSetting == null) {
            final String errorMsg = "send password email contents are not configured";
            return new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
        }

        final EmailItemBean emailItemBean = new EmailItemBean(
                configuredEmailSetting.getTo(),
                configuredEmailSetting.getFrom(),
                configuredEmailSetting.getSubject(),
                configuredEmailSetting.getBodyPlain().replace("%TOKEN%", newPassword.getStringValue()),
                configuredEmailSetting.getBodyHtml().replace("%TOKEN%", newPassword.getStringValue())
        );
        pwmApplication.getEmailQueue().submitEmail(
                emailItemBean,
                userInfo,
                macroMachine
        );


        LOGGER.debug("new password email to " + userInfo.getUserIdentity() + " added to send queue for " + toAddress);
        return null;
    }


    enum PasswordPolicySource {
        MERGE,
        LDAP,
        PWM,
    }

    private PasswordUtility() {
    }

    /**
     * This is the entry point under which all password changes are managed.
     * The following is the general procedure when this method is invoked.
     * <ul>
     * <li> password is checked against PWM password requirement </li>
     * <li> ldap password set is attempted<br/>
     * <br/>if successful:
     * <ul>
     * <li> uiBean is updated with old and new passwords </li>
     * <li> uiBean's password expire flag is set to false </li>
     * <li> any configured external methods are invoked </li>
     * <li> user email notification is sent </li>
     * <li> return true </li>
     * </ul>
     * <br/>if unsuccessful
     * <ul>
     * <li> ssBean is updated with appropriate error </li>
     * <li> return false </li>
     * </ul>
     * </li>
     * </ul>
     *
     * @param newPassword the new password that is being set.
     * @param pwmSession  beanmanager for config and user info lookup
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *          if the ldap directory is not unavailable
     * @throws password.pwm.error.PwmUnrecoverableException
     *          if user is not authenticated
     */
    public static void setActorPassword(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final PasswordData newPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final UserInfo userInfo = pwmSession.getUserInfo();

        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.CHANGE_PASSWORD)) {
            final String errorMsg = "attempt to setActorPassword, but user does not have password change permission";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        // double check to make sure password meets PWM rule requirements.  This should
        // have been done before setActorPassword() is invoked, so it should be redundant
        // but we do it just in case.
        try {
            final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator(pwmApplication,userInfo.getPasswordPolicy());
            pwmPasswordRuleValidator.testPassword(newPassword,null,userInfo,pwmSession.getSessionManager().getActor(pwmApplication));
        } catch (PwmDataValidationException e) {
            final String errorMsg = "attempt to setActorPassword, but password does not pass local policy validator";
            final ErrorInformation errorInformation = new ErrorInformation(e.getErrorInformation().getError(), errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        // retrieve the user's old password from the userInfoBean in the session
        final PasswordData oldPassword = pwmSession.getLoginInfoBean().getUserCurrentPassword();

        boolean setPasswordWithoutOld = false;
        if (oldPassword == null) {
            if (pwmSession.getSessionManager().getActor(pwmApplication).getChaiProvider().getDirectoryVendor() == DirectoryVendor.ACTIVE_DIRECTORY) {
                setPasswordWithoutOld = true;
            }
        }

        if (!setPasswordWithoutOld) {
            // Check to make sure we actually have an old password
            if (oldPassword == null) {
                final String errorMsg = "cannot set password for user, old password is not available";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }

        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();

        setPassword(pwmApplication, pwmSession.getLabel(), provider, userInfo.getUserIdentity(), setPasswordWithoutOld ? null : oldPassword, newPassword);

        // update the session state bean's password modified flag
        pwmSession.getSessionStateBean().setPasswordModified(true);

        // update the login info bean with the user's new password
        pwmSession.getLoginInfoBean().setUserCurrentPassword(newPassword);

        //close any outstanding ldap connections (since they cache the old password)
        pwmSession.getSessionManager().updateUserPassword(pwmApplication, userInfo.getUserIdentity(), newPassword);

        // clear the "requires new password flag"
        pwmSession.getLoginInfoBean().getLoginFlags().remove(LoginInfoBean.LoginFlag.forcePwChange);

        // mark the auth type as authenticatePd now that we have the user's natural password.
        pwmSession.getLoginInfoBean().setType(AuthenticationType.AUTHENTICATED);

        // update the uibean's "password expired flag".
        pwmSession.reloadUserInfoBean(pwmApplication);

        // create a proxy user object for pwm to update/read the user.
        final ChaiUser proxiedUser = pwmSession.getSessionManager().getActor(pwmApplication);

        // update statistics
        {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.PASSWORD_CHANGES);
        }

        // invoke post password change actions
        invokePostChangePasswordActions(pwmSession, newPassword.getStringValue());

        {  // execute configured actions
            LOGGER.debug(pwmSession, "executing configured actions to user " + proxiedUser.getEntryDN());
            final List<ActionConfiguration> configValues = pwmApplication.getConfig().readSettingAsAction(PwmSetting.CHANGE_PASSWORD_WRITE_ATTRIBUTES);
            if (configValues != null && !configValues.isEmpty()) {
                final LoginInfoBean clonedLoginInfoBean = JsonUtil.cloneUsingJson(pwmSession.getLoginInfoBean(), LoginInfoBean.class);
                clonedLoginInfoBean.setUserCurrentPassword(newPassword);

                final MacroMachine macroMachine = new MacroMachine(
                        pwmApplication,
                        pwmSession.getLabel(),
                        pwmSession.getUserInfo(),
                        clonedLoginInfoBean
                );

                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, userInfo.getUserIdentity())
                        .setMacroMachine(macroMachine)
                        .setExpandPwmMacros(true)
                        .createActionExecutor();
                actionExecutor.executeActions(configValues, pwmSession.getLabel());
            }
        }

        //update the current last password update field in ldap
        LdapOperationsHelper.updateLastPasswordUpdateAttribute(pwmApplication, pwmSession.getLabel(), userInfo.getUserIdentity());
    }

    public static void setPassword(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final ChaiProvider chaiProvider,
            final UserIdentity userIdentity,
            final PasswordData oldPassword,
            final PasswordData newPassword
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Instant startTime = Instant.now();
        final boolean bindIsSelf;
        final String bindDN;

        try {
            final ChaiUser theUser = chaiProvider.getEntryFactory().newChaiUser(userIdentity.getUserDN());
            bindDN = chaiProvider.getChaiConfiguration().getSetting(ChaiSetting.BIND_DN);
            bindIsSelf = userIdentity.canonicalEquals(new UserIdentity(bindDN, userIdentity.getLdapProfileID()), pwmApplication);

            LOGGER.trace(sessionLabel, "preparing to setActorPassword for '" + theUser.getEntryDN() + "', using bind DN: " + bindDN);

            final boolean setting_enableChange = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.LDAP_PASSWORD_CHANGE_SELF_ENABLE));
            if (setting_enableChange) {
                if (oldPassword == null) {
                    theUser.setPassword(newPassword.getStringValue(), true);
                } else {
                    theUser.changePassword(oldPassword.getStringValue(), newPassword.getStringValue());
                }
            } else {
                LOGGER.debug(sessionLabel, "skipping actual ldap password change operation due to app property " + AppProperty.LDAP_PASSWORD_CHANGE_SELF_ENABLE.getKey() + "=false");
            }
        } catch (ChaiPasswordPolicyException e) {
            final String errorMsg = "error setting password for user '" + userIdentity.toDisplayString() + "'' " + e.toString();
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError, errorMsg);
            throw new PwmOperationalException(error);
        } catch (ChaiOperationException e) {
            final String errorMsg = "error setting password for user '" + userIdentity.toDisplayString() + "'' " + e.getMessage();
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(pwmError, errorMsg);
            throw new PwmOperationalException(error);
        } catch (ChaiUnavailableException e) {
            throw PwmUnrecoverableException.fromChaiException(e);
        }

        // add the old password to the global history list (if the old password is known)
        if (oldPassword != null && pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_SHAREDHISTORY_ENABLE)) {
            pwmApplication.getSharedHistoryManager().addWord(sessionLabel, oldPassword.getStringValue());
        }

        // update stats
        pwmApplication.getStatisticsManager().updateEps(EpsStatistic.PASSWORD_CHANGES,1);

        final int passwordStrength = PasswordUtility.judgePasswordStrength(pwmApplication.getConfig(), newPassword.getStringValue());
        pwmApplication.getStatisticsManager().updateAverageValue(Statistic.AVG_PASSWORD_STRENGTH,passwordStrength);

        // at this point the password has been changed, so log it.
        final String msg = (bindIsSelf
                ? "user " + userIdentity.toDisplayString() + " has changed own password"
                : "password for user '" + userIdentity.toDisplayString() + "' has been changed by " + bindDN)
                + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")";

        LOGGER.info(sessionLabel, msg);
    }

    public static void helpdeskSetUserPassword(
            final PwmSession pwmSession,
            final ChaiUser chaiUser,
            final UserIdentity userIdentity,
            final PwmApplication pwmApplication,
            final PasswordData newPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final SessionLabel sessionLabel = pwmSession.getLabel();

        if (!pwmSession.isAuthenticated()) {
            final String errorMsg = "attempt to helpdeskSetUserPassword, but user is not authenticated";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        final HelpdeskProfile helpdeskProfile = pwmSession.getSessionManager().getHelpdeskProfile(pwmApplication);
        if (helpdeskProfile == null) {
            final String errorMsg = "attempt to helpdeskSetUserPassword, but user does not have helpdesk permission";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        setPassword(pwmApplication, pwmSession.getLabel(), chaiUser.getChaiProvider(), userIdentity, null, newPassword);

        // create a proxy user object for pwm to update/read the user.
        final ChaiUser proxiedUser = pwmApplication.getProxiedChaiUser(userIdentity);

        // mark the event log
        {
            final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmApplication, pwmSession).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_SET_PASSWORD,
                    pwmSession.getUserInfo().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmApplication.getAuditManager().submit(auditRecord);
        }

        // update statistics
        pwmApplication.getStatisticsManager().incrementValue(Statistic.HELPDESK_PASSWORD_SET);

        // create a uib for end user
        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmApplication,
                pwmSession.getLabel(),
                pwmSession.getSessionStateBean().getLocale(),
                userIdentity,
                proxiedUser.getChaiProvider()
        );

        {  // execute configured actions
            LOGGER.debug(sessionLabel, "executing changepassword and helpdesk post password change writeAttributes to user " + userIdentity);
            final List<ActionConfiguration> actions = new ArrayList<>();
            actions.addAll(pwmApplication.getConfig().readSettingAsAction(PwmSetting.CHANGE_PASSWORD_WRITE_ATTRIBUTES));
            actions.addAll(helpdeskProfile.readSettingAsAction(PwmSetting.HELPDESK_POST_SET_PASSWORD_WRITE_ATTRIBUTES));
            if (!actions.isEmpty()) {

                final LoginInfoBean loginInfoBean = new LoginInfoBean();
                loginInfoBean.setUserCurrentPassword(newPassword);

                final MacroMachine macroMachine = new MacroMachine(
                        pwmApplication,
                        sessionLabel,
                        userInfo,
                        loginInfoBean
                );

                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, userIdentity)
                        .setMacroMachine(macroMachine)
                        .setExpandPwmMacros(true)
                        .createActionExecutor();

                actionExecutor.executeActions(actions, pwmSession.getLabel());
            }
        }

        final HelpdeskClearResponseMode settingClearResponses = HelpdeskClearResponseMode.valueOf(
                helpdeskProfile.readSettingAsString(PwmSetting.HELPDESK_CLEAR_RESPONSES)
        );

        if (settingClearResponses == HelpdeskClearResponseMode.yes) {
            final String userGUID = LdapOperationsHelper.readLdapGuidValue(pwmApplication, sessionLabel, userIdentity, false);
            pwmApplication.getCrService().clearResponses(pwmSession.getLabel(), userIdentity, proxiedUser, userGUID);

            // mark the event log
            final HelpdeskAuditRecord auditRecord = new AuditRecordFactory(pwmApplication, pwmSession).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_CLEAR_RESPONSES,
                    pwmSession.getUserInfo().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmApplication.getAuditManager().submit(auditRecord);
        }

        // send email notification
        sendChangePasswordHelpdeskEmailNotice(pwmSession, pwmApplication, userInfo);

        // expire if so configured
        if (helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_FORCE_PW_EXPIRATION)) {
            LOGGER.trace(pwmSession,"preparing to expire password for user " + userIdentity.toDisplayString());
            try {
                proxiedUser.expirePassword();
            } catch (ChaiOperationException e) {
                LOGGER.warn(pwmSession, "error while forcing password expiration for user " + userIdentity.toDisplayString() + ", error: " + e.getMessage());
            }
        }

        // send password
        final boolean sendPassword = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_SEND_PASSWORD);
        if (sendPassword) {
            final MessageSendMethod messageSendMethod;
            {
                final String profileID = ProfileUtility.discoverProfileIDforUser(pwmApplication, sessionLabel, userIdentity, ProfileType.ForgottenPassword);
                final ForgottenPasswordProfile forgottenPasswordProfile = pwmApplication.getConfig().getForgottenPasswordProfiles().get(profileID);
                messageSendMethod = forgottenPasswordProfile.readSettingAsEnum(PwmSetting.RECOVERY_SENDNEWPW_METHOD, MessageSendMethod.class);

            }
            PasswordUtility.sendNewPassword(
                    userInfo,
                    pwmApplication,
                    newPassword,
                    pwmSession.getSessionStateBean().getLocale(),
                    messageSendMethod
            );
        }
    }

    public static Map<String,Instant> readIndividualReplicaLastPasswordTimes(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Map<String,Instant> returnValue = new LinkedHashMap<>();
        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID());
        final Collection<ChaiConfiguration> perReplicaConfigs = ChaiUtility.splitConfigurationPerReplica(
                chaiProvider.getChaiConfiguration(),
                Collections.singletonMap(ChaiSetting.FAILOVER_CONNECT_RETRIES, "1")
        );
        for (final ChaiConfiguration loopConfiguration : perReplicaConfigs) {
            final String loopReplicaUrl = loopConfiguration.getSetting(ChaiSetting.BIND_DN);
            ChaiProvider loopProvider = null;
            try {
                loopProvider = pwmApplication.getLdapConnectionService().getChaiProviderFactory().newProvider(loopConfiguration);
                final Instant lastModifiedDate = determinePwdLastModified(pwmApplication, sessionLabel, userIdentity);
                returnValue.put(loopReplicaUrl, lastModifiedDate);
            } catch (ChaiUnavailableException e) {
                LOGGER.error(sessionLabel, "unreachable server during replica password sync check");
                e.printStackTrace();
            } finally {
                if (loopProvider != null) {
                    try {
                        loopProvider.close();
                    } catch (Exception e) {
                        final String errorMsg = "error closing loopProvider to " + loopReplicaUrl + " while checking individual password sync status";
                        LOGGER.error(sessionLabel, errorMsg);
                    }
                }
            }
        }
        return returnValue;
    }


    private static void invokePostChangePasswordActions(final PwmSession pwmSession, final String newPassword)
            throws PwmUnrecoverableException
    {
        final List<PostChangePasswordAction> postChangePasswordActions = pwmSession.getUserSessionDataCacheBean().removePostChangePasswordActions();
        if (postChangePasswordActions == null || postChangePasswordActions.isEmpty()) {
            LOGGER.trace(pwmSession, "no post change password actions pending from previous operations");
            return;
        }

        for (final PostChangePasswordAction postChangePasswordAction : postChangePasswordActions) {
            try {
                postChangePasswordAction.doAction(pwmSession, newPassword);
            } catch (PwmUnrecoverableException e) {
                LOGGER.error(pwmSession, "error during post change password action '" + postChangePasswordAction.getLabel() + "' " + e.getMessage());
                throw e;
            } catch (Exception e) {
                LOGGER.error(pwmSession, "unexpected error during post change password action '" + postChangePasswordAction.getLabel() + "' " + e.getMessage(), e);
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNKNOWN, e.getMessage());
                throw new PwmUnrecoverableException(errorInfo);
            }
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
            final Configuration configuration,
            final String password
    )
            throws PwmUnrecoverableException
    {
        final StrengthMeterType strengthMeterType = configuration.readSettingAsEnum(PwmSetting.PASSWORD_STRENGTH_METER_TYPE, StrengthMeterType.class);
        switch (strengthMeterType) {
            case ZXCVBN:
                return judgePasswordStrengthUsingZxcvbnAlgorithm(configuration, password);

            case PWM:
                return judgePasswordStrengthUsingTraditionalAlgorithm(password);

            default:
                JavaHelper.unhandledSwitchStatement(strengthMeterType);
        }

        return -1;
    }

    public static int judgePasswordStrengthUsingZxcvbnAlgorithm(
            final Configuration configuration,
            final String password
    ) {
        final Zxcvbn zxcvbn = new Zxcvbn();
        final Strength strength = zxcvbn.measure(password);

        final int zxcvbnScore = strength.getScore();

        // zxcvbn returns a score of 0-4 (see: https://github.com/dropbox/zxcvbn)
        switch (zxcvbnScore) {
            case 4: return Integer.parseInt(configuration.readAppProperty(AppProperty.PASSWORD_STRENGTH_THRESHOLD_VERY_STRONG));
            case 3: return Integer.parseInt(configuration.readAppProperty(AppProperty.PASSWORD_STRENGTH_THRESHOLD_STRONG));
            case 2: return Integer.parseInt(configuration.readAppProperty(AppProperty.PASSWORD_STRENGTH_THRESHOLD_GOOD));
            case 1: return Integer.parseInt(configuration.readAppProperty(AppProperty.PASSWORD_STRENGTH_THRESHOLD_WEAK));
            default: return Integer.parseInt(configuration.readAppProperty(AppProperty.PASSWORD_STRENGTH_THRESHOLD_VERY_WEAK));
        }
    }

    public static int judgePasswordStrengthUsingTraditionalAlgorithm (
            final String password
    )
            throws PwmUnrecoverableException
    {
        if (password == null || password.length() < 1) {
            return 0;
        }

        int score = 0;
        final PasswordCharCounter charCounter = new PasswordCharCounter(password);

        // -- Additions --
        // amount of unique chars
        if (charCounter.getUniqueChars() > 7) {
            score = score + 10;
        }
        score = score + ((charCounter.getUniqueChars()) * 3);

        // Numbers
        if (charCounter.getNumericCharCount() > 0) {
            score = score + 8;
            score = score + (charCounter.getNumericCharCount()) * 4;
        }

        // specials
        if (charCounter.getSpecialCharsCount() > 0) {
            score = score + 14;
            score = score + (charCounter.getSpecialCharsCount()) * 5;
        }

        // mixed case
        if ((charCounter.getAlphaChars().length() != charCounter.getUpperChars().length())
                && (charCounter.getAlphaChars().length() != charCounter.getLowerChars().length())) {
            score = score + 10;
        }

        // -- Deductions --

        // sequential numbers
        if (charCounter.getSequentialNumericChars() > 2) {
            score = score - (charCounter.getSequentialNumericChars() - 1) * 4;
        }

        // sequential chars
        if (charCounter.getSequentialRepeatedChars() > 1) {
            score = score - (charCounter.getSequentialRepeatedChars()) * 5;
        }

        return score > 100 ? 100 : score < 0 ? 0 : score;
    }


    public static PwmPasswordPolicy readPasswordPolicyForUser(
            final PwmApplication pwmApplication,
            final SessionLabel pwmSession,
            final UserIdentity userIdentity,
            final ChaiUser theUser,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final PasswordPolicySource ppSource = PasswordPolicySource.valueOf(pwmApplication.getConfig().readSettingAsString(PwmSetting.PASSWORD_POLICY_SOURCE));

        final PwmPasswordPolicy returnPolicy;
        switch (ppSource) {
            case MERGE:
                final PwmPasswordPolicy pwmPolicy = determineConfiguredPolicyProfileForUser(pwmApplication,pwmSession,userIdentity,locale);
                final PwmPasswordPolicy userPolicy = readLdapPasswordPolicy(pwmApplication, theUser);
                LOGGER.trace(pwmSession, "read user policy for '" + theUser.getEntryDN() + "', policy: " + userPolicy.toString());
                returnPolicy = pwmPolicy.merge(userPolicy);
                LOGGER.debug(pwmSession, "merged user password policy of '" + theUser.getEntryDN() + "' with PWM configured policy: " + returnPolicy.toString());
                break;

            case LDAP:
                returnPolicy = readLdapPasswordPolicy(pwmApplication, theUser);
                LOGGER.debug(pwmSession, "discovered assigned password policy for " + theUser.getEntryDN() + " " + returnPolicy.toString());
                break;

            case PWM:
                returnPolicy = determineConfiguredPolicyProfileForUser(pwmApplication,pwmSession,userIdentity,locale);
                break;

            default:
                throw new IllegalStateException("unknown policy source defined: " + ppSource.name());
        }

        LOGGER.trace(pwmSession, "readPasswordPolicyForUser completed in " + TimeDuration.fromCurrent(startTime).asCompactString());
        return returnPolicy;
    }

    public static PwmPasswordPolicy determineConfiguredPolicyProfileForUser(
            final PwmApplication pwmApplication,
            final SessionLabel pwmSession,
            final UserIdentity userIdentity,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final List<String> profiles = pwmApplication.getConfig().getPasswordProfileIDs();
        if (profiles.isEmpty()) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NO_PROFILE_ASSIGNED,"no password profiles are configured"));
        }

        for (final String profile : profiles) {
            final PwmPasswordPolicy loopPolicy = pwmApplication.getConfig().getPasswordPolicy(profile,locale);
            final List<UserPermission> userPermissions = loopPolicy.getUserPermissions();
            LOGGER.debug(pwmSession, "testing password policy profile '" + profile + "'");
            try {
                final boolean match = LdapPermissionTester.testUserPermissions(pwmApplication, pwmSession, userIdentity, userPermissions);
                if (match) {
                    return loopPolicy;
                }
            } catch (PwmUnrecoverableException e) {
                LOGGER.error(pwmSession,"unexpected error while testing password policy profile '" + profile + "', error: " + e.getMessage());
            }
        }

        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NO_PROFILE_ASSIGNED,"no challenge profile is configured"));
    }


    public static PwmPasswordPolicy readLdapPasswordPolicy(
            final PwmApplication pwmApplication,
            final ChaiUser theUser)
            throws PwmUnrecoverableException {
        try {
            final Map<String, String> ruleMap = new HashMap<>();
            final ChaiPasswordPolicy chaiPolicy;
            try {
                chaiPolicy = theUser.getPasswordPolicy();
            } catch (ChaiUnavailableException e) {
                throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
            }
            if (chaiPolicy != null) {
                for (final String key : chaiPolicy.getKeys()) {
                    ruleMap.put(key, chaiPolicy.getValue(key));
                }

                if (!"read".equals(pwmApplication.getConfig().readSettingAsString(PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY))) {
                    ruleMap.put(
                            PwmPasswordRule.CaseSensitive.getKey(),
                            pwmApplication.getConfig().readSettingAsString(PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY)
                    );
                }

                return PwmPasswordPolicy.createPwmPasswordPolicy(ruleMap, chaiPolicy);
            }
        } catch (ChaiOperationException e) {
            LOGGER.warn("error reading password policy for user " + theUser.getEntryDN() + ", error: " + e.getMessage());
        }
        return PwmPasswordPolicy.defaultPolicy();
    }

    public static PasswordCheckInfo checkEnteredPassword(
            final PwmApplication pwmApplication,
            final Locale locale,
            final ChaiUser user,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean,
            final PasswordData password,
            final PasswordData confirmPassword
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        if (userInfo == null) {
            throw new NullPointerException("userInfoBean cannot be null");
        }

        boolean pass = false;
        String userMessage = "";
        int errorCode = 0;

        final boolean passwordIsCaseSensitive = userInfo.getPasswordPolicy() == null || userInfo.getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.CaseSensitive);
        final CachePolicy cachePolicy;
        {
            final long cacheLifetimeMS = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.CACHE_PWRULECHECK_LIFETIME_MS));
            cachePolicy = CachePolicy.makePolicyWithExpirationMS(cacheLifetimeMS);
        }

        if (password == null) {
            userMessage = new ErrorInformation(PwmError.PASSWORD_MISSING).toUserStr(locale, pwmApplication.getConfig());
        } else {
            final CacheService cacheService = pwmApplication.getCacheService();
            final CacheKey cacheKey = user != null && userInfo.getUserIdentity() != null
                    ? CacheKey.makeCacheKey(
                    PasswordUtility.class,
                    userInfo.getUserIdentity(),
                    user.getEntryDN() + ":" + password.hash())
                    : null;
            if (pwmApplication.getConfig().isDevDebugMode()) {
                LOGGER.trace("generated cacheKey for password check request: " + cacheKey);
            }
            try {
                if (cacheService != null && cacheKey != null) {
                    final String cachedValue = cacheService.get(cacheKey);
                    if (cachedValue != null) {
                        if (NEGATIVE_CACHE_HIT.equals(cachedValue)) {
                            pass = true;
                        } else {
                            LOGGER.trace("cache hit!");
                            final ErrorInformation errorInformation = JsonUtil.deserialize(cachedValue, ErrorInformation.class);
                            throw new PwmDataValidationException(errorInformation);
                        }
                    }
                }
                if (!pass) {
                    final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator(pwmApplication, userInfo.getPasswordPolicy(), locale);
                    final PasswordData oldPassword = loginInfoBean == null ? null : loginInfoBean.getUserCurrentPassword();
                    pwmPasswordRuleValidator.testPassword(password, oldPassword, userInfo, user);
                    pass = true;
                    if (cacheService != null && cacheKey != null) {
                        cacheService.put(cacheKey, cachePolicy, NEGATIVE_CACHE_HIT);
                    }
                }
            } catch (PwmDataValidationException e) {
                errorCode = e.getError().getErrorCode();
                userMessage = e.getErrorInformation().toUserStr(locale, pwmApplication.getConfig());
                pass = false;
                if (cacheService != null && cacheKey != null) {
                    final String jsonPayload = JsonUtil.serialize(e.getErrorInformation());
                    cacheService.put(cacheKey, cachePolicy, jsonPayload);
                }
            }
        }

        final PasswordCheckInfo.MatchStatus matchStatus = figureMatchStatus(passwordIsCaseSensitive ,password, confirmPassword);
        if (pass) {
            switch (matchStatus) {
                case EMPTY:
                    userMessage = new ErrorInformation(PwmError.PASSWORD_MISSING_CONFIRM).toUserStr(locale, pwmApplication.getConfig());
                    break;
                case MATCH:
                    userMessage = new ErrorInformation(PwmError.PASSWORD_MEETS_RULES).toUserStr(locale, pwmApplication.getConfig());
                    break;
                case NO_MATCH:
                    userMessage = new ErrorInformation(PwmError.PASSWORD_DOESNOTMATCH).toUserStr(locale, pwmApplication.getConfig());
                    break;
                default:
                    userMessage = "";
            }
        }

        final int strength = judgePasswordStrength(pwmApplication.getConfig(), password == null ? null : password.getStringValue());
        return new PasswordCheckInfo(userMessage, pass, strength, matchStatus, errorCode);
    }


    public static PasswordCheckInfo.MatchStatus figureMatchStatus(
            final boolean caseSensitive,
            final PasswordData password1,
            final PasswordData password2
    ) {
        final PasswordCheckInfo.MatchStatus matchStatus;
        if (password2 == null) {
            matchStatus = PasswordCheckInfo.MatchStatus.EMPTY;
        } else if (password1 == null) {
            matchStatus = PasswordCheckInfo.MatchStatus.NO_MATCH;
        } else {
            if (caseSensitive) {
                matchStatus = password1.equals(password2) ? PasswordCheckInfo.MatchStatus.MATCH : PasswordCheckInfo.MatchStatus.NO_MATCH;
            } else {
                matchStatus = password1.equalsIgnoreCase(password2) ? PasswordCheckInfo.MatchStatus.MATCH : PasswordCheckInfo.MatchStatus.NO_MATCH;
            }
        }

        return matchStatus;
    }


    public static class PasswordCheckInfo implements Serializable {
        private final String message;
        private final boolean passed;
        private final int strength;
        private final MatchStatus match;
        private final int errorCode;

        public enum MatchStatus {
            MATCH, NO_MATCH, EMPTY
        }

        public PasswordCheckInfo(final String message, final boolean passed, final int strength, final MatchStatus match, final int errorCode) {
            this.message = message;
            this.passed = passed;
            this.strength = strength;
            this.match = match;
            this.errorCode = errorCode;
        }

        public String getMessage() {
            return message;
        }

        public boolean isPassed() {
            return passed;
        }

        public int getStrength() {
            return strength;
        }

        public MatchStatus getMatch() {
            return match;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

    private static void sendChangePasswordHelpdeskEmailNotice(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_CHANGEPASSWORD_HELPDESK, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession, "skipping send change password email for '" + pwmSession.getUserInfo().getUserIdentity() + "' no email configured");
            return;
        }

        final MacroMachine macroMachine = userInfo == null
                ? null
                : new MacroMachine(
                pwmApplication,
                pwmSession.getLabel(),
                userInfo,
                null
        );

        pwmApplication.getEmailQueue().submitEmail(configuredEmailSetting, userInfo, macroMachine);
    }

    public static Instant determinePwdLastModified(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
        return determinePwdLastModified(pwmApplication, sessionLabel, theUser, userIdentity);
    }

    private static Instant determinePwdLastModified(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final ChaiUser theUser,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        // fetch last password modification time from pwm last update attribute operation
        try {
            final Instant chaiReadDate = theUser.readPasswordModificationDate();
            if (chaiReadDate != null) {
                LOGGER.trace(sessionLabel, "read last user password change timestamp (via chai) as: " + JavaHelper.toIsoDate(chaiReadDate));
                return chaiReadDate;
            }
        } catch (ChaiOperationException e) {
            LOGGER.error(sessionLabel, "unexpected error reading password last modified timestamp: " + e.getMessage());
        }

        final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get(userIdentity.getLdapProfileID());
        final String pwmLastSetAttr = ldapProfile.readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE);
        if (pwmLastSetAttr != null && pwmLastSetAttr.length() > 0) {
            try {
                final Instant pwmPwdLastModified = theUser.readDateAttribute(pwmLastSetAttr);
                LOGGER.trace(sessionLabel, "read pwmPasswordChangeTime as: " + (pwmPwdLastModified == null ? "n/a" : JavaHelper.toIsoDate(pwmPwdLastModified)));
                return pwmPwdLastModified;
            } catch (ChaiOperationException e) {
                LOGGER.error(sessionLabel, "error parsing password last modified PWM password value for user " + theUser.getEntryDN() + "; error: " + e.getMessage());
            }
        }

        LOGGER.debug(sessionLabel, "unable to determine time of user's last password modification");
        return null;
    }

    public static void checkIfPasswordWithinMinimumLifetime(
            final ChaiUser chaiUser,
            final SessionLabel sessionLabel,
            final PwmPasswordPolicy passwordPolicy,
            final Instant lastModified,
            final PasswordStatus passwordStatus
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {

        // for oracle DS; this check is also handled in UserAuthenticator.
        try {
            if (DirectoryVendor.ORACLE_DS == chaiUser.getChaiProvider().getDirectoryVendor()) {
                final String oracleDS_PrePasswordAllowChangeTime = chaiUser.readStringAttribute("passwordAllowChangeTime");
                if (oracleDS_PrePasswordAllowChangeTime != null && !oracleDS_PrePasswordAllowChangeTime.isEmpty()) {
                    final Instant date = OracleDSEntries.convertZuluToDate(oracleDS_PrePasswordAllowChangeTime);
                    if (Instant.now().isBefore(date)) {
                        LOGGER.debug("discovered oracleds allowed change time is set to: " + JavaHelper.toIsoDate(date) + ", won't permit password change");
                        final String errorMsg = "change not permitted until " + JavaHelper.toIsoDate(date);
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.PASSWORD_TOO_SOON, errorMsg);
                        throw new PwmUnrecoverableException(errorInformation);
                    }
                }
                return;
            }
        } catch(ChaiException e) {
            LOGGER.debug(sessionLabel, "unexpected error reading OracleDS password allow modification time: " + e.getMessage());
        }

        final TimeDuration minimumLifetime;
        {
            final int minimumLifetimeSeconds = passwordPolicy.getRuleHelper().readIntValue(PwmPasswordRule.MinimumLifetime);
            if (minimumLifetimeSeconds < 1) {
                return;
            }

            if (lastModified == null) {
                LOGGER.debug(sessionLabel, "skipping minimum lifetime check, password last set time is unknown");
                return;
            }

            minimumLifetime = new TimeDuration(minimumLifetimeSeconds, TimeUnit.SECONDS);
        }

        final TimeDuration passwordAge = TimeDuration.fromCurrent(lastModified);
        LOGGER.trace(sessionLabel, "beginning check for minimum lifetime, lastModified="
                + JavaHelper.toIsoDate(lastModified)
                + ", minimumLifetimeSeconds=" + minimumLifetime.asCompactString()
                + ", passwordAge=" + passwordAge.asCompactString());


        if (lastModified.isAfter(Instant.now())) {
            LOGGER.debug(sessionLabel, "skipping minimum lifetime check, password lastModified time is in the future");
            return;
        }

        final boolean passwordTooSoon = passwordAge.isShorterThan(minimumLifetime);
        if (!passwordTooSoon) {
            LOGGER.trace(sessionLabel, "minimum lifetime check passed, password age ");
            return;
        }

        if (passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isWarnPeriod()) {
            LOGGER.debug(sessionLabel, "current password is too young, but skipping enforcement of minimum lifetime check because current password is expired");
            return;
        }

        final Instant allowedChangeDate = Instant.ofEpochMilli(lastModified.toEpochMilli() + minimumLifetime.getTotalMilliseconds());
        final String errorMsg = "last password change was at "
                + JavaHelper.toIsoDate(lastModified)
                + " and is too recent (" + passwordAge.asCompactString()
                + " ago), password cannot be changed within minimum lifetime of "
                + minimumLifetime.asCompactString()
                + ", next eligible time to change is after " + JavaHelper.toIsoDate(allowedChangeDate);

        final ErrorInformation errorInformation = new ErrorInformation(PwmError.PASSWORD_TOO_SOON,errorMsg);
        throw new PwmOperationalException(errorInformation);
    }
}

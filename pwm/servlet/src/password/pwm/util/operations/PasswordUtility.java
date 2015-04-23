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

package password.pwm.util.operations;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiPasswordPolicy;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.util.ChaiUtility;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.*;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.UserPermission;
import password.pwm.config.option.HelpdeskClearResponseMode;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.event.HelpdeskAuditRecord;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.LoginInfoBean;
import password.pwm.ldap.*;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.util.*;
import password.pwm.util.cache.CacheKey;
import password.pwm.util.cache.CachePolicy;
import password.pwm.util.cache.CacheService;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.stats.Statistic;

import java.io.Serializable;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class PasswordUtility {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PasswordUtility.class);
    private static final String NEGATIVE_CACHE_HIT = "NEGATIVE_CACHE_HIT";

    public static String sendNewPassword(
            final UserInfoBean userInfoBean,
            final PwmApplication pwmApplication,
            final MacroMachine macroMachine,
            final PasswordData newPassword,
            final Locale userLocale,
            final MessageSendMethod messageSendMethod
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String emailAddress = userInfoBean.getUserEmailAddress();
        final String smsNumber = userInfoBean.getUserSmsNumber();
        String returnToAddress = emailAddress;

        ErrorInformation error = null;
        switch (messageSendMethod) {
            case BOTH:
                // Send both email and SMS, success if one of both succeeds
                final ErrorInformation err1 = sendNewPasswordEmail(userInfoBean, pwmApplication, macroMachine, newPassword, emailAddress, userLocale);
                final ErrorInformation err2 = sendNewPasswordSms(userInfoBean, pwmApplication, macroMachine, newPassword, smsNumber, userLocale);
                if (err1 != null) {
                    error = err1;
                    returnToAddress = smsNumber;
                } else if (err2 != null) {
                    error = err2;
                }
                break;
            case EMAILFIRST:
                // Send email first, try SMS if email is not available
                error = sendNewPasswordEmail(userInfoBean, pwmApplication, macroMachine, newPassword, emailAddress, userLocale);
                if (error != null) {
                    error = sendNewPasswordSms(userInfoBean, pwmApplication, macroMachine, newPassword, smsNumber, userLocale);
                    returnToAddress = smsNumber;
                }
                break;
            case SMSFIRST:
                // Send SMS first, try email if SMS is not available
                error = sendNewPasswordSms(userInfoBean, pwmApplication, macroMachine, newPassword, smsNumber, userLocale);
                if (error != null) {
                    error = sendNewPasswordEmail(userInfoBean, pwmApplication, macroMachine, newPassword, emailAddress, userLocale);
                } else {
                    returnToAddress = smsNumber;
                }
                break;
            case SMSONLY:
                // Only try SMS
                error = sendNewPasswordSms(userInfoBean, pwmApplication, macroMachine, newPassword, smsNumber, userLocale);
                returnToAddress = smsNumber;
                break;
            case EMAILONLY:
            default:
                // Only try email
                error = sendNewPasswordEmail(userInfoBean, pwmApplication, macroMachine, newPassword, emailAddress, userLocale);
                break;
        }
        if (error != null) {
            throw new PwmOperationalException(error);
        }
        return returnToAddress;
    }

    private static ErrorInformation sendNewPasswordSms(
            final UserInfoBean userInfoBean,
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
            final String errorMsg = String.format("unable to send new password email for '%s'; no SMS number available in ldap", userInfoBean.getUserIdentity());
            return new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
        }

        message = message.replace("%TOKEN%", newPassword.getStringValue());

        pwmApplication.sendSmsUsingQueue(new SmsItemBean(toNumber, message), macroMachine);
        LOGGER.debug(String.format("password SMS added to send queue for %s", toNumber));
        return null;
    }

    private static ErrorInformation sendNewPasswordEmail(
            final UserInfoBean userInfoBean,
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
                userInfoBean,
                macroMachine
        );


        LOGGER.debug("new password email to " + userInfoBean.getUserIdentity() + " added to send queue for " + toAddress);
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
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();

        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.CHANGE_PASSWORD)) {
            final String errorMsg = "attempt to setActorPassword, but user does not have password change permission";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        // double check to make sure password meets PWM rule requirements.  This should
        // have been done before setActorPassword() is invoked, so it should be redundant
        // but we do it just in case.
        try {
            final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator(pwmApplication,uiBean.getPasswordPolicy());
            pwmPasswordRuleValidator.testPassword(newPassword,null,uiBean,pwmSession.getSessionManager().getActor(pwmApplication));
        } catch (PwmDataValidationException e) {
            final String errorMsg = "attempt to setActorPassword, but password does not pass local policy validator";
            final ErrorInformation errorInformation = new ErrorInformation(e.getErrorInformation().getError(), errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        // retrieve the user's old password from the userInfoBean in the session
        final PasswordData oldPassword = pwmSession.getLoginInfoBean().getUserCurrentPassword();

        boolean setPasswordWithoutOld = false;
        if (oldPassword == null) {
            if (pwmSession.getSessionManager().getActor(pwmApplication).getChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY) {
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

        try {
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser theUser = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserIdentity().getUserDN(), provider);
            final boolean boundAsSelf = theUser.getEntryDN().equals(provider.getChaiConfiguration().getSetting(ChaiSetting.BIND_DN));
            LOGGER.trace(pwmSession, "preparing to setActorPassword for '" + theUser.getEntryDN() + "', bindAsSelf=" + boundAsSelf + ", authType=" + pwmSession.getLoginInfoBean().getAuthenticationType());
            if (setPasswordWithoutOld) {
                theUser.setPassword(newPassword.getStringValue(), true);
            } else {
                theUser.changePassword(oldPassword.getStringValue(), newPassword.getStringValue());
            }
        } catch (ChaiPasswordPolicyException e) {
            final String errorMsg = "error setting password for user '" + uiBean.getUserIdentity() + "'' " + e.toString();
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError, errorMsg);
            throw new PwmOperationalException(error);
        } catch (ChaiOperationException e) {
            final String errorMsg = "error setting password for user '" + uiBean.getUserIdentity() + "'' " + e.getMessage();
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(pwmError, errorMsg);
            throw new PwmOperationalException(error);
        }

        // at this point the password has been changed, so log it.
        LOGGER.info(pwmSession, "user '" + uiBean.getUserIdentity() + "' successfully changed password");

        // update the session state bean's password modified flag
        pwmSession.getSessionStateBean().setPasswordModified(true);

        // update the login info bean with the user's new password
        pwmSession.getLoginInfoBean().setUserCurrentPassword(newPassword);

        //close any outstanding ldap connections (since they cache the old password)
        pwmSession.getSessionManager().updateUserPassword(pwmApplication, uiBean.getUserIdentity(), newPassword);

        // clear the "requires new password flag"
        uiBean.setRequiresNewPassword(false);

        // mark the auth type as authenticatePd now that we have the user's natural password.
        pwmSession.getLoginInfoBean().setAuthenticationType(AuthenticationType.AUTHENTICATED);

        // update the uibean's "password expired flag".
        final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication, pwmSession.getLabel());
        uiBean.setPasswordState(userStatusReader.readPasswordStatus(
                pwmSession.getSessionManager().getActor(pwmApplication),
                uiBean.getPasswordPolicy(),
                uiBean,
                newPassword
        ));

        // create a proxy user object for pwm to update/read the user.
        final ChaiUser proxiedUser = pwmSession.getSessionManager().getActor(pwmApplication);

        // update statistics
        {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.PASSWORD_CHANGES);
            pwmApplication.getStatisticsManager().updateEps(Statistic.EpsType.PASSWORD_CHANGES,1);
            final int passwordStrength = PasswordUtility.judgePasswordStrength(newPassword.getStringValue());
            pwmApplication.getStatisticsManager().updateAverageValue(Statistic.AVG_PASSWORD_STRENGTH,passwordStrength);
        }

        // add the old password to the global history list (if the old password is known)
        if (oldPassword != null && pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_SHAREDHISTORY_ENABLE)) {
            pwmApplication.getSharedHistoryManager().addWord(pwmSession, oldPassword.getStringValue());
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
                        pwmSession.getUserInfoBean(),
                        clonedLoginInfoBean,
                        pwmSession.getSessionManager().getUserDataReader(pwmApplication)
                );

                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, uiBean.getUserIdentity())
                        .setMacroMachine(macroMachine)
                        .setExpandPwmMacros(true)
                        .createActionExecutor();
                actionExecutor.executeActions(configValues, pwmSession);
            }
        }

        //update the current last password update field in ldap
        LdapOperationsHelper.updateLastPasswordUpdateAttribute(pwmApplication, pwmSession.getLabel(), uiBean.getUserIdentity());
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

        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
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

        try {
            chaiUser.setPassword(newPassword.getStringValue());
        } catch (ChaiPasswordPolicyException e) {
            final String errorMsg = "error setting password for user '" + chaiUser.getEntryDN() + "'' " + e.toString();
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError, errorMsg);
            throw new PwmOperationalException(error);
        } catch (ChaiOperationException e) {
            final String errorMsg = "error setting password for user '" + chaiUser.getEntryDN() + "'' " + e.getMessage();
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(pwmError, errorMsg);
            throw new PwmOperationalException(error);
        }

        // at this point the password has been changed, so log it.
        LOGGER.info(sessionLabel, "user '" + pwmSession.getUserInfoBean().getUserIdentity() + "' successfully changed password for " + chaiUser.getEntryDN());

        // create a proxy user object for pwm to update/read the user.
        final ChaiUser proxiedUser = pwmApplication.getProxiedChaiUser(userIdentity);

        // mark the event log
        {
            final HelpdeskAuditRecord auditRecord = pwmApplication.getAuditManager().createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_SET_PASSWORD,
                    pwmSession.getUserInfoBean().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmApplication.getAuditManager().submit(auditRecord);
        }

        // update statistics
        pwmApplication.getStatisticsManager().updateEps(Statistic.EpsType.PASSWORD_CHANGES,1);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.HELPDESK_PASSWORD_SET);

        // create a uib for end user
        final UserInfoBean userInfoBean = new UserInfoBean();
        final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication, pwmSession.getLabel());
        userStatusReader.populateUserInfoBean(
                userInfoBean,
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

                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, userIdentity)
                        .setMacroMachine(MacroMachine.forUser(
                                pwmApplication,
                                pwmSession.getSessionStateBean().getLocale(),
                                sessionLabel,
                                userIdentity
                        ))
                        .setExpandPwmMacros(true)
                        .createActionExecutor();

                actionExecutor.executeActions(actions,pwmSession);
            }
        }

        final HelpdeskClearResponseMode settingClearResponses = HelpdeskClearResponseMode.valueOf(
                helpdeskProfile.readSettingAsString(PwmSetting.HELPDESK_CLEAR_RESPONSES));
        if (settingClearResponses == HelpdeskClearResponseMode.yes) {
            final String userGUID = LdapOperationsHelper.readLdapGuidValue(pwmApplication, sessionLabel, userIdentity, false);
            pwmApplication.getCrService().clearResponses(pwmSession, proxiedUser, userGUID);

            // mark the event log
            final HelpdeskAuditRecord auditRecord = pwmApplication.getAuditManager().createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_CLEAR_RESPONSES,
                    pwmSession.getUserInfoBean().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmApplication.getAuditManager().submit(auditRecord);
        }

        // send email notification
        sendChangePasswordHelpdeskEmailNotice(pwmSession, pwmApplication, userInfoBean);

        // expire if so configured
        if (helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_FORCE_PW_EXPIRATION)) {
            LOGGER.trace(pwmSession,"preparing to expire password for user " + userIdentity.toDisplayString());
            try {
                proxiedUser.expirePassword();
            } catch (ChaiOperationException e) {
                LOGGER.warn(pwmSession, "error while forcing password expiration for user " + userIdentity.toDisplayString() + ", error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // send password
        final boolean sendPassword = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_SEND_PASSWORD);
        if (sendPassword) {
            final UserDataReader userDataReader = new LdapUserDataReader(userIdentity, chaiUser);
            final LoginInfoBean loginInfoBean = new LoginInfoBean();
            loginInfoBean.setUserCurrentPassword(newPassword);
            final MacroMachine macroMachine = new MacroMachine(pwmApplication, pwmSession.getLabel(), userInfoBean, loginInfoBean, userDataReader);
            PasswordUtility.sendNewPassword(
                    userInfoBean,
                    pwmApplication,
                    macroMachine,
                    newPassword,
                    pwmSession.getSessionStateBean().getLocale(),
                    MessageSendMethod.EMAILONLY
            );
        }
    }

    public static Map<String,Date> readIndividualReplicaLastPasswordTimes(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Map<String,Date> returnValue = new LinkedHashMap<>();
        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID());
        final Collection<ChaiConfiguration> perReplicaConfigs = ChaiUtility.splitConfigurationPerReplica(
                chaiProvider.getChaiConfiguration(),
                Collections.singletonMap(ChaiSetting.FAILOVER_CONNECT_RETRIES,"1")
        );
        for (final ChaiConfiguration loopConfiguration : perReplicaConfigs) {
            final String loopReplicaUrl = loopConfiguration.getSetting(ChaiSetting.BIND_DN);
            ChaiProvider loopProvider = null;
            try {
                loopProvider = ChaiProviderFactory.createProvider(loopConfiguration);
                final Date lastModifiedDate = determinePwdLastModified(pwmApplication, sessionLabel, userIdentity);
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
        final List<PostChangePasswordAction> postChangePasswordActions = pwmSession.getLoginInfoBean().removePostChangePasswordActions();
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
        if ((charCounter.getAlphaChars() != charCounter.getUpperChars()) && (charCounter.getAlphaChars() != charCounter.getLowerChars())) {
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
        final long startTime = System.currentTimeMillis();
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

    protected static PwmPasswordPolicy determineConfiguredPolicyProfileForUser(
            final PwmApplication pwmApplication,
            final SessionLabel pwmSession,
            final UserIdentity userIdentity,
            final Locale locale
    ) throws PwmUnrecoverableException {
        final List<String> profiles = pwmApplication.getConfig().getPasswordProfileIDs();
        if (profiles.isEmpty()) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_NO_PROFILE_ASSIGNED,"no password profiles are configured"));
        }

        for (final String profile : profiles) {
            final PwmPasswordPolicy loopPolicy = pwmApplication.getConfig().getPasswordPolicy(profile,locale);
            final List<UserPermission> userPermissions = loopPolicy.getUserPermissions();
            LOGGER.debug(pwmSession, "testing password policy profile '" + profile + "'");
            try {
                boolean match = LdapPermissionTester.testUserPermissions(pwmApplication, pwmSession, userIdentity, userPermissions);
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
            final UserInfoBean userInfoBean,
            final LoginInfoBean loginInfoBean,
            final PasswordData password,
            final PasswordData confirmPassword
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        if (userInfoBean == null) {
            throw new NullPointerException("userInfoBean cannot be null");
        }

        boolean pass = false;
        String userMessage = "";
        int errorCode = 0;

        final boolean passwordIsCaseSensitive = userInfoBean.getPasswordPolicy() == null || userInfoBean.getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.CaseSensitive);
        final CachePolicy cachePolicy;
        {
            final long cacheLifetimeMS = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.CACHE_PWRULECHECK_LIFETIME_MS));
            cachePolicy = CachePolicy.makePolicyWithExpirationMS(cacheLifetimeMS);
        }

        if (password == null) {
            userMessage = new ErrorInformation(PwmError.PASSWORD_MISSING).toUserStr(locale, pwmApplication.getConfig());
        } else {
            final CacheService cacheService = pwmApplication.getCacheService();
            final CacheKey cacheKey = user != null && userInfoBean.getUserIdentity() != null
                    ? CacheKey.makeCacheKey(
                    PasswordUtility.class,
                    userInfoBean.getUserIdentity(),
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
                    final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator(pwmApplication, userInfoBean.getPasswordPolicy(), locale);
                    final PasswordData oldPassword = loginInfoBean == null ? null : loginInfoBean.getUserCurrentPassword();
                    pwmPasswordRuleValidator.testPassword(password, oldPassword, userInfoBean, user);
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

        final PasswordCheckInfo.MATCH_STATUS matchStatus = figureMatchStatus(passwordIsCaseSensitive ,password, confirmPassword);
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

        final int strength = judgePasswordStrength(password == null ? null : password.getStringValue());
        return new PasswordCheckInfo(userMessage, pass, strength, matchStatus, errorCode);
    }


    public static PasswordCheckInfo.MATCH_STATUS figureMatchStatus(
            final boolean caseSensitive,
            final PasswordData password1,
            final PasswordData password2
    ) {
        final PasswordCheckInfo.MATCH_STATUS matchStatus;
        if (password2 == null) {
            matchStatus = PasswordCheckInfo.MATCH_STATUS.EMPTY;
        } else if (password1 == null) {
            matchStatus = PasswordCheckInfo.MATCH_STATUS.NO_MATCH;
        } else {
            if (caseSensitive) {
                matchStatus = password1.equals(password2) ? PasswordCheckInfo.MATCH_STATUS.MATCH : PasswordCheckInfo.MATCH_STATUS.NO_MATCH;
            } else {
                matchStatus = password1.equalsIgnoreCase(password2) ? PasswordCheckInfo.MATCH_STATUS.MATCH : PasswordCheckInfo.MATCH_STATUS.NO_MATCH;
            }
        }

        return matchStatus;
    }


    public static class PasswordCheckInfo implements Serializable {
        private final String message;
        private final boolean passed;
        private final int strength;
        private final MATCH_STATUS match;
        private final int errorCode;

        public enum MATCH_STATUS {
            MATCH, NO_MATCH, EMPTY
        }

        public PasswordCheckInfo(String message, boolean passed, int strength, MATCH_STATUS match, int errorCode) {
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

        public MATCH_STATUS getMatch() {
            return match;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

    private static void sendChangePasswordHelpdeskEmailNotice(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final UserInfoBean userInfoBean
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_CHANGEPASSWORD_HELPDESK, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession, "skipping send change password email for '" + pwmSession.getUserInfoBean().getUserIdentity() + "' no email configured");
            return;
        }

        pwmApplication.getEmailQueue().submitEmail(configuredEmailSetting, userInfoBean, null);
    }

    public static Date determinePwdLastModified(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
        return determinePwdLastModified(pwmApplication, sessionLabel, theUser, userIdentity);
    }

    private static Date determinePwdLastModified(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final ChaiUser theUser,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        // fetch last password modification time from pwm last update attribute operation
        try {
            final Date chaiReadDate = theUser.readPasswordModificationDate();
            if (chaiReadDate != null) {
                LOGGER.trace(sessionLabel, "read last user password change timestamp (via chai) as: " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(chaiReadDate));
                return chaiReadDate;
            }
        } catch (ChaiOperationException e) {
            LOGGER.error(sessionLabel, "unexpected error reading password last modified timestamp: " + e.getMessage());
        }

        final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get(userIdentity.getLdapProfileID());
        final String pwmLastSetAttr = ldapProfile.readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE);
        if (pwmLastSetAttr != null && pwmLastSetAttr.length() > 0) {
            try {
                final Date pwmPwdLastModified = theUser.readDateAttribute(pwmLastSetAttr);
                LOGGER.trace(sessionLabel, "read pwmPasswordChangeTime as: " + (pwmPwdLastModified == null ? "n/a" : PwmConstants.DEFAULT_DATETIME_FORMAT.format(pwmPwdLastModified)));
                return pwmPwdLastModified;
            } catch (ChaiOperationException e) {
                LOGGER.error(sessionLabel, "error parsing password last modified PWM password value for user " + theUser.getEntryDN() + "; error: " + e.getMessage());
            }
        }

        LOGGER.debug(sessionLabel, "unable to determine time of user's last password modification");
        return null;
    }


}

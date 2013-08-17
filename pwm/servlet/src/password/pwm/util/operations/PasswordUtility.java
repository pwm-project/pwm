/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.ChaiUtility;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.event.AuditRecord;
import password.pwm.servlet.ForgottenPasswordServlet;
import password.pwm.servlet.HelpdeskServlet;
import password.pwm.util.*;
import password.pwm.util.stats.Statistic;

import java.io.Serializable;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class PasswordUtility {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(PasswordUtility.class);

    public static String sendNewPassword(
            final UserInfoBean userInfoBean,
            final PwmApplication pwmApplication,
            final UserDataReader userDataReader,
            final String newPassword,
            final Locale userLocale
    )
            throws PwmOperationalException
    {
        final Configuration config = pwmApplication.getConfig();

        final PwmSetting.MessageSendMethod pref = PwmSetting.MessageSendMethod.valueOf(config.readSettingAsString(PwmSetting.CHALLENGE_SENDNEWPW_METHOD));
        final String emailAddress = userInfoBean.getUserEmailAddress();
        final String smsNumber = userInfoBean.getUserSmsNumber();
        String returnToAddress = emailAddress;

        ErrorInformation error = null;
        switch (pref) {
            case BOTH:
                // Send both email and SMS, success if one of both succeeds
                final ErrorInformation err1 = sendNewPasswordEmail(userInfoBean, pwmApplication, userDataReader, newPassword, emailAddress, userLocale);
                final ErrorInformation err2 = sendNewPasswordSms(userInfoBean, pwmApplication, userDataReader, newPassword, smsNumber, userLocale);
                if (err1 != null) {
                    error = err1;
                    returnToAddress = smsNumber;
                } else if (err2 != null) {
                    error = err2;
                }
                break;
            case EMAILFIRST:
                // Send email first, try SMS if email is not available
                error = sendNewPasswordEmail(userInfoBean, pwmApplication, userDataReader, newPassword, emailAddress, userLocale);
                if (error != null) {
                    error = sendNewPasswordSms(userInfoBean, pwmApplication, userDataReader, newPassword, smsNumber, userLocale);
                    returnToAddress = smsNumber;
                }
                break;
            case SMSFIRST:
                // Send SMS first, try email if SMS is not available
                error = sendNewPasswordSms(userInfoBean, pwmApplication, userDataReader, newPassword, smsNumber, userLocale);
                if (error != null) {
                    error = sendNewPasswordEmail(userInfoBean, pwmApplication, userDataReader, newPassword, emailAddress, userLocale);
                } else {
                    returnToAddress = smsNumber;
                }
                break;
            case SMSONLY:
                // Only try SMS
                error = sendNewPasswordSms(userInfoBean, pwmApplication, userDataReader, newPassword, smsNumber, userLocale);
                returnToAddress = smsNumber;
                break;
            case EMAILONLY:
            default:
                // Only try email
                error = sendNewPasswordEmail(userInfoBean, pwmApplication, userDataReader, newPassword, emailAddress, userLocale);
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
            final UserDataReader userDataReader,
            final String newPassword,
            final String toNumber,
            final Locale userLocale
    )
            throws PwmOperationalException
    {
        final Configuration config = pwmApplication.getConfig();
        String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
        if (senderId == null) { senderId = ""; }
        String message = config.readSettingAsLocalizedString(PwmSetting.SMS_CHALLENGE_NEW_PASSWORD_TEXT, userLocale);

        if (toNumber == null || toNumber.length() < 1) {
            final String errorMsg = String.format("unable to send new password email for '%s'; no SMS number available in ldap", userInfoBean.getUserDN());
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return errorInformation;
        }

        message = message.replace("%TOKEN%", newPassword);

        final Integer maxlen = ((Long) config.readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH)).intValue();
        pwmApplication.sendSmsUsingQueue(new SmsItemBean(toNumber, senderId, message, maxlen), userInfoBean, userDataReader);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug(String.format("password SMS added to send queue for %s", toNumber));
        return null;
    }

    private static ErrorInformation sendNewPasswordEmail(
            final UserInfoBean userInfoBean,
            final PwmApplication pwmApplication,
            final UserDataReader userDataReader,
            final String newPassword,
            final String toAddress,
            final Locale userLocale
    )
            throws PwmOperationalException
    {
        final Configuration config = pwmApplication.getConfig();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_SENDPASSWORD, userLocale);

        if (toAddress == null || toAddress.length() < 1) {
            final String errorMsg = "unable to send new password email for '" + userInfoBean.getUserDN() + "' no email address available in ldap";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return errorInformation;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(
                toAddress,
                configuredEmailSetting.getFrom(),
                configuredEmailSetting.getSubject(),
                configuredEmailSetting.getBodyPlain().replace("%TOKEN%", newPassword),
                configuredEmailSetting.getBodyHtml().replace("%TOKEN%", newPassword)
        ), userInfoBean, userDataReader);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        LOGGER.debug("new password email to " + userInfoBean.getUserDN() + " added to send queue for " + toAddress);
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
    public static void setUserPassword(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String newPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();

        if (!Permission.checkPermission(Permission.CHANGE_PASSWORD, pwmSession, pwmApplication)) {
            final String errorMsg = "attempt to setUserPassword, but user does not have password change permission";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        // double check to make sure password meets PWM rule requirements.  This should
        // have been done before setUserPassword() is invoked, so it should be redundant
        // but we do it just in case.
        try {
            final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator(pwmApplication,uiBean.getPasswordPolicy());
            pwmPasswordRuleValidator.testPassword(newPassword,null,uiBean,pwmSession.getSessionManager().getActor());
        } catch (PwmDataValidationException e) {
            final String errorMsg = "attempt to setUserPassword, but password does not pass PWM validator";
            final ErrorInformation errorInformation = new ErrorInformation(e.getErrorInformation().getError(), errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        // retrieve the user's old password from the userInfoBean in the session
        final String oldPassword = pwmSession.getUserInfoBean().getUserCurrentPassword();

        boolean setPasswordWithoutOld = false;
        if (oldPassword == null || oldPassword.length() < 1) {
            if (pwmApplication.getProxyChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY) {
                setPasswordWithoutOld = true;
            }
        }

        if (!setPasswordWithoutOld) {
            // Check to make sure we actually have an old password
            if (oldPassword == null || oldPassword.length() < 1) {
                final String errorMsg = "cannot set password for user, old password is not available";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }

        final long passwordSetTimestamp = System.currentTimeMillis();
        try {
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser theUser = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
            if (setPasswordWithoutOld) {
                theUser.setPassword(newPassword);
            } else {
                theUser.changePassword(oldPassword, newPassword);
            }
        } catch (ChaiPasswordPolicyException e) {
            final String errorMsg = "error setting password for user '" + uiBean.getUserDN() + "'' " + e.toString();
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError, errorMsg);
            throw new PwmOperationalException(error);
        } catch (ChaiOperationException e) {
            final String errorMsg = "error setting password for user '" + uiBean.getUserDN() + "'' " + e.getMessage();
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(pwmError, errorMsg);
            throw new PwmOperationalException(error);
        }

        // at this point the password has been changed, so log it.
        LOGGER.info(pwmSession, "user '" + uiBean.getUserDN() + "' successfully changed password");

        // clear out the password change bean
        pwmSession.clearChangePasswordBean();

        // update the session state bean's password modified flag
        pwmSession.getSessionStateBean().setPasswordModified(true);

        // update the uibean with the user's new password
        uiBean.setUserCurrentPassword(newPassword);

        //close any outstanding ldap connections (since they cache the old password)
        pwmSession.getSessionManager().closeConnections();

        // clear the "requires new password flag"
        uiBean.setRequiresNewPassword(false);

        // mark the auth type as authenticated now that we have the user's natural password.
        uiBean.setAuthenticationType(UserInfoBean.AuthenticationType.AUTHENTICATED);

        // update the uibean's "password expired flag".
        uiBean.setPasswordState(UserStatusHelper.readPasswordStatus(pwmSession, newPassword, pwmApplication, pwmSession.getSessionManager().getActor(), uiBean.getPasswordPolicy(),uiBean));

        // create a proxy user object for pwm to update/read the user.
        final ChaiUser proxiedUser = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), pwmApplication.getProxyChaiProvider());

        // update statistics
        {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.PASSWORD_CHANGES);
            pwmApplication.getStatisticsManager().updateEps(Statistic.EpsType.PASSWORD_CHANGES,1);
            final int passwordStrength = PasswordUtility.checkPasswordStrength(pwmApplication.getConfig(), newPassword);
            pwmApplication.getStatisticsManager().updateAverageValue(Statistic.AVG_PASSWORD_STRENGTH,passwordStrength);
        }

        // add the old password to the global history list (if the old password is known)
        if (oldPassword != null && oldPassword.length() > 0 && pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_SHAREDHISTORY_ENABLE)) {
            pwmApplication.getSharedHistoryManager().addWord(pwmSession, oldPassword);
        }

        // invoke post password change actions
        invokePostChangePasswordActions(pwmSession, newPassword);

        // call out to external methods.
        Helper.invokeExternalChangeMethods(pwmSession, pwmApplication, uiBean.getUserDN(), oldPassword, newPassword);

        {  // execute configured actions
            LOGGER.debug(pwmSession, "executing configured actions to user " + proxiedUser.getEntryDN());
            final List<ActionConfiguration> configValues = pwmApplication.getConfig().readSettingAsAction(PwmSetting.CHANGE_PASSWORD_WRITE_ATTRIBUTES);
            final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
            settings.setExpandPwmMacros(true);
            settings.setUserInfoBean(uiBean);
            settings.setUser(proxiedUser);
            final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
            actionExecutor.executeActions(configValues, settings, pwmSession);
        }

        performReplicaSyncCheck(pwmSession, pwmApplication, proxiedUser, passwordSetTimestamp);

    }

    public static void helpdeskSetUserPassword(
            final PwmSession pwmSession,
            final ChaiUser chaiUser,
            final PwmApplication pwmApplication,
            final String newPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            final String errorMsg = "attempt to helpdeskSetUserPassword, but user is not authenticated";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        if (!Permission.checkPermission(Permission.HELPDESK, pwmSession, pwmApplication)) {
            final String errorMsg = "attempt to helpdeskSetUserPassword, but user does not have helpdesk permission";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        try {
            chaiUser.setPassword(newPassword);
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
        LOGGER.info(pwmSession, "user '" + pwmSession.getUserInfoBean().getUserDN() + "' successfully changed password for " + chaiUser.getEntryDN());

        // create a proxy user object for pwm to update/read the user.
        final ChaiUser proxiedUser = ChaiFactory.createChaiUser(chaiUser.getEntryDN(), pwmApplication.getProxyChaiProvider());

        //
        String userID = "";
        try {
            userID = Helper.readLdapUserIDValue(pwmApplication, chaiUser);
        } catch (ChaiOperationException e) {
            LOGGER.error(pwmSession, "trouble reading userID for user " + chaiUser.getEntryDN());
        }

        // mark the event log
        {
            final AuditRecord auditRecord = new AuditRecord(
                    AuditEvent.HELPDESK_SET_PASSWORD,
                    pwmSession.getUserInfoBean().getUserID(),
                    pwmSession.getUserInfoBean().getUserDN(),
                    new Date(),
                    null,
                    userID,
                    chaiUser.getEntryDN(),
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmApplication.getAuditManager().submitAuditRecord(auditRecord);
        }

        // update statistics
        pwmApplication.getStatisticsManager().updateEps(Statistic.EpsType.PASSWORD_CHANGES,1);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.HELPDESK_PASSWORD_SET);

        // call out to external methods.
        Helper.invokeExternalChangeMethods(pwmSession, pwmApplication, chaiUser.getEntryDN(), null, newPassword);

        // create a uib for end user
        final UserInfoBean userInfoBean = new UserInfoBean();
        UserStatusHelper.populateUserInfoBean(
                pwmSession,
                userInfoBean,
                pwmApplication,
                pwmSession.getSessionStateBean().getLocale(),
                proxiedUser.getEntryDN(),
                newPassword,
                proxiedUser.getChaiProvider()
        );

        {  // execute configured actions
            LOGGER.debug(pwmSession, "executing changepassword and helpdesk post password change writeAttributes to user " + proxiedUser.getEntryDN());
            final List<ActionConfiguration> actions = new ArrayList<ActionConfiguration>();
            actions.addAll(pwmApplication.getConfig().readSettingAsAction(PwmSetting.CHANGE_PASSWORD_WRITE_ATTRIBUTES));
            actions.addAll(pwmApplication.getConfig().readSettingAsAction(PwmSetting.HELPDESK_POST_SET_PASSWORD_WRITE_ATTRIBUTES));
            if (!actions.isEmpty()) {
                final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
                settings.setExpandPwmMacros(true);
                settings.setUserInfoBean(userInfoBean);
                settings.setUser(proxiedUser);
                final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
                actionExecutor.executeActions(actions,settings,pwmSession);
            }
        }

        final HelpdeskServlet.SETTING_CLEAR_RESPONSES settingClearResponses = HelpdeskServlet.SETTING_CLEAR_RESPONSES.valueOf(pwmApplication.getConfig().readSettingAsString(PwmSetting.HELPDESK_CLEAR_RESPONSES));
        if (settingClearResponses == HelpdeskServlet.SETTING_CLEAR_RESPONSES.yes) {
            final String userGUID = Helper.readLdapGuidValue(pwmApplication, proxiedUser.getEntryDN());
            pwmApplication.getCrService().clearResponses(pwmSession, proxiedUser, userGUID);

            // mark the event log
            final AuditRecord auditRecord = new AuditRecord(
                    AuditEvent.HELPDESK_CLEAR_RESPONSES,
                    pwmSession.getUserInfoBean().getUserID(),
                    pwmSession.getUserInfoBean().getUserDN(),
                    new Date(),
                    null,
                    userID,
                    chaiUser.getEntryDN(),
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            pwmApplication.getAuditManager().submitAuditRecord(auditRecord);
        }

        // send email notification
        sendChangePasswordHelpdeskEmailNotice(pwmSession, pwmApplication, userInfoBean, proxiedUser);
    }

    private static void performReplicaSyncCheck(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theUser,
            final long passwordSetTimestamp
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        //update the current last password update field in ldap
        final boolean successfullyWrotePwdUpdateAttr = UserStatusHelper.updateLastUpdateAttribute(pwmSession, pwmApplication, theUser);
        boolean doReplicaCheck = true;

        if (!successfullyWrotePwdUpdateAttr) {
            LOGGER.trace(pwmSession, "unable to perform password replication checking, unable to write last update attribute");
            doReplicaCheck = false;
        }

        if (pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS).size() <= 1) {
            LOGGER.trace(pwmSession, "skipping replication checking, only one ldap server url is configured");
            doReplicaCheck = false;
        }

        final long maxWaitTime = pwmApplication.getConfig().readSettingAsLong(PwmSetting.PASSWORD_SYNC_MAX_WAIT_TIME) * 1000;

        if (doReplicaCheck) {
            LOGGER.trace(pwmSession, "beginning password replication checking");
            // if the last password update worked, test that it is replicated across all ldap servers.
            boolean isReplicated = false;
            Helper.pause(PwmConstants.PASSWORD_UPDATE_INITIAL_DELAY_MS);
            try {
                long timeSpentTrying = 0;
                while (!isReplicated && timeSpentTrying < (maxWaitTime)) {
                    timeSpentTrying = System.currentTimeMillis() - passwordSetTimestamp;
                    isReplicated = ChaiUtility.testAttributeReplication(theUser, pwmApplication.getConfig().readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE), null);
                    Helper.pause(PwmConstants.PASSWORD_UPDATE_CYCLE_DELAY_MS);
                }
            } catch (ChaiOperationException e) {
                //oh well, give up.
                LOGGER.trace(pwmSession, "error during password sync check: " + e.getMessage());
            }
        }

        final long totalTime = System.currentTimeMillis() - passwordSetTimestamp;
        pwmApplication.getStatisticsManager().updateAverageValue(Statistic.AVG_PASSWORD_SYNC_TIME, totalTime);

        // be sure minimum wait time has passed
        final long minWaitTime = pwmApplication.getConfig().readSettingAsLong(PwmSetting.PASSWORD_SYNC_MIN_WAIT_TIME) * 1000L;
        if ((System.currentTimeMillis() - passwordSetTimestamp) < minWaitTime) {
            LOGGER.trace(pwmSession, "waiting for minimum replication time of " + minWaitTime + "ms....");
            while ((System.currentTimeMillis() - passwordSetTimestamp) < minWaitTime) {
                Helper.pause(500);
            }
        }
    }


    private static void invokePostChangePasswordActions(final PwmSession pwmSession, final String newPassword)
            throws PwmUnrecoverableException
    {
        final List<PostChangePasswordAction> postChangePasswordActions = pwmSession.getUserInfoBean().removePostChangePasswordActions();
        if (postChangePasswordActions == null || postChangePasswordActions.isEmpty()) {
            LOGGER.trace("no post change password actions ");
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

    public static int checkPasswordStrength(
            final Configuration config,
            final String password
    )
    {
        final List<Integer> judgeResults = Helper.invokeExternalJudgeMethods(config, password);

        // strip invalid values
        for (final Iterator<Integer> iter = judgeResults.iterator(); iter.hasNext();) {
            final Integer loopInt = iter.next();
            if (loopInt > 100 || loopInt < 0) {
                iter.remove();
            }
        }

        if (judgeResults.isEmpty()) {
            return 0;
        }

        int returnResult = 100;
        for (final int loopInt : judgeResults) {
            if (loopInt < returnResult) {
                returnResult = loopInt;
            }
        }

        return returnResult;
    }


    public static PwmPasswordPolicy readPasswordPolicyForUser(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final Locale locale
    ) throws ChaiUnavailableException
    {
        final long startTime = System.currentTimeMillis();
        final PasswordPolicySource ppSource = PasswordPolicySource.valueOf(pwmApplication.getConfig().readSettingAsString(PwmSetting.PASSWORD_POLICY_SOURCE));

        final PwmPasswordPolicy returnPolicy;
        switch (ppSource) {
            case MERGE:
                final PwmPasswordPolicy pwmPolicy = pwmApplication.getConfig().getGlobalPasswordPolicy(locale);
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
                returnPolicy = pwmApplication.getConfig().getGlobalPasswordPolicy(locale);
                break;

            default:
                throw new IllegalStateException("unknown policy source defined: " + ppSource.name());
        }

        LOGGER.trace(pwmSession, "readPasswordPolicyForUser completed in " + TimeDuration.fromCurrent(startTime).asCompactString());
        return returnPolicy;
    }


    public static PwmPasswordPolicy readLdapPasswordPolicy(
            final PwmApplication pwmApplication,
            final ChaiUser theUser)
            throws ChaiUnavailableException {
        try {
            final Map<String, String> ruleMap = new HashMap<String, String>();
            final ChaiPasswordPolicy chaiPolicy = theUser.getPasswordPolicy();
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

                return new PwmPasswordPolicy(ruleMap, chaiPolicy);
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
            final String password,
            final String confirmPassword,
            final SessionManager sessionManager
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

        if (password.length() < 0) {
            userMessage = new ErrorInformation(PwmError.PASSWORD_MISSING).toUserStr(locale, pwmApplication.getConfig());
        } else {
            final Boolean NEGATIVE_CACHE_HIT = Boolean.FALSE;
            final String cacheKey = "passwordCheck_" + (user == null ? "" : user.getEntryDN()) + "_" + password;
            try {
                if (sessionManager != null) {
                    final Object cachedValue = sessionManager.getTypingCacheValue(cacheKey);
                    if (cachedValue != null) {
                        if (NEGATIVE_CACHE_HIT.equals(cachedValue)) {
                            pass = true;
                        } else {
                            throw new PwmDataValidationException((ErrorInformation)cachedValue);
                        }
                    }
                }
                if (!pass) {
                    final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator(pwmApplication, userInfoBean.getPasswordPolicy());
                    final String oldPassword = userInfoBean.getUserCurrentPassword();
                    pwmPasswordRuleValidator.testPassword(password, oldPassword, userInfoBean, user);
                    pass = true;
                    if (sessionManager != null) {
                        sessionManager.putLruTypingCacheValue(cacheKey,NEGATIVE_CACHE_HIT);
                    }
                }
            } catch (PwmDataValidationException e) {
                errorCode = e.getError().getErrorCode();
                userMessage = e.getErrorInformation().toUserStr(locale, pwmApplication.getConfig());
                pass = false;
                if (sessionManager != null) {
                    sessionManager.putLruTypingCacheValue(cacheKey,e.getErrorInformation());
                }
            }
        }

        final PasswordCheckInfo.MATCH_STATUS matchStatus = figureMatchStatus(passwordIsCaseSensitive ,password, confirmPassword);
        if (pass == true) {
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

        final int strength = checkPasswordStrength(pwmApplication.getConfig(), password);
        return new PasswordCheckInfo(userMessage, pass, strength, matchStatus, errorCode);
    }


    private static PasswordCheckInfo.MATCH_STATUS figureMatchStatus(final boolean caseSensitive, final String password1, final String password2) {
        final PasswordCheckInfo.MATCH_STATUS matchStatus;
        if (password2 == null || password2.length() < 1) {
            matchStatus = PasswordCheckInfo.MATCH_STATUS.EMPTY;
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
            final UserInfoBean userInfoBean,
            final ChaiUser user
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_CHANGEPASSWORD_HELPDESK, locale);

        final String toAddress = userInfoBean.getUserEmailAddress();
        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send change password email for '" + pwmSession.getUserInfoBean().getUserDN() + "' no ' user email address available");
            return;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(
                toAddress,
                configuredEmailSetting.getFrom(),
                configuredEmailSetting.getSubject(),
                configuredEmailSetting.getBodyPlain(),
                configuredEmailSetting.getBodyHtml()
        ), userInfoBean, new UserDataReader(user));
    }
}

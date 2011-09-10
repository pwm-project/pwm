/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.ChaiUtility;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PostChangePasswordAction;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * @author Jason D. Rivard
 */
public class PasswordUtility {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(PasswordUtility.class);

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
     * @return true if the set was successful
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *          if the ldap directory is not unavailable
     * @throws password.pwm.error.PwmUnrecoverableException
     *          if user is not authenticated
     */
    public static boolean setUserPassword(
            final PwmSession pwmSession,
            final String newPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        if (!Permission.checkPermission(Permission.CHANGE_PASSWORD, pwmSession)) {
            ssBean.setSessionError(PwmError.ERROR_UNAUTHORIZED.toInfo());
            LOGGER.debug(pwmSession, "attempt to setUserPassword, but user does not have password change permission");
            return false;
        }

        // double check to make sure password meets PWM rule requirements.  This should
        // have been done before setUserPassword() is invoked, so it should be redundant
        // but we do it just in case.
        try {
            Validator.testPasswordAgainstPolicy(newPassword, pwmSession, false);
        } catch (PwmDataValidationException e) {
            ssBean.setSessionError(new ErrorInformation(e.getErrorInformation().getError()));
            LOGGER.debug(pwmSession, "attempt to setUserPassword, but password does not pass PWM validator");
            return false;
        }

        // retrieve the user's old password from the userInfoBean in the session
        final String oldPassword = pwmSession.getUserInfoBean().getUserCurrentPassword();

        // Check to make sure we actually have an old password
        if (oldPassword == null || oldPassword.length() < 1) {
            ssBean.setSessionError(PwmError.ERROR_WRONGPASSWORD.toInfo());
            LOGGER.warn(pwmSession, pwmSession.getUserInfoBean().getUserDN() + "can't set password for user, old password is null");
            return false;
        }

        try {
            doPasswordSetOperation(pwmSession, newPassword, oldPassword);
        } catch (ChaiPasswordPolicyException e) {
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError);
            ssBean.setSessionError(error);
            LOGGER.warn(pwmSession, "error setting password for user '" + uiBean.getUserDN() + "'' " + error.toDebugStr());
            return false;
        } catch (ChaiOperationException e) {
            final PwmError returnMsg = PwmError.forChaiError(e.getErrorCode()) == null ? PwmError.ERROR_UNKNOWN : PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation error = new ErrorInformation(returnMsg, e.getMessage());
            ssBean.setSessionError(error);
            LOGGER.warn(pwmSession, "error setting password for user '" + uiBean.getUserDN() + "'' " + error.toDebugStr() + ", " + e.getMessage());
            return false;
        }

        // at this point the password has been changed, so log it.
        LOGGER.info(pwmSession, "user '" + uiBean.getUserDN() + "' successfully changed password");

        // clear out the password change bean
        pwmSession.clearChangePasswordBean();

        // update the uibean with the user's new password
        uiBean.setUserCurrentPassword(newPassword);

        //close any outstanding ldap connections (since they cache the old password)
        pwmSession.getSessionManager().closeConnections();

        // clear the "requires new password flag"
        uiBean.setRequiresNewPassword(false);

        // update the uibean's "password expired flag".
        uiBean.setPasswordState(UserStatusHelper.readPasswordStatus(pwmSession, newPassword, pwmSession.getConfig(), pwmSession.getSessionManager().getActor(), uiBean.getPasswordPolicy()));

        //update the current last password update field in ldap
        final ChaiUser proxiedUser = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), pwmSession.getPwmApplication().getProxyChaiProvider());
        final long delayStartTime = System.currentTimeMillis();
        final boolean successfullyWrotePwdUpdateAttr = Helper.updateLastUpdateAttribute(pwmSession, proxiedUser);

        if (pwmSession.getConfig().readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS).size() <= 1) {
            LOGGER.trace(pwmSession, "skipping replication checking, only one ldap server url is configured");
        } else {
            final long maxWaitTime = pwmSession.getConfig().readSettingAsLong(PwmSetting.PASSWORD_SYNC_MAX_WAIT_TIME) * 1000;

            if (successfullyWrotePwdUpdateAttr && maxWaitTime > 0) {
                LOGGER.trace(pwmSession, "beginning password replication checking");
                // if the last password update worked, test that it is replicated across all ldap servers.
                boolean isReplicated = false;
                Helper.pause(PwmConstants.PASSWORD_UPDATE_INITIAL_DELAY);
                try {
                    long timeSpentTrying = 0;
                    while (!isReplicated && timeSpentTrying < (maxWaitTime)) {
                        timeSpentTrying = System.currentTimeMillis() - delayStartTime;
                        isReplicated = ChaiUtility.testAttributeReplication(proxiedUser, pwmSession.getConfig().readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE), null);
                        Helper.pause(PwmConstants.PASSWORD_UPDATE_CYCLE_DELAY);
                    }
                } catch (ChaiOperationException e) {
                    //oh well, give up.
                    LOGGER.trace(pwmSession, "error during password sync check: " + e.getMessage());
                }
                final long totalTime = System.currentTimeMillis() - delayStartTime;
                pwmSession.getPwmApplication().getStatisticsManager().updateAverageValue(Statistic.AVG_PASSWORD_SYNC_TIME, totalTime);
            }
        }

        // be sure minimum wait time has passed
        final long minWaitTime = pwmSession.getConfig().readSettingAsLong(PwmSetting.PASSWORD_SYNC_MIN_WAIT_TIME) * 1000L;
        if ((System.currentTimeMillis() - delayStartTime) < minWaitTime) {
            LOGGER.trace(pwmSession, "waiting for minimum replication time of " + minWaitTime + "ms....");
            while ((System.currentTimeMillis() - delayStartTime) < minWaitTime) {
                Helper.pause(500);
            }
        }

        // send user an email confirmation
        sendChangePasswordEmailNotice(pwmSession);

        // update the status bean
        pwmSession.getPwmApplication().getStatisticsManager().incrementValue(Statistic.PASSWORD_CHANGES);

        // add the old password to the global history list (if the old password is known)
        if (!pwmSession.getUserInfoBean().isAuthFromUnknownPw() && pwmSession.getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_SHAREDHISTORY_ENABLE)) {
            pwmSession.getPwmApplication().getSharedHistoryManager().addWord(pwmSession, oldPassword);
        }

        // invoke post password change actions
        invokePostChangePasswordActions(pwmSession, newPassword);

        // call out to external methods.
        Helper.invokeExternalChangeMethods(pwmSession, oldPassword, newPassword);

        return true;
    }

    private static void doPasswordSetOperation(final PwmSession pwmSession, final String newPassword, final String oldPassword)
            throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException
    {
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
        final ChaiUser theUser = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
        theUser.changePassword(oldPassword, newPassword);
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
            Helper.pause(PwmConstants.PASSWORD_UPDATE_CYCLE_DELAY);
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
            final PwmSession pwmSession,
            final String password
    )  {
        final List<Integer> judgeResults = Helper.invokeExternalJudgeMethods(config, pwmSession, password);

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


    public static void sendChangePasswordEmailNotice(final PwmSession pwmSession) throws PwmUnrecoverableException {
        final Configuration config = pwmSession.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();

        final String fromAddress = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHANGEPASSWORD_FROM, locale);
        final String subject = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHANGEPASSWORD_SUBJECT, locale);
        final String plainBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHANGEPASSWORD_BODY, locale);
        final String htmlBody = config.readSettingAsLocalizedString(PwmSetting.EMAIL_CHANGEPASSWORD_BODY_HMTL, locale);

        final String toAddress = pwmSession.getUserInfoBean().getUserEmailAddress();
        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send change password email for '" + pwmSession.getUserInfoBean().getUserDN() + "' no ' user email address available");
            return;
        }

        pwmSession.getPwmApplication().sendEmailUsingQueue(new EmailItemBean(toAddress, fromAddress, subject, plainBody, htmlBody));
    }


}

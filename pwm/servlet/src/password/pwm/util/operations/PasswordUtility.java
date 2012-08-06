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
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.servlet.HelpdeskServlet;
import password.pwm.util.Helper;
import password.pwm.util.PostChangePasswordAction;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class PasswordUtility {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(PasswordUtility.class);

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
     * @return true if the set was successful
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
            Validator.testPasswordAgainstPolicy(newPassword, pwmSession, pwmApplication);
        } catch (PwmDataValidationException e) {
            final String errorMsg = "attempt to setUserPassword, but password does not pass PWM validator";
            final ErrorInformation errorInformation = new ErrorInformation(e.getErrorInformation().getError(), errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        // retrieve the user's old password from the userInfoBean in the session
        final String oldPassword = pwmSession.getUserInfoBean().getUserCurrentPassword();

        // Check to make sure we actually have an old password
        if (oldPassword == null || oldPassword.length() < 1) {
            final String errorMsg = pwmSession.getUserInfoBean().getUserDN() + "can't set password for user, old password is null";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        final long passwordSetTimestamp = System.currentTimeMillis();
        try {
            doPasswordSetOperation(pwmSession, newPassword, oldPassword);
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

        // update the uibean with the user's new password
        uiBean.setUserCurrentPassword(newPassword);

        //close any outstanding ldap connections (since they cache the old password)
        pwmSession.getSessionManager().closeConnections();

        // clear the "requires new password flag"
        uiBean.setRequiresNewPassword(false);

        // update the uibean's "password expired flag".
        uiBean.setPasswordState(UserStatusHelper.readPasswordStatus(pwmSession, newPassword, pwmApplication, pwmSession.getSessionManager().getActor(), uiBean.getPasswordPolicy()));

        // create a proxy user object for pwm to update/read the user.
        final ChaiUser proxiedUser = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), pwmApplication.getProxyChaiProvider());

        // update statistics
        {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.PASSWORD_CHANGES);
            pwmApplication.getStatisticsManager().updateEps(StatisticsManager.EpsType.PASSWORD_CHANGES_60,1);
            pwmApplication.getStatisticsManager().updateEps(StatisticsManager.EpsType.PASSWORD_CHANGES_240,1);
            pwmApplication.getStatisticsManager().updateEps(StatisticsManager.EpsType.PASSWORD_CHANGES_1440,1);
            final int passwordStrength = PasswordUtility.checkPasswordStrength(pwmApplication.getConfig(), pwmSession, newPassword);
            pwmApplication.getStatisticsManager().updateAverageValue(Statistic.AVG_PASSWORD_STRENGTH,passwordStrength);
        }

        // add the old password to the global history list (if the old password is known)
        if (!pwmSession.getUserInfoBean().isAuthFromUnknownPw() && pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_SHAREDHISTORY_ENABLE)) {
            pwmApplication.getSharedHistoryManager().addWord(pwmSession, oldPassword);
        }

        // invoke post password change actions
        invokePostChangePasswordActions(pwmSession, newPassword);

        // call out to external methods.
        Helper.invokeExternalChangeMethods(pwmSession, pwmApplication, uiBean.getUserDN(), oldPassword, newPassword);

        // call out to external REST methods.
        Helper.invokeExternalRestChangeMethods(pwmSession, pwmApplication, uiBean.getUserDN(), oldPassword, newPassword);

        {  // write out configured attributes.
            LOGGER.debug(pwmSession, "writing changePassword.writeAttributes to user " + proxiedUser.getEntryDN());
            final List<String> configValues = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.CHANGE_PASSWORD_WRITE_ATTRIBUTES);
            final Map<String, String> configNameValuePairs = Configuration.convertStringListToNameValuePair(configValues, "=");
            Helper.writeMapToLdap(pwmApplication, pwmSession, proxiedUser, configNameValuePairs, true);
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

        final long passwordSetTimestamp = System.currentTimeMillis();
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

        // mark the event log
        {
            final String message = "(" + pwmSession.getUserInfoBean().getUserID() + ")";
            UserHistory.updateUserHistory(pwmSession, pwmApplication, proxiedUser, UserHistory.Record.Event.HELPDESK_SET_PASSWORD, message);
        }

        // update statistics
        pwmApplication.getStatisticsManager().updateEps(StatisticsManager.EpsType.PASSWORD_CHANGES_60,1);
        pwmApplication.getStatisticsManager().updateEps(StatisticsManager.EpsType.PASSWORD_CHANGES_240,1);
        pwmApplication.getStatisticsManager().updateEps(StatisticsManager.EpsType.PASSWORD_CHANGES_1440,1);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.HELPDESK_PASSWORD_SET);

        // call out to external methods.
        Helper.invokeExternalChangeMethods(pwmSession, pwmApplication, chaiUser.getEntryDN(), null, newPassword);

        // call out to external REST methods.
        Helper.invokeExternalRestChangeMethods(pwmSession, pwmApplication, chaiUser.getEntryDN(), null, newPassword);

        {  // write out configured attributes.
            LOGGER.debug(pwmSession, "writing changePassword.writeAttributes to user " + proxiedUser.getEntryDN());
            final List<String> configValues = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.CHANGE_PASSWORD_WRITE_ATTRIBUTES);
            final Map<String, String> configNameValuePairs = Configuration.convertStringListToNameValuePair(configValues, "=");
            Helper.writeMapToLdap(pwmApplication, pwmSession, proxiedUser, configNameValuePairs, true);
        }

        final HelpdeskServlet.SETTING_CLEAR_RESPONSES settingClearResponses = HelpdeskServlet.SETTING_CLEAR_RESPONSES.valueOf(pwmApplication.getConfig().readSettingAsString(PwmSetting.HELPDESK_CLEAR_RESPONSES));
        if (settingClearResponses == HelpdeskServlet.SETTING_CLEAR_RESPONSES.yes) {
            final String userGUID = Helper.readLdapGuidValue(pwmApplication, proxiedUser.getEntryDN());
            CrUtility.clearResponses(pwmSession, pwmApplication, proxiedUser, userGUID);

            // mark the event log
            final String message = "(" + pwmSession.getUserInfoBean().getUserID() + ")";
            UserHistory.updateUserHistory(pwmSession, pwmApplication, proxiedUser, UserHistory.Record.Event.HELPDESK_CLEAR_RESPONSES, message);
        }

        performReplicaSyncCheck(pwmSession, pwmApplication, proxiedUser, passwordSetTimestamp);
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
        final boolean successfullyWrotePwdUpdateAttr = Helper.updateLastUpdateAttribute(pwmSession, pwmApplication, theUser);
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
}

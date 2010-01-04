/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.impl.edir.entry.EdirEntries;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.*;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class UserStatusHelper {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserStatusHelper.class);


    private UserStatusHelper() {
    }


    /**
     * Read password status values from directory
     *
     * @param pwmSession users pwm Session
     * @param theUser the user to check
     * @param passwordPolicy the password policy to use for checking if current password violates current policy
     * @return bean describing the status of the user's password
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException of directory is unavailable
     */
    public static PasswordStatus readPasswordStatus(
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final PwmPasswordPolicy passwordPolicy
    )
            throws ChaiUnavailableException
    {
        final PasswordStatus returnState = new PasswordStatus();
        final Configuration config = pwmSession.getConfig();
        final String userDN = theUser.getEntryDN();

        final long startTime = System.currentTimeMillis();
        LOGGER.trace(pwmSession, "beginning password status check process for " + userDN);

        // check if password meets existing policy.
        if (passwordPolicy.getRuleHelper().readBooleanValue(PwmPasswordRule.EnforceAtLogin)) {
            final String password = pwmSession.getUserInfoBean().getUserCurrentPassword();
            if (password != null && password.length() > 0) {
                try {
                    Validator.testPasswordAgainstPolicy(password, pwmSession, false, passwordPolicy);
                } catch (ValidationException e) {
                    LOGGER.info(pwmSession, "user " + userDN + " password does not conform to current password policy (" + e.getMessage() + "), marking as requiring change.");
                    returnState.setViolatesPolicy(true);
                } catch (PwmException e) {
                    LOGGER.info(pwmSession, "user " + userDN + " password does not conform to current password policy (" + e.getMessage() + "), marking as requiring change.");
                    returnState.setViolatesPolicy(true);
                }
            }
        }

        try {
            final boolean ldapPasswordExpired = theUser.isPasswordExpired();

            if (ldapPasswordExpired) {
                returnState.setExpired(true);
                LOGGER.trace(pwmSession,"password for " + userDN + " appears to be expired");
            } else {
                LOGGER.trace(pwmSession,"password for " + userDN + " does not appear to be expired");
            }
        } catch (ChaiOperationException e) {
            LOGGER.info(pwmSession, "error reading user attrs for " + userDN + " while reading isPasswordExpired(): " + e.getMessage());
        }

        try {
            final Date ldapPasswordExpirationTime = theUser.readPasswordExpirationDate();

            if (ldapPasswordExpirationTime != null) {
                final long diff = ldapPasswordExpirationTime.getTime() - System.currentTimeMillis();

                // now check to see if the user's expire time is within the 'preExpireTime' setting.
                final int preExpireMs = config.readSettingAsInt(PwmSetting.PASSWORD_EXPIRE_PRE_TIME) * 1000;
                if (diff < preExpireMs) {
                    LOGGER.info(pwmSession, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as expired");
                    returnState.setPreExpired(true);
                }

                // now check to see if the user's expire time is within the 'preWarnTime' setting.
                final int preWarnMs = config.readSettingAsInt(PwmSetting.PASSWORD_EXPIRE_WARN_TIME) * 1000;
                if (diff < preWarnMs) {
                    LOGGER.info(pwmSession, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as warn");
                    returnState.setWarnPeriod(true);
                }
            }

        } catch (ChaiOperationException e) {
            LOGGER.info(pwmSession, "error reading user attrs for " + userDN + " while reading passwordExpirationDate(): " + e.getMessage());
        }

        LOGGER.debug(pwmSession,"completed user password status check for " + userDN + " " + returnState + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
        return returnState;
    }

    public static boolean checkIfResponseConfigNeeded(final PwmSession pwmSession, final ChaiUser theUser, final ChallengeSet challengeSet)
            throws ChaiUnavailableException, PwmException
    {
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            throw PwmException.createPwmException(new ErrorInformation(Message.ERROR_AUTHENTICATION_REQUIRED,"user must be authenticated to check if response configuration is needed"));
        }

        LOGGER.trace(pwmSession,"beginning check to determine if responses need to be configured for user");

        final String userDN = theUser.getEntryDN();

        if (!Helper.testUserMatchQueryString(pwmSession, userDN, pwmSession.getConfig().readSettingAsString(PwmSetting.QUERY_MATCH_CHECK_RESPONSES))) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " is not eligible for checkIfResponseConfigNeeded due to query match");
            return false;
        }

        // check to be sure there are actually challenges in the challenge set
        if (challengeSet.getChallenges().isEmpty()) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: no challenge sets configured for user " + userDN);
            return false;
        }

        // read the user's response
        final ResponseSet usersResponses = PasswordUtility.readUserResponseSet(pwmSession, theUser);

        try {
            // check if responses exist
            if (usersResponses == null) {
                throw new Exception("no responses configured");
            }

            // check if responses meet the challenge set policy for the user
            usersResponses.meetsChallengeSetRequirements(challengeSet);

            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " has good responses");
            return false;
        } catch (Exception e) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " does not have good responses: " + e.getMessage());
            return true;
        }
    }


    public static void populateActorUserInfoBean(
            final PwmSession pwmSession,
            final String userDN,
            final String userCurrentPassword
    )
            throws ChaiUnavailableException, PwmException
    {
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        populateUserInfoBean(uiBean, pwmSession,userDN, userCurrentPassword, provider);
    }


    public static void populateUserInfoBean(
            final UserInfoBean uiBean,
            final PwmSession pwmSession,
            final String userDN,
            final String userCurrentPassword,
            final ChaiProvider provider
    )
            throws ChaiUnavailableException, PwmException
    {
        final long methodStartTime = System.currentTimeMillis();

        if (userDN != null && userDN.length() < 1) {
            throw new NullPointerException("userDN can not be null");
        }

        if (userCurrentPassword != null && userCurrentPassword.length() < 1) {
            throw new NullPointerException("userCurrentPassword can not be null");
        }

        uiBean.setUserDN(userDN);
        uiBean.setUserCurrentPassword(userCurrentPassword);

        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, provider);

        //populate password policy
        uiBean.setPasswordPolicy(PwmPasswordPolicy.createPwmPasswordPolicy(pwmSession, theUser));

        //populate c/r challenge set.
        uiBean.setChallengeSet(PasswordUtility.readUserChallengeSet(pwmSession, theUser, uiBean.getPasswordPolicy(),pwmSession.getSessionStateBean().getLocale()));

        //populate all user attributes.
        try {
            final Set<String> interestingUserAttributes = pwmSession.getConfig().getAllUsedLdapAttributes();
            interestingUserAttributes.addAll(uiBean.getPasswordPolicy().getRuleHelper().getDisallowedAttributes());
            interestingUserAttributes.add(ChaiConstant.ATTR_LDAP_PASSWORD_EXPIRE_TIME);
            interestingUserAttributes.add(pwmSession.getContextManager().getParameter(Constants.CONTEXT_PARAM.LDAP_NAMING_ATTRIBUTE));
            if (uiBean.getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.ADComplexity)) {
                interestingUserAttributes.add("cn");
                interestingUserAttributes.add("displayName");
                interestingUserAttributes.add("fullname");
            }
            final Properties allUserAttrs = theUser.readStringAttributes(null);
            uiBean.setAllUserAttributes(allUserAttrs);
            uiBean.setUserID(allUserAttrs.getProperty(pwmSession.getContextManager().getParameter(Constants.CONTEXT_PARAM.LDAP_NAMING_ATTRIBUTE)));
        } catch (ChaiOperationException e) {
            LOGGER.warn("error retrieving user attributes " + e);
        }

        // write password state
        uiBean.setPasswordState(readPasswordStatus(pwmSession, theUser, uiBean.getPasswordPolicy()));

        final String userPasswordExpireTime = uiBean.getAllUserAttributes().getProperty(ChaiConstant.ATTR_LDAP_PASSWORD_EXPIRE_TIME, "");
        if (userPasswordExpireTime.length() > 0)  {
            uiBean.setPasswordExpirationTime(EdirEntries.convertZuluToDate(userPasswordExpireTime));
        } else {
            uiBean.setPasswordExpirationTime(null);
        }

        // write response state
        uiBean.setRequiresResponseConfig(checkIfResponseConfigNeeded(pwmSession, theUser, pwmSession.getUserInfoBean().getChallengeSet()));

        // fetch last password modification time;
        final String pwdLastModifiedStr = uiBean.getAllUserAttributes().getProperty(pwmSession.getConfig().readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE),"");
        if (pwdLastModifiedStr.length() > 0) {
            try {
                uiBean.setPasswordLastModifiedTime(EdirEntries.convertZuluToDate(pwdLastModifiedStr));
            } catch (Exception e) {
                LOGGER.error(pwmSession,"error parsing password last modified value: " + e.getMessage());
            }
        }

        LOGGER.trace(pwmSession, "populateUserInfoBean for " + userDN + " completed in " + TimeDuration.fromCurrent(methodStartTime).asCompactString());
    }

    /**
     * For a given username, find an appropriate objectDN.  Uses parameters in the PWM
     * configuration to specify how the search should be performed.
     * <p/>
     * If exactly one match is discovered, then that value is returned.  Otherwise if
     * no matches or if multiple matches are discovered then null is returned.  Multiple
     * matches are considered an error condition.
     * <p/>
     * If the username appears to already be a valid DN, then the context search is not performed
     * and instead the username value is returned.
     *
     * @param username          username to search for
     * @param pwmSession        for grabbing required beans
     * @param context           specify context to use to search, or null to use pwm configured attribute
     * @return the discovered objectDN of the user, or null if none found.
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException of directory is unavailable
     */
    public static String convertUsernameFieldtoDN(
            final String username,
            final PwmSession pwmSession,
            final String context
    )
            throws ChaiUnavailableException
    {
        if (username == null || username.length() < 1) {
            return "";
        }

        String baseDN = pwmSession.getConfig().readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT);

        // see if the baseDN should be the context parameter
        if (context != null && context.length() > 0) {
            if (pwmSession.getConfig().getLoginContexts().containsKey(context)) {
                if (context.endsWith(baseDN)) {
                    baseDN = context;
                } else {
                    LOGGER.debug(pwmSession, "attempt to use '" + context + "' context for search, but does not end with configured contextless root: " + baseDN);
                }
            }
        }

        if (baseDN == null || baseDN.length() < 1) {
            return username;
        }

        final String usernameAttribute = pwmSession.getContextManager().getParameter(Constants.CONTEXT_PARAM.LDAP_NAMING_ATTRIBUTE);

        //if supplied user name starts with username attr assume its the full dn and skip the contextless login
        if (username.toLowerCase().startsWith(usernameAttribute.toLowerCase() + "=")) {
            LOGGER.trace(pwmSession, "username appears to be a DN; skipping username search");
            return username;
        }

        LOGGER.trace(pwmSession, "attempting username search for '" + username + "'" + ((context != null && context.length() > 0) ? " in context " + context : ""));

        final String filterSetting = pwmSession.getConfig().readSettingAsString(PwmSetting.USERNAME_SEARCH_FILTER);
        final String filter = filterSetting.replace(Constants.VALUE_REPLACEMENT_USERNAME,username);

        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setFilter(filter);
        searchHelper.setAttributes("");
        searchHelper.setSearchScope(ChaiProvider.SEARCH_SCOPE.SUBTREE);

        LOGGER.trace(pwmSession, "search for username: " + searchHelper.getFilter() + ", baseDN: " + baseDN);

        try {
            final SessionManager sessionMgr = pwmSession.getSessionManager();
            assert sessionMgr != null;

            final ChaiProvider provider = pwmSession.getContextManager().getProxyChaiProvider();
            assert provider != null;

            final Map<String, Properties> results = provider.search(baseDN, searchHelper);

            if (results == null || results.size() == 0) {
                LOGGER.trace(pwmSession, "no matches found");
                return null;
            } else if (results.size() > 1) {
                LOGGER.trace(pwmSession, "multiple matches found");
                LOGGER.warn(pwmSession, "multiple matches found when doing search for username: " + username);
            } else {
                final String userDN = results.keySet().iterator().next();
                LOGGER.trace(pwmSession, "username match found: " + userDN);
                return userDN;
            }
        } catch (ChaiOperationException e) {
            LOGGER.warn(pwmSession, "error during username search: " + e.getMessage());
        }
        return null;
    }
}

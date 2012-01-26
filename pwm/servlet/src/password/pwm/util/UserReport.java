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

package password.pwm.util;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.util.operations.CrUtility;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.UserStatusHelper;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PasswordStatus;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;

import java.util.*;

public class UserReport {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserReport.class);

    private final PwmApplication pwmApplication;

    public UserReport(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    public Iterator<UserInformation> resultIterator()
            throws ChaiUnavailableException, ChaiOperationException
    {
        final Set<String> userDNs = generateListOfUsers();
        return new ResultIterator(userDNs);
    }

    private Set<String> generateListOfUsers()
            throws ChaiUnavailableException, ChaiOperationException
    {
        final String baseDN = pwmApplication.getConfig().readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT);
        final String usernameSearchFilter = "(objectClass=inetOrgPerson)";

        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setAttributes(Collections.<String>emptyList());
        searchHelper.setFilter(usernameSearchFilter);
        searchHelper.setSearchScope(ChaiProvider.SEARCH_SCOPE.SUBTREE);

        final Map<String,Map<String,String>> searchResults = pwmApplication.getProxyChaiProvider().search(baseDN, searchHelper);
        return searchResults.keySet();
    }

    private UserInformation readUserInformation(final String userDN)
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final UserInformation userInformation = new UserInformation();
        userInformation.setUserDN(userDN);

        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, pwmApplication.getProxyChaiProvider());
        final UserInfoBean uiBean = new UserInfoBean();

        UserStatusHelper.populateUserInfoBean(null, uiBean, pwmApplication, PwmConstants.DEFAULT_LOCALE ,userDN, null, pwmApplication.getProxyChaiProvider());
        userInformation.setUserInfoBean(uiBean);

        userInformation.setGuid(uiBean.getUserGuid());
        userInformation.setHasValidResponses(!uiBean.isRequiresResponseConfig());
        userInformation.setPasswordChangeTime(uiBean.getPasswordLastModifiedTime());
        userInformation.setPasswordExpirationTime(uiBean.getPasswordExpirationTime());
        userInformation.setUserDN(uiBean.getUserDN());
        userInformation.setPasswordStatus(uiBean.getPasswordState());

        try {
            final ResponseSet responseSet = CrUtility.readUserResponseSet(null, pwmApplication, theUser);
            userInformation.setResponseSetTime(responseSet == null ? null : responseSet.getTimestamp());
        } catch (ChaiOperationException e) {
            LOGGER.debug("error reading response set for " + userDN + " : " + e.getMessage());
        }

        return userInformation;
    }

    private class ResultIterator implements Iterator<UserInformation> {
        private final Iterator<String> userDNs;

        private ResultIterator(final Set<String> userDNs) {
            this.userDNs = userDNs.iterator();
        }

        public boolean hasNext() {
            return userDNs.hasNext();
        }

        public UserInformation next() {
            final String userDN = userDNs.next();
            try {
                return readUserInformation(userDN);
            } catch (ChaiUnavailableException e) {
                throw new IllegalStateException("the ldap directory is unavailable: " + e.getMessage());
            } catch (PwmUnrecoverableException e) {
                throw new IllegalStateException("the pwm application is unavailable: " + e.getMessage());
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static class UserInformation {
        private String userDN;
        private String guid;

        private PasswordStatus passwordStatus;

        private Date passwordChangeTime;
        private Date passwordExpirationTime;
        private Date responseSetTime;

        private boolean hasValidResponses;

        private UserInfoBean userInfoBean;

        public String getUserDN() {
            return userDN;
        }

        public void setUserDN(final String userDN) {
            this.userDN = userDN;
        }

        public String getGuid() {
            return guid;
        }

        public void setGuid(final String guid) {
            this.guid = guid;
        }

        public Date getPasswordChangeTime() {
            return passwordChangeTime;
        }

        public void setPasswordChangeTime(final Date passwordChangeTime) {
            this.passwordChangeTime = passwordChangeTime;
        }

        public Date getPasswordExpirationTime() {
            return passwordExpirationTime;
        }

        public void setPasswordExpirationTime(final Date passwordExpirationTime) {
            this.passwordExpirationTime = passwordExpirationTime;
        }

        public boolean isHasValidResponses() {
            return hasValidResponses;
        }

        public void setHasValidResponses(final boolean hasValidResponses) {
            this.hasValidResponses = hasValidResponses;
        }


        public Date getResponseSetTime() {
            return responseSetTime;
        }

        public void setResponseSetTime(Date responseSetTime) {
            this.responseSetTime = responseSetTime;
        }

        public PasswordStatus getPasswordStatus() {
            return passwordStatus;
        }

        public void setPasswordStatus(PasswordStatus passwordStatus) {
            this.passwordStatus = passwordStatus;
        }

        public UserInfoBean getUserInfoBean() {
            return userInfoBean;
        }

        public void setUserInfoBean(UserInfoBean userInfoBean) {
            this.userInfoBean = userInfoBean;
        }
    }
}

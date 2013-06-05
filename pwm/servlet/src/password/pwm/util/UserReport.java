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

import com.google.gson.Gson;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PasswordStatus;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.csv.CsvWriter;
import password.pwm.util.operations.CrService;
import password.pwm.util.operations.UserSearchEngine;
import password.pwm.util.operations.UserStatusHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.*;

public class UserReport {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserReport.class);

    private final PwmApplication pwmApplication;

    public UserReport(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    public Iterator<UserInformation> resultIterator(final int maxResults)
            throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final List<ChaiUser> userDNs = generateListOfUsers(maxResults);
        return new ResultIterator(userDNs);
    }

    private List<ChaiUser> generateListOfUsers(final int maxResults)
            throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setChaiProvider(pwmApplication.getProxyChaiProvider());
        searchConfiguration.setEnableValueEscaping(false);
        searchConfiguration.setUsername("*");

        LOGGER.debug("beginning UserReport user search using parameters: " + (new Gson()).toJson(searchConfiguration));

        final Map<ChaiUser,Map<String,String>> searchResults = userSearchEngine.performMultiUserSearch(null, searchConfiguration, maxResults, Collections.<String>emptyList());
        LOGGER.debug("UserReport user search found " + searchResults.size() + " users for reporting");
        return new ArrayList<ChaiUser>(searchResults.keySet());
    }

    private UserInformation readUserInformation(final ChaiUser theUser)
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final UserInformation userInformation = new UserInformation();

        final UserInfoBean uiBean = new UserInfoBean();

        UserStatusHelper.populateUserInfoBean(null, uiBean, pwmApplication, PwmConstants.DEFAULT_LOCALE ,theUser.getEntryDN(), null, pwmApplication.getProxyChaiProvider());
        userInformation.setUserInfoBean(uiBean);

        userInformation.setHasValidResponses(!uiBean.isRequiresResponseConfig());
        userInformation.setPasswordChangeTime(uiBean.getPasswordLastModifiedTime());
        userInformation.setPasswordExpirationTime(uiBean.getPasswordExpirationTime());
        userInformation.setPasswordStatus(uiBean.getPasswordState());

        try {
            final ResponseSet responseSet = pwmApplication.getCrService().readUserResponseSet(null, theUser);
            userInformation.setResponseSetTime(responseSet == null ? null : responseSet.getTimestamp());
        } catch (ChaiOperationException e) {
            LOGGER.debug("error reading response set for " + theUser.getEntryDN() + " : " + e.getMessage());
        }

        return userInformation;
    }

    private class ResultIterator implements Iterator<UserInformation> {
        private final Iterator<ChaiUser> userDNs;

        private ResultIterator(final List<ChaiUser> userDNs) {
            this.userDNs = userDNs.iterator();
        }

        public boolean hasNext() {
            return userDNs.hasNext();
        }

        public UserInformation next() {
            final ChaiUser nextUser = userDNs.next();
            try {
                return readUserInformation(nextUser);
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
        private PasswordStatus passwordStatus;

        private Date passwordChangeTime;
        private Date passwordExpirationTime;
        private Date responseSetTime;

        private boolean hasValidResponses;

        private UserInfoBean userInfoBean;

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

    public void outputToCsv(final OutputStream outputStream, final boolean includeHeader, final int maxResults)
            throws IOException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException {
        final CsvWriter csvWriter = new CsvWriter(outputStream, ',', Charset.forName("UTF8"));

        if (includeHeader) {
            final List<String> headerRow = new ArrayList<String>();
            headerRow.add("UserID");
            headerRow.add("UserDN");
            headerRow.add("UserGuid");
            headerRow.add("Password Expiration Time");
            headerRow.add("Password Change Time");
            headerRow.add("Response Save Time");
            headerRow.add("Has Valid Responses");
            headerRow.add("Password Expired");
            headerRow.add("Password Pre-Expired");
            headerRow.add("Password Violates Policy");
            headerRow.add("Password In Warn Period");
            csvWriter.writeRecord(headerRow.toArray(new String[headerRow.size()]));
        }

        for (final Iterator<UserReport.UserInformation> resultIterator = this.resultIterator(maxResults); resultIterator.hasNext(); ) {
            final UserReport.UserInformation userInformation = resultIterator.next();
            final List<String> csvRow = new ArrayList<String>();

            csvRow.add(userInformation.getUserInfoBean().getUserID());
            csvRow.add(userInformation.getUserInfoBean().getUserDN());
            csvRow.add(userInformation.getUserInfoBean().getUserGuid());
            csvRow.add(userInformation.getPasswordExpirationTime() == null ? "n/a" : PwmConstants.DEFAULT_DATETIME_FORMAT.format(userInformation.getPasswordExpirationTime()));
            csvRow.add(userInformation.getPasswordChangeTime() == null ? "n/a" : PwmConstants.DEFAULT_DATETIME_FORMAT.format(userInformation.getPasswordChangeTime()));
            csvRow.add(userInformation.getResponseSetTime() == null ? "n/a" : PwmConstants.DEFAULT_DATETIME_FORMAT.format(userInformation.getResponseSetTime()));
            csvRow.add(Boolean.toString(userInformation.isHasValidResponses()));
            csvRow.add(Boolean.toString(userInformation.getPasswordStatus().isExpired()));
            csvRow.add(Boolean.toString(userInformation.getPasswordStatus().isPreExpired()));
            csvRow.add(Boolean.toString(userInformation.getPasswordStatus().isViolatesPolicy()));
            csvRow.add(Boolean.toString(userInformation.getPasswordStatus().isWarnPeriod()));

            csvWriter.writeRecord(csvRow.toArray(new String[csvRow.size()]));
        }

        csvWriter.flush();
    }
}

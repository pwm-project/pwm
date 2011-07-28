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

package password.pwm.util;

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;

import java.util.*;

public class UserReport {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserReport.class);

    private final Configuration config;
    private final ChaiProvider provider;

    public UserReport(final Configuration config, final ChaiProvider provider) {
        this.config = config;
        this.provider = provider;
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
        final String baseDN = config.readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT);
        final String usernameSearchFilter = "(objectClass=inetOrgPerson)";

        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setAttributes(Collections.<String>emptyList());
        searchHelper.setFilter(usernameSearchFilter);
        searchHelper.setSearchScope(ChaiProvider.SEARCH_SCOPE.SUBTREE);

        final Map<String,Map<String,String>> searchResults = provider.search(baseDN, searchHelper);
        return searchResults.keySet();
    }

    private UserInformation readUserInformation(final String userDN) {
        final UserInformation userInformation = new UserInformation();
        userInformation.setUserDN(userDN);

        try {
            final String userGUID = Helper.readLdapGuidValue(provider, config, userDN);
            userInformation.setGuid(userGUID);
        } catch (Exception e) {
            LOGGER.error("error reading GUID for user " + userDN + ", error: " + e.getMessage());
        }

        //@todo add rest of stuff

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
            return readUserInformation(userDN);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static class UserInformation {
        private String userDN;
        private String guid;

        private Date passwordChangeTime;
        private Date passwordExpirationTime;

        private boolean hasValidResponses;

        private Date pwmDbResponseSetTime;
        private Date dbResponseSetTime;
        private Date ldapResponseSetTime;

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

        public Date getPwmDbResponseSetTime() {
            return pwmDbResponseSetTime;
        }

        public void setPwmDbResponseSetTime(final Date pwmDbResponseSetTime) {
            this.pwmDbResponseSetTime = pwmDbResponseSetTime;
        }

        public Date getDbResponseSetTime() {
            return dbResponseSetTime;
        }

        public void setDbResponseSetTime(final Date dbResponseSetTime) {
            this.dbResponseSetTime = dbResponseSetTime;
        }

        public Date getLdapResponseSetTime() {
            return ldapResponseSetTime;
        }

        public void setLdapResponseSetTime(final Date ldapResponseSetTime) {
            this.ldapResponseSetTime = ldapResponseSetTime;
        }

        public String toCsvLine() {
            return Helper.toCsvLine(
                    userDN,
                    guid,

                    passwordChangeTime == null ? "n/a" : PwmConstants.PWM_STANDARD_DATE_FORMAT.format(passwordChangeTime),
                    passwordExpirationTime == null ? "n/a" : PwmConstants.PWM_STANDARD_DATE_FORMAT.format(passwordExpirationTime),

                    Boolean.toString(hasValidResponses),

                    pwmDbResponseSetTime == null ? "n/a" : PwmConstants.PWM_STANDARD_DATE_FORMAT.format(pwmDbResponseSetTime),
                    dbResponseSetTime == null ? "n/a" : PwmConstants.PWM_STANDARD_DATE_FORMAT.format(dbResponseSetTime),
                    ldapResponseSetTime == null ? "n/a" : PwmConstants.PWM_STANDARD_DATE_FORMAT.format(ldapResponseSetTime)
            );
       }
    }
}

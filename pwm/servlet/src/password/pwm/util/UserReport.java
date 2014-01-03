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

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserStatusCacheBean;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.csv.CsvWriter;
import password.pwm.ldap.UserSearchEngine;

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

    private List<UserIdentity> generateListOfUsers(final int maxResults)
            throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setEnableValueEscaping(false);
        searchConfiguration.setUsername("*");

        LOGGER.debug("beginning UserReport user search using parameters: " + (Helper.getGson()).toJson(searchConfiguration));

        final Map<UserIdentity,Map<String,String>> searchResults = userSearchEngine.performMultiUserSearch(null, searchConfiguration, maxResults, Collections.<String>emptyList());
        LOGGER.debug("UserReport user search found " + searchResults.size() + " users for reporting");
        return new ArrayList<UserIdentity>(searchResults.keySet());
    }


    public void outputToCsv(final OutputStream outputStream, final boolean includeHeader, final int maxResults)
            throws IOException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException {
        final CsvWriter csvWriter = new CsvWriter(outputStream, ',', Charset.forName("UTF8"));

        if (includeHeader) {
            final List<String> headerRow = new ArrayList<String>();
            headerRow.add("UserDN");
            headerRow.add("LDAP Profile");
            headerRow.add("Username");
            headerRow.add("Email");
            headerRow.add("UserGuid");
            headerRow.add("Password Expiration Time");
            headerRow.add("Password Change Time");
            headerRow.add("Response Save Time");
            headerRow.add("Has Valid Responses");
            headerRow.add("Response Storage Method");
            headerRow.add("Password Expired");
            headerRow.add("Password Pre-Expired");
            headerRow.add("Password Violates Policy");
            headerRow.add("Password In Warn Period");
            csvWriter.writeRecord(headerRow.toArray(new String[headerRow.size()]));
        }

        final Iterator<UserStatusCacheBean> cacheBeanIterator = pwmApplication.getUserStatusCacheManager().iterator();
        int records = 0;
        while (cacheBeanIterator.hasNext() && records < maxResults) {
            final UserStatusCacheBean userStatusCacheBean = cacheBeanIterator.next();
            final List<String> csvRow = new ArrayList<String>();

            csvRow.add(userStatusCacheBean.getUserDN());
            csvRow.add(userStatusCacheBean.getLdapProfile());
            csvRow.add(userStatusCacheBean.getUsername());
            csvRow.add(userStatusCacheBean.getEmail());
            csvRow.add(userStatusCacheBean.getUserGUID());
            csvRow.add(userStatusCacheBean.getPasswordExpirationTime() == null ? "n/a" : PwmConstants.DEFAULT_DATETIME_FORMAT.format(userStatusCacheBean.getPasswordExpirationTime()));
            csvRow.add(userStatusCacheBean.getPasswordChangeTime() == null ? "n/a" : PwmConstants.DEFAULT_DATETIME_FORMAT.format(userStatusCacheBean.getPasswordChangeTime()));
            csvRow.add(userStatusCacheBean.getResponseSetTime() == null ? "n/a" : PwmConstants.DEFAULT_DATETIME_FORMAT.format(userStatusCacheBean.getResponseSetTime()));
            csvRow.add(Boolean.toString(userStatusCacheBean.isHasResponses()));
            csvRow.add(userStatusCacheBean.getResponseStorageMethod() == null ? "n/a" : userStatusCacheBean.getResponseStorageMethod().toString());
            csvRow.add(Boolean.toString(userStatusCacheBean.getPasswordStatus().isExpired()));
            csvRow.add(Boolean.toString(userStatusCacheBean.getPasswordStatus().isPreExpired()));
            csvRow.add(Boolean.toString(userStatusCacheBean.getPasswordStatus().isViolatesPolicy()));
            csvRow.add(Boolean.toString(userStatusCacheBean.getPasswordStatus().isWarnPeriod()));

            csvWriter.writeRecord(csvRow.toArray(new String[csvRow.size()]));
            records++;
        }

        csvWriter.flush();
    }
}

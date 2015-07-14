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
package password.pwm.util.operations.cr;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiCrFactory;
import com.novell.ldapchai.cr.ChaiResponseSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

public class LdapCrOperator implements CrOperator {

    private static final PwmLogger LOGGER = PwmLogger.forClass(LdapCrOperator.class);

    private final Configuration config;

    public LdapCrOperator(Configuration config) {
        this.config = config;
    }

    public void close() {
    }

    public ResponseSet readResponseSet(final ChaiUser theUser, final UserIdentity userIdentity, final String userGuid)
            throws PwmUnrecoverableException
    {
        try {
            return ChaiCrFactory.readChaiResponseSet(theUser);
        } catch (ChaiException e) {
            LOGGER.debug("ldap error reading response set: " + e.getMessage());
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return null;
    }

    public ResponseInfoBean readResponseInfo(ChaiUser theUser, final UserIdentity userIdentity, String userGUID)
            throws PwmUnrecoverableException
    {
        try {
            final ResponseSet responseSet = readResponseSet(theUser, userIdentity, userGUID);
            return responseSet == null ? null : CrOperators.convertToNoAnswerInfoBean(responseSet, DataStorageMethod.LDAP);
        } catch (ChaiException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,"unexpected error reading response info " + e.getMessage()));
        }
    }

    public void clearResponses(final ChaiUser theUser, final String userGuid)
            throws PwmUnrecoverableException
    {
        final String ldapStorageAttribute = config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE);
        if (ldapStorageAttribute == null || ldapStorageAttribute.length() < 1) {
            final String errorMsg = "ldap storage attribute is not configured, unable to clear user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);

        }
        try {
            final String currentValue = theUser.readStringAttribute(ldapStorageAttribute);
            if (currentValue != null && currentValue.length() > 0) {
                theUser.deleteAttribute(ldapStorageAttribute, null);
            }
            LOGGER.info("cleared responses for user to chai-ldap format");
        } catch (ChaiOperationException e) {
            final String errorMsg;
            if (e.getErrorCode() == ChaiError.NO_ACCESS) {
                errorMsg = "permission error clearing responses to ldap attribute '" + ldapStorageAttribute + "', user does not appear to have correct permissions to clear responses: " + e.getMessage();
            } else {
                errorMsg = "error clearing responses to ldap attribute '" + ldapStorageAttribute + "': " + e.getMessage();
            }
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, errorMsg);
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
        }
    }

    public void writeResponses(final ChaiUser theUser, final String userGuid, final ResponseInfoBean responseInfoBean)
            throws PwmUnrecoverableException
    {
        final String ldapStorageAttribute = config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE);
        if (ldapStorageAttribute == null || ldapStorageAttribute.length() < 1) {
            final String errorMsg = "ldap storage attribute is not configured, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        try {
            final ChaiResponseSet responseSet = ChaiCrFactory.newChaiResponseSet(
                    responseInfoBean.getCrMap(),
                    responseInfoBean.getHelpdeskCrMap(),
                    responseInfoBean.getLocale(),
                    responseInfoBean.getMinRandoms(),
                    theUser.getChaiProvider().getChaiConfiguration(),
                    responseInfoBean.getCsIdentifier()
            );
            ChaiCrFactory.writeChaiResponseSet(responseSet, theUser);
            LOGGER.info("saved responses for user to chai-ldap format");
        } catch (ChaiException e) {
            final String errorMsg;
            if (e.getErrorCode() == ChaiError.NO_ACCESS) {
                errorMsg = "permission error writing user responses to ldap attribute '" + ldapStorageAttribute + "', user does not appear to have correct permissions to save responses: " + e.getMessage();
            } else {
                errorMsg = "error writing user responses to ldap attribute '" + ldapStorageAttribute + "': " + e.getMessage();
            }
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, errorMsg);
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        }
    }
}

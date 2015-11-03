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
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmApplication;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.db.DatabaseAccessorImpl;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.logging.PwmLogger;


public class DbCrOperator implements CrOperator {

    final static private PwmLogger LOGGER = PwmLogger.forClass(DbCrOperator.class);

    final PwmApplication pwmApplication;

    public DbCrOperator(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    public void close() {
    }

    public ResponseSet readResponseSet(
            final ChaiUser theUser,
            final UserIdentity userIdentity,
            final String userGUID
    )
            throws PwmUnrecoverableException
    {
        if (userGUID == null || userGUID.length() < 1) {
            final String errorMsg = "user " + theUser.getEntryDN() + " does not have a guid, unable to search for responses in remote database";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_GUID, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try {
            final DatabaseAccessorImpl databaseAccessor = pwmApplication.getDatabaseAccessor();
            final String responseStringBlob = databaseAccessor.get(DatabaseTable.PWM_RESPONSES, userGUID);
            if (responseStringBlob != null && responseStringBlob.length() > 0) {
                final ResponseSet userResponseSet = ChaiResponseSet.parseChaiResponseSetXML(responseStringBlob, theUser);
                LOGGER.debug("found responses for " + theUser.getEntryDN() + " in remote database: " + userResponseSet.toString());
                return userResponseSet;
            } else {
                LOGGER.trace("user guid for " + theUser.getEntryDN() + " not found in remote database (key=" + userGUID + ")");
            }
        } catch (ChaiValidationException e) {
            final String errorMsg = "unexpected error reading responses for " + theUser.getEntryDN() + " from remote database: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        } catch (PwmOperationalException e) {
            final String errorMsg = "unexpected error reading responses for " + theUser.getEntryDN() + " from remote database: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(e.getErrorInformation().getError(), errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        return null;
    }

    public ResponseInfoBean readResponseInfo(ChaiUser theUser, final UserIdentity userIdentity, String userGUID)
            throws PwmUnrecoverableException
    {
        try {
            final ResponseSet responseSet = readResponseSet(theUser, userIdentity, userGUID);
            return responseSet == null ? null : CrOperators.convertToNoAnswerInfoBean(responseSet, DataStorageMethod.DB);
        } catch (ChaiException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,"unexpected error reading response info for " + theUser.getEntryDN() + ", error: "  + e.getMessage()));
        }
    }

    public void clearResponses(final ChaiUser theUser, final String userGUID)
            throws PwmUnrecoverableException
    {
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot clear responses to remote database, user " + theUser.getEntryDN() + " does not have a guid"));
        }

        try {
            final DatabaseAccessorImpl databaseAccessor = pwmApplication.getDatabaseAccessor();
            databaseAccessor.remove(DatabaseTable.PWM_RESPONSES, userGUID);
            LOGGER.info("cleared responses for user " + theUser.getEntryDN() + " in remote database");
        } catch (DatabaseException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, "unexpected error clearing responses for " + theUser.getEntryDN() + " in remote database, error: " + e.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        }
    }

    @Override
    public void writeResponses(ChaiUser theUser, String userGUID, ResponseInfoBean responseInfoBean)
            throws PwmUnrecoverableException
    {
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to remote database, user " + theUser.getEntryDN() +  " does not have a guid"));
        }

        LOGGER.trace("attempting to save responses for " + theUser.getEntryDN() + " in remote database (key=" + userGUID + ")");

        try {
            final ChaiResponseSet responseSet = ChaiCrFactory.newChaiResponseSet(
                    responseInfoBean.getCrMap(),
                    responseInfoBean.getHelpdeskCrMap(),
                    responseInfoBean.getLocale(),
                    responseInfoBean.getMinRandoms(),
                    theUser.getChaiProvider().getChaiConfiguration(),
                    responseInfoBean.getCsIdentifier()
            );

            final DatabaseAccessorImpl databaseAccessor = pwmApplication.getDatabaseAccessor();
            databaseAccessor.put(DatabaseTable.PWM_RESPONSES, userGUID, responseSet.stringValue());
            LOGGER.info("saved responses for " + theUser.getEntryDN() + " in remote database (key=" + userGUID + ")");
        } catch (ChaiException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected error saving responses for " + theUser.getEntryDN() + " in remote database: " + e.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            LOGGER.error(errorInfo.toDebugStr());
            pwmOE.initCause(e);
            throw pwmOE;
        } catch (DatabaseException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected error saving responses for " + theUser.getEntryDN() + " in remote database: " + e.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            LOGGER.error(errorInfo.toDebugStr());
            pwmOE.initCause(e);
            throw pwmOE;
        }
    }
}

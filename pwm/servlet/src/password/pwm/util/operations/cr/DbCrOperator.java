/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2013 The PWM Project
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
import com.novell.ldapchai.cr.*;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmApplication;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.db.DatabaseException;


public class DbCrOperator implements CrOperator {

    final static private PwmLogger LOGGER = PwmLogger.getLogger(DbCrOperator.class);

    final PwmApplication pwmApplication;

    public DbCrOperator(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    public void close() {
    }

    public ResponseSet readResponseSet(
            final ChaiUser theUser,
            final String userGUID
    )
            throws PwmUnrecoverableException
    {
        if (userGUID == null || userGUID.length() < 1) {
            final String errorMsg = "user " + theUser.getEntryDN() + " does not have a pwmGUID, unable to search for responses in Database";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_GUID, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try {
            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
            final String responseStringBlob = databaseAccessor.get(DatabaseAccessor.TABLE.PWM_RESPONSES, userGUID);
            if (responseStringBlob != null && responseStringBlob.length() > 0) {
                final ResponseSet userResponseSet = ChaiResponseSet.parseChaiResponseSetXML(responseStringBlob, theUser);
                LOGGER.debug("found user responses in database: " + userResponseSet.toString());
                return userResponseSet;
            } else {
                LOGGER.trace("user guid not found in database");
            }
        } catch (ChaiValidationException e) {
            final String errorMsg = "unexpected chai error reading responses from database: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        } catch (PwmOperationalException e) {
            final String errorMsg = "unexpected error reading responses from database: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        return null;
    }

    public ResponseInfoBean readResponseInfo(ChaiUser theUser, String userGUID)
            throws PwmUnrecoverableException
    {
        try {
            final ResponseSet responseSet = readResponseSet(theUser,userGUID);
            return responseSet == null ? null : CrOperators.convertToNoAnswerInfoBean(responseSet);
        } catch (ChaiException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,"unexpected error reading response info " + e.getMessage()));
        }
    }

    public void clearResponses(final ChaiUser theUser, final String userGUID)
            throws PwmUnrecoverableException
    {
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot clear responses to remote database, user does not have a pwmGUID"));
        }

        try {
            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
            databaseAccessor.remove(DatabaseAccessor.TABLE.PWM_RESPONSES, userGUID);
            LOGGER.info("cleared responses for user " + theUser.getEntryDN() + " in remote database");
        } catch (DatabaseException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, "unexpected error clearing responses to remote database: " + e.getMessage());
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
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to remote database, user does not have a pwmGUID"));
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

            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
            databaseAccessor.put(DatabaseAccessor.TABLE.PWM_RESPONSES, userGUID, responseSet.stringValue());
            LOGGER.info("saved responses for user in remote database");
        } catch (ChaiException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected error saving responses to remote database: " + e.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        } catch (DatabaseException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected error saving responses to remote database: " + e.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        }
    }
}

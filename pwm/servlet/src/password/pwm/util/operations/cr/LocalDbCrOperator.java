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
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

public class LocalDbCrOperator implements CrOperator {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LocalDbCrOperator.class);

    private final LocalDB localDB;

    public LocalDbCrOperator(LocalDB localDB) {
        this.localDB = localDB;
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
            final String errorMsg = "unable to read guid for user " + userIdentity.toString() + ", unable to search for responses in LocalDB";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_GUID, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        if (localDB == null) {
            final String errorMsg = "LocalDB is not available, unable to search for user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try {
            final String responseStringBlob = localDB.get(LocalDB.DB.RESPONSE_STORAGE, userGUID);
            if (responseStringBlob != null && responseStringBlob.length() > 0) {
                final ResponseSet userResponseSet = ChaiResponseSet.parseChaiResponseSetXML(responseStringBlob, theUser);
                LOGGER.debug("found user responses in LocalDB: " + userResponseSet.toString());
                return userResponseSet;
            }
        } catch (LocalDBException e) {
            final String errorMsg = "unexpected LocalDB error reading responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        } catch (ChaiException e) {
            final String errorMsg = "unexpected chai error reading responses from LocalDB: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        return null;
    }

    public ResponseInfoBean readResponseInfo(ChaiUser theUser, UserIdentity userIdentity, String userGUID)
            throws PwmUnrecoverableException
    {
        try {
            final ResponseSet responseSet = readResponseSet(theUser, userIdentity, userGUID);
            return responseSet == null ? null : CrOperators.convertToNoAnswerInfoBean(responseSet, DataStorageMethod.LOCALDB);
        } catch (ChaiException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_RESPONSES_NORESPONSES,"unexpected error reading response info " + e.getMessage()));
        }
    }

    public void clearResponses(final ChaiUser theUser, final String userGUID) throws PwmUnrecoverableException {
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot clear responses to localDB, user does not have a pwmGUID"));
        }

        if (localDB == null) {
            final String errorMsg = "LocalDB is not available, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try {
            localDB.remove(LocalDB.DB.RESPONSE_STORAGE, userGUID);
            LOGGER.info("cleared responses for user " + theUser.getEntryDN() + " in local LocalDB");
        } catch (LocalDBException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, "unexpected LocalDB error clearing responses: " + e.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        }
    }

    public void writeResponses(ChaiUser theUser, String userGUID, ResponseInfoBean responseInfoBean)
            throws PwmUnrecoverableException
    {
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to localDB, user does not have a pwmGUID"));
        }

        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            final String errorMsg = "LocalDB is not available, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg);
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

            localDB.put(LocalDB.DB.RESPONSE_STORAGE, userGUID, responseSet.stringValue());
            LOGGER.info("saved responses for user in LocalDB");
        } catch (LocalDBException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected LocalDB error saving responses to localDB: " + e.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        } catch (ChaiException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected error saving responses to localDB: " + e.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        }
    }
}

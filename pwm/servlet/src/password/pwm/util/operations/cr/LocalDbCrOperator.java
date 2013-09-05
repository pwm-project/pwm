package password.pwm.util.operations.cr;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiCrFactory;
import com.novell.ldapchai.cr.ChaiResponseSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiException;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

public class LocalDbCrOperator implements CrOperator {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(LocalDbCrOperator.class);

    private final LocalDB localDB;

    public LocalDbCrOperator(LocalDB localDB) {
        this.localDB = localDB;
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
            final String errorMsg = "user " + theUser.getEntryDN() + " does not have a pwmGUID, unable to search for responses in PwmDB";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_GUID, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        if (localDB == null) {
            final String errorMsg = "LocalDB is not available, unable to search for user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE, errorMsg);
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

    public void clearResponses(final ChaiUser theUser, final String userGUID) throws PwmUnrecoverableException {
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot clear responses to pwmDB, user does not have a pwmGUID"));
        }

        if (localDB == null) {
            final String errorMsg = "LocalDB is not available, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE, errorMsg);
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
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to pwmDB, user does not have a pwmGUID"));
        }

        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            final String errorMsg = "LocalDB is not available, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE, errorMsg);
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
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected LocalDB error saving responses to pwmDB: " + e.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        } catch (ChaiException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected error saving responses to pwmDB: " + e.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        }
    }
}

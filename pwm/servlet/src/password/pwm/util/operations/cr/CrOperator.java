package password.pwm.util.operations.cr;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.error.PwmUnrecoverableException;

import java.util.LinkedHashMap;
import java.util.Map;

public interface CrOperator {
    /**
    Read a response set suitable for use in forgotten password scenarios
     */
    public ResponseSet readResponseSet(final ChaiUser theUser, final String userGUID)
            throws PwmUnrecoverableException;

    /**
     * Read a response info bean suitable for examing the user's stored response data, but not for use during forgotten password.
     * @param theUser
     * @param userGUID
     * @return
     * @throws PwmUnrecoverableException
     */
    public ResponseInfoBean readResponseInfo(final ChaiUser theUser, final String userGUID)
            throws PwmUnrecoverableException;

    public void clearResponses(final ChaiUser theUser, final String userGUID)
            throws PwmUnrecoverableException;

    public void writeResponses(final ChaiUser theUser, final String userGuid, final ResponseInfoBean responseInfoBean)
            throws PwmUnrecoverableException;

    public void close();

    static class CrOperators {
        static ResponseInfoBean convertToNoAnswerInfoBean(final ResponseSet responseSet)
                throws ChaiUnavailableException, ChaiOperationException, ChaiValidationException
        {
            final Map<Challenge,String> crMap = new LinkedHashMap<Challenge,String>();
            for (final Challenge challenge : responseSet.getChallengeSet().getChallenges()) {
                crMap.put(challenge,"");
            }

            ResponseInfoBean responseInfoBean = new ResponseInfoBean(
                    crMap,
                    responseSet.getHelpdeskResponses(),
                    responseSet.getLocale(),
                    responseSet.getChallengeSet().getMinRandomRequired(),
                    responseSet.getChallengeSet().getIdentifier()
            );
            responseInfoBean.setTimestamp(responseSet.getTimestamp());
            return responseInfoBean;
        }
    }
}

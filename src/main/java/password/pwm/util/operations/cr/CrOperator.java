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
import com.novell.ldapchai.cr.Answer;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.impl.edir.NmasResponseSet;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface CrOperator {
    /**
     Read a response set suitable for use in forgotten password scenarios
     */
    public ResponseSet readResponseSet(final ChaiUser theUser, final UserIdentity userIdentity, final String userGUID)
            throws PwmUnrecoverableException;

    /**
     * Read a response info bean suitable for examining the user's stored response data, but not for use during forgotten password.
     * @param theUser
     * @param userGUID
     * @return
     * @throws PwmUnrecoverableException
     */
    public ResponseInfoBean readResponseInfo(final ChaiUser theUser, final UserIdentity userIdentity, final String userGUID)
            throws PwmUnrecoverableException;

    public void clearResponses(final ChaiUser theUser, final String userGUID)
            throws PwmUnrecoverableException;

    public void writeResponses(final ChaiUser theUser, final String userGuid, final ResponseInfoBean responseInfoBean)
            throws PwmUnrecoverableException;

    public void close();

    static class CrOperators {
        private static final PwmLogger LOGGER = PwmLogger.forClass(CrOperator.class);

        static ResponseInfoBean convertToNoAnswerInfoBean(final ResponseSet responseSet, final DataStorageMethod dataSource
        )
                throws ChaiUnavailableException, ChaiOperationException, ChaiValidationException
        {
            final Map<Challenge,String> crMap = new LinkedHashMap<>();
            Answer.FormatType formatType = null;
            try {
                if (responseSet instanceof NmasResponseSet) {
                    formatType = Answer.FormatType.NMAS;
                } else {
                    final List<ChallengeBean> challengeBeans = responseSet.asChallengeBeans(true);
                    if (challengeBeans != null && !challengeBeans.isEmpty()) {
                        formatType = challengeBeans.get(0).answer.getType();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("unable to determine formatType of stored responses: " + e.getMessage());
            }
            for (final Challenge challenge : responseSet.getChallengeSet().getChallenges()) {
                crMap.put(challenge,"");
            }

            ResponseInfoBean responseInfoBean = new ResponseInfoBean(
                    crMap,
                    responseSet.getHelpdeskResponses(),
                    responseSet.getLocale(),
                    responseSet.getChallengeSet().getMinRandomRequired(),
                    responseSet.getChallengeSet().getIdentifier(),
                    dataSource,
                    formatType
            );
            responseInfoBean.setTimestamp(responseSet.getTimestamp());
            return responseInfoBean;
        }
    }
}

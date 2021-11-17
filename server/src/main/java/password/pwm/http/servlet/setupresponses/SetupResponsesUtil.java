/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http.servlet.setupresponses;

import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.ChaiCrFactory;
import com.novell.ldapchai.cr.ChaiResponseSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.Permission;
import password.pwm.PwmConstants;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.SetupResponsesBean;
import password.pwm.util.logging.PwmLogger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class SetupResponsesUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SetupResponsesUtil.class );

    public static boolean hasChallenges(
            final PwmRequest pwmRequest,
            final ResponseMode responseMode
    )
            throws PwmUnrecoverableException
    {
        final ChallengeProfile challengeProfile = SetupResponsesServlet.getChallengeProfile( pwmRequest );
        if ( challengeProfile == null )
        {
            return false;
        }

        final Optional<ChallengeSet> optionalChallengeSet = responseMode == ResponseMode.helpdesk
                ? challengeProfile.getHelpdeskChallengeSet()
                : challengeProfile.getChallengeSet();

        return optionalChallengeSet.isPresent() && !optionalChallengeSet.get().getChallenges().isEmpty();
    }

    static boolean checkIfAllowSkipCr( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( pwmRequest.isForcedPageView() )
        {
            final boolean admin = pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmDomain(), Permission.PWMADMIN );
            if ( admin )
            {
                if ( pwmRequest.getDomainConfig().readSettingAsBoolean( PwmSetting.ADMIN_ALLOW_SKIP_FORCED_ACTIVITIES ) )
                {
                    LOGGER.trace( pwmRequest, () -> "allowing c/r answer setup skipping due to user being admin and setting "
                            + PwmSetting.ADMIN_ALLOW_SKIP_FORCED_ACTIVITIES.toMenuLocationDebug( null, pwmRequest.getLocale() ) );
                    return true;
                }
            }
        }

        return false;
    }

    static Map<Challenge, String> paramMapToChallengeMap(
            final ChallengeSet challengeSet,
            final Map<String, String> inputMap,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        //final SetupResponsesBean responsesBean = pwmSession.getSetupResponseBean();
        final Map<Challenge, String> readResponses = new LinkedHashMap<>();

        {
            // read in the question texts and responses
            for ( final String indexKey : setupData.getIndexedChallenges().keySet() )
            {
                final Challenge loopChallenge = setupData.getIndexedChallenges().get( indexKey );
                if ( loopChallenge.isRequired() || !setupData.isSimpleMode() )
                {

                    final Challenge newChallenge;
                    if ( !loopChallenge.isAdminDefined() )
                    {
                        final String questionText = inputMap.get( PwmConstants.PARAM_QUESTION_PREFIX + indexKey );
                        newChallenge = new ChaiChallenge(
                                loopChallenge.isRequired(),
                                questionText,
                                loopChallenge.getMinLength(),
                                loopChallenge.getMaxLength(),
                                loopChallenge.isAdminDefined(),
                                loopChallenge.getMaxQuestionCharsInAnswer(),
                                loopChallenge.isEnforceWordlist()
                        );
                    }
                    else
                    {
                        newChallenge = loopChallenge;
                    }

                    final String answer = inputMap.get( PwmConstants.PARAM_RESPONSE_PREFIX + indexKey );

                    if ( answer != null && answer.length() > 0 )
                    {
                        readResponses.put( newChallenge, answer );
                    }
                }
            }

            if ( setupData.isSimpleMode() )
            {
                // if in simple mode, read the select-based random challenges
                for ( int i = 0; i < setupData.getIndexedChallenges().size(); i++ )
                {
                    final String questionText = inputMap.get( PwmConstants.PARAM_QUESTION_PREFIX + "Random_" + String.valueOf( i ) );

                    Challenge challenge = null;
                    for ( final Challenge loopC : challengeSet.getRandomChallenges() )
                    {
                        if ( loopC.isAdminDefined() && questionText != null && questionText.equals( loopC.getChallengeText() ) )
                        {
                            challenge = loopC;
                            break;
                        }
                    }

                    final String answer = inputMap.get( PwmConstants.PARAM_RESPONSE_PREFIX + "Random_" + String.valueOf( i ) );
                    if ( answer != null && answer.length() > 0 )
                    {
                        readResponses.put( challenge, answer );
                    }
                }
            }
        }

        return readResponses;
    }

    static ResponseInfoBean generateResponseInfoBean(
            final PwmRequest pwmRequest,
            final ChallengeSet challengeSet,
            final Map<Challenge, String> readResponses,
            final Map<Challenge, String> helpdeskResponses
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final ChaiProvider provider = pwmRequest.getPwmSession().getSessionManager().getChaiProvider();

        try
        {
            final ResponseInfoBean responseInfoBean = new ResponseInfoBean(
                    readResponses,
                    helpdeskResponses,
                    challengeSet != null ? challengeSet.getLocale() : null,
                    challengeSet != null ? challengeSet.getMinRandomRequired() : 0,
                    challengeSet != null ? challengeSet.getIdentifier() : null,
                    null,
                    null
            );

            if ( challengeSet != null )
            {
                final ChaiResponseSet responseSet = ChaiCrFactory.newChaiResponseSet(
                        readResponses,
                        challengeSet.getLocale(),
                        challengeSet.getMinRandomRequired(),
                        provider.getChaiConfiguration(),
                        challengeSet.getIdentifier() );

                responseSet.meetsChallengeSetRequirements( challengeSet );

                final SetupResponsesBean setupResponsesBean = pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, SetupResponsesBean.class );
                final int minRandomRequiredSetup = setupResponsesBean.getChallengeData().get( ResponseMode.user ).getMinRandomSetup();
                if ( minRandomRequiredSetup == 0 )
                {
                    // if using recover style, then all readResponseSet must be supplied at this point.
                    if ( responseSet.getChallengeSet().getRandomChallenges().size() < challengeSet.getRandomChallenges().size() )
                    {
                        throw new ChaiValidationException( "too few random responses", ChaiError.CR_TOO_FEW_RANDOM_RESPONSES );
                    }
                }
            }

            return responseInfoBean;
        }
        catch ( final ChaiValidationException e )
        {
            final ErrorInformation errorInfo = convertChaiValidationException( e );
            throw new PwmDataValidationException( errorInfo );
        }
    }

    private static ErrorInformation convertChaiValidationException(
            final ChaiValidationException e
    )
    {
        final String[] fieldNames = new String[] {
                e.getFieldName(),
        };

        switch ( e.getErrorCode() )
        {
            case CR_TOO_FEW_CHALLENGES:
                return new ErrorInformation( PwmError.ERROR_MISSING_REQUIRED_RESPONSE, null, fieldNames );

            case CR_TOO_FEW_RANDOM_RESPONSES:
                return new ErrorInformation( PwmError.ERROR_MISSING_RANDOM_RESPONSE, null, fieldNames );

            case CR_MISSING_REQUIRED_CHALLENGE_TEXT:
                return new ErrorInformation( PwmError.ERROR_MISSING_CHALLENGE_TEXT, null, fieldNames );

            case CR_RESPONSE_TOO_LONG:
                return new ErrorInformation( PwmError.ERROR_RESPONSE_TOO_LONG, null, fieldNames );

            case CR_RESPONSE_TOO_SHORT:
            case CR_MISSING_REQUIRED_RESPONSE_TEXT:
                return new ErrorInformation( PwmError.ERROR_RESPONSE_TOO_SHORT, null, fieldNames );

            case CR_DUPLICATE_RESPONSES:
                return new ErrorInformation( PwmError.ERROR_RESPONSE_DUPLICATE, null, fieldNames );

            case CR_TOO_MANY_QUESTION_CHARS:
                return new ErrorInformation( PwmError.ERROR_CHALLENGE_IN_RESPONSE, null, fieldNames );

            default:
                return new ErrorInformation( PwmError.ERROR_INTERNAL );
        }
    }

    static SetupResponsesBean.SetupData populateSetupData(
            final ChallengeSet challengeSet,
            final int minRandomSetup
    )
    {
        boolean useSimple = true;
        final Map<String, Challenge> indexedChallenges = new LinkedHashMap<>();

        int minRandom = minRandomSetup;

        {
            if ( minRandom != 0 && minRandom < challengeSet.getMinRandomRequired() )
            {
                minRandom = challengeSet.getMinRandomRequired();
            }
            if ( minRandom > challengeSet.getRandomChallenges().size() )
            {
                minRandom = 0;
            }
        }
        {
            {
                if ( minRandom == 0 )
                {
                    useSimple = false;
                }

                for ( final Challenge challenge : challengeSet.getChallenges() )
                {
                    if ( !challenge.isRequired() && !challenge.isAdminDefined() )
                    {
                        useSimple = false;
                    }
                }

                if ( challengeSet.getRandomChallenges().size() == challengeSet.getMinRandomRequired() )
                {
                    useSimple = false;
                }
            }
        }

        {
            int index = 0;
            for ( final Challenge loopChallenge : challengeSet.getChallenges() )
            {
                indexedChallenges.put( String.valueOf( index ), loopChallenge );
                index++;
            }
        }

        final SetupResponsesBean.SetupData setupData = new SetupResponsesBean.SetupData();
        setupData.setSimpleMode( useSimple );
        setupData.setIndexedChallenges( indexedChallenges );
        setupData.setMinRandomSetup( minRandom );
        return setupData;
    }
}

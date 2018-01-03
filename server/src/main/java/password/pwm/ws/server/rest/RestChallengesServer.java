/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.ws.server.rest;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.exception.ChaiException;
import lombok.Data;
import password.pwm.PwmConstants;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.operations.CrService;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.server.RestMethodHandler;
import password.pwm.ws.server.RestRequest;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServlet;
import password.pwm.ws.server.RestWebServer;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@WebServlet(
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + PwmConstants.URL_PREFIX_REST + "/challenges"
        }
)
@RestWebServer( webService = WebServiceUsage.CheckPassword, requireAuthentication = true )
public class RestChallengesServer extends RestServlet
{

    @Data
    public static class Policy implements Serializable
    {
        public List<ChallengeBean> challenges;
        public List<ChallengeBean> helpdeskChallenges;
        public int minimumRandoms;
    }

    @Data
    public static class JsonChallengesData implements Serializable
    {
        public String username;
        public List<ChallengeBean> challenges;
        public List<ChallengeBean> helpdeskChallenges;
        public int minimumRandoms;
        public Policy policy;

        public ResponseInfoBean toResponseInfoBean( final Locale locale, final String csIdentifier )
                throws PwmOperationalException
        {
            final Map<Challenge, String> crMap = new LinkedHashMap<>();
            if ( challenges != null )
            {
                for ( final ChallengeBean challengeBean : challenges )
                {
                    final Challenge challenge = ChaiChallenge.fromChallengeBean( challengeBean );
                    final String answerText = challengeBean.getAnswer().getAnswerText();
                    if ( answerText == null || answerText.length() < 1 )
                    {
                        throw new IllegalArgumentException( "missing answerText for challenge '" + challenge.getChallengeText() + "'" );
                    }
                    crMap.put( challenge, answerText );
                }
            }

            final Map<Challenge, String> helpdeskCrMap = new LinkedHashMap<>();
            if ( helpdeskChallenges != null )
            {
                for ( final ChallengeBean challengeBean : helpdeskChallenges )
                {
                    final Challenge challenge = ChaiChallenge.fromChallengeBean( challengeBean );
                    final String answerText = challengeBean.getAnswer().getAnswerText();
                    if ( answerText == null || answerText.length() < 1 )
                    {
                        throw new IllegalArgumentException( "missing answerText for helpdesk challenge '" + challenge.getChallengeText() + "'" );
                    }
                    helpdeskCrMap.put( challenge, answerText );
                }
            }

            final ResponseInfoBean responseInfoBean = new ResponseInfoBean(
                    crMap,
                    helpdeskCrMap,
                    locale,
                    minimumRandoms,
                    csIdentifier,
                    null,
                    null
            );
            responseInfoBean.setTimestamp( Instant.now() );
            return responseInfoBean;
        }
    }

    @Override
    public void preCheckRequest( final RestRequest request ) throws PwmUnrecoverableException
    {
    }

    @RestMethodHandler( method = HttpMethod.GET, produces = HttpContentType.json )
    public RestResultBean doFormGetChallengeData( final RestRequest restRequest )

            throws PwmUnrecoverableException
    {
        final boolean answers = restRequest.readParameterAsBoolean( "answers" );
        final boolean helpdesk = restRequest.readParameterAsBoolean( "helpdesk" );
        final String username = restRequest.readParameterAsString( "username", PwmHttpRequestWrapper.Flag.BypassValidation );

        try
        {
            if ( answers && !restRequest.getPwmApplication().getConfig().readSettingAsBoolean( PwmSetting.ENABLE_WEBSERVICES_READANSWERS ) )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "retrieval of answers is not permitted" ) );
            }

            final TargetUserIdentity targetUserIdentity = resolveRequestedUsername( restRequest, username );

            // gather data
            final ResponseSet responseSet;
            final ChallengeSet challengeSet;
            final ChallengeSet helpdeskChallengeSet;
            final String outputUsername;

            final ChaiUser chaiUser = targetUserIdentity.getChaiUser();
            final Locale userLocale = restRequest.getLocale();
            final CrService crService = restRequest.getPwmApplication().getCrService();
            responseSet = crService.readUserResponseSet( restRequest.getSessionLabel(), targetUserIdentity.getUserIdentity(), chaiUser );

            final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getUserIdentity(),
                    chaiUser,
                    userLocale
            );
            final ChallengeProfile challengeProfile = crService.readUserChallengeProfile(
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getUserIdentity(),
                    chaiUser,
                    passwordPolicy,
                    userLocale
            );

            challengeSet = challengeProfile.getChallengeSet();
            helpdeskChallengeSet = challengeProfile.getHelpdeskChallengeSet();
            outputUsername = targetUserIdentity.getUserIdentity().toDelimitedKey();

            // build output
            final JsonChallengesData jsonData = new JsonChallengesData();
            {
                jsonData.username = outputUsername;
                if ( responseSet != null )
                {
                    jsonData.challenges = responseSet.asChallengeBeans( answers );
                    if ( helpdesk )
                    {
                        jsonData.helpdeskChallenges = responseSet.asHelpdeskChallengeBeans( answers );
                    }
                    jsonData.minimumRandoms = responseSet.getChallengeSet().getMinRandomRequired();
                }
                final Policy policy = new Policy();
                if ( challengeSet != null )
                {
                    policy.challenges = challengesToBeans( challengeSet.getChallenges() );
                    policy.minimumRandoms = challengeSet.getMinRandomRequired();
                }
                if ( helpdeskChallengeSet != null && helpdesk )
                {
                    policy.helpdeskChallenges = challengesToBeans( helpdeskChallengeSet.getChallenges() );
                }
                if ( policy.challenges != null || policy.helpdeskChallenges != null )
                {
                    jsonData.policy = policy;
                }
            }

            // update statistics
            StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_CHALLENGES );
            return RestResultBean.withData( jsonData );
        }
        catch ( ChaiException e )
        {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMsg );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }

    @RestMethodHandler( method = HttpMethod.POST, consumes = HttpContentType.json, produces = HttpContentType.json )
    public RestResultBean doSetChallengeDataJson( final RestRequest restRequest )
            throws IOException, PwmUnrecoverableException
    {
        final JsonChallengesData jsonInput = deserializeJsonBody( restRequest, JsonChallengesData.class );

        final TargetUserIdentity targetUserIdentity = resolveRequestedUsername( restRequest, jsonInput.getUsername() );

        try
        {
            final ChaiUser chaiUser;
            final String userGUID;
            final String csIdentifer;
            final UserIdentity userIdentity;
            final CrService crService = restRequest.getPwmApplication().getCrService();

            userIdentity = targetUserIdentity.getUserIdentity();
            chaiUser = targetUserIdentity.getChaiUser();
            userGUID = LdapOperationsHelper.readLdapGuidValue(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    userIdentity,
                    false
            );
            final ChallengeProfile challengeProfile = crService.readUserChallengeProfile(
                    restRequest.getSessionLabel(),
                    userIdentity,
                    chaiUser,
                    PwmPasswordPolicy.defaultPolicy(),
                    restRequest.getLocale()
            );

            csIdentifer = challengeProfile.getChallengeSet().getIdentifier();

            final ResponseInfoBean responseInfoBean = jsonInput.toResponseInfoBean( restRequest.getLocale(), csIdentifer );
            crService.writeResponses( userIdentity, chaiUser, userGUID, responseInfoBean );

            // update statistics
            StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_CHALLENGES );

            return RestResultBean.forSuccessMessage( restRequest, Message.Success_SetupResponse );
        }
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error reading json input: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMsg );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }

    @RestMethodHandler( method = HttpMethod.DELETE, produces = HttpContentType.json )
    public RestResultBean doDeleteChallengeData( final RestRequest restRequest )
            throws IOException, PwmUnrecoverableException
    {
        final String username = restRequest.readParameterAsString( "username", PwmHttpRequestWrapper.Flag.BypassValidation );

        final TargetUserIdentity targetUserIdentity = resolveRequestedUsername( restRequest, username );

        try
        {
            final ChaiUser chaiUser;
            final String userGUID;

            chaiUser = targetUserIdentity.getChaiUser();
            userGUID = LdapOperationsHelper.readLdapGuidValue(
                    restRequest.getPwmApplication(),
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getUserIdentity(),
                    false );

            final CrService crService = restRequest.getPwmApplication().getCrService();
            crService.clearResponses(
                    restRequest.getSessionLabel(),
                    targetUserIdentity.getUserIdentity(),
                    chaiUser,
                    userGUID
            );

            // update statistics
            StatisticsManager.incrementStat( restRequest.getPwmApplication(), Statistic.REST_CHALLENGES );

            return RestResultBean.forSuccessMessage( restRequest, Message.Success_Unknown );
        }
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error delete responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMsg );
            return RestResultBean.fromError( restRequest, errorInformation );
        }
    }

    private static List<ChallengeBean> challengesToBeans( final List<Challenge> challenges )
    {
        final List<ChallengeBean> returnList = new ArrayList<>();
        for ( final Challenge challenge : challenges )
        {
            returnList.add( challenge.asChallengeBean() );
        }
        return returnList;
    }
}

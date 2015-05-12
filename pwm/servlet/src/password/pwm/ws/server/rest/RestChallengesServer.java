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

package password.pwm.ws.server.rest;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import password.pwm.Permission;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.event.HelpdeskAuditRecord;
import password.pwm.event.UserAuditRecord;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.operations.CrService;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.*;

@Path("/challenges")
public class RestChallengesServer extends AbstractRestServer {
    public static class Policy {
        public List<ChallengeBean> challenges;
        public List<ChallengeBean> helpdeskChallenges;
        public int minimumRandoms;
    }

    public static class JsonChallengesData implements Serializable {
        public String username;
        public List<ChallengeBean> challenges;
        public List<ChallengeBean> helpdeskChallenges;
        public int minimumRandoms;
        public Policy policy;

        public ResponseInfoBean toResponseInfoBean(final Locale locale, final String csIdentifier)
                throws PwmOperationalException
        {
            final Map<Challenge,String> crMap = new LinkedHashMap<>();
            if (challenges != null) {
                for (final ChallengeBean challengeBean : challenges) {
                    final Challenge challenge = ChaiChallenge.fromChallengeBean(challengeBean);
                    final String answerText = challengeBean.getAnswer().getAnswerText();
                    if (answerText == null || answerText.length() < 1) {
                        throw new IllegalArgumentException("missing answerText for challenge '" + challenge.getChallengeText() + "'");
                    }
                    crMap.put(challenge,answerText);
                }
            }

            final Map<Challenge,String> helpdeskCrMap = new LinkedHashMap<>();
            if (helpdeskChallenges != null) {
                for (final ChallengeBean challengeBean : helpdeskChallenges) {
                    final Challenge challenge = ChaiChallenge.fromChallengeBean(challengeBean);
                    final String answerText = challengeBean.getAnswer().getAnswerText();
                    if (answerText == null || answerText.length() < 1) {
                        throw new IllegalArgumentException("missing answerText for helpdesk challenge '" + challenge.getChallengeText() + "'");
                    }
                    helpdeskCrMap.put(challenge,answerText);
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
            responseInfoBean.setTimestamp(new Date());
            return responseInfoBean;
        }
    }

    @Context
    HttpServletRequest request;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        return RestServerHelper.doHtmlRedirect();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doFormGetChallengeData(
            @QueryParam("answers") final boolean answers,
            @QueryParam("helpdesk") final boolean helpdesk,
            @QueryParam("username") final String username
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = new ServicePermissions();
            servicePermissions.setAdminOnly(false);
            servicePermissions.setAuthRequired(true);
            servicePermissions.setBlockExternal(true);
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, username);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        try {
            if (answers && !restRequestBean.getPwmApplication().getConfig().readSettingAsBoolean(PwmSetting.ENABLE_WEBSERVICES_READANSWERS)) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"retrieval of answers is not permitted"));
            }

            // gather data
            final ResponseSet responseSet;
            final ChallengeSet challengeSet;
            final ChallengeSet helpdeskChallengeSet;
            final String outputUsername;

            if (restRequestBean.getUserIdentity() == null) {
                final ChaiUser chaiUser = restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication());
                final CrService crService = restRequestBean.getPwmApplication().getCrService();
                responseSet = crService.readUserResponseSet(restRequestBean.getPwmSession().getLabel(), restRequestBean.getPwmSession().getUserInfoBean().getUserIdentity(), chaiUser);
                challengeSet = restRequestBean.getPwmSession().getUserInfoBean().getChallengeProfile().getChallengeSet();
                helpdeskChallengeSet = restRequestBean.getPwmSession().getUserInfoBean().getChallengeProfile().getHelpdeskChallengeSet();
                outputUsername = restRequestBean.getPwmSession().getUserInfoBean().getUserIdentity().getLdapProfileID();
            } else {
                final ChaiUser chaiUser = restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication(),restRequestBean.getUserIdentity());                final Locale userLocale = restRequestBean.getPwmSession().getSessionStateBean().getLocale();
                final CrService crService = restRequestBean.getPwmApplication().getCrService();
                responseSet = crService.readUserResponseSet(restRequestBean.getPwmSession().getLabel(),restRequestBean.getUserIdentity(), chaiUser);
                final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                        restRequestBean.getPwmApplication(),
                        restRequestBean.getPwmSession().getLabel(),
                        restRequestBean.getUserIdentity(),
                        chaiUser,userLocale
                );
                final ChallengeProfile challengeProfile = crService.readUserChallengeProfile(
                        restRequestBean.getPwmSession().getLabel(),
                        restRequestBean.getUserIdentity(),
                        chaiUser,
                        passwordPolicy,
                        userLocale
                );
                challengeSet = challengeProfile.getChallengeSet();
                helpdeskChallengeSet = challengeProfile.getHelpdeskChallengeSet();
                outputUsername = restRequestBean.getUserIdentity().toDelimitedKey();
            }

            // build output
            final JsonChallengesData jsonData = new JsonChallengesData();
            {
                jsonData.username = outputUsername;
                if (responseSet != null) {
                    jsonData.challenges = responseSet.asChallengeBeans(answers);
                    if (helpdesk) {
                        jsonData.helpdeskChallenges = responseSet.asHelpdeskChallengeBeans(answers);
                    }
                    jsonData.minimumRandoms = responseSet.getChallengeSet().getMinRandomRequired();
                }
                final Policy policy = new Policy();
                if (challengeSet != null) {
                    policy.challenges = challengesToBeans(challengeSet.getChallenges());
                    policy.minimumRandoms = challengeSet.getMinRandomRequired();
                }
                if (helpdeskChallengeSet != null && helpdesk) {
                    policy.helpdeskChallenges = challengesToBeans(helpdeskChallengeSet.getChallenges());
                }
                if (policy.challenges != null || policy.helpdeskChallenges != null) {
                    jsonData.policy = policy;
                }
            }

            // update statistics
            if (!restRequestBean.isExternal()) {
                StatisticsManager.incrementStat(restRequestBean.getPwmApplication(), Statistic.REST_CHALLENGES);
            }

            final RestResultBean resultBean = new RestResultBean();
            resultBean.setData(jsonData);
            return resultBean.asJsonResponse();
        } catch (PwmException e) {
            return RestResultBean.fromError(e.getErrorInformation(), restRequestBean).asJsonResponse();
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doSetChallengeDataJson(
            final JsonChallengesData jsonInput
    )
    {
        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = new ServicePermissions();
            servicePermissions.setAdminOnly(false);
            servicePermissions.setAuthRequired(true);
            servicePermissions.setBlockExternal(true);
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, jsonInput.username);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        try {
            if (!restRequestBean.getPwmSession().getSessionManager().checkPermission(restRequestBean.getPwmApplication(), Permission.SETUP_RESPONSE)) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"actor does not have required permission"));
            }

            final ChaiUser chaiUser;
            final String userGUID;
            final String csIdentifer;
            final CrService crService = restRequestBean.getPwmApplication().getCrService();
            if (restRequestBean.getUserIdentity() == null) {
                chaiUser = restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication());
                userGUID = restRequestBean.getPwmSession().getUserInfoBean().getUserGuid();
                csIdentifer = restRequestBean.getPwmSession().getUserInfoBean().getChallengeProfile().getChallengeSet().getIdentifier();
            } else {
                final UserIdentity userIdentity = restRequestBean.getUserIdentity();
                chaiUser = restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication(),userIdentity);
                userGUID = LdapOperationsHelper.readLdapGuidValue(
                        restRequestBean.getPwmApplication(),
                        restRequestBean.getPwmSession().getLabel(),
                        userIdentity,
                        false);
                final ChallengeProfile challengeProfile = crService.readUserChallengeProfile(
                        restRequestBean.getPwmSession().getLabel(),
                        userIdentity,
                        chaiUser,
                        PwmPasswordPolicy.defaultPolicy(),
                        request.getLocale());
                csIdentifer = challengeProfile.getChallengeSet().getIdentifier();
            }

            final ResponseInfoBean responseInfoBean = jsonInput.toResponseInfoBean(request.getLocale(), csIdentifer);
            crService.writeResponses(chaiUser,userGUID,responseInfoBean);

            // update statistics
            if (!restRequestBean.isExternal()) {
                StatisticsManager.incrementStat(restRequestBean.getPwmApplication(), Statistic.REST_CHALLENGES);
            }

            final String successMsg = Message.Success_SetupResponse.getLocalizedMessage(request.getLocale(),restRequestBean.getPwmApplication().getConfig());
            final RestResultBean resultBean = new RestResultBean();
            resultBean.setError(false);
            resultBean.setSuccessMessage(successMsg);
            return resultBean.asJsonResponse();
        } catch (PwmException e) {
            return RestResultBean.fromError(e.getErrorInformation(), restRequestBean).asJsonResponse();
        } catch (Exception e) {
            final String errorMsg = "unexpected error reading json input: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doDeleteChallengeData(
            @QueryParam("username") final String username
    )
    {
        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = new ServicePermissions();
            servicePermissions.setAdminOnly(false);
            servicePermissions.setAuthRequired(true);
            servicePermissions.setBlockExternal(true);
            servicePermissions.setHelpdeskPermitted(true);
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, username);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        final HelpdeskProfile helpdeskProfile = restRequestBean.getPwmSession().getSessionManager().getHelpdeskProfile(restRequestBean.getPwmApplication());
        try {
            if (restRequestBean.getUserIdentity() == null) {
                if (!restRequestBean.getPwmSession().getSessionManager().checkPermission(restRequestBean.getPwmApplication(), Permission.SETUP_RESPONSE)) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"actor does not have required permission"));
                }
            } else {
                if (helpdeskProfile == null) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"actor does not have required permission"));
                }
            }

            final ChaiUser chaiUser;
            final String userGUID;
            if (restRequestBean.getUserIdentity() == null) {
                /* clear self */
                chaiUser = restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication());
                userGUID = restRequestBean.getPwmSession().getUserInfoBean().getUserGuid();

                // mark the event log
                final UserAuditRecord auditRecord = restRequestBean.getPwmApplication().getAuditManager().createUserAuditRecord(
                        AuditEvent.CLEAR_RESPONSES,
                        restRequestBean.getPwmSession().getUserInfoBean(),
                        restRequestBean.getPwmSession()
                );
                restRequestBean.getPwmApplication().getAuditManager().submit(auditRecord);
            } else {
                /* clear 3rd party (helpdesk) */
                final boolean useProxy = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY);
                chaiUser = useProxy
                        ? restRequestBean.getPwmApplication().getProxiedChaiUser(restRequestBean.getUserIdentity())
                        : restRequestBean.getPwmSession().getSessionManager().getActor(restRequestBean.getPwmApplication(),restRequestBean.getUserIdentity());
                userGUID = LdapOperationsHelper.readLdapGuidValue(
                        restRequestBean.getPwmApplication(),
                        restRequestBean.getPwmSession().getLabel(),
                        restRequestBean.getUserIdentity(),
                        false);

                // mark the event log
                final HelpdeskAuditRecord auditRecord = restRequestBean.getPwmApplication().getAuditManager().createHelpdeskAuditRecord(
                        AuditEvent.HELPDESK_CLEAR_RESPONSES,
                        restRequestBean.getPwmSession().getUserInfoBean().getUserIdentity(),
                        null,
                        restRequestBean.getUserIdentity(),
                        restRequestBean.getPwmSession().getSessionStateBean().getSrcAddress(),
                        restRequestBean.getPwmSession().getSessionStateBean().getSrcHostname()
                );
                restRequestBean.getPwmApplication().getAuditManager().submit(auditRecord);
            }

            final CrService crService = restRequestBean.getPwmApplication().getCrService();
            crService.clearResponses(
                    restRequestBean.getPwmSession(),
                    chaiUser,
                    userGUID
            );

            // update statistics
            if (!restRequestBean.isExternal()) {
                StatisticsManager.incrementStat(restRequestBean.getPwmApplication(), Statistic.REST_CHALLENGES);
            }

            final String successMsg = Message.Success_Unknown.getLocalizedMessage(request.getLocale(),restRequestBean.getPwmApplication().getConfig());
            RestResultBean resultBean = new RestResultBean();
            resultBean.setError(false);
            resultBean.setSuccessMessage(successMsg);
            return resultBean.asJsonResponse();
        } catch (PwmException e) {
            return RestResultBean.fromError(e.getErrorInformation(), restRequestBean).asJsonResponse();
        } catch (Exception e) {
            final String errorMsg = "unexpected error delete responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }
    }

    private static List<ChallengeBean> challengesToBeans(final List<Challenge> challenges) {
        final List<ChallengeBean> returnList = new ArrayList<>();
        for (final Challenge challenge : challenges) {
            returnList.add(challenge.asChallengeBean());
        }
        return returnList;
    }
}

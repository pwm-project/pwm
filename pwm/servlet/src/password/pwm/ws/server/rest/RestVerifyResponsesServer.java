/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.Permission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/verifyresponses")
public class RestVerifyResponsesServer {
    public static class JsonPutChallengesInput implements Serializable {
        public List<ChallengeBean> challenges;
        public String username;

        public Map<Challenge,String> toCrMap()
        {
            final Map<Challenge,String> crMap = new LinkedHashMap<Challenge, String>();
            if (challenges != null) {
                for (final ChallengeBean challengeBean : challenges) {
                    if (challengeBean.getAnswer() == null) {
                        throw new IllegalArgumentException("json challenge object must include an answer object");
                    }
                    if (challengeBean.getAnswer().getAnswerText() == null) {
                        throw new IllegalArgumentException("json answer object must include answerText property");
                    }
                    final String answerText = challengeBean.getAnswer().getAnswerText();
                    final Challenge challenge = ChaiChallenge.fromChallengeBean(challengeBean);
                    crMap.put(challenge,answerText);
                }
            }

            return crMap;
        }
    }

    @Context
    HttpServletRequest request;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        final URI uri = javax.ws.rs.core.UriBuilder.fromUri("../rest.jsp?forwardedFromRestServer=true").build();
        return javax.ws.rs.core.Response.temporaryRedirect(uri).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String doSetChallengeDataJson(
            final JsonPutChallengesInput jsonInput
    )
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, true, jsonInput.username);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        try {
            if (!Permission.checkPermission(Permission.CHANGE_PASSWORD, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"actor does not have required permission"));
            }

            final ChaiUser chaiUser;
            if (restRequestBean.getUserDN() == null) {
                chaiUser = restRequestBean.getPwmSession().getSessionManager().getActor();
            } else {
                final String userDN = restRequestBean.getUserDN();
                final ChaiProvider actorProvider = restRequestBean.getPwmSession().getSessionManager().getChaiProvider();
                chaiUser = ChaiFactory.createChaiUser(userDN, actorProvider);
            }

            final ResponseSet responseSet = restRequestBean.getPwmApplication().getCrService().readUserResponseSet(restRequestBean.getPwmSession(), chaiUser);
            final boolean verified = responseSet.test(jsonInput.toCrMap());

            final String successMsg = Message.SUCCESS_UNKNOWN.getLocalizedMessage(request.getLocale(),restRequestBean.getPwmApplication().getConfig());
            RestResultBean resultBean = new RestResultBean();
            resultBean.setError(false);
            resultBean.setData(verified);
            resultBean.setSuccessMessage(successMsg);
            return resultBean.toJson();
        } catch (PwmException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        } catch (Exception e) {
            final String errorMsg = "unexpected error reading json input: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }
    }
}

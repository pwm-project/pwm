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
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.CrUtility;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/getchallenges")
public class RestGetChallengeServer {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestGetChallengeServer.class);

    public static class JsonData {
        public List<ChallengeBean> challenges;
        public List<ChallengeBean> helpdeskChallenges;
        public int minimumRandoms;
    }

    @Context
    HttpServletRequest request;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doGetChallengeData(
            @QueryParam("answers") final boolean answers,
            @QueryParam("helpdesk") final boolean helpdesk
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(request);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);

        try {
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
            RestServerHelper.initializeRestRequest(request,"");
            final boolean isExternal = RestServerHelper.determineIfRestClientIsExternal(request);

            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED);
                return RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession).toJson();
            }

            try {
                final ChaiProvider actorProvider = pwmSession.getSessionManager().getChaiProvider();
                final ChaiUser chaiUser = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), actorProvider);
                final ResponseSet responseSet = CrUtility.readUserResponseSet(pwmSession, pwmApplication,chaiUser);
                final JsonData jsonData = new JsonData();
                jsonData.challenges = responseSet.asChallengeBeans(answers);
                jsonData.minimumRandoms = responseSet.getChallengeSet().getMinRandomRequired();
                if (helpdesk) {
                    jsonData.helpdeskChallenges = responseSet.asHelpdeskChallengeBeans(answers);
                }
                RestResultBean resultBean = new RestResultBean();
                resultBean.setData(jsonData);
                return resultBean.toJson();
            } catch (PwmException e) {
                return RestResultBean.fromErrorInformation(e.getErrorInformation(), pwmApplication, pwmSession).toJson();
            }
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response for /getchallenges rest service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession).toJson();
        }
    }
}

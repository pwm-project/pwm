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
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.CrUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/clearresponses")
public class RestClearResponsesServer {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestClearResponsesServer.class);

    @Context
    HttpServletRequest request;

    public static class JsonOutput {
        public boolean success;
        public String errorMsg;
        public int errorCode;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public JsonOutput doPostClearResponses(
            final @FormParam("username") String username
    ) {
        final JsonOutput outputMap = new JsonOutput();

        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
            //final RestServerHelper.RestRequestBean restRequestBean = RestServerHelper.initializeRestRequest(request,"username");
            final boolean isExternal = RestServerHelper.determineIfRestClientIsExternal(request);

            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                outputMap.success = false;
                outputMap.errorMsg = PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo().toDebugStr();
                outputMap.errorCode = PwmError.ERROR_AUTHENTICATION_REQUIRED.getErrorCode();
                return outputMap;
            }

            try {
                if (username != null && username.length() > 0) {
                    if (!Permission.checkPermission(Permission.HELPDESK, pwmSession, pwmApplication)) {
                        outputMap.success = false;
                        outputMap.errorMsg = PwmError.ERROR_UNAUTHORIZED.toInfo().toDebugStr();
                        outputMap.errorCode = PwmError.ERROR_UNAUTHORIZED.getErrorCode();
                        return outputMap;
                    }

                    final ChaiProvider actorProvider = pwmSession.getSessionManager().getChaiProvider();
                    final ChaiUser chaiUser = ChaiFactory.createChaiUser(username, actorProvider);
                    final String userGUID = Helper.readLdapGuidValue(pwmApplication, chaiUser.getEntryDN());
                    CrUtility.clearResponses(pwmSession,pwmApplication, chaiUser, userGUID);

                    // mark the event log
                    final String message = "(" + pwmSession.getUserInfoBean().getUserID() + ")";
                    UserHistory.updateUserHistory(pwmSession, pwmApplication, chaiUser, UserHistory.Record.Event.HELPDESK_CLEAR_RESPONSES, message);

                } else {
                    CrUtility.clearResponses(
                            pwmSession,
                            pwmApplication,
                            pwmSession.getSessionManager().getActor(),
                            pwmSession.getUserInfoBean().getUserGuid()
                    );
                    UserHistory.updateUserHistory(pwmSession, pwmApplication, UserHistory.Record.Event.HELPDESK_CLEAR_RESPONSES,null);
                }
                outputMap.success = true;
                if (isExternal) {
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.REST_CLEARRESPONSE);
                }
                return outputMap;
            } catch (PwmOperationalException e) {
                outputMap.success = false;
                outputMap.errorCode = e.getError().getErrorCode();
                outputMap.errorMsg = e.getErrorInformation().getDetailedErrorMsg();
                return outputMap;
            }
        } catch (Exception e) {
            outputMap.success = false;
            outputMap.errorCode = PwmError.ERROR_UNKNOWN.getErrorCode();
            outputMap.errorMsg = "unexpected error building json response for /clearresponses rest service: " + e.getMessage();
            return outputMap;
        }
    }
}

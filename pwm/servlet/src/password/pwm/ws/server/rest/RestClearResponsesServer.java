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
import password.pwm.ContextManager;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.event.AuditRecord;
import password.pwm.i18n.Message;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.CrUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Date;

@Path("/clearresponses")
public class RestClearResponsesServer {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestClearResponsesServer.class);

    @Context
    HttpServletRequest request;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doPostClearResponses(
            final @FormParam("username") String username
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(request);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);

        try {
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
            final boolean isExternal = RestServerHelper.determineIfRestClientIsExternal(request);

            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED);
                final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
                return restResultBean.toJson();
            }

            try {
                if (username != null && username.length() > 0) {
                    if (!Permission.checkPermission(Permission.HELPDESK, pwmSession, pwmApplication)) {
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED);
                        final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
                        return restResultBean.toJson();
                    }

                    final ChaiProvider actorProvider = pwmSession.getSessionManager().getChaiProvider();
                    final ChaiUser chaiUser = ChaiFactory.createChaiUser(username, actorProvider);
                    final String userGUID = Helper.readLdapGuidValue(pwmApplication, chaiUser.getEntryDN());
                    final String userID = Helper.readLdapUserIDValue(pwmApplication, chaiUser);
                    CrUtility.clearResponses(pwmSession,pwmApplication, chaiUser, userGUID);

                    // mark the event log
                    final AuditRecord auditRecord = new AuditRecord(
                            AuditEvent.HELPDESK_CLEAR_RESPONSES,
                            pwmSession.getUserInfoBean().getUserID(),
                            pwmSession.getUserInfoBean().getUserDN(),
                            new Date(),
                            null,
                            userID,
                            chaiUser.getEntryDN(),
                            request.getRemoteAddr(),
                            request.getRemoteHost()
                            );
                    pwmApplication.getAuditManager().submitAuditRecord(auditRecord);

                } else {
                    CrUtility.clearResponses(
                            pwmSession,
                            pwmApplication,
                            pwmSession.getSessionManager().getActor(),
                            pwmSession.getUserInfoBean().getUserGuid()
                    );
                    pwmApplication.getAuditManager().submitAuditRecord(AuditEvent.HELPDESK_CLEAR_RESPONSES, pwmSession.getUserInfoBean(),pwmSession);
                }
                if (isExternal) {
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.REST_CLEARRESPONSE);
                }
                final RestResultBean restResultBean = new RestResultBean();
                restResultBean.setSuccessMessage(Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(),Message.SUCCESS_UNKNOWN,pwmApplication.getConfig()));
                return restResultBean.toJson();
            } catch (PwmOperationalException e) {
                final RestResultBean restResultBean = RestResultBean.fromErrorInformation(e.getErrorInformation(), pwmApplication, pwmSession);
                return restResultBean.toJson();
            }
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response for /clearresponses rest service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
            return restResultBean.toJson();
        }
    }
}

/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.ws.server;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmSession;
import password.pwm.http.filter.AuthenticationFilter;
import password.pwm.i18n.LocaleHelper;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.intruder.RecordType;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

public abstract class RestServerHelper {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestServerHelper.class);

    public static javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        final URI uri = javax.ws.rs.core.UriBuilder.fromUri("../rest.jsp?forwardedFromRestServer=true").build();
        return javax.ws.rs.core.Response.temporaryRedirect(uri).build();
    }

    public static RestRequestBean initializeRestRequest(
            final HttpServletRequest request,
            final ServicePermissions servicePermissions,
            final String requestedUsername
    )
            throws PwmUnrecoverableException {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
        final PwmSession pwmSession = PwmSession.getPwmSession(request);

        ServletHelper.handleRequestInitialization(request, pwmApplication, pwmSession);

        if (servicePermissions.isAuthRequired()) {
            ServletHelper.handleRequestSecurityChecks(request, pwmApplication, pwmSession);
        }

        if (pwmSession.getSessionStateBean().getLocale() == null) {
            final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
            final Locale userLocale = LocaleHelper.localeResolver(request.getLocale(), knownLocales);
            pwmSession.getSessionStateBean().setLocale(userLocale == null ? PwmConstants.DEFAULT_LOCALE : userLocale);
        }

        final StringBuilder logMsg = new StringBuilder();
        logMsg.append("REST WebService Request: ");
        logMsg.append(ServletHelper.debugHttpRequest(pwmApplication,request));
        LOGGER.debug(pwmSession,logMsg);

        try {
            handleAuthentication(request,pwmApplication,pwmSession);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.ERROR_DIRECTORY_UNAVAILABLE);
        }

        final RestRequestBean restRequestBean = new RestRequestBean();
        restRequestBean.setAuthenticated(pwmSession.getSessionStateBean().isAuthenticated());
        restRequestBean.setExternal(determineIfRestClientIsExternal(pwmSession,request));
        restRequestBean.setUserIdentity(
                lookupUsername(pwmApplication, pwmSession, restRequestBean.isExternal(), servicePermissions, requestedUsername));
        restRequestBean.setPwmApplication(pwmApplication);
        restRequestBean.setPwmSession(pwmSession);


        if (servicePermissions.isPublicDuringConfig()) {
            if (pwmApplication.getApplicationMode() == PwmApplication.MODE.NEW || pwmApplication.getApplicationMode() == PwmApplication.MODE.CONFIGURATION) {
                return restRequestBean;
            }
        }

        // check permissions
        final boolean authenticated = pwmSession.getSessionStateBean().isAuthenticated();
        if (servicePermissions.isAuthRequired()) {
            if (!authenticated) {
                throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            }
        }

        if (servicePermissions.isBlockExternal()) {
            if (restRequestBean.isExternal()) {
                final boolean allowExternal = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.ENABLE_EXTERNAL_WEBSERVICES);
                if (!allowExternal) {
                    final String errorMsg = "external web services are not enabled";
                    LOGGER.warn(pwmSession, errorMsg);
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg));
                }
            }
        }

        final boolean adminPermission;
        try {
            adminPermission = restRequestBean.getPwmSession().getSessionManager().checkPermission(pwmApplication, Permission.PWMADMIN);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
        }

        if (servicePermissions.isAdminOnly()) {
            if (!adminPermission) {
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"admin authorization is required");
                throw new PwmUnrecoverableException(errorInfo);
            }
        }

        return restRequestBean;
    }

    private static UserIdentity lookupUsername(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final boolean isExternal,
            final ServicePermissions servicePermissions,
            final String username
    )
            throws PwmUnrecoverableException
    {
        if (username == null || username.length() < 1) {
            return null;
        }

        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED));
        }

        pwmApplication.getIntruderManager().check(RecordType.USERNAME,username);

        if (isExternal) {
            try {
                if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.WEBSERVICE_THIRDPARTY)) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"authenticated user does not match thirdparty webservices query filter");
                    throw new PwmUnrecoverableException(errorInformation);
                }
            } catch (ChaiUnavailableException e) {
                throw new PwmUnrecoverableException(PwmError.ERROR_DIRECTORY_UNAVAILABLE);
            }
        }


        if (!isExternal) {
            if (servicePermissions.isHelpdeskPermitted()) {
                try {
                    if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.HELPDESK)) {
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"authenticated user does not match third-party webservices query filter");
                        throw new PwmUnrecoverableException(errorInformation);
                    }
                } catch (ChaiUnavailableException e) {
                    throw new PwmUnrecoverableException(PwmError.ERROR_DIRECTORY_UNAVAILABLE);
                }
            } else {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"web service is not permitted for internal third-party username");
                throw new PwmUnrecoverableException(errorInformation);
            }
        }


        try {
            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
            return userSearchEngine.resolveUsername(pwmSession, username, null, null);
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.ERROR_DIRECTORY_UNAVAILABLE);
        }
    }


    private static void handleAuthentication(
            final HttpServletRequest request,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, ChaiUnavailableException {
        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            return;
        }

        if (BasicAuthInfo.parseAuthHeader(pwmApplication, request) != null) {
            try {
                AuthenticationFilter.authUserUsingBasicHeader(request,
                        BasicAuthInfo.parseAuthHeader(pwmApplication, request));
            } catch (PwmOperationalException e) {
                throw new PwmUnrecoverableException(e.getErrorInformation());
            }
        }
    }

    public static boolean determineIfRestClientIsExternal(final PwmSession pwmSession, final HttpServletRequest request)
            throws PwmUnrecoverableException
    {
        final String requestClientKey = request.getHeader(PwmConstants.HTTP_HEADER_REST_CLIENT_KEY);
        if (requestClientKey == null || requestClientKey.length() < 1) {
            return true;
        }

        final String sessionClientKey = pwmSession.getRestClientKey();
        return !requestClientKey.equals(sessionClientKey);
    }

    public static void handleNonJsonErrorResult(final ErrorInformation errorInformation) {
        Response.ResponseBuilder responseBuilder = Response.serverError();
        responseBuilder.entity(errorInformation.toDebugStr() + "\n");
        throw new WebApplicationException(responseBuilder.build());
    }
}

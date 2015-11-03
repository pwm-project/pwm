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

package password.pwm.ws.server;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.ServletHelper;
import password.pwm.http.filter.AuthenticationFilter;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.svc.intruder.RecordType;
import password.pwm.util.LocaleHelper;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

public abstract class RestServerHelper {
    private static final PwmLogger LOGGER = PwmLogger.forClass(RestServerHelper.class);

    public static javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        final URI uri = javax.ws.rs.core.UriBuilder.fromUri("../reference/rest.jsp?forwardedFromRestServer=true").build();
        return javax.ws.rs.core.Response.temporaryRedirect(uri).build();
    }

    public static RestRequestBean initializeRestRequest(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final ServicePermissions servicePermissions,
            final String requestedUsername
    )
            throws PwmUnrecoverableException
    {
        final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        ServletHelper.handleRequestInitialization(pwmRequest, pwmApplication, pwmSession);

        if (servicePermissions.isAuthRequired()) {
            ServletHelper.handleRequestSecurityChecks(request, pwmApplication, pwmSession);
        }

        if (pwmSession.getSessionStateBean().getLocale() == null) {
            final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
            final Locale userLocale = LocaleHelper.localeResolver(request.getLocale(), knownLocales);
            pwmSession.getSessionStateBean().setLocale(userLocale == null ? PwmConstants.DEFAULT_LOCALE : userLocale);
        }

        pwmRequest.debugHttpRequestToLog("[REST WebService Request]");

        try {
            handleAuthentication(request,response,pwmApplication,pwmSession);
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

        if (restRequestBean.isExternal()) {
            if (servicePermissions.isBlockExternal()) {
                final boolean allowExternal = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.ENABLE_EXTERNAL_WEBSERVICES);
                if (!allowExternal) {
                    final String errorMsg = "external web services are not enabled";
                    LOGGER.warn(pwmSession, errorMsg);
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg));
                }
            }

            if (restRequestBean.isAuthenticated()) {
                if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.WEBSERVICE)) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, "authenticated user does not have external webservices permission");
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }

            final PasswordData secretKey = pwmApplication.getConfig().readSettingAsPassword(PwmSetting.WEBSERVICES_EXTERNAL_SECRET);
            if (secretKey != null) {
                final String headerName = "RestSecretKey";
                final String headerValue = pwmRequest.readHeaderValueAsString(headerName);
                if (headerValue == null || headerValue.isEmpty()) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, "request is missing security header " + headerName);
                    throw new PwmUnrecoverableException(errorInformation);
                }
                if (headerValue.equals(secretKey.getStringValue())) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED, "authenticated user does not have external webservices permission");
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
        }

        final boolean adminPermission= restRequestBean.getPwmSession().getSessionManager().checkPermission(pwmApplication, Permission.PWMADMIN);
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
            if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.WEBSERVICE_THIRDPARTY)) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"authenticated user does not match thirdparty webservices query filter");
                throw new PwmUnrecoverableException(errorInformation);
            }
        }


        if (!isExternal) {
            if (servicePermissions.isHelpdeskPermitted()) {
                final HelpdeskProfile helpdeskProfile = pwmSession.getSessionManager().getHelpdeskProfile(pwmApplication);
                if (helpdeskProfile == null) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"authenticated non-external request for third-party does not match third-party webservices query filter");
                    throw new PwmUnrecoverableException(errorInformation);
                }
            } else {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"web service is not permitted for internal third-party username");
                throw new PwmUnrecoverableException(errorInformation);
            }
        }


        try {
            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, pwmSession.getLabel());
            return userSearchEngine.resolveUsername(username, null, null);
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.ERROR_DIRECTORY_UNAVAILABLE);
        }
    }


    private static void handleAuthentication(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, ChaiUnavailableException {
        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            return;
        }

        final PwmRequest pwmRequest = PwmRequest.forRequest(request,response);
        new AuthenticationFilter.BasicFilterAuthenticationProvider().attemptAuthentication(pwmRequest);
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

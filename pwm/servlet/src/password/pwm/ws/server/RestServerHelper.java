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

package password.pwm.ws.server;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.UserSearchEngine;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

public abstract class RestServerHelper {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestServerHelper.class);

    public static enum ServiceType {
        AUTH_REQUIRED, NORMAL, PUBLIC,
    };

    public static javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        final URI uri = javax.ws.rs.core.UriBuilder.fromUri("../rest.jsp?forwardedFromRestServer=true").build();
        return javax.ws.rs.core.Response.temporaryRedirect(uri).build();
    }

    public static RestRequestBean initializeRestRequest(
            final HttpServletRequest request,
            final boolean requiresAuthentication,
            final String requestedUsername
    )
            throws PwmUnrecoverableException
    {
        return initializeRestRequest(
                request,
                requiresAuthentication ? ServiceType.AUTH_REQUIRED : ServiceType.NORMAL,
                requestedUsername);
    }

    public static RestRequestBean initializeRestRequest(
            final HttpServletRequest request,
            final ServiceType serviceType,
            final String requestedUsername
    )
            throws PwmUnrecoverableException {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
        final PwmSession pwmSession = PwmSession.getPwmSession(request);

        ServletHelper.handleRequestInitialization(request, pwmApplication, pwmSession);

        if (serviceType != ServiceType.PUBLIC) {
            ServletHelper.handleRequestSecurityChecks(request, pwmApplication, pwmSession);
        }

        if (pwmSession.getSessionStateBean().getLocale() == null) {
            final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
            final Locale userLocale = Helper.localeResolver(request.getLocale(), knownLocales);
            pwmSession.getSessionStateBean().setLocale(userLocale == null ? PwmConstants.DEFAULT_LOCALE : userLocale);
        }

        final StringBuilder logMsg = new StringBuilder();
        logMsg.append("REST WebService Request: ");
        logMsg.append(ServletHelper.debugHttpRequest(request));
        LOGGER.debug(pwmSession,logMsg);

        try {
            handleAuthentication(request,pwmSession);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.ERROR_DIRECTORY_UNAVAILABLE);
        }

        final RestRequestBean restRequestBean = new RestRequestBean();
        restRequestBean.setAuthenticated(pwmSession.getSessionStateBean().isAuthenticated());
        restRequestBean.setExternal(determineIfRestClientIsExternal(request, pwmSession));
        restRequestBean.setUserDN(lookupUsername(pwmApplication, pwmSession, restRequestBean.isExternal(), requestedUsername));
        restRequestBean.setPwmApplication(pwmApplication);
        restRequestBean.setPwmSession(pwmSession);

        if (serviceType == ServiceType.AUTH_REQUIRED) {
            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            }
        }

        if (serviceType != ServiceType.PUBLIC) {
            if (restRequestBean.isExternal()) {
                final boolean allowExternal = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.ENABLE_EXTERNAL_WEBSERVICES);
                if (!allowExternal) {
                    final String errorMsg = "external web services are not enabled";
                    LOGGER.warn(pwmSession, errorMsg);
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg));
                }

            }
        }
        return restRequestBean;
    }

    private static String lookupUsername(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final boolean isExternal,
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

        pwmApplication.getIntruderManager().check(username,null,null);

        try {
            if (isExternal) {
                if (!Permission.checkPermission(Permission.WEBSERVICE_THIRDPARTY,pwmSession,pwmApplication)) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"authenticated user does not match thirdparty webservices query filter");
                    throw new PwmUnrecoverableException(errorInformation);
                }
            } else {
                if (!Permission.checkPermission(Permission.HELPDESK,pwmSession,pwmApplication)) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"authenticated user does not match third-party webservices query filter");
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.ERROR_DIRECTORY_UNAVAILABLE);
        }

        try {
            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
            final ChaiProvider chaiProvider = pwmSession.getSessionManager().getChaiProvider();
            //see if we need to a contextless search.
            if (userSearchEngine.checkIfStringIsDN(pwmSession, username)) {
                final ChaiUser theUser = ChaiFactory.createChaiUser(username,chaiProvider);
                pwmApplication.getIntruderManager().check(null,theUser.getEntryDN(),null);
                if (theUser.isValid()) {
                    return theUser.getEntryDN();
                } else {
                    throw new PwmUnrecoverableException(PwmError.ERROR_CANT_MATCH_USER);
                }
            } else {
                final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
                searchConfiguration.setUsername(username);
                searchConfiguration.setChaiProvider(chaiProvider);
                final ChaiUser theUser = userSearchEngine.performSingleUserSearch(pwmSession, searchConfiguration);
                return theUser.readCanonicalDN();
            }
        } catch (ChaiOperationException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage()));
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(PwmError.ERROR_DIRECTORY_UNAVAILABLE);
        }
    }


    private static void handleAuthentication(HttpServletRequest request, PwmSession pwmSession)
            throws PwmUnrecoverableException, ChaiUnavailableException {
        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            return;
        }

        if (BasicAuthInfo.parseAuthHeader(request) != null) {
            try {
                AuthenticationFilter.authUserUsingBasicHeader(request, BasicAuthInfo.parseAuthHeader(request));
            } catch (PwmOperationalException e) {
                throw new PwmUnrecoverableException(e.getErrorInformation());
            }
        }
    }

    public static boolean determineIfRestClientIsExternal(HttpServletRequest request, PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);

        boolean requestHasCorrectID = false;

        try {
            Validator.validatePwmFormID(request);
            requestHasCorrectID = true;
        } catch (PwmUnrecoverableException e) {
            if (e.getError() == PwmError.ERROR_INVALID_FORMID) {
                requestHasCorrectID = false;
            } else {
                throw e;
            }
        }

        return !requestHasCorrectID;
    }

    public static String outputJsonErrorResult(final ErrorInformation errorInformation, HttpServletRequest request) {
        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            return RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession).toJson();
        } catch (Exception e) {
            RestResultBean restRequestBean = new RestResultBean();
            restRequestBean.setError(true);
            restRequestBean.setErrorCode(errorInformation.getError().getErrorCode());
            return restRequestBean.toJson();
        }
    }

    public static void handleNonJsonErrorResult(final ErrorInformation errorInformation) {
        Response.ResponseBuilder responseBuilder = Response.serverError();
        responseBuilder.entity(errorInformation.toDebugStr());
        throw new WebApplicationException(responseBuilder.build());
    }
}

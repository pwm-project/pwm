/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.http.state;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.http.IdleTimeoutCalculator;
import password.pwm.http.PwmHttpResponseWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmResponse;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.Date;
import java.util.concurrent.TimeUnit;

class CryptoRequestLoginImpl implements SessionLoginProvider {
    private static final PwmLogger LOGGER = PwmLogger.forClass(CryptoRequestLoginImpl.class);

    private static final PwmResponse.CookiePath COOKIE_PATH = PwmHttpResponseWrapper.CookiePath.Application;
    private String cookieName = "SESSION";

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        cookieName = pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_LOGIN_NAME);
    }

    @Override
    public void clearLoginSession(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        pwmRequest.getPwmResponse().removeCookie(cookieName, COOKIE_PATH);
    }

    @Override
    public void saveLoginSessionState(final PwmRequest pwmRequest) {
        try {
            final LoginInfoBean loginInfoBean = pwmRequest.getPwmSession().getLoginInfoBean();
            loginInfoBean.setReqTime(new Date());

            pwmRequest.getPwmResponse().writeEncryptedCookie(
                    cookieName,
                    loginInfoBean,
                    COOKIE_PATH
            );

            LOGGER.trace(pwmRequest, "wrote LoginInfoBean=" + loginInfoBean.toDebugString());
        } catch (PwmUnrecoverableException e) {
            final String errorMsg = "unexpected error writing login cookie to response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            LOGGER.error(pwmRequest, errorInformation);
        }
    }

    @Override
    public void readLoginSessionState(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final LoginInfoBean remoteLoginCookie;
        try {
            remoteLoginCookie = pwmRequest.readEncryptedCookie(cookieName, LoginInfoBean.class);
        } catch (PwmUnrecoverableException e) {
            final String errorMsg = "unexpected error reading login cookie, will clear and ignore; error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            LOGGER.error(pwmRequest, errorInformation);
            clearLoginSession(pwmRequest);
            return;
        }

        if (remoteLoginCookie != null) {
            try {
                try {
                    checkIfRemoteLoginCookieIsValid(pwmRequest, remoteLoginCookie);
                } catch (PwmOperationalException e) {
                    LOGGER.warn(pwmRequest, e.getErrorInformation().toDebugStr());
                    clearLoginSession(pwmRequest);
                    return;
                }

                checkIfLoginCookieIsForeign(pwmRequest, remoteLoginCookie);

                importRemoteCookie(pwmRequest, remoteLoginCookie);
            } catch (Exception e) {
                final String errorMsg = "unexpected error authenticating using crypto session cookie: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                LOGGER.error(pwmRequest, errorInformation);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }
    }


    private static void importRemoteCookie(
            final PwmRequest pwmRequest,
            final LoginInfoBean remoteLoginCookie
    ) throws PwmUnrecoverableException
    {
        if (remoteLoginCookie == null) {
            return;
        }

        final LoginInfoBean localLoginCookie = pwmRequest.getPwmSession().getLoginInfoBean();

        if (remoteLoginCookie.isAuthenticated()) {

            if (localLoginCookie.isAuthenticated()) {
                // should never get here unless one of container session and app session key are swapped between users.
                final UserIdentity remoteIdentity = remoteLoginCookie.getUserIdentity();
                final UserIdentity localIdentity = localLoginCookie.getUserIdentity();
                if (remoteIdentity != null && localIdentity != null && !remoteIdentity.equals(localIdentity)) {
                    throw new PwmUnrecoverableException(
                            new ErrorInformation(PwmError.ERROR_BAD_SESSION,"remote and local session identities differ")
                    );
                }
            } else {
                LOGGER.debug(pwmRequest, "triggering authentication because request contains an authenticated session but local session is unauthenticated");
                final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                        pwmRequest.getPwmApplication(),
                        pwmRequest.getPwmSession(),
                        remoteLoginCookie.getAuthSource()
                );
                try {
                    if (remoteLoginCookie.getUserIdentity() == null) {
                        sessionAuthenticator.authUserWithUnknownPassword(
                                remoteLoginCookie.getUserIdentity(),
                                remoteLoginCookie.getType()
                        );

                    } else {
                        sessionAuthenticator.authenticateUser(
                                remoteLoginCookie.getUserIdentity(),
                                remoteLoginCookie.getUserCurrentPassword()
                        );
                    }
                    remoteLoginCookie.getAuthFlags().add(AuthenticationType.AUTH_FROM_REQ_COOKIE);
                    LOGGER.debug(pwmRequest, "logged in using encrypted request cookie = " + JsonUtil.serialize(remoteLoginCookie));
                } catch (Exception e) {
                    final String errorMsg = "unexpected error reading session cookie: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                    LOGGER.error(pwmRequest, errorInformation);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
        }

        if (pwmRequest.getConfig().isDevDebugMode()) {
            LOGGER.trace(pwmRequest, "imported LoginInfoBean=" + remoteLoginCookie.toDebugString());
        }
        pwmRequest.getPwmSession().setLoginInfoBean(remoteLoginCookie);
    }

    private static void checkIfRemoteLoginCookieIsValid(
            final PwmRequest pwmRequest,
            final LoginInfoBean loginInfoBean
    )
            throws PwmOperationalException
    {
        if (loginInfoBean.isAuthenticated() && loginInfoBean.getAuthTime() == null) {
            final String errorMsg = "decrypted login cookie does not specify a local auth time";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        if (loginInfoBean.getAuthTime() != null) {
            final long sessionMaxSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.SESSION_MAX_SECONDS);
            final TimeDuration sessionTotalAge = TimeDuration.fromCurrent(loginInfoBean.getAuthTime());
            final TimeDuration sessionMaxAge = new TimeDuration(sessionMaxSeconds, TimeUnit.SECONDS);
            if (sessionTotalAge.isLongerThan(sessionMaxAge)) {
                final String errorMsg = "decrypted login cookie age ("
                        + sessionTotalAge.asCompactString()
                        + ") is older than max session seconds ("
                        + sessionMaxAge.asCompactString()
                        + ")";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }
        if (loginInfoBean.getReqTime() == null) {
            final String errorMsg = "decrypted login cookie does not specify a issue time";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
        {
            final TimeDuration loginCookieIssueAge = TimeDuration.fromCurrent(loginInfoBean.getReqTime());
            final TimeDuration maxIdleDuration = IdleTimeoutCalculator.idleTimeoutForRequest(pwmRequest);
            if (loginCookieIssueAge.isLongerThan(maxIdleDuration)) {
                final String errorMsg = "decrypted login cookie issue time ("
                        + loginCookieIssueAge.asCompactString()
                        + ") is older than max idle seconds ("
                        + maxIdleDuration.asCompactString()
                        + ")";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }
    }

    private static void checkIfLoginCookieIsForeign(final PwmRequest pwmRequest, final LoginInfoBean remoteLoginInfoBean) throws PwmUnrecoverableException {
        final String remoteGuid = remoteLoginInfoBean.getGuid();
        final String localGuid = pwmRequest.getPwmSession().getLoginInfoBean().getGuid();
        if (remoteGuid != null && !remoteGuid.equals(localGuid)) {
            final String logMsg = "login cookie session was generated by a foreign instance, seen login cookie value = "
                    + remoteLoginInfoBean.toDebugString();
            StatisticsManager.incrementStat(pwmRequest.getPwmApplication(), Statistic.FOREIGN_SESSIONS_ACCEPTED);
            LOGGER.trace(pwmRequest, logMsg);
        }
    }
}

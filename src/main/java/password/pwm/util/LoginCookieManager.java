package password.pwm.util;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.LoginInfoBean;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LoginCookieManager {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LoginCookieManager.class);

    private LoginCookieManager() {
    }

    public static void clearLoginCookie(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final String loginCookieName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_LOGIN_NAME);
        if (loginCookieName == null || loginCookieName.isEmpty() ) {
            return;
        }

        pwmRequest.getPwmResponse().removeCookie(loginCookieName, makeCookiePath(pwmRequest));
    }

    public static void writeLoginCookieToResponse(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final String loginCookieName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_LOGIN_NAME);
        if (loginCookieName == null || loginCookieName.isEmpty() ) {
            return;
        }

        if (!pwmRequest.isAuthenticated() || !pwmRequest.getURL().isPrivateUrl()) {
            return;
        }

        pwmRequest.getPwmResponse().writeEncryptedCookie(
                loginCookieName,
                LoginCookieManager.LoginCookieBean.fromSession(
                        pwmRequest.getPwmApplication(),
                        pwmRequest.getPwmSession().getLoginInfoBean(),
                        pwmRequest.getUserInfoIfLoggedIn()
                ),
                makeCookiePath(pwmRequest)
        );

    }

    private static String makeCookiePath(final PwmRequest pwmRequest) {
        return pwmRequest.getContextPath()  + PwmConstants.URL_PREFIX_PRIVATE;
    }

    public static void readLoginInfoCookie(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final String loginCookieName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_LOGIN_NAME);
        if (loginCookieName == null || loginCookieName.isEmpty() ) {
            return;
        }

        final LoginCookieBean loginCookieBean;
        try {
            loginCookieBean = pwmRequest.readEncryptedCookie(loginCookieName, LoginCookieBean.class);
        } catch (PwmUnrecoverableException e) {
            final String errorMsg = "unexpected error reading login cookie, will clear and ignore; error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            LOGGER.error(pwmRequest, errorInformation);
            clearLoginCookie(pwmRequest);
            return;
        }

        if (loginCookieBean != null) {
            if (!pwmRequest.getPwmSession().getSessionStateBean().isAuthenticated()) {
                try {
                    final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                            pwmRequest.getPwmApplication(),
                            pwmRequest.getPwmSession(),
                            PwmAuthenticationSource.LOGIN_COOKIE
                    );

                    if (!checkIfLoginCookieIsValid(pwmRequest, loginCookieBean)) {
                        clearLoginCookie(pwmRequest);
                        return;
                    }

                    if (loginCookieBean.getPassword() == null) {
                        sessionAuthenticator.authUserWithUnknownPassword(
                                loginCookieBean.getUserIdentity(),
                                loginCookieBean.getAuthenticationType()
                        );

                    } else {
                        sessionAuthenticator.authenticateUser(
                                loginCookieBean.getUserIdentity(),
                                new PasswordData(loginCookieBean.getPassword())
                        );
                    }

                    postLoginTasks(pwmRequest, loginCookieBean);

                } catch (Exception e) {
                    final String errorMsg = "unexpected error authenticating using login cookie: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                    LOGGER.error(pwmRequest, errorInformation);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
        }
    }

    private static void postLoginTasks(
            final PwmRequest pwmRequest,
            final LoginCookieBean loginCookieBean
    ) {
        pwmRequest.getPwmSession().getLoginInfoBean().setLocalAuthTime(loginCookieBean.getLocalAuthTime());
    }

    private static boolean checkIfLoginCookieIsValid(
            final PwmRequest pwmRequest,
            final LoginCookieBean loginCookieBean
    ) {
        if (loginCookieBean.getUserIdentity() == null) {
            LOGGER.warn(pwmRequest, "decrypted login cookie does not specify a user");
            return false;
        }
        if (loginCookieBean.getLocalAuthTime() == null) {
            LOGGER.warn(pwmRequest, "decrypted login cookie does not specify a local auth time");
            return false;
        }
        {
            final long sessionMaxSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.SESSION_MAX_SECONDS);
            final TimeDuration sessionTotalAge = TimeDuration.fromCurrent(loginCookieBean.getLocalAuthTime());
            final TimeDuration sessionMaxAge = new TimeDuration(sessionMaxSeconds, TimeUnit.SECONDS);
            if (sessionTotalAge.isLongerThan(sessionMaxAge)) {
                LOGGER.warn(pwmRequest, "decrypted login cookie age ("
                        + sessionTotalAge.asCompactString()
                        + ") is older than max session seconds ("
                        + sessionMaxAge.asCompactString()
                        + ")"
                );
                return false;
            }
        }
        if (loginCookieBean.getIssueTimestamp() == null) {
            LOGGER.warn(pwmRequest, "decrypted login cookie does not specify a issue time");
            return false;
        }
        {
            final long sessionMaxIdleSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);
            final TimeDuration loginCookieIssueAge = TimeDuration.fromCurrent(loginCookieBean.getIssueTimestamp());
            final TimeDuration maxIdleDuration = new TimeDuration(sessionMaxIdleSeconds, TimeUnit.SECONDS);
            if (loginCookieIssueAge.isLongerThan(maxIdleDuration)) {
                LOGGER.warn(pwmRequest, "decrypted login cookie issue time ("
                                + loginCookieIssueAge.asCompactString()
                                + ") is older than max session seconds ("
                                + maxIdleDuration.asCompactString()
                                + ")"
                );
                return false;
            }
        }

        return true;
    }


    public static class LoginCookieBean implements Serializable {
        private UserIdentity userIdentity;
        private String password;

        private AuthenticationType authenticationType = AuthenticationType.UNAUTHENTICATED;
        private List<AuthenticationType> authenticationFlags = new ArrayList<>();

        private Date localAuthTime;
        private Date issueTimestamp;
        private String issueInstanceID;

        public UserIdentity getUserIdentity() {
            return userIdentity;
        }

        public String getPassword() {
            return password;
        }

        public AuthenticationType getAuthenticationType() {
            return authenticationType;
        }

        public List<AuthenticationType> getAuthenticationFlags() {
            return authenticationFlags;
        }

        public Date getLocalAuthTime() {
            return localAuthTime;
        }

        public Date getIssueTimestamp() {
            return issueTimestamp;
        }

        public String getIssueInstanceID() {
            return issueInstanceID;
        }

        public static LoginCookieBean fromSession(
                final PwmApplication pwmApplication,
                final LoginInfoBean loginInfoBean,
                final UserIdentity loginIdentity
        )
                throws PwmUnrecoverableException
        {
            final LoginCookieBean loginCookieBean = new LoginCookieBean();
            loginCookieBean.userIdentity = loginIdentity;
            loginCookieBean.password = loginInfoBean.getUserCurrentPassword() == null
                    ? null
                    : loginInfoBean.getUserCurrentPassword().getStringValue();
            loginCookieBean.authenticationType = loginInfoBean.getAuthenticationType();
            loginCookieBean.authenticationFlags = loginInfoBean.getAuthenticationFlags();
            loginCookieBean.localAuthTime = loginInfoBean.getLocalAuthTime();
            loginCookieBean.issueTimestamp = new Date();
            loginCookieBean.issueInstanceID = pwmApplication.getInstanceID();
            return loginCookieBean;
        }
    }
}

package password.pwm.util;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmHttpResponseWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.bean.LoginInfoBean;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LoginCookieManager implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LoginCookieManager.class);

    private Settings settings = new Settings(false,"SESSION");

    @Override
    public STATUS status() {
        return settings != null && settings.isEnabled()
                ? STATUS.OPEN
                : STATUS.CLOSED;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        settings = Settings.fromConfiguration(pwmApplication.getConfig());
    }

    @Override
    public void close() {

    }

    @Override
    public List<HealthRecord> healthCheck() {
        return null;
    }

    @Override
    public ServiceInfo serviceInfo() {
        return null;
    }

    public void clearLoginCookie(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        if (!settings.isEnabled()) {
            return;
        }

        pwmRequest.getPwmResponse().removeCookie(settings.getCookieName(), PwmHttpResponseWrapper.CookiePath.Application);
    }

    public void writeLoginCookieToResponse(final PwmRequest pwmRequest) {
        if (!settings.isEnabled()) {
            return;
        }

        try {
            pwmRequest.getPwmResponse().writeEncryptedCookie(
                    settings.getCookieName(),
                    LoginCookieManager.LoginCookieBean.fromSession(
                            pwmRequest.getPwmApplication(),
                            pwmRequest.getPwmSession().getLoginInfoBean(),
                            pwmRequest.getUserInfoIfLoggedIn()
                    ),
                    PwmHttpResponseWrapper.CookiePath.Application
            );
        } catch (PwmUnrecoverableException e) {
            final String errorMsg = "unexpected error writing login cookie to response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            LOGGER.error(pwmRequest, errorInformation);
        }
    }

    public void readLoginInfoCookie(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        if (!settings.isEnabled()) {
            return;
        }


        final LoginCookieBean loginCookieBean;
        try {
            loginCookieBean = pwmRequest.readEncryptedCookie(settings.getCookieName(), LoginCookieBean.class);
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
                    try {
                        checkIfLoginCookieIsValid(pwmRequest, loginCookieBean);
                    } catch (PwmOperationalException e) {
                        LOGGER.warn(pwmRequest, e.getErrorInformation().toDebugStr());
                        clearLoginCookie(pwmRequest);
                        return;
                    }

                    checkIfLoginCookieIsForeign(pwmRequest, loginCookieBean);

                    final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                            pwmRequest.getPwmApplication(),
                            pwmRequest.getPwmSession(),
                            PwmAuthenticationSource.LOGIN_COOKIE
                    );

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
        final LoginInfoBean loginInfoBean = pwmRequest.getPwmSession().getLoginInfoBean();
        loginInfoBean.setAuthTime(loginCookieBean.getLocalAuthTime());
        loginInfoBean.setGuid(loginCookieBean.getGuid());
        loginInfoBean.setPostReqCounter(loginCookieBean.getpC());
    }

    private static void checkIfLoginCookieIsValid(
            final PwmRequest pwmRequest,
            final LoginCookieBean loginCookieBean
    )
            throws PwmOperationalException
    {
        if (loginCookieBean.getUserIdentity() == null) {
            final String errorMsg = "decrypted login cookie does not specify a user";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        if (loginCookieBean.getLocalAuthTime() == null) {
            final String errorMsg = "decrypted login cookie does not specify a local auth time";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
        {
            final long sessionMaxSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.SESSION_MAX_SECONDS);
            final TimeDuration sessionTotalAge = TimeDuration.fromCurrent(loginCookieBean.getLocalAuthTime());
            final TimeDuration sessionMaxAge = new TimeDuration(sessionMaxSeconds, TimeUnit.SECONDS);
            if (sessionTotalAge.isLongerThan(sessionMaxAge)) {
                final String errorMsg = "decrypted login cookie age ("
                        + sessionTotalAge.asCompactString()
                        + ") is older than max session seconds ("
                        + sessionMaxAge.asCompactString()
                        + ")";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }
        if (loginCookieBean.getIssueTimestamp() == null) {
            final String errorMsg = "decrypted login cookie does not specify a issue time";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
        {
            final long sessionMaxIdleSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);
            final TimeDuration loginCookieIssueAge = TimeDuration.fromCurrent(loginCookieBean.getIssueTimestamp());
            final TimeDuration maxIdleDuration = new TimeDuration(sessionMaxIdleSeconds, TimeUnit.SECONDS);
            if (loginCookieIssueAge.isLongerThan(maxIdleDuration)) {
                final String errorMsg = "decrypted login cookie issue time ("
                        + loginCookieIssueAge.asCompactString()
                        + ") is older than max session seconds ("
                        + maxIdleDuration.asCompactString()
                        + ")";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }
    }

    private static void checkIfLoginCookieIsForeign(final PwmRequest pwmRequest, final LoginCookieBean loginCookieBean)
    {
        final String cookieInstanceNonce = loginCookieBean.getInstanceNonce();
        if (cookieInstanceNonce != null && !cookieInstanceNonce.equals(pwmRequest.getPwmApplication().getInstanceNonce())) {
            final LoginCookieBean debugLoginCookieBean = JsonUtil.cloneUsingJson(loginCookieBean, LoginCookieBean.class);
            debugLoginCookieBean.p = PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;

            final String logMsg = "login cookie session was generated by a foreign instance, seen login cookie value = "
                    + JsonUtil.serialize(debugLoginCookieBean);
            StatisticsManager.incrementStat(pwmRequest.getPwmApplication(), Statistic.FOREIGN_SESSIONS_ACCEPTED);
            LOGGER.trace(pwmRequest, logMsg);
        }
    }


    /**
     * Serialized cookie for cross-server authentication handling.  field names are short to help with cookie length
     */
    public static class LoginCookieBean implements Serializable {
        private String g;
        private UserIdentity u;
        private String p;

        private AuthenticationType aT = AuthenticationType.UNAUTHENTICATED;
        private List<AuthenticationType> aF = new ArrayList<>();

        private Date t;
        private Date i;
        private String n;

        private int pC;

        public String getGuid() {
            return g;
        }

        public UserIdentity getUserIdentity() {
            return u;
        }

        public String getPassword() {
            return p;
        }

        public AuthenticationType getAuthenticationType() {
            return aT;
        }

        public List<AuthenticationType> getAuthenticationFlags() {
            return aF;
        }

        public Date getLocalAuthTime() {
            return t;
        }

        public Date getIssueTimestamp() {
            return i;
        }

        public String getInstanceNonce() {
            return n;
        }

        public int getpC() {
            return pC;
        }

        public void setpC(int pC) {
            this.pC = pC;
        }

        public static LoginCookieBean fromSession(
                final PwmApplication pwmApplication,
                final LoginInfoBean loginInfoBean,
                final UserIdentity loginIdentity
        )
                throws PwmUnrecoverableException
        {
            final LoginCookieBean loginCookieBean = new LoginCookieBean();
            loginCookieBean.u = loginIdentity;
            loginCookieBean.p = loginInfoBean.getUserCurrentPassword() == null
                    ? null
                    : loginInfoBean.getUserCurrentPassword().getStringValue();
            loginCookieBean.aT = loginInfoBean.getAuthenticationType();
            loginCookieBean.aF = loginInfoBean.getAuthenticationFlags();
            loginCookieBean.t = loginInfoBean.getAuthTime();
            loginCookieBean.i = new Date();
            loginCookieBean.n = pwmApplication.getInstanceNonce();
            loginCookieBean.g = loginInfoBean.getGuid();
            loginCookieBean.pC = loginInfoBean.getPostReqCounter();
            return loginCookieBean;
        }
    }

    private static class Settings implements Serializable {
        final private boolean enabled;
        final private String cookieName;

        public Settings(boolean enabled, String cookieName) {
            this.enabled = enabled;
            this.cookieName = cookieName;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getCookieName() {
            return cookieName;
        }

        static Settings fromConfiguration(final Configuration configuration) {
            final String loginCookieName = configuration.readAppProperty(AppProperty.HTTP_COOKIE_LOGIN_NAME);
            final boolean enabled = loginCookieName != null  && !loginCookieName.isEmpty() &&
                    configuration.readSettingAsBoolean(PwmSetting.SECURITY_ENABLE_LOGIN_COOKIE);

            return new Settings(enabled, loginCookieName);

        }
    }
}

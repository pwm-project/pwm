package password.pwm.http.filter;

import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.ServletHelper;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

public class ConfigAccessFilter extends AbstractPwmFilter {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ConfigAccessFilter.class);


    @Override
    void processFilter(PwmRequest pwmRequest, PwmFilterChain filterChain) throws PwmException, IOException, ServletException {
        final PwmApplication.MODE appMode = pwmRequest.getPwmApplication().getApplicationMode();
        if (appMode == PwmApplication.MODE.NEW || appMode == PwmApplication.MODE.CONFIGURATION) {
            filterChain.doFilter();
            return;
        }

        final ConfigManagerBean configManagerBean = pwmRequest.getPwmSession().getConfigManagerBean();
        if (!checkAuthentication(pwmRequest, configManagerBean)) {
            filterChain.doFilter();
        }
    }

    static boolean checkAuthentication(
            final PwmRequest pwmRequest,
            final ConfigManagerBean configManagerBean
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ConfigurationReader runningConfigReader = ContextManager.getContextManager(pwmRequest.getHttpServletRequest().getSession()).getConfigReader();
        final StoredConfigurationImpl storedConfig = runningConfigReader.getStoredConfiguration();

        boolean authRequired = false;
        if (storedConfig.hasPassword()) {
            authRequired = true;
        }

        if (PwmApplication.MODE.RUNNING == pwmRequest.getPwmApplication().getApplicationMode()) {
            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            }

            if (!pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN)) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED);
                pwmRequest.respondWithError(errorInformation);
                return true;
            }

            if (pwmSession.getLoginInfoBean().getAuthenticationType() != AuthenticationType.AUTHENTICATED) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED,
                        "Username/Password authentication is required to edit configuration.  This session has not been authenticated using a user password (SSO or other method used)."));
            }
        }

        if (PwmApplication.MODE.CONFIGURATION != pwmRequest.getPwmApplication().getApplicationMode()) {
            authRequired = true;
        }

        if (!authRequired) {
            return false;
        }

        if (!storedConfig.hasPassword()) {
            final String errorMsg = "config file does not have a configuration password";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg,new String[]{errorMsg});
            pwmRequest.respondWithError(errorInformation, true);
            return true;
        }

        if (configManagerBean.isPasswordVerified()) {
            return false;
        }

        String persistentLoginValue = null;
        boolean persistentLoginAccepted = false;
        boolean persistentLoginEnabled = false;
        if (pwmRequest.getConfig().isDefaultValue(PwmSetting.PWM_SECURITY_KEY)) {
            LOGGER.debug(pwmRequest, "security not available, persistent login not possible.");
        } else {
            persistentLoginEnabled = true;
            final PwmSecurityKey securityKey = pwmRequest.getConfig().getSecurityKey();

            if (PwmApplication.MODE.RUNNING == pwmRequest.getPwmApplication().getApplicationMode()) {
                persistentLoginValue = SecureEngine.hash(
                        storedConfig.readConfigProperty(StoredConfigurationImpl.ConfigProperty.PROPERTY_KEY_PASSWORD_HASH)
                                + pwmSession.getUserInfoBean().getUserIdentity().toDelimitedKey(),
                        PwmHashAlgorithm.SHA512);

            } else {
                persistentLoginValue = SecureEngine.hash(
                        storedConfig.readConfigProperty(StoredConfigurationImpl.ConfigProperty.PROPERTY_KEY_PASSWORD_HASH),
                        PwmHashAlgorithm.SHA512);
            }

            {
                final String cookieStr = ServletHelper.readCookie(
                        pwmRequest.getHttpServletRequest(),
                        PwmConstants.COOKIE_PERSISTENT_CONFIG_LOGIN
                );
                if (securityKey != null && cookieStr != null && !cookieStr.isEmpty()) {
                    try {
                        final String jsonStr = pwmApplication.getSecureService().decryptStringValue(cookieStr);
                        final PersistentLoginInfo persistentLoginInfo = JsonUtil.deserialize(jsonStr, PersistentLoginInfo.class);
                        if (persistentLoginInfo != null && persistentLoginValue != null) {
                            if (persistentLoginInfo.getExpireDate().after(new Date())) {
                                if (persistentLoginValue.equals(persistentLoginInfo.getPassword())) {
                                    persistentLoginAccepted = true;
                                    LOGGER.debug(pwmRequest, "accepting persistent config login from cookie (expires "
                                                    + PwmConstants.DEFAULT_DATETIME_FORMAT.format(persistentLoginInfo.getExpireDate())
                                                    + ")"
                                    );
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error(pwmRequest, "error examining persistent config login cookie: " + e.getMessage());
                    }
                    if (!persistentLoginAccepted) {
                        Cookie removalCookie = new Cookie(PwmConstants.COOKIE_PERSISTENT_CONFIG_LOGIN, null);
                        removalCookie.setMaxAge(0);
                        pwmRequest.getPwmResponse().addCookie(removalCookie);
                        LOGGER.debug(pwmRequest, "removing non-working persistent config login cookie");
                    }
                }
            }
        }


        final String password = pwmRequest.readParameterAsString("password");
        boolean passwordAccepted = false;
        if (!persistentLoginAccepted) {
            if (password != null && password.length() > 0) {
                if (storedConfig.verifyPassword(password)) {
                    passwordAccepted = true;
                    LOGGER.trace(pwmRequest, "valid configuration password accepted");
                } else{
                    LOGGER.trace(pwmRequest, "configuration password is not correct");
                    pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                    pwmApplication.getIntruderManager().mark(RecordType.USERNAME, PwmConstants.CONFIGMANAGER_INTRUDER_USERNAME, pwmSession.getLabel());
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD);
                    pwmRequest.setResponseError(errorInformation);
                }
            }
        }

        final int persistentSeconds = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_MAX_PERSISTENT_LOGIN_SECONDS));
        if ((persistentLoginAccepted || passwordAccepted)) {
            configManagerBean.setPasswordVerified(true);
            pwmApplication.getIntruderManager().convenience().clearAddressAndSession(pwmSession);
            pwmApplication.getIntruderManager().clear(RecordType.USERNAME, PwmConstants.CONFIGMANAGER_INTRUDER_USERNAME);
            if (persistentLoginEnabled && !persistentLoginAccepted && "on".equals(pwmRequest.readParameterAsString("remember"))) {
                if (persistentSeconds > 0) {
                    final Date expirationDate = new Date(System.currentTimeMillis() + (persistentSeconds * 1000));
                    final PersistentLoginInfo persistentLoginInfo = new PersistentLoginInfo(expirationDate, persistentLoginValue);
                    final String jsonPersistentLoginInfo = JsonUtil.serialize(persistentLoginInfo);
                    final String cookieValue = pwmApplication.getSecureService().encryptToString(jsonPersistentLoginInfo);
                    pwmRequest.getPwmResponse().writeCookie(
                            PwmConstants.COOKIE_PERSISTENT_CONFIG_LOGIN,
                            cookieValue,
                            persistentSeconds
                    );
                    LOGGER.debug(pwmRequest, "set persistent config login cookie (expires "
                                    + PwmConstants.DEFAULT_DATETIME_FORMAT.format(expirationDate)
                                    + ")"
                    );
                }
            }

            if (configManagerBean.getPrePasswordEntryUrl() != null) {
                final String originalUrl = configManagerBean.getPrePasswordEntryUrl();
                configManagerBean.setPrePasswordEntryUrl(null);
                pwmRequest.getPwmResponse().sendRedirect(originalUrl);
                return true;
            }
            return false;
        }

        if (configManagerBean.getPrePasswordEntryUrl() == null) {
            configManagerBean.setPrePasswordEntryUrl(pwmRequest.getHttpServletRequest().getRequestURL().toString());
        }

        final String time = new TimeDuration(persistentSeconds * 1000).asLongString(pwmRequest.getLocale());
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.ConfigPasswordRememberTime,time);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CONFIG_MANAGER_LOGIN);
        return true;
    }

    private static class PersistentLoginInfo implements Serializable {
        private Date expireDate;
        private String password;

        private PersistentLoginInfo(
                Date expireDate,
                String password
        )
        {
            this.expireDate = expireDate;
            this.password = password;
        }

        public Date getExpireDate()
        {
            return expireDate;
        }

        public String getPassword()
        {
            return password;
        }
    }
}

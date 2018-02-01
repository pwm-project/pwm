/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http.filter;

import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.ConfigManagerBean;
import password.pwm.svc.intruder.RecordType;
import password.pwm.svc.sessiontrack.UserAgentUtils;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigAccessFilter extends AbstractPwmFilter {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ConfigAccessFilter.class);


    @Override
    void processFilter(final PwmApplicationMode mode, final PwmRequest pwmRequest, final PwmFilterChain filterChain) throws PwmException, IOException, ServletException {
        final PwmApplicationMode appMode = pwmRequest.getPwmApplication().getApplicationMode();
        if (appMode == PwmApplicationMode.NEW) {
            filterChain.doFilter();
            return;
        }

        final boolean blockOldIE = Boolean.parseBoolean( pwmRequest.getPwmApplication().getConfig().readAppProperty( AppProperty.CONFIG_EDITOR_BLOCK_OLD_IE ) );
        if (blockOldIE) {
            try {
                UserAgentUtils.checkIfPreIE11( pwmRequest );
            } catch ( PwmException e ) {
                pwmRequest.respondWithError( e.getErrorInformation() );
                return;
            }
        }

        final ConfigManagerBean configManagerBean = pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest, ConfigManagerBean.class);
        if (checkAuthentication(pwmRequest, configManagerBean) == ProcessStatus.Continue) {
            filterChain.doFilter();
        }
    }

    @Override
    boolean isInterested(final PwmApplicationMode mode, final PwmURL pwmURL) {
        return pwmURL.isConfigManagerURL();
    }

    static ProcessStatus checkAuthentication(
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

        if (PwmApplicationMode.RUNNING == pwmRequest.getPwmApplication().getApplicationMode()) {
            if (!pwmRequest.isAuthenticated()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            }

            if (!pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN)) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED);
                denyAndError(pwmRequest, errorInformation);
                return ProcessStatus.Halt;
            }
        }

        if (PwmApplicationMode.CONFIGURATION != pwmRequest.getPwmApplication().getApplicationMode()) {
            authRequired = true;
        }

        if (!authRequired) {
            return ProcessStatus.Continue;
        }

        if (!storedConfig.hasPassword()) {
            final String errorMsg = "config file does not have a configuration password";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg,new String[]{errorMsg});
            return denyAndError(pwmRequest, errorInformation);
        }

        if (configManagerBean.isPasswordVerified()) {
            return ProcessStatus.Continue;
        }

        String persistentLoginValue = null;
        boolean persistentLoginAccepted = false;
        boolean persistentLoginEnabled = false;
        if (pwmRequest.getConfig().isDefaultValue(PwmSetting.PWM_SECURITY_KEY)) {
            LOGGER.debug(pwmRequest, "security not available, persistent login not possible.");
        } else {
            persistentLoginEnabled = true;
            final PwmSecurityKey securityKey = pwmRequest.getConfig().getSecurityKey();

            if (PwmApplicationMode.RUNNING == pwmRequest.getPwmApplication().getApplicationMode()) {
                persistentLoginValue = SecureEngine.hash(
                        storedConfig.readConfigProperty(ConfigurationProperty.PASSWORD_HASH)
                                + pwmSession.getUserInfo().getUserIdentity().toDelimitedKey(),
                        PwmHashAlgorithm.SHA512);

            } else {
                persistentLoginValue = SecureEngine.hash(
                        storedConfig.readConfigProperty(ConfigurationProperty.PASSWORD_HASH),
                        PwmHashAlgorithm.SHA512);
            }

            {
                final String cookieStr = pwmRequest.readCookie(PwmConstants.COOKIE_PERSISTENT_CONFIG_LOGIN);
                if (securityKey != null && cookieStr != null && !cookieStr.isEmpty()) {
                    try {
                        final String jsonStr = pwmApplication.getSecureService().decryptStringValue(cookieStr);
                        final PersistentLoginInfo persistentLoginInfo = JsonUtil.deserialize(jsonStr, PersistentLoginInfo.class);
                        if (persistentLoginInfo != null && persistentLoginValue != null) {
                            if (persistentLoginInfo.getExpireDate().isAfter(Instant.now())) {
                                if (persistentLoginValue.equals(persistentLoginInfo.getPassword())) {
                                    persistentLoginAccepted = true;
                                    LOGGER.debug(pwmRequest, "accepting persistent config login from cookie (expires "
                                                    + JavaHelper.toIsoDate(persistentLoginInfo.getExpireDate())
                                                    + ")"
                                    );
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error(pwmRequest, "error examining persistent config login cookie: " + e.getMessage());
                    }
                    if (!persistentLoginAccepted) {
                        pwmRequest.getPwmResponse().removeCookie(PwmConstants.COOKIE_PERSISTENT_CONFIG_LOGIN, null);
                        LOGGER.debug(pwmRequest, "removing non-working persistent config login cookie");
                    }
                }
            }
        }


        final String password = pwmRequest.readParameterAsString("password");
        boolean passwordAccepted = false;
        if (!persistentLoginAccepted) {
            if (password != null && password.length() > 0) {
                if (storedConfig.verifyPassword(password, pwmRequest.getConfig())) {
                    passwordAccepted = true;
                    LOGGER.trace(pwmRequest, "valid configuration password accepted");
                    updateLoginHistory(pwmRequest,pwmRequest.getUserInfoIfLoggedIn(), true);
                } else{
                    LOGGER.trace(pwmRequest, "configuration password is not correct");
                    pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                    pwmApplication.getIntruderManager().mark(RecordType.USERNAME, PwmConstants.CONFIGMANAGER_INTRUDER_USERNAME, pwmSession.getLabel());
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PASSWORD_ONLY_BAD);
                    updateLoginHistory(pwmRequest,pwmRequest.getUserInfoIfLoggedIn(), false);
                    return denyAndError(pwmRequest, errorInformation);
                }
            }
        }

        if ((persistentLoginAccepted || passwordAccepted)) {
            configManagerBean.setPasswordVerified(true);
            pwmApplication.getIntruderManager().convenience().clearAddressAndSession(pwmSession);
            pwmApplication.getIntruderManager().clear(RecordType.USERNAME, PwmConstants.CONFIGMANAGER_INTRUDER_USERNAME);
            if (persistentLoginEnabled && !persistentLoginAccepted && "on".equals(pwmRequest.readParameterAsString("remember"))) {
                final int persistentSeconds = figureMaxLoginSeconds(pwmRequest);
                if (persistentSeconds > 0) {
                    final Instant expirationDate = Instant.ofEpochMilli(System.currentTimeMillis() + (persistentSeconds * 1000));
                    final PersistentLoginInfo persistentLoginInfo = new PersistentLoginInfo(expirationDate, persistentLoginValue);
                    final String jsonPersistentLoginInfo = JsonUtil.serialize(persistentLoginInfo);
                    final String cookieValue = pwmApplication.getSecureService().encryptToString(jsonPersistentLoginInfo);
                    pwmRequest.getPwmResponse().writeCookie(
                            PwmConstants.COOKIE_PERSISTENT_CONFIG_LOGIN,
                            cookieValue,
                            persistentSeconds
                    );
                    LOGGER.debug(pwmRequest, "set persistent config login cookie (expires "
                                    + JavaHelper.toIsoDate(expirationDate)
                                    + ")"
                    );
                }
            }

            if (configManagerBean.getPrePasswordEntryUrl() != null) {
                final String originalUrl = configManagerBean.getPrePasswordEntryUrl();
                configManagerBean.setPrePasswordEntryUrl(null);
                pwmRequest.getPwmResponse().sendRedirect(originalUrl);
                return ProcessStatus.Halt;
            }
            return ProcessStatus.Continue;
        }

        if (configManagerBean.getPrePasswordEntryUrl() == null) {
            configManagerBean.setPrePasswordEntryUrl(pwmRequest.getHttpServletRequest().getRequestURL().toString());
        }

        forwardToJsp(pwmRequest);
        return ProcessStatus.Halt;
    }

    private static void forwardToJsp(final PwmRequest pwmRequest)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final int persistentSeconds = figureMaxLoginSeconds(pwmRequest);
        final String time = new TimeDuration(persistentSeconds * 1000).asLongString(pwmRequest.getLocale());

        final ConfigLoginHistory configLoginHistory = readConfigLoginHistory(pwmRequest);

        pwmRequest.setAttribute(PwmRequestAttribute.ConfigLoginHistory, configLoginHistory);
        pwmRequest.setAttribute(PwmRequestAttribute.ConfigPasswordRememberTime,time);
        pwmRequest.forwardToJsp(JspUrl.CONFIG_MANAGER_LOGIN);

    }

    private static ConfigLoginHistory readConfigLoginHistory(final PwmRequest pwmRequest) {
        final ConfigLoginHistory configLoginHistory = pwmRequest.getPwmApplication().readAppAttribute(PwmApplication.AppAttribute.CONFIG_LOGIN_HISTORY, ConfigLoginHistory.class);
        return configLoginHistory == null
                ? new ConfigLoginHistory()
                : configLoginHistory;
    }

    private static void updateLoginHistory(final PwmRequest pwmRequest, final UserIdentity userIdentity, final boolean successful) {
        final ConfigLoginHistory configLoginHistory = readConfigLoginHistory(pwmRequest);
        final ConfigLoginEvent event = new ConfigLoginEvent(
                userIdentity == null ? "n/a" : userIdentity.toDisplayString(),
                Instant.now(),
                pwmRequest.getPwmSession().getSessionStateBean().getSrcAddress()
        );
        final int maxEvents = Integer.parseInt(pwmRequest.getPwmApplication().getConfig().readAppProperty(AppProperty.CONFIG_HISTORY_MAX_ITEMS));
        configLoginHistory.addEvent(event, maxEvents, successful);
        pwmRequest.getPwmApplication().writeAppAttribute(PwmApplication.AppAttribute.CONFIG_LOGIN_HISTORY, configLoginHistory);
    }

    private static class PersistentLoginInfo implements Serializable {
        private Instant expireDate;
        private String password;

        private PersistentLoginInfo(
                final Instant expireDate,
                final String password
        )
        {
            this.expireDate = expireDate;
            this.password = password;
        }

        public Instant getExpireDate()
        {
            return expireDate;
        }

        public String getPassword()
        {
            return password;
        }
    }



    public static class ConfigLoginHistory implements Serializable {
        private final List<ConfigLoginEvent> successEvents = new ArrayList<>();
        private final List<ConfigLoginEvent> failedEvents = new ArrayList<>();

        void addEvent(final ConfigLoginEvent event, final int maxEvents, final boolean successful) {
            final List<ConfigLoginEvent> events = successful ? successEvents : failedEvents;
            events.add(event);
            if (maxEvents > 0) {
                while (events.size() > maxEvents) {
                    events.remove(0);
                }
            }
        }

        public List<ConfigLoginEvent> successEvents() {
            return Collections.unmodifiableList(successEvents);
        }

        public List<ConfigLoginEvent> failedEvents() {
            return Collections.unmodifiableList(failedEvents);
        }
    }

    public static class ConfigLoginEvent implements Serializable {
        private final String userIdentity;
        private final Instant date;
        private final String networkAddress;

        public ConfigLoginEvent(final String userIdentity, final Instant date, final String networkAddress) {
            this.userIdentity = userIdentity;
            this.date = date;
            this.networkAddress = networkAddress;
        }

        public String getUserIdentity() {
            return userIdentity;
        }

        public Instant getDate() {
            return date;
        }

        public String getNetworkAddress() {
            return networkAddress;
        }
    }

    static int figureMaxLoginSeconds(final PwmRequest pwmRequest) {
        return Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_MAX_PERSISTENT_LOGIN_SECONDS));
    }


    private static ProcessStatus denyAndError(final PwmRequest pwmRequest, final ErrorInformation errorInformation)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        pwmRequest.setAttribute(PwmRequestAttribute.PwmErrorInfo, errorInformation);
        forwardToJsp(pwmRequest);
        return ProcessStatus.Halt;
    }
}

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

package password.pwm;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.LdapProfile;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Wraps an <i>HttpSession</i> to provide additional PWM-related session
 * management activities.
 * <p/>
 *
 * @author Jason D. Rivard
 */
public class SessionManager implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(SessionManager.class);

    private transient volatile ChaiProvider chaiProvider;

    final private PwmSession pwmSession;

    final Lock providerLock = new ReentrantLock();

    private transient ConcurrentMap<Serializable,Serializable> lruTypingCache;
    private transient UserDataReader userDataReader;

    private List<CloseConnectionListener> closeConnectionListeners = new ArrayList<CloseConnectionListener>();

    public interface CloseConnectionListener {
        void connectionsClosed();
    }


// --------------------------- CONSTRUCTORS ---------------------------

    public SessionManager(final PwmSession pwmSession) {
        this.pwmSession = pwmSession;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public ChaiProvider getChaiProvider(final PwmApplication pwmApplication, final UserIdentity userIdentity, final String userPassword)
            throws ChaiUnavailableException, PwmUnrecoverableException {
        try {
            providerLock.lock();
            closeConnectionImpl();
            final Configuration config = pwmApplication.getConfig();
            chaiProvider = makeChaiProvider(pwmSession, userIdentity, userPassword, config);
            return chaiProvider;
        } finally {
            providerLock.unlock();
        }
    }

    public ChaiProvider getChaiProvider(final PwmApplication pwmApplication)
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final String userPassword = pwmSession.getUserInfoBean().getUserCurrentPassword();
        final UserIdentity userDN = pwmSession.getUserInfoBean().getUserIdentity();

        if (pwmSession.getUserInfoBean().getAuthenticationType() == UserInfoBean.AuthenticationType.AUTH_WITHOUT_PASSWORD) {
            if (userPassword == null || userPassword.length() < 1) {
                throw new PwmUnrecoverableException(PwmError.ERROR_PASSWORD_REQUIRED);
            }
        }

        try {
            providerLock.lock();
            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            }

            if (chaiProvider == null) {
                final Configuration config = pwmApplication.getConfig();
                chaiProvider = makeChaiProvider(pwmSession, userDN, userPassword, config);
            }

            return chaiProvider;
        } finally {
            providerLock.unlock();
        }
    }

    private static ChaiProvider makeChaiProvider(
            final PwmSession pwmSession,
            final UserIdentity userIdentity,
            final String userPassword,
            final Configuration config
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        LOGGER.trace(pwmSession, "attempting to open new ldap connection for " + userIdentity);

        final boolean authIsFromForgottenPw = pwmSession.getUserInfoBean().getAuthenticationType() == UserInfoBean.AuthenticationType.AUTH_FROM_FORGOTTEN;
        final boolean authIsBindInhibit = pwmSession.getUserInfoBean().getAuthenticationType() == UserInfoBean.AuthenticationType.AUTH_BIND_INHIBIT;
        final boolean alwaysUseProxyIsEnabled = config.readSettingAsBoolean(PwmSetting.AD_USE_PROXY_FOR_FORGOTTEN);
        final boolean passwordNotPresent = userPassword == null || userPassword.length() < 1;

        final LdapProfile profile = config.getLdapProfiles().get(userIdentity.getLdapProfileID());
        if (authIsBindInhibit || (authIsFromForgottenPw && (alwaysUseProxyIsEnabled || passwordNotPresent))) {
            final String proxyDN = profile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            final String proxyPassword = profile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);
            return LdapOperationsHelper.createChaiProvider(profile, config, proxyDN, proxyPassword);
        }

        return LdapOperationsHelper.createChaiProvider(profile, config, userIdentity.getUserDN(), userPassword);
    }

// ------------------------ CANONICAL METHODS ------------------------

    protected void finalize()
            throws Throwable {
        super.finalize();
        this.closeConnections();
    }

    public void closeConnections() {
        closeConnectionImpl();
        lruTypingCache = null;
    }

    private void closeConnectionImpl() {
        if (!closeConnectionListeners.isEmpty()) {
            try {
                LOGGER.debug(pwmSession, "closing user ldap connection");
                for (CloseConnectionListener closeConnectionListener : closeConnectionListeners) {
                    closeConnectionListener.connectionsClosed();
                }
                closeConnectionListeners.clear();
            } catch (Throwable e) {
                LOGGER.error(pwmSession, "error while calling close connection listeners: " + e.getMessage());
                e.printStackTrace();
            }
        }

        try {
            providerLock.lock();
            if (chaiProvider != null) {
                try {
                    LOGGER.debug(pwmSession, "closing user ldap connection");
                    chaiProvider.close();
                    chaiProvider = null;
                } catch (Exception e) {
                    LOGGER.error(pwmSession, "error while closing user connection: " + e.getMessage());
                }
            }
        } finally {
            providerLock.unlock();
        }
    }

// -------------------------- OTHER METHODS --------------------------

    public ChaiUser getActor(final PwmApplication pwmApplication)
            throws ChaiUnavailableException, PwmUnrecoverableException {

        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            throw new IllegalStateException("user not logged in");
        }

        final UserIdentity userDN = pwmSession.getUserInfoBean().getUserIdentity();

        if (userDN == null || userDN.getUserDN() == null || userDN.getUserDN().length() < 1) {
            throw new IllegalStateException("user not logged in");
        }

        return ChaiFactory.createChaiUser(userDN.getUserDN(), this.getChaiProvider(pwmApplication));
    }

    public boolean hasActiveLdapConnection() {
        try {
            providerLock.lock();
            return this.chaiProvider != null && this.chaiProvider.isConnected();
        } finally {
            providerLock.unlock();
        }
    }

    private Map<Serializable, Serializable> getLruTypingCache() {
        if (lruTypingCache == null) {
            final ConcurrentMap newCache = new ConcurrentLinkedHashMap.Builder<Serializable, Serializable>()
                    .maximumWeightedCapacity(PwmConstants.SERVER_AJAX_TYPING_CACHE_SIZE)
                    .build();
            lruTypingCache = newCache;
            return newCache;
        }
        return lruTypingCache;
    }

    public Serializable getTypingCacheValue(final Serializable key) {

        return PwmConstants.SERVER_AJAX_TYPING_CACHE_SIZE < 1 ? null : getLruTypingCache().get(key);
    }

    public void putLruTypingCacheValue(final Serializable key, final Serializable value) {
        if (PwmConstants.SERVER_AJAX_TYPING_CACHE_SIZE < 1) {
            return;
        }
        getLruTypingCache().put(key,value);
    }

    public ChaiUser getActor(final PwmApplication pwmApplication, final UserIdentity userIdentity)
            throws PwmUnrecoverableException, ChaiUnavailableException {
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
        }
        final UserIdentity thisIdentity = pwmSession.getUserInfoBean().getUserIdentity();
        if (thisIdentity.getLdapProfileID() == null || userIdentity.getLdapProfileID() == null) {
            throw new PwmUnrecoverableException(PwmError.ERROR_NO_LDAP_CONNECTION);
        }
        final ChaiProvider provider = this.getChaiProvider(pwmApplication);
        return ChaiFactory.createChaiUser(userIdentity.getUserDN(), provider);
    }

    public UserDataReader getUserDataReader(final PwmApplication pwmApplication) throws PwmUnrecoverableException, ChaiUnavailableException {
        if (pwmSession == null || !pwmSession.getSessionStateBean().isAuthenticated()) {
            return null;
        }

        if (userDataReader == null) {
            userDataReader = LdapUserDataReader.appProxiedReader(pwmApplication,
                    pwmSession.getUserInfoBean().getUserIdentity());
        }
        return userDataReader;
    }

    public void clearUserDataReader() {
        userDataReader = null;
    }

    public void addCloseConnectionListener(CloseConnectionListener closeConnectionListener) {
        this.closeConnectionListeners.add(closeConnectionListener);
    }

    public void incrementRequestCounter() {
        if (this.pwmSession != null) {
            final SessionStateBean ssBean = this.pwmSession.getSessionStateBean();
            final int currentValue = ssBean.getRequestCounter();
            ssBean.setRequestCounter(currentValue+1);
            final String pwmFormID = Helper.buildPwmFormID(ssBean);
            LOGGER.trace(pwmSession, "incremented request counter, current pwmFormID=" + pwmFormID);
        }
    }
}

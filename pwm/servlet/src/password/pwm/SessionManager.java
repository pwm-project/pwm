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

package password.pwm;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;

import java.io.Serializable;
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
    final private Configuration config;

    final Lock providerLock = new ReentrantLock();

// --------------------------- CONSTRUCTORS ---------------------------

    public SessionManager(final PwmSession pwmSession, final Configuration config) {
        this.pwmSession = pwmSession;
        this.config = config;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public ChaiProvider getChaiProvider(final String userDN, final String userPassword)
            throws ChaiUnavailableException, PwmUnrecoverableException {
        try {
            providerLock.lock();
            closeConnectionImpl();
            chaiProvider = makeChaiProvider(pwmSession, userDN, userPassword, config);
            return chaiProvider;
        } finally {
            providerLock.unlock();
        }
    }


    public ChaiProvider getChaiProvider()
            throws ChaiUnavailableException, PwmUnrecoverableException {
        try {
            providerLock.lock();
            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_AUTHENTICATION_REQUIRED);
            }

            if (chaiProvider == null) {
                final String userPassword = pwmSession.getUserInfoBean().getUserCurrentPassword();
                final String userDN = pwmSession.getUserInfoBean().getUserDN();
                chaiProvider = makeChaiProvider(pwmSession, userDN, userPassword, config);
            }

            return chaiProvider;
        } finally {
            providerLock.unlock();
        }
    }

    private static ChaiProvider makeChaiProvider(
            final PwmSession pwmSession,
            final String userDN,
            final String userPassword,
            final Configuration config
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        LOGGER.trace(pwmSession, "attempting to open new ldap connection for " + userDN);
        final int idleTimeoutMs = (int) config.readSettingAsLong(PwmSetting.LDAP_IDLE_TIMEOUT) * 1000;

        if (pwmSession.getUserInfoBean().isCurrentPasswordUnknownToPwm()) {
            final String proxyDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            final String proxyPassword = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);
            return Helper.createChaiProvider(config, proxyDN, proxyPassword, idleTimeoutMs);
        }

        return Helper.createChaiProvider(config, userDN, userPassword, idleTimeoutMs);
    }

// ------------------------ CANONICAL METHODS ------------------------

    protected void finalize()
            throws Throwable {
        super.finalize();
        this.closeConnections();
    }

    public void closeConnections() {
        closeConnectionImpl();
    }

    private void closeConnectionImpl() {
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

    public ChaiUser getActor()
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final String userDN = pwmSession.getUserInfoBean().getUserDN();

        if (userDN == null || userDN.length() < 1) {
            throw new IllegalStateException("user not logged in");
        }

        return ChaiFactory.createChaiUser(userDN, this.getChaiProvider());
    }

    public boolean hasActiveLdapConnection() {
        try {
            providerLock.lock();
            return this.chaiProvider != null && this.chaiProvider.isConnected();
        } finally {
            providerLock.unlock();
        }
    }
}

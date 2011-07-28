/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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
import password.pwm.util.TimeDuration;

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

    final Lock providerLock = new ReentrantLock();

// --------------------------- CONSTRUCTORS ---------------------------

    public SessionManager(final PwmSession pwmSession) {
        this.pwmSession = pwmSession;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public ChaiProvider getChaiProvider(final String userDN, final String userPassword)
            throws ChaiUnavailableException, PwmUnrecoverableException {
        try {
            providerLock.lock();
            closeConnectionImpl();
            chaiProvider = makeChaiProvider(pwmSession, userDN, userPassword);
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
                chaiProvider = makeChaiProvider(pwmSession, userDN, userPassword);
            }

            return chaiProvider;
        } finally {
            providerLock.unlock();
        }
    }

    private static ChaiProvider makeChaiProvider(final PwmSession pwmSession, final String userDN, final String userPassword)
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final long startTime = System.currentTimeMillis();
        final StringBuilder debugLogText = new StringBuilder();
        final Configuration config = pwmSession.getConfig();

        final ChaiProvider returnProvider;
        final String username;
        final String password;

        if (
                config.readSettingAsBoolean(PwmSetting.EDIRECTORY_ALWAYS_USE_PROXY) &&
                        pwmSession.getContextManager().getProxyChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY
                ) {
            username = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            password = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);
            debugLogText.append("opened new proxy ldap connection for ");
        } else {
            username = userDN;
            password = userPassword;
            debugLogText.append("opened new ldap connection for ");
        }

        debugLogText.append(userDN);
        debugLogText.append(" (").append(TimeDuration.fromCurrent(startTime).asCompactString()).append(")");
        LOGGER.trace(pwmSession, debugLogText.toString());

        returnProvider = Helper.createChaiProvider(pwmSession.getConfig(), username, password);
        return returnProvider;
    }

// ------------------------ CANONICAL METHODS ------------------------

    protected void finalize()
            throws Throwable {
        super.finalize();
        this.closeConnections();
    }

    public void closeConnections() {
        synchronized (providerLock) {
            closeConnectionImpl();
        }
    }

    private void closeConnectionImpl() {
        if (chaiProvider != null) {
            try {
                LOGGER.debug(pwmSession, "closing user ldap connection");
                chaiProvider.close();
                chaiProvider = null;
            } catch (Exception e) {
                LOGGER.error(pwmSession, "error while closing user connection: " + e.getMessage());
            }
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
}

/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.io.Serializable;

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

// --------------------------- CONSTRUCTORS ---------------------------

    public SessionManager(final PwmSession pwmSession)
    {
        this.pwmSession = pwmSession;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public ChaiProvider getChaiProvider()
            throws ChaiUnavailableException, PwmException
    {
        synchronized (this) {
            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                throw PwmException.createPwmException(Message.ERROR_AUTHENTICATION_REQUIRED);
            }

            if (chaiProvider == null) {
                final long startTime = System.currentTimeMillis();
                final StringBuilder debugLogText = new StringBuilder();
                final Configuration config = pwmSession.getConfig();

                if (config.readSettingAsBoolean(PwmSetting.EDIRECTORY_ALWAYS_USE_PROXY) && pwmSession.getContextManager().getProxyChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                    chaiProvider = Helper.createChaiProvider(pwmSession.getContextManager(), config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN), config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD));
                    debugLogText.append("opened new proxy ldap connection for ");
                } else {
                    chaiProvider = Helper.createChaiProvider(pwmSession.getContextManager(), pwmSession.getUserInfoBean().getUserDN(), pwmSession.getUserInfoBean().getUserCurrentPassword());
                    debugLogText.append("opened new ldap connection for ");
                }

                debugLogText.append(pwmSession.getUserInfoBean().getUserDN());
                debugLogText.append(" (").append(TimeDuration.fromCurrent(startTime).asCompactString()).append(")");
                LOGGER.trace(pwmSession, debugLogText.toString());
            }

            return chaiProvider;
        }
    }

    public void setChaiProvider(final ChaiProvider chaiProvider)
            throws ChaiUnavailableException, PwmException
    {
        synchronized (this) {
            if (this.chaiProvider == null) {
                this.chaiProvider = chaiProvider;
            }
        }
    }


// ------------------------ CANONICAL METHODS ------------------------

    protected void finalize()
            throws Throwable
    {
        super.finalize();
        this.closeConnections();
    }

    public synchronized void closeConnections()
    {
        synchronized (this) {
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
    }

// -------------------------- OTHER METHODS --------------------------

    public ChaiUser getActor()
            throws ChaiUnavailableException, PwmException
    {
        final String userDN = pwmSession.getUserInfoBean().getUserDN();

        if (userDN == null || userDN.length() < 1) {
            throw new IllegalStateException("user not logged in");
        }

        return ChaiFactory.createChaiUser(userDN, this.getChaiProvider());
    }
}

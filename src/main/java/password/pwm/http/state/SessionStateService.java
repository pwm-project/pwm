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

import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.svc.PwmService;
import password.pwm.util.logging.PwmLogger;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionStateService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(SessionStateService.class);

    private SessionBeanProvider sessionBeanProvider = new LocalSessionBeanImpl();
    private final SessionBeanProvider httpSessionProvider = new LocalSessionBeanImpl();

    private SessionLoginProvider sessionLoginProvider = new LocalLoginSessionImpl();

    private final Map<Class<? extends PwmSessionBean>,PwmSessionBean> beanInstanceCache = new HashMap<>();

    @Override
    public STATUS status() {
        return STATUS.OPEN;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        {
            final SessionBeanMode sessionBeanMode = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.SECURITY_MODULE_SESSION_MODE, SessionBeanMode.class);
            if (sessionBeanMode != null) {
                switch (sessionBeanMode) {
                    case LOCAL:
                        sessionBeanProvider = new LocalSessionBeanImpl();
                        break;

                    case CRYPTCOOKIE:
                        sessionBeanProvider = new CryptoRequestBeanImpl();
                        break;

                    default:
                        throw new IllegalStateException("unhandled session bean state: " + sessionBeanMode);
                }
            }
        }

        {
            final SessionBeanMode loginSessionMode = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.SECURITY_LOGIN_SESSION_MODE, SessionBeanMode.class);
            {
                if (loginSessionMode != null) {
                    switch (loginSessionMode) {
                        case LOCAL:
                            sessionLoginProvider = new LocalLoginSessionImpl();
                            break;

                        case CRYPTCOOKIE:
                            sessionLoginProvider = new CryptoRequestLoginImpl();
                    }
                }
                sessionLoginProvider.init(pwmApplication);
            }
        }


        LOGGER.trace("initialized " + sessionBeanProvider.getClass().getName() + " provider");
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

    public <E extends PwmSessionBean> E getBean(PwmRequest pwmRequest, Class<E> theClass) throws PwmUnrecoverableException {
        if (beanClassHasFlag(theClass, PwmSessionBean.Flag.ProhibitCookieSession)) {
            return httpSessionProvider.getSessionBean(pwmRequest, theClass);
        }
        return sessionBeanProvider.getSessionBean(pwmRequest, theClass);
    }

    public void clearBean(final PwmRequest pwmRequest, final Class<? extends PwmSessionBean> theClass) throws PwmUnrecoverableException {
        if (beanClassHasFlag(theClass, PwmSessionBean.Flag.ProhibitCookieSession)) {
            httpSessionProvider.clearSessionBean(pwmRequest, theClass);
            return;
        }
        sessionBeanProvider.clearSessionBean(pwmRequest, theClass);
    }

    public void saveSessionBeans(final PwmRequest pwmRequest) {
        sessionBeanProvider.saveSessionBeans(pwmRequest);
    }

    public void clearLoginSession(PwmRequest pwmRequest) throws PwmUnrecoverableException {
        sessionLoginProvider.clearLoginSession(pwmRequest);
    }

    public void saveLoginSessionState(PwmRequest pwmRequest) {
        sessionLoginProvider.saveLoginSessionState(pwmRequest);
    }

    public void readLoginSessionState(PwmRequest pwmRequest) throws PwmUnrecoverableException {
        sessionLoginProvider.readLoginSessionState(pwmRequest);
    }



    private boolean beanClassHasFlag(Class<? extends PwmSessionBean> theClass, PwmSessionBean.Flag flag) throws PwmUnrecoverableException {
        if (theClass == null) {
            return false;
        }
        if (!beanInstanceCache.containsKey(theClass)) {
            beanInstanceCache.put(theClass, newBean(null,theClass));
        }
        try {
            return theClass.newInstance().getFlags().contains(flag);
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    static <E extends PwmSessionBean> E newBean(final String sessionGuid, Class<E> theClass) throws PwmUnrecoverableException {
        try {
            final E newBean = theClass.newInstance();
            newBean.setGuid(sessionGuid);
            newBean.setTimestamp(new Date());
            return newBean;
        } catch (Exception e) {
            final String errorMsg = "unexpected error trying to instantiate bean class " + theClass.getName() + ": " + e.getMessage();
            LOGGER.error(errorMsg, e);
            throw PwmUnrecoverableException.newException(PwmError.ERROR_UNKNOWN, errorMsg);
        }
    }
}

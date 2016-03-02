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

import password.pwm.PwmConstants;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

class LocalSessionBeanImpl implements SessionBeanProvider {

    private final static PwmLogger LOGGER = PwmLogger.forClass(LocalSessionBeanImpl.class);

    @Override
    public <E extends PwmSessionBean> E getSessionBean(final PwmRequest pwmRequest, final Class<E> theClass) {
        final Map<Class<? extends PwmSessionBean>,PwmSessionBean> sessionBeans = getSessionBeanMap(pwmRequest);

        if (!sessionBeans.containsKey(theClass)) {
            try {
                final Object newBean = SessionStateService.newBean(null, theClass);
                sessionBeans.put(theClass,(PwmSessionBean)newBean);
            } catch (Exception e) {
                LOGGER.error("unexpected error trying to instantiate bean class " + theClass.getName() + ": " + e.getMessage(),e);
            }

        }
        return (E)sessionBeans.get(theClass);
    }

    @Override
    public void clearSessionBean(PwmRequest pwmRequest, Class<? extends PwmSessionBean> userBeanClass) {
        final Map<Class<? extends PwmSessionBean>,PwmSessionBean> sessionBeans = getSessionBeanMap(pwmRequest);
        sessionBeans.remove(userBeanClass);
    }

    private Map<Class<? extends PwmSessionBean>,PwmSessionBean> getSessionBeanMap(final PwmRequest pwmRequest) {
        final String attributeName = "SessionBeans";
        final HttpSession httpSession = pwmRequest.getHttpServletRequest().getSession();
        Map<Class<? extends PwmSessionBean>,PwmSessionBean> sessionBeans = (Map<Class<? extends PwmSessionBean>,PwmSessionBean>)httpSession.getAttribute(PwmConstants.SESSION_ATTR_BEANS);
        if (sessionBeans == null) {
            sessionBeans = new HashMap<>();
            httpSession.setAttribute(attributeName, sessionBeans);
        }
        return sessionBeans;
    }

    @Override
    public void saveSessionBeans(PwmRequest pwmRequest) {

    }
}

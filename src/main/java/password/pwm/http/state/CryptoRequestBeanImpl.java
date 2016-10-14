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
import password.pwm.bean.FormNonce;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureService;

import java.util.HashMap;
import java.util.Map;

public class CryptoRequestBeanImpl implements SessionBeanProvider {
    private static final PwmLogger LOGGER = PwmLogger.forClass(CryptoRequestBeanImpl.class);
    private static String attrName = "ssi_cache_map";

    @Override
    public <E extends PwmSessionBean> E getSessionBean(PwmRequest pwmRequest, Class<E> theClass) throws PwmUnrecoverableException {
        final Map<Class<E>, E> cachedMap = getBeanMap(pwmRequest);
        if (cachedMap.containsKey(theClass)) {
            return cachedMap.get(theClass);
        }

        final String submittedPwmFormID = pwmRequest.readParameterAsString(PwmConstants.PARAM_FORM_ID);
        if (submittedPwmFormID != null && submittedPwmFormID.length() > 0) {
            final FormNonce formNonce = pwmRequest.getPwmApplication().getSecureService().decryptObject(
                    submittedPwmFormID,
                    FormNonce.class
            );
            final SecureService secureService = pwmRequest.getPwmApplication().getSecureService();
            E bean = secureService.decryptObject(formNonce.getPayload(), theClass);
            cachedMap.put(theClass, bean);
            return bean;
        }
        final String sessionGuid = pwmRequest.getPwmSession().getLoginInfoBean().getGuid();
        final E newBean = SessionStateService.newBean(sessionGuid, theClass);
        cachedMap.put(theClass, newBean);
        return newBean;
    }

    public void saveSessionBeans(final PwmRequest pwmRequest) {
    }

    @Override
    public String getSessionStateInfo(PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final SecureService secureService = pwmRequest.getPwmApplication().getSecureService();
        Map<Class, PwmSessionBean> cachedMap = (Map<Class, PwmSessionBean>)pwmRequest.getHttpServletRequest().getAttribute(attrName);
        if (cachedMap == null || cachedMap.isEmpty()) {
            return "";
        }
        if (cachedMap.size() > 1) {
            throw new IllegalStateException("unable to handle multiple session state beans");
        }
        final Class beanClass= cachedMap.keySet().iterator().next();
        final PwmSessionBean bean = cachedMap.values().iterator().next();
        return secureService.encryptObjectToString(bean);
    }

    public <E extends PwmSessionBean> void clearSessionBean(PwmRequest pwmRequest, Class<E> userBeanClass) throws PwmUnrecoverableException {
        final Map<Class<E>, E> cachedMap = getBeanMap(pwmRequest);
        if (cachedMap != null) {
            cachedMap.remove(userBeanClass);
        }
    }

    private static <E extends PwmSessionBean> Map<Class<E>,E> getBeanMap(final PwmRequest pwmRequest) {
        Map<Class<E>, E> cachedMap = (Map<Class<E>, E>)pwmRequest.getHttpServletRequest().getAttribute(attrName);
        if (cachedMap == null) {
            cachedMap = new HashMap<>();
            pwmRequest.getHttpServletRequest().setAttribute(attrName, cachedMap);
        }
        return cachedMap;
    }

    private static String nameForClass(final Class<? extends PwmSessionBean> theClass) {
        return theClass.getSimpleName();
    }
}

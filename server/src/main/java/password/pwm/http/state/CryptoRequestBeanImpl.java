/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

public class CryptoRequestBeanImpl implements SessionBeanProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CryptoRequestBeanImpl.class );
    private static String attrName = "ssi_cache_map";

    @Override
    public <E extends PwmSessionBean> E getSessionBean( final PwmRequest pwmRequest, final Class<E> theClass ) throws PwmUnrecoverableException
    {
        final Map<Class<E>, E> cachedMap = getBeanMap( pwmRequest );
        if ( cachedMap.containsKey( theClass ) )
        {
            return cachedMap.get( theClass );
        }

        final String submittedPwmFormID = pwmRequest.readParameterAsString( PwmConstants.PARAM_FORM_ID );
        if ( submittedPwmFormID != null && submittedPwmFormID.length() > 0 )
        {
            final FormNonce formNonce = pwmRequest.getPwmApplication().getSecureService().decryptObject(
                    submittedPwmFormID,
                    FormNonce.class
            );
            final SecureService secureService = pwmRequest.getPwmApplication().getSecureService();
            final E bean = secureService.decryptObject( formNonce.getPayload(), theClass );
            cachedMap.put( theClass, bean );
            return bean;
        }
        final String sessionGuid = pwmRequest.getPwmSession().getLoginInfoBean().getGuid();
        final E newBean = SessionStateService.newBean( sessionGuid, theClass );
        cachedMap.put( theClass, newBean );
        return newBean;
    }

    @Override
    public void saveSessionBeans( final PwmRequest pwmRequest )
    {
    }

    @Override
    public String getSessionStateInfo( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final SecureService secureService = pwmRequest.getPwmApplication().getSecureService();
        final Map<Class, PwmSessionBean> cachedMap = ( Map<Class, PwmSessionBean> ) pwmRequest.getHttpServletRequest().getAttribute( attrName );
        if ( cachedMap == null || cachedMap.isEmpty() )
        {
            return "";
        }
        if ( cachedMap.size() > 1 )
        {
            throw new IllegalStateException( "unable to handle multiple session state beans" );
        }
        final PwmSessionBean bean = cachedMap.values().iterator().next();
        return secureService.encryptObjectToString( bean );
    }

    @Override
    public <E extends PwmSessionBean> void clearSessionBean( final PwmRequest pwmRequest, final Class<E> userBeanClass ) throws PwmUnrecoverableException
    {
        final Map<Class<E>, E> cachedMap = getBeanMap( pwmRequest );
        if ( cachedMap != null )
        {
            cachedMap.remove( userBeanClass );
        }
    }

    private static <E extends PwmSessionBean> Map<Class<E>, E> getBeanMap( final PwmRequest pwmRequest )
    {
        Map<Class<E>, E> cachedMap = ( Map<Class<E>, E> ) pwmRequest.getHttpServletRequest().getAttribute( attrName );
        if ( cachedMap == null )
        {
            cachedMap = new HashMap<>();
            pwmRequest.getHttpServletRequest().setAttribute( attrName, cachedMap );
        }
        return cachedMap;
    }

    private static String nameForClass( final Class<? extends PwmSessionBean> theClass )
    {
        return theClass.getSimpleName();
    }
}

/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

class LocalSessionBeanImpl implements SessionBeanProvider
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalSessionBeanImpl.class );

    @Override
    public <E extends PwmSessionBean> E getSessionBean( final PwmRequest pwmRequest, final Class<E> theClass )
    {
        final Map<Class<? extends PwmSessionBean>, PwmSessionBean> sessionBeans = getSessionBeanMap( pwmRequest );

        if ( !sessionBeans.containsKey( theClass ) )
        {
            try
            {
                final Object newBean = SessionStateService.newBean( null, theClass );
                sessionBeans.put( theClass, ( PwmSessionBean ) newBean );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unexpected error trying to instantiate bean class " + theClass.getName() + ": " + e.getMessage(), e );
            }

        }
        return ( E ) sessionBeans.get( theClass );
    }

    @Override
    public <E extends PwmSessionBean> void clearSessionBean( final PwmRequest pwmRequest, final Class<E> userBeanClass ) throws PwmUnrecoverableException
    {
        final Map<Class<? extends PwmSessionBean>, PwmSessionBean> sessionBeans = getSessionBeanMap( pwmRequest );
        sessionBeans.remove( userBeanClass );
    }

    private Map<Class<? extends PwmSessionBean>, PwmSessionBean> getSessionBeanMap( final PwmRequest pwmRequest )
    {
        final String attributeName = "SessionBeans";
        final HttpSession httpSession = pwmRequest.getHttpServletRequest().getSession();
        Map<Class<? extends PwmSessionBean>, PwmSessionBean> sessionBeans =
                ( Map<Class<? extends PwmSessionBean>, PwmSessionBean> ) httpSession.getAttribute( PwmConstants.SESSION_ATTR_BEANS );
        if ( sessionBeans == null )
        {
            sessionBeans = new HashMap<>();
            httpSession.setAttribute( attributeName, sessionBeans );
        }
        return sessionBeans;
    }

    @Override
    public void saveSessionBeans( final PwmRequest pwmRequest )
    {

    }

    @Override
    public String getSessionStateInfo( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return null;
    }
}

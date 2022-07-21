/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmCookiePath;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.svc.secure.DomainSecureService;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class CryptoCookieBeanImpl implements SessionBeanProvider
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( CryptoCookieBeanImpl.class );

    private static final PwmCookiePath COOKIE_PATH = PwmCookiePath.PwmServlet;

    @Override
    public <E extends PwmSessionBean> E getSessionBean( final PwmRequest pwmRequest, final Class<E> theClass )
            throws PwmUnrecoverableException
    {
        final Map<Class<? extends PwmSessionBean>, PwmSessionBean> sessionBeans = getRequestBeanMap( pwmRequest );

        if ( sessionBeans.containsKey( theClass ) && sessionBeans.get( theClass ) != null )
        {
            return ( E ) sessionBeans.get( theClass );
        }

        final String sessionGuid = pwmRequest.getPwmSession().getLoginInfoBean().getGuid();
        final String cookieName = nameForClass( pwmRequest, theClass );

        try
        {
            final Optional<String> rawValue = pwmRequest.readCookie( cookieName );
            final PwmSecurityKey key = keyForSession( pwmRequest );
            if ( rawValue.isPresent() )
            {
                final E cookieBean = pwmRequest.getPwmDomain().getSecureService().decryptObject( rawValue.get(), key, theClass );
                if ( validateCookie( pwmRequest, cookieName, cookieBean ) )
                {
                    sessionBeans.put( theClass, cookieBean );
                    return cookieBean;
                }
            }
        }
        catch ( final PwmException e )
        {
            LOGGER.debug( pwmRequest, () -> "ignoring existing existing " + cookieName + " cookie bean due to error: " + e.getMessage() );
        }

        final E newBean = SessionStateService.newBean( sessionGuid, theClass );
        sessionBeans.put( theClass, newBean );
        return newBean;
    }

    private boolean validateCookie( final PwmRequest pwmRequest, final String cookieName, final PwmSessionBean cookieBean )
    {
        if ( cookieBean == null )
        {
            return false;
        }

        if ( cookieBean.getBeanType() == PwmSessionBean.BeanType.AUTHENTICATED )
        {
            if ( cookieBean.getGuid() == null )
            {
                LOGGER.trace( pwmRequest, () -> "disregarded existing " + cookieName + " cookie bean due to missing guid" );
                return false;
            }

            final String sessionGuid = pwmRequest.getPwmSession().getLoginInfoBean().getGuid();
            if ( !cookieBean.getGuid().equals( sessionGuid ) )
            {
                LOGGER.trace( pwmRequest, () -> "disregarded existing " + cookieName + " cookie bean due to session change" );
                return false;
            }
        }

        if ( cookieBean.getBeanType() == PwmSessionBean.BeanType.PUBLIC )
        {
            if ( cookieBean.getTimestamp() == null )
            {
                LOGGER.trace( pwmRequest, () -> "disregarded existing " + cookieName + " cookie bean due to missing timestamp" );
                return false;
            }

            final TimeDuration cookieLifeDuration = TimeDuration.fromCurrent( cookieBean.getTimestamp() );
            final long maxIdleSeconds = pwmRequest.getDomainConfig().readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
            if ( cookieLifeDuration.isLongerThan( maxIdleSeconds, TimeDuration.Unit.SECONDS ) )
            {
                LOGGER.trace( pwmRequest, () -> "disregarded existing " + cookieName + " cookie bean due to outdated timestamp (" + cookieLifeDuration.asCompactString() + ")" );
                return false;
            }
        }

        return true;
    }


    @Override
    public void saveSessionBeans( final PwmRequest pwmRequest )
    {
        if ( pwmRequest == null || pwmRequest.getPwmResponse().isCommitted() || pwmRequest.getPwmResponse() == null  )
        {
            return;
        }
        try
        {
            final Map<Class<? extends PwmSessionBean>, PwmSessionBean> beansInRequest = getRequestBeanMap( pwmRequest );
            if ( beansInRequest != null )
            {
                for ( final Map.Entry<Class<? extends PwmSessionBean>, PwmSessionBean> entry : beansInRequest.entrySet() )
                {
                    final Class<? extends PwmSessionBean> theClass = entry.getKey();
                    final String cookieName = nameForClass( pwmRequest, theClass );
                    final PwmSessionBean bean = entry.getValue();
                    if ( bean == null )
                    {
                        pwmRequest.getPwmResponse().removeCookie( cookieName, COOKIE_PATH );
                    }
                    else
                    {
                        final PwmSecurityKey key = keyForSession( pwmRequest );
                        final String encryptedValue = pwmRequest.getPwmDomain().getSecureService().encryptObjectToString( entry.getValue(), key );
                        pwmRequest.getPwmResponse().writeCookie( cookieName, encryptedValue, -1, COOKIE_PATH );
                    }
                }
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( pwmRequest, () -> "error writing cookie bean to response: " + e.getMessage(), e );
        }
    }

    @Override
    public <E extends PwmSessionBean> void clearSessionBean( final PwmRequest pwmRequest, final Class<E> userBeanClass )
    {
        final Map<Class<? extends PwmSessionBean>, PwmSessionBean> sessionBeans = getRequestBeanMap( pwmRequest );
        sessionBeans.put( userBeanClass, null );
        saveSessionBeans( pwmRequest );
    }

    private static Map<Class<? extends PwmSessionBean>, PwmSessionBean> getRequestBeanMap( final PwmRequest pwmRequest )
    {
        Object sessionBeans = pwmRequest.getAttribute( PwmRequestAttribute.CookieBeanStorage );
        if ( sessionBeans == null )
        {
            sessionBeans = new HashMap<>();
            pwmRequest.setAttribute( PwmRequestAttribute.CookieBeanStorage, sessionBeans );
        }
        return ( Map<Class<? extends PwmSessionBean>, PwmSessionBean> ) sessionBeans;
    }

    private static String nameForClass( final PwmRequest pwmRequest, final Class<? extends PwmSessionBean> theClass )
            throws PwmUnrecoverableException
    {
        final DomainSecureService domainSecureService = pwmRequest.getPwmDomain().getSecureService();
        return "b-" + StringUtil.truncate( domainSecureService.ephemeralHmac( theClass.getName() ), 8 );
    }

    @Override
    public String getSessionStateInfo( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return null;
    }

    private PwmSecurityKey keyForSession( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PwmSecurityKey pwmSecurityKey = pwmRequest.getDomainConfig().getSecurityKey();
        final String keyHash = pwmSecurityKey.keyHash( pwmRequest.getPwmDomain().getSecureService() );
        final String userGuid = pwmRequest.getPwmSession().getLoginInfoBean().getGuid();
        final String keyData = keyHash + pwmRequest.getPwmDomain().getSecureService().ephemeralHmac( userGuid );
        return new PwmSecurityKey( keyData );
    }
}

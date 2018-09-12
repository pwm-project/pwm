/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmHttpResponseWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.util.PasswordData;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class CryptoCookieBeanImpl implements SessionBeanProvider
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( CryptoCookieBeanImpl.class );

    private static final PwmHttpResponseWrapper.CookiePath COOKIE_PATH = PwmHttpResponseWrapper.CookiePath.PwmServlet;

    @Override
    public <E extends PwmSessionBean> E getSessionBean( final PwmRequest pwmRequest, final Class<E> theClass ) throws PwmUnrecoverableException
    {
        final Map<Class<? extends PwmSessionBean>, PwmSessionBean> sessionBeans = getRequestBeanMap( pwmRequest );

        if ( sessionBeans.containsKey( theClass ) && sessionBeans.get( theClass ) != null )
        {
            return ( E ) sessionBeans.get( theClass );
        }

        final String sessionGuid = pwmRequest.getPwmSession().getLoginInfoBean().getGuid();
        final String cookieName = nameForClass( theClass );

        try
        {
            final String rawValue = pwmRequest.readCookie( cookieName );
            final PwmSecurityKey key = keyForSession( pwmRequest );
            final E cookieBean = pwmRequest.getPwmApplication().getSecureService().decryptObject( rawValue, key, theClass );
            if ( validateCookie( pwmRequest, cookieName, cookieBean ) )
            {
                sessionBeans.put( theClass, cookieBean );
                return cookieBean;
            }
        }
        catch ( PwmException e )
        {
            LOGGER.debug( pwmRequest, "ignoring existing existing " + cookieName + " cookie bean due to error: " + e.getMessage() );
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

        if ( cookieBean.getType() == PwmSessionBean.Type.AUTHENTICATED )
        {
            if ( cookieBean.getGuid() == null )
            {
                LOGGER.trace( pwmRequest, "disregarded existing " + cookieName + " cookie bean due to missing guid" );
                return false;
            }

            final String sessionGuid = pwmRequest.getPwmSession().getLoginInfoBean().getGuid();
            if ( !cookieBean.getGuid().equals( sessionGuid ) )
            {
                LOGGER.trace( pwmRequest, "disregarded existing " + cookieName + " cookie bean due to session change" );
                return false;
            }
        }

        if ( cookieBean.getType() == PwmSessionBean.Type.PUBLIC )
        {
            if ( cookieBean.getTimestamp() == null )
            {
                LOGGER.trace( pwmRequest, "disregarded existing " + cookieName + " cookie bean due to missing timestamp" );
                return false;
            }

            final TimeDuration cookieLifeDuration = TimeDuration.fromCurrent( cookieBean.getTimestamp() );
            final long maxIdleSeconds = pwmRequest.getConfig().readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
            if ( cookieLifeDuration.isLongerThan( maxIdleSeconds, TimeUnit.SECONDS ) )
            {
                LOGGER.trace( pwmRequest, "disregarded existing " + cookieName + " cookie bean due to outdated timestamp (" + cookieLifeDuration.asCompactString() + ")" );
                return false;
            }
        }

        return true;
    }


    public void saveSessionBeans( final PwmRequest pwmRequest )
    {
        if ( pwmRequest == null || pwmRequest.getPwmResponse().isCommitted() )
        {
            return;
        }
        try
        {
            if ( pwmRequest != null && pwmRequest.getPwmResponse() != null )
            {
                final Map<Class<? extends PwmSessionBean>, PwmSessionBean> beansInRequest = getRequestBeanMap( pwmRequest );
                if ( beansInRequest != null )
                {
                    for ( final Map.Entry<Class<? extends PwmSessionBean>, PwmSessionBean> entry : beansInRequest.entrySet() )
                    {
                        final Class<? extends PwmSessionBean> theClass = entry.getKey();
                        final String cookieName = nameForClass( theClass );
                        final PwmSessionBean bean = entry.getValue();
                        if ( bean == null )
                        {
                            pwmRequest.getPwmResponse().removeCookie( cookieName, COOKIE_PATH );
                        }
                        else
                        {
                            final PwmSecurityKey key = keyForSession( pwmRequest );
                            final String encrytedValue = pwmRequest.getPwmApplication().getSecureService().encryptObjectToString( entry.getValue(), key );
                            pwmRequest.getPwmResponse().writeCookie( cookieName, encrytedValue, -1, COOKIE_PATH );
                        }
                    }
                }
            }
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.error( pwmRequest, "error writing cookie bean to response: " + e.getMessage(), e );
        }
    }

    @Override
    public <E extends PwmSessionBean> void clearSessionBean( final PwmRequest pwmRequest, final Class<E> userBeanClass ) throws PwmUnrecoverableException
    {
        final Map<Class<? extends PwmSessionBean>, PwmSessionBean> sessionBeans = getRequestBeanMap( pwmRequest );
        sessionBeans.put( userBeanClass, null );
        saveSessionBeans( pwmRequest );
    }

    private static Map<Class<? extends PwmSessionBean>, PwmSessionBean> getRequestBeanMap( final PwmRequest pwmRequest )
    {
        Serializable sessionBeans = pwmRequest.getAttribute( PwmRequestAttribute.CookieBeanStorage );
        if ( sessionBeans == null )
        {
            sessionBeans = new HashMap<>();
            pwmRequest.setAttribute( PwmRequestAttribute.CookieBeanStorage, sessionBeans );
        }
        return ( Map<Class<? extends PwmSessionBean>, PwmSessionBean> ) sessionBeans;
    }

    private static String nameForClass( final Class<? extends PwmSessionBean> theClass )
    {
        return theClass.getSimpleName();
    }

    @Override
    public String getSessionStateInfo( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return null;
    }

    private PwmSecurityKey keyForSession( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PasswordData configKey = pwmRequest.getConfig().readSettingAsPassword( PwmSetting.PWM_SECURITY_KEY );
        final String userGuid = pwmRequest.getPwmSession().getLoginInfoBean().getGuid();
        return new PwmSecurityKey( configKey.getStringValue() + userGuid );
    }
}

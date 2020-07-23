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
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionStateService implements PwmService
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( SessionStateService.class );

    private SessionBeanProvider sessionBeanProvider = new LocalSessionBeanImpl();
    private final SessionBeanProvider httpSessionProvider = new LocalSessionBeanImpl();

    private SessionLoginProvider sessionLoginProvider = new LocalLoginSessionImpl();

    private final Map<Class<? extends PwmSessionBean>, PwmSessionBean> beanInstanceCache = new HashMap<>();

    @Override
    public STATUS status( )
    {
        return STATUS.OPEN;
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        {
            final SessionBeanMode sessionBeanMode = pwmApplication.getConfig().readSettingAsEnum( PwmSetting.SECURITY_MODULE_SESSION_MODE, SessionBeanMode.class );
            if ( sessionBeanMode != null )
            {
                switch ( sessionBeanMode )
                {
                    case LOCAL:
                        sessionBeanProvider = new LocalSessionBeanImpl();
                        break;

                    case CRYPTCOOKIE:
                        sessionBeanProvider = new CryptoCookieBeanImpl();
                        break;

                    case CRYPTREQUEST:
                        sessionBeanProvider = new CryptoRequestBeanImpl();
                        break;

                    default:
                        throw new IllegalStateException( "unhandled session bean state: " + sessionBeanMode );
                }
            }
        }

        {
            final SessionBeanMode loginSessionMode = pwmApplication.getConfig().readSettingAsEnum( PwmSetting.SECURITY_LOGIN_SESSION_MODE, SessionBeanMode.class );
            {
                if ( loginSessionMode != null )
                {
                    switch ( loginSessionMode )
                    {
                        case LOCAL:
                            sessionLoginProvider = new LocalLoginSessionImpl();
                            break;

                        case CRYPTCOOKIE:
                            sessionLoginProvider = new CryptoCookieLoginImpl();
                            break;

                        default:
                            JavaHelper.unhandledSwitchStatement( loginSessionMode );
                    }
                }
                sessionLoginProvider.init( pwmApplication );
            }
        }


        LOGGER.trace( () -> "initialized " + sessionBeanProvider.getClass().getName() + " provider" );
    }

    @Override
    public void close( )
    {

    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return null;
    }

    public <E extends PwmSessionBean> E getBean( final PwmRequest pwmRequest, final Class<E> theClass ) throws PwmUnrecoverableException
    {
        if ( theClass == null )
        {
            return null;
        }

        if ( beanSupportsMode( theClass, SessionBeanMode.CRYPTCOOKIE ) )
        {
            return sessionBeanProvider.getSessionBean( pwmRequest, theClass );
        }
        return httpSessionProvider.getSessionBean( pwmRequest, theClass );
    }

    public void clearBean( final PwmRequest pwmRequest, final Class<? extends PwmSessionBean> theClass ) throws PwmUnrecoverableException
    {
        if ( beanSupportsMode( theClass, SessionBeanMode.CRYPTCOOKIE ) )
        {
            sessionBeanProvider.clearSessionBean( pwmRequest, theClass );
            return;
        }
        httpSessionProvider.clearSessionBean( pwmRequest, theClass );
    }

    public void saveSessionBeans( final PwmRequest pwmRequest )
    {
        sessionBeanProvider.saveSessionBeans( pwmRequest );
    }

    public void clearLoginSession( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        sessionLoginProvider.clearLoginSession( pwmRequest );
    }

    public void saveLoginSessionState( final PwmRequest pwmRequest )
    {
        sessionLoginProvider.saveLoginSessionState( pwmRequest );
    }

    public void readLoginSessionState( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        sessionLoginProvider.readLoginSessionState( pwmRequest );
    }

    public String getSessionStateInfo( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return sessionBeanProvider.getSessionStateInfo( pwmRequest );
    }


    private boolean beanSupportsMode( final Class<? extends PwmSessionBean> theClass, final SessionBeanMode mode ) throws PwmUnrecoverableException
    {
        if ( theClass == null )
        {
            return false;
        }
        if ( !beanInstanceCache.containsKey( theClass ) )
        {
            beanInstanceCache.put( theClass, newBean( null, theClass ) );
        }
        try
        {
            return theClass.newInstance().supportedModes().contains( mode );
        }
        catch ( final InstantiationException | IllegalAccessException e )
        {
            e.printStackTrace();
        }
        return false;
    }

    static <E extends PwmSessionBean> E newBean( final String sessionGuid, final Class<E> theClass ) throws PwmUnrecoverableException
    {
        try
        {
            final E newBean = theClass.newInstance();
            newBean.setGuid( sessionGuid );
            newBean.setTimestamp( Instant.now() );
            return newBean;
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error trying to instantiate bean class " + theClass.getName() + ": " + e.getMessage();
            LOGGER.error( () -> errorMsg, e );
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, errorMsg );
        }
    }
}

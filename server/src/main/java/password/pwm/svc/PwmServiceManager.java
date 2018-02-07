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

package password.pwm.svc;

import password.pwm.PwmApplication;
import password.pwm.PwmEnvironment;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PwmServiceManager
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmServiceManager.class );


    private final PwmApplication pwmApplication;
    private final Map<Class<? extends PwmService>, PwmService> runningServices = new HashMap<>();
    private boolean initialized;

    public PwmServiceManager( final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
    }

    public PwmService getService( final Class<? extends PwmService> serviceClass )
    {
        return runningServices.get( serviceClass );
    }

    public void initAllServices( )
            throws PwmUnrecoverableException
    {

        final boolean internalRuntimeInstance = pwmApplication.getPwmEnvironment().isInternalRuntimeInstance()
                || pwmApplication.getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.CommandLineInstance );

        for ( final PwmServiceEnum serviceClassEnum : PwmServiceEnum.values() )
        {
            boolean startService = true;
            if ( internalRuntimeInstance && !serviceClassEnum.isInternalRuntime() )
            {
                startService = false;
            }
            if ( startService )
            {
                final Class<? extends PwmService> serviceClass = serviceClassEnum.getPwmServiceClass();
                final PwmService newServiceInstance = initService( serviceClass );
                runningServices.put( serviceClass, newServiceInstance );
            }
        }
        initialized = true;
    }

    private PwmService initService( final Class<? extends PwmService> serviceClass )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final PwmService newServiceInstance;
        final String serviceName = serviceClass.getName();
        try
        {
            final Object newInstance = serviceClass.newInstance();
            newServiceInstance = ( PwmService ) newInstance;
        }
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error instantiating service class '" + serviceName + "', error: " + e.toString();
            LOGGER.fatal( errorMsg, e );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg ) );
        }

        try
        {
            LOGGER.debug( "initializing service " + serviceName );
            newServiceInstance.init( pwmApplication );
            final TimeDuration startupDuration = TimeDuration.fromCurrent( startTime );
            LOGGER.debug( "completed initialization of service " + serviceName + " in " + startupDuration.asCompactString() + ", status=" + newServiceInstance.status() );
        }
        catch ( PwmException e )
        {
            LOGGER.warn( "error instantiating service class '" + serviceName + "', service will remain unavailable, error: " + e.getMessage() );
        }
        catch ( Exception e )
        {
            String errorMsg = "unexpected error instantiating service class '" + serviceName + "', cannot load, error: " + e.getMessage();
            if ( e.getCause() != null )
            {
                errorMsg += ", cause: " + e.getCause();
            }
            LOGGER.fatal( errorMsg );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg ) );
        }
        return newServiceInstance;
    }

    public void shutdownAllServices( )
    {
        if ( !initialized )
        {
            return;
        }

        final List<Class<? extends PwmService>> reverseServiceList = new ArrayList<>( PwmServiceEnum.allClasses() );
        Collections.reverse( reverseServiceList );
        for ( final Class<? extends PwmService> serviceClass : reverseServiceList )
        {
            if ( runningServices.containsKey( serviceClass ) )
            {
                shutDownService( serviceClass );
            }
        }
        initialized = false;
    }

    private void shutDownService( final Class<? extends PwmService> serviceClass )
    {
        LOGGER.trace( "closing service " + serviceClass.getName() );
        final PwmService loopService = runningServices.get( serviceClass );
        LOGGER.trace( "successfully closed service " + serviceClass.getName() );
        try
        {
            loopService.close();
        }
        catch ( Exception e )
        {
            LOGGER.error( "error closing " + loopService.getClass().getSimpleName() + ": " + e.getMessage(), e );
        }
    }

    public List<PwmService> getRunningServices( )
    {
        return Collections.unmodifiableList( new ArrayList<>( this.runningServices.values() ) );
    }
}

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
        final Instant startTime = Instant.now();

        final boolean internalRuntimeInstance = pwmApplication.getPwmEnvironment().isInternalRuntimeInstance()
                || pwmApplication.getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.CommandLineInstance );

        int serviceCounter = 0;

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
                serviceCounter++;
            }
        }

        initialized = true;

        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
        {
            final int finalServiceCounter = serviceCounter;
            LOGGER.trace( () -> "started " + finalServiceCounter + " services in " + timeDuration.asCompactString() );
        }
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
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error instantiating service class '" + serviceName + "', error: " + e.toString();
            LOGGER.fatal( () -> errorMsg, e );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg ) );
        }

        try
        {
            LOGGER.debug( () -> "initializing service " + serviceName );
            newServiceInstance.init( pwmApplication );
            final TimeDuration startupDuration = TimeDuration.fromCurrent( startTime );
            LOGGER.debug( () -> "completed initialization of service " + serviceName + " in " + startupDuration.asCompactString() + ", status=" + newServiceInstance.status() );
        }
        catch ( final PwmException e )
        {
            LOGGER.warn( () -> "error instantiating service class '" + serviceName + "', service will remain unavailable, error: " + e.getMessage() );
        }
        catch ( final Exception e )
        {
            String errorMsg = "unexpected error instantiating service class '" + serviceName + "', cannot load, error: " + e.getMessage();
            if ( e.getCause() != null )
            {
                errorMsg += ", cause: " + e.getCause();
            }
            final String errorMsgFinal = errorMsg;
            LOGGER.fatal( () -> errorMsgFinal );
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

        LOGGER.trace( () -> "beginning to close all services" );
        final Instant startTime = Instant.now();


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

        LOGGER.trace( () -> "closed all services in " + TimeDuration.compactFromCurrent( startTime ) );
    }

    private void shutDownService( final Class<? extends PwmService> serviceClass )
    {

        LOGGER.trace( () -> "closing service " + serviceClass.getName() );
        final PwmService loopService = runningServices.get( serviceClass );
        try
        {
            final Instant startTime = Instant.now();
            loopService.close();
            final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
            LOGGER.trace( () -> "successfully closed service " + serviceClass.getName() + " (" + timeDuration.asCompactString() + ")" );
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error closing " + loopService.getClass().getSimpleName() + ": " + e.getMessage(), e );
        }
    }

    public List<PwmService> getRunningServices( )
    {
        return Collections.unmodifiableList( new ArrayList<>( this.runningServices.values() ) );
    }
}

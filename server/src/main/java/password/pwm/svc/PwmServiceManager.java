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

package password.pwm.svc;

import password.pwm.PwmApplication;
import password.pwm.PwmEnvironment;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class PwmServiceManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmServiceManager.class );

    private final DomainID domainID;
    private final PwmApplication pwmApplication;
    private final Map<PwmServiceEnum, PwmService> runningServices = new ConcurrentHashMap<>();
    private final List<PwmServiceEnum> availableServices;
    private final SessionLabel sessionLabel;

    private boolean initialized;

    public PwmServiceManager(
            final SessionLabel sessionLabel,
            final PwmApplication pwmApplication,
            final DomainID domainID,
            final List<PwmServiceEnum> services
    )
    {
        this.sessionLabel = sessionLabel;
        this.domainID = Objects.requireNonNull( domainID );
        this.availableServices = List.copyOf( services );
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
    }

    private enum InitializationStats
    {
        starts,
        stops,
        restarts,
    }

    public PwmService getService( final PwmServiceEnum serviceClass )
    {
        return runningServices.get( serviceClass );
    }

    public void initAllServices()
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final boolean internalRuntimeInstance = pwmApplication.getPwmEnvironment().isInternalRuntimeInstance()
                || pwmApplication.getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.CommandLineInstance );

        final String logVerb = initialized ? "restart" : "start";
        final StatisticCounterBundle<InitializationStats> statCounter = new StatisticCounterBundle<>( InitializationStats.class );
        LOGGER.trace( sessionLabel, () -> "beginning service " + logVerb + " process" );

        for ( final PwmServiceEnum serviceClassEnum : availableServices )
        {
            boolean serviceShouldBeRunning = true;
            if ( internalRuntimeInstance && !serviceClassEnum.isInternalRuntime() )
            {
                serviceShouldBeRunning = false;
            }

            if ( serviceShouldBeRunning )
            {
                if ( runningServices.containsKey( serviceClassEnum ) )
                {
                    shutDownService( serviceClassEnum, runningServices.get( serviceClassEnum ) );
                    statCounter.increment( InitializationStats.restarts );
                }
                else
                {
                    statCounter.increment( InitializationStats.starts );
                }

                final PwmService newServiceInstance = initService( serviceClassEnum );
                runningServices.put( serviceClassEnum, newServiceInstance );
            }
            else
            {
                if ( runningServices.containsKey( serviceClassEnum ) )
                {
                    shutDownService( serviceClassEnum, runningServices.get( serviceClassEnum ) );
                    statCounter.increment( InitializationStats.stops );
                }
            }
        }

        initialized = true;

        LOGGER.trace( sessionLabel, () -> logVerb + "ed services, " + statCounter.debugStats(), TimeDuration.fromCurrent( startTime ) );
    }

    private String debugSvcType()
    {
        return ( domainID.isSystem() ? "system" : "domain" ) + " service";
    }

    private PwmService initService( final PwmServiceEnum pwmServiceEnum )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final PwmService newServiceInstance;

        final String serviceName = pwmServiceEnum.serviceName( domainID );

        try
        {
            final Class<? extends PwmService> serviceClass = pwmServiceEnum.getPwmServiceClass();
            newServiceInstance = serviceClass.getDeclaredConstructor().newInstance();
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error instantiating " + debugSvcType() + " class '" + serviceName + "', error: " + e;
            LOGGER.fatal( () -> errorMsg, e );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg ) );
        }

        try
        {
            LOGGER.trace( sessionLabel, () -> "initializing service " + serviceName );
            newServiceInstance.init( pwmApplication, domainID );
            final TimeDuration startupDuration = TimeDuration.fromCurrent( startTime );
            LOGGER.debug( sessionLabel, () -> "completed initialization of " + debugSvcType()
                    + " " + serviceName + " in " + startupDuration.asCompactString()
                    + ", status=" + newServiceInstance.status() );
        }
        catch ( final PwmException e )
        {
            LOGGER.warn( sessionLabel, () -> "error instantiating " + debugSvcType() + " class '" + serviceName + "', service will remain unavailable, error: " + e.getMessage() );
        }
        catch ( final Exception e )
        {
            String errorMsg = "unexpected error instantiating " + debugSvcType() + " class '" + serviceName + "', cannot load, error: " + e.getMessage();
            if ( e.getCause() != null )
            {
                errorMsg += ", cause: " + e.getCause();
            }
            final String errorMsgFinal = errorMsg;
            LOGGER.fatal( sessionLabel, () -> errorMsgFinal );
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

        LOGGER.trace( sessionLabel, () -> "beginning to close all services" );
        final Instant startTime = Instant.now();


        final List<PwmServiceEnum> reverseServiceList = new ArrayList<>( availableServices );
        Collections.reverse( reverseServiceList );
        for ( final PwmServiceEnum pwmServiceEnum : reverseServiceList )
        {
            if ( runningServices.containsKey( pwmServiceEnum ) )
            {
                shutDownService( pwmServiceEnum, runningServices.get( pwmServiceEnum ) );
            }
        }
        initialized = false;

        LOGGER.trace( sessionLabel, () -> "closed all services", TimeDuration.fromCurrent( startTime ) );
    }

    private void shutDownService( final PwmServiceEnum pwmServiceEnum, final PwmService serviceInstance )
    {

        LOGGER.trace( sessionLabel, () -> "closing " + debugSvcType() + " " + pwmServiceEnum.serviceName( domainID ) );

        try
        {
            final Instant startTime = Instant.now();
            serviceInstance.shutdown();
            final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
            LOGGER.trace( sessionLabel, () -> "successfully closed " + debugSvcType() + " " + pwmServiceEnum.serviceName( domainID )
                    + " (" + timeDuration.asCompactString() + ")" );
        }
        catch ( final Exception e )
        {
            LOGGER.error( sessionLabel, () -> "error closing " + debugSvcType() + " " + pwmServiceEnum.serviceName( domainID ) + ": " + e.getMessage(), e );
        }
    }

    public List<PwmService> getRunningServices( )
    {
        return List.copyOf( this.runningServices.values() );
    }
}

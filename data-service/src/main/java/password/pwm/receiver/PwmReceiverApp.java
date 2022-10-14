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

package password.pwm.receiver;

import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StatisticRateBundle;
import password.pwm.util.java.StringUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class PwmReceiverApp
{
    private static final PwmReceiverLogger LOGGER = PwmReceiverLogger.forClass( PwmReceiverApp.class );
    private static final String ENV_NAME = "DATA_SERVICE_PROPS";

    private Storage storage;
    private Settings settings;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor( THREAD_FACTORY );
    private final Status status = new Status();
    private final StatisticCounterBundle<CounterStatsKey> statisticCounterBundle = new StatisticCounterBundle<>( CounterStatsKey.class );
    private final StatisticRateBundle<EpsStatKey> statisticRateBundle = new StatisticRateBundle<>( EpsStatKey.class );
    private final Instant startupTime = Instant.now();

    private static final ThreadFactory THREAD_FACTORY = makeThreadFactory();

    public enum EpsStatKey
    {
        VersionCheckRequests,
        TelemetryPublishRequests,
        TelemetryViewRequests,
    }

    public enum CounterStatsKey
    {
        VersionCheckRequests,
        TelemetryPublishRequests,
        TelemetryViewRequests,
    }


    public PwmReceiverApp( )
    {
        final String propsFile = System.getenv( ENV_NAME );
        if ( StringUtil.isEmpty( propsFile ) )
        {
            final String errorMsg = "Missing environment variable '" + ENV_NAME + "', can't load configuration";
            status.setErrorState( errorMsg );
            LOGGER.error( errorMsg );
            return;
        }

        try
        {
            settings = Settings.readFromFile( propsFile );
        }
        catch ( final IOException e )
        {
            final String errorMsg = "can't read configuration: " + JavaHelper.readHostileExceptionMessage( e );
            status.setErrorState( errorMsg );
            LOGGER.error( errorMsg, e );
            return;
        }

        try
        {
            storage = new Storage( settings );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "can't start storage system: " + JavaHelper.readHostileExceptionMessage( e );
            status.setErrorState( errorMsg );
            LOGGER.error( errorMsg, e );
            return;
        }

        if ( settings.getSetting( Settings.Setting.ftpSite ) != null && !settings.getSetting( Settings.Setting.ftpSite ).isEmpty() )
        {
            final Runnable ftpThread = ( ) ->
            {
                final FtpDataIngestor ftpDataIngestor = new FtpDataIngestor( this, settings );
                ftpDataIngestor.readData( storage );
            };
            scheduledExecutorService.scheduleAtFixedRate( ftpThread, 0, 1, TimeUnit.HOURS );
        }
    }

    public Settings getSettings( )
    {
        return settings;
    }

    public Storage getStorage( )
    {
        return storage;
    }

    void close( )
    {
        storage.close();
        scheduledExecutorService.shutdownNow();
    }

    public Status getStatus( )
    {
        return status;
    }

    public StatisticCounterBundle<CounterStatsKey> getStatisticCounterBundle()
    {
        return statisticCounterBundle;
    }

    public StatisticRateBundle<EpsStatKey> getStatisticEpsBundle()
    {
        return statisticRateBundle;
    }

    public Instant getStartupTime()
    {
        return startupTime;
    }

    private static ThreadFactory makeThreadFactory()
    {
        return new ThreadFactory()
        {
            private final AtomicLoopIntIncrementer counter = new AtomicLoopIntIncrementer();

            @Override
            public Thread newThread( final Runnable runnable )
            {
                final Thread t = new Thread( runnable );
                t.setDaemon( true );
                t.setName( PwmReceiverApp.class.getName() + "-" + counter.next() );
                return t;
            }
        };
    }
}

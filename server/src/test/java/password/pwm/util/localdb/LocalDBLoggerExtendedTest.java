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

package password.pwm.util.localdb;

import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import password.pwm.AppProperty;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.util.EventRateMeter;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.Percent;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.LocalDBLoggerSettings;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogMessage;
import password.pwm.util.secure.PwmRandom;

import java.io.File;
import java.io.Serializable;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class LocalDBLoggerExtendedTest
{

    private final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    private LocalDBLogger localDBLogger;
    private LocalDB localDB;
    private AppConfig config;

    private final AtomicInteger eventsAdded = new AtomicInteger( 0 );

    private final EventRateMeter eventRateMeter = new EventRateMeter( TimeDuration.of( 60, TimeDuration.Unit.SECONDS ).asDuration() );

    private static Settings settings;
    private Instant startTime;

    @TempDir
    public Path temporaryFolder;


    @BeforeEach
    public void setUp() throws Exception
    {
        final File localDBPath = FileSystemUtility.createDirectory( temporaryFolder, "test-localdb-logger-test" );

        config = AppConfig.forStoredConfig( StoredConfigurationFactory.newConfig() );

        localDB = LocalDBFactory.getInstance(
                localDBPath,
                false,
                null,
                config
        );

        //localDB.truncate(LocalDB.DB.EVENTLOG_EVENTS);
        //System.out.println(localDB.size(LocalDB.DB.EVENTLOG_EVENTS));
        //new TimeDuration(1,TimeUnit.HOURS).pause();

        // open localDBLogger based on configuration settings;
        {
            final LocalDBLoggerSettings settings = LocalDBLoggerSettings.builder()
                    .maxEvents( Integer.MAX_VALUE )
                    .maxAge( TimeDuration.of( 1, TimeDuration.Unit.MINUTES ) )
                    .flags( Collections.emptySet() )
                    .build();
            localDBLogger = new LocalDBLogger( null, localDB, PwmLogLevel.TRACE, settings );
        }

        settings = Settings.builder()
                .threads( 10 )
                .testDuration( TimeDuration.of( 1, TimeDuration.Unit.MINUTES ) )
                .valueLength( 5000 )
                .batchSize( 100 )
                .build();
    }

    private void out( final String output )
    {
        //System.out.println( JavaHelper.toIsoDate( new Date() ) + " " + output );
    }

    @Test
    public void testBulkAddEvents() throws InterruptedException
    {
        out( "starting bulk add...  " );
        out( "settings=" + JsonFactory.get().serialize( settings ) );
        startTime = Instant.now();
        final Timer timer = new Timer();

        final int threadCount = settings.threads;
        final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                threadCount,
                threadCount,
                1,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>( threadCount + 1 )
        );

        timer.scheduleAtFixedRate( new DebugOutputTimerTask(), 5 * 1000, 30 * 1000 );

        for ( int loopCount = 0; loopCount < threadCount; loopCount++ )
        {
            threadPoolExecutor.execute( new PopulatorThread() );
        }

        threadPoolExecutor.shutdown();
        threadPoolExecutor.awaitTermination( 1, TimeUnit.DAYS );
        timer.cancel();
        out( "bulk operations completed" );
        out( "settings=" + JsonFactory.get().serialize( settings ) );
        out( " results=" + JsonFactory.get().serialize( makeResults() ) );
        outputDebugInfo();
    }

    private class PopulatorThread extends Thread
    {
        @Override
        public void run()
        {
            final RandomValueMaker randomValueMaker = new RandomValueMaker( settings.valueLength );
            while ( TimeDuration.fromCurrent( startTime ).isShorterThan( settings.testDuration ) )
            {
                final Collection<PwmLogMessage> events = makeEvents( randomValueMaker );
                for ( final PwmLogMessage logEvent : events )
                {
                    localDBLogger.writeEvent( logEvent );
                    eventRateMeter.markEvent();
                    eventsAdded.incrementAndGet();
                }
            }
        }
    }

    private Collection<PwmLogMessage> makeEvents( final RandomValueMaker randomValueMaker )
    {
        final int count = settings.batchSize;
        final Collection<PwmLogMessage> events = new ArrayList<>();
        for ( int i = 0; i < count; i++ )
        {
            final Supplier<? extends CharSequence> description = ( Supplier<CharSequence> ) randomValueMaker::next;
            final PwmLogMessage event = PwmLogMessage.create(
                    Instant.now(),
                    LocalDBLogger.class.getName(),
                    PwmLogLevel.TRACE,
                    SessionLabel.TEST_SESSION_LABEL,
                    description,
                    TimeDuration.ZERO,
                    null,
                    "threadName" );
            events.add( event );
        }

        return events;
    }

    private void outputDebugInfo()
    {
        final Map<String, String> debugParams = new HashMap<>( Map.of(
                "size", StringUtil.formatDiskSize( FileSystemUtility.getFileDirectorySize( localDB.getFileLocation() ) ),
                "eventsInDb", figureEventsInDbStat(),
                "free", StringUtil.formatDiskSize( FileSystemUtility.diskSpaceRemaining( localDB.getFileLocation() ) ),
                "eps", eventRateMeter.rawEps().setScale( 0, RoundingMode.UP ).toString(),
                "remain", settings.testDuration.subtract( TimeDuration.fromCurrent( startTime ) ).asCompactString() ) );
        localDBLogger.getTailDate().ifPresent( tailDate -> debugParams.put( "tail", TimeDuration.compactFromCurrent( tailDate ) ) );
        out( "added " + StringUtil.mapToString( debugParams ) );
    }

    private String figureEventsInDbStat()
    {
        final long maxEvents = config.readSettingAsLong( PwmSetting.EVENTS_PWMDB_MAX_EVENTS );
        final long eventCount = localDBLogger.getStoredEventCount();
        final Percent percent = Percent.of( eventCount, maxEvents );
        return numberFormat.format( localDBLogger.getStoredEventCount() ) + "/" + numberFormat.format( maxEvents )
                + " (" + percent.pretty( 2 ) + ")";
    }

    private Results makeResults()
    {
        return Results.builder()
                .dbClass( config.readAppProperty( AppProperty.LOCALDB_IMPLEMENTATION ) )
                .duration( TimeDuration.fromCurrent( startTime ).asCompactString() )
                .recordsAdded( eventsAdded.get() )
                .dbSize( StringUtil.formatDiskSize( FileSystemUtility.getFileDirectorySize( localDB.getFileLocation() ) ) )
                .eventsInDb( figureEventsInDbStat() )
                .build();
    }

    private class DebugOutputTimerTask extends TimerTask
    {
        @Override
        public void run()
        {
            outputDebugInfo();
        }
    }

    @Value
    @Builder
    private static class Settings implements Serializable
    {
        private TimeDuration testDuration;
        private int threads;
        private int valueLength;
        private int batchSize;
    }

    @Value
    @Builder
    private static class Results implements Serializable
    {
        private String dbClass;
        private String duration;
        private int recordsAdded;
        private String dbSize;
        private String eventsInDb;
    }

    private static class RandomValueMaker
    {
        private int outputLength;
        final StringBuilder randomValue = new StringBuilder();
        final Random random = new Random();

        RandomValueMaker( final int outputLength )
        {
            this.outputLength = outputLength;
            randomValue.append( PwmRandom.getInstance().alphaNumericString( outputLength * 50 ) );
        }

        public String next()
        {
            final int randomPos = random.nextInt( randomValue.length() - 1 );
            randomValue.replace( randomPos, randomPos + 1, String.valueOf( random.nextInt( 9 ) ) );

            final int startPos = random.nextInt( randomValue.length() - outputLength );
            final int endPos = startPos + outputLength;


            return randomValue.substring( startPos, endPos );
        }
    }
}

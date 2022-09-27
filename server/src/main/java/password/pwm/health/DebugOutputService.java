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

package password.pwm.health;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.debug.DebugItemGenerator;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogManager;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

public class DebugOutputService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DebugOutputService.class );


    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {

        scheduleNextZipOutput();

        final TimeDuration threadDumpInterval = TimeDuration.of(
            Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.LOGGING_EXTRA_PERIODIC_THREAD_DUMP_INTERVAL ) ), TimeDuration.Unit.SECONDS );

        if ( threadDumpInterval.as( TimeDuration.Unit.SECONDS ) > 0 )
        {
            scheduleFixedRateJob(
                    new ThreadDumpLogger( getSessionLabel() ),
                    TimeDuration.SECOND,
                    threadDumpInterval );
        }

        return STATUS.OPEN;
    }


    @Override
    public void shutdownImpl( )
    {
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder()
                .build();
    }


    private void scheduleNextZipOutput()
    {
        final int intervalSeconds = JavaHelper.silentParseInt( getPwmApplication().getConfig().readAppProperty( AppProperty.HEALTH_SUPPORT_BUNDLE_WRITE_INTERVAL_SECONDS ), 0 );
        if ( intervalSeconds > 0 )
        {
            final TimeDuration intervalDuration = TimeDuration.of( intervalSeconds, TimeDuration.Unit.SECONDS );
            scheduleJob( new SupportZipFileWriter( getPwmApplication() ), intervalDuration );
        }
    }

    private class SupportZipFileWriter implements Runnable
    {
        private final PwmApplication pwmApplication;

        SupportZipFileWriter( final PwmApplication pwmApplication )
        {
            this.pwmApplication = pwmApplication;
        }

        @Override
        public void run()
        {
            PwmLogManager.executeWithThreadSessionData( getSessionLabel(), () ->
                    {
                        try
                        {
                            writeSupportZipToAppPath();
                        }
                        catch ( final Exception e )
                        {
                            LOGGER.debug( getSessionLabel(), () -> "error writing support zip to file system: " + e.getMessage() );
                        }

                        scheduleNextZipOutput();
                    } );

        }

        private void writeSupportZipToAppPath()
                throws IOException, PwmUnrecoverableException
        {
            final File appPath = getPwmApplication().getPwmEnvironment().getApplicationPath();
            if ( !appPath.exists() )
            {
                return;
            }

            final int rotationCount = JavaHelper.silentParseInt( pwmApplication.getConfig().readAppProperty( AppProperty.HEALTH_SUPPORT_BUNDLE_FILE_WRITE_COUNT ), 10 );
            final DebugItemGenerator debugItemGenerator = new DebugItemGenerator( pwmApplication, getSessionLabel() );

            final File supportPath = new File( appPath.getPath() + File.separator + "support" );

            Files.createDirectories( supportPath.toPath() );

            final File supportFile = new File ( supportPath.getPath() + File.separator + debugItemGenerator.getFilename() );

            FileSystemUtility.rotateBackups( supportFile, rotationCount );

            final File newSupportFile = new File ( supportFile.getPath() + ".new" );
            Files.deleteIfExists( newSupportFile.toPath() );

            try ( ZipOutputStream zipOutputStream = new ZipOutputStream( new FileOutputStream( newSupportFile ) ) )
            {
                LOGGER.trace( getSessionLabel(), () -> "beginning periodic support bundle filesystem output" );
                debugItemGenerator.outputZipDebugFile( zipOutputStream );
            }

            Files.move( newSupportFile.toPath(), supportFile.toPath() );
        }
    }

    private static class ThreadDumpLogger implements Runnable
    {
        private static final PwmLogger LOGGER = PwmLogger.forClass( ThreadDumpLogger.class );
        private static final AtomicLoopIntIncrementer COUNTER = new AtomicLoopIntIncrementer();

        private final SessionLabel sessionLabel;

        ThreadDumpLogger( final SessionLabel sessionLabel )
        {
            this.sessionLabel = sessionLabel;
        }

        @Override
        public void run()
        {
            if ( !LOGGER.isInterestingLevel( PwmLogLevel.TRACE ) )
            {
                return;
            }

            final int count = COUNTER.next();
            output( "---BEGIN OUTPUT THREAD DUMP #" + count + "---" );

            {
                final Map<String, String> debugValues = new LinkedHashMap<>();
                debugValues.put( "Memory Free", Long.toString( Runtime.getRuntime().freeMemory() ) );
                debugValues.put( "Memory Allocated", Long.toString( Runtime.getRuntime().totalMemory() ) );
                debugValues.put( "Memory Max", Long.toString( Runtime.getRuntime().maxMemory() ) );
                debugValues.put( "Thread Count", Integer.toString( Thread.activeCount() ) );
                output( "Thread Data #: " + StringUtil.mapToString( debugValues ) );
            }

            final ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads( true, true );
            for ( final ThreadInfo threadInfo : threads )
            {
                output( JavaHelper.threadInfoToString( threadInfo ) );
            }

            output( "---END OUTPUT THREAD DUMP #" + count + "---" );
        }

        private void output( final CharSequence output )
        {
            LOGGER.trace( sessionLabel, () -> output );
        }
    }
}

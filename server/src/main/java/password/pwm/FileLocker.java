/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm;

import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.time.Instant;
import java.util.Properties;

class FileLocker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( FileLocker.class );

    private final PwmEnvironment pwmEnvironment;
    private FileLock lock;
    private final File lockfile;

    FileLocker( final PwmEnvironment pwmEnvironment )
    {
        this.pwmEnvironment = pwmEnvironment;
        final String lockfileName = pwmEnvironment.getConfig().readAppProperty( AppProperty.APPLICATION_FILELOCK_FILENAME );
        lockfile = new File( pwmEnvironment.getApplicationPath(), lockfileName );
    }

    private boolean lockingAllowed( )
    {
        return !pwmEnvironment.isInternalRuntimeInstance() && !pwmEnvironment.getFlags().contains( PwmEnvironment.ApplicationFlag.NoFileLock );
    }

    public boolean isLocked( )
    {
        return !lockingAllowed() || lock != null && lock.isValid();
    }

    public void attemptFileLock( )
    {
        if ( lockingAllowed() && !isLocked() )
        {
            try
            {
                final RandomAccessFile file = new RandomAccessFile( lockfile, "rw" );
                final FileChannel f = file.getChannel();
                lock = f.tryLock();
                if ( lock != null )
                {
                    LOGGER.debug( () -> "obtained file lock on file " + lockfile.getAbsolutePath() + " lock is valid=" + lock.isValid() );
                    writeLockFileContents( file );
                }
                else
                {
                    LOGGER.debug( () -> "unable to obtain file lock on file " + lockfile.getAbsolutePath() );
                }
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unable to obtain file lock on file " + lockfile.getAbsolutePath() + " due to error: " + e.getMessage() );
            }
        }
    }

    void writeLockFileContents( final RandomAccessFile file )
    {
        try
        {
            final Properties props = new Properties();
            props.put( "timestamp", JavaHelper.toIsoDate( Instant.now() ) );
            props.put( "applicationPath", pwmEnvironment.getApplicationPath() == null ? "n/a" : pwmEnvironment.getApplicationPath().getAbsolutePath() );
            props.put( "configurationFile", pwmEnvironment.getConfigurationFile() == null ? "n/a" : pwmEnvironment.getConfigurationFile().getAbsolutePath() );
            final String comment = PwmConstants.PWM_APP_NAME + " file lock";
            final StringWriter stringWriter = new StringWriter();
            props.store( stringWriter, comment );
            file.write( stringWriter.getBuffer().toString().getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
        catch ( final IOException e )
        {
            LOGGER.error( () -> "unable to write contents of application lock file: " + e.getMessage() );
        }
        // do not close FileWriter, otherwise lock is released.
    }

    public void releaseFileLock( )
    {
        if ( lock != null && lock.isValid() )
        {
            try
            {
                lock.release();
            }
            catch ( final IOException e )
            {
                LOGGER.error( () -> "error releasing file lock: " + e.getMessage() );
            }

            LOGGER.debug( () -> "released file lock on file " + lockfile.getAbsolutePath() );
        }
    }

    public void waitForFileLock( )
            throws PwmUnrecoverableException
    {
        final Configuration configuration = pwmEnvironment.getConfig();
        final int maxWaitSeconds = pwmEnvironment.getFlags().contains( PwmEnvironment.ApplicationFlag.CommandLineInstance )
                ? 1
                : Integer.parseInt( configuration.readAppProperty( AppProperty.APPLICATION_FILELOCK_WAIT_SECONDS ) );
        final Instant startTime = Instant.now();
        final TimeDuration attemptInterval = TimeDuration.of( 5021, TimeDuration.Unit.MILLISECONDS );

        while ( !isLocked() && TimeDuration.fromCurrent( startTime ).isShorterThan( maxWaitSeconds, TimeDuration.Unit.SECONDS ) )
        {
            attemptFileLock();

            if ( !isLocked() )
            {
                LOGGER.debug( () -> "can't establish application file lock after "
                        + TimeDuration.fromCurrent( startTime ).asCompactString()
                        + ", will retry;" );
                attemptInterval.pause();
            }
        }

        if ( !isLocked() )
        {
            final String errorMsg = "unable to obtain application path file lock";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }
}

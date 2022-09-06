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

import password.pwm.AppProperty;
import password.pwm.PwmEnvironment;
import password.pwm.config.AppConfig;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Factory for building {@link LocalDB} instances.
 * @author Jason D. Rivard
 */
public class LocalDBFactory
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LocalDBFactory.class );
    private static final Lock CREATION_LOCK = new ReentrantLock();

    public static LocalDB getInstance(
            final File dbDirectory,
            final boolean readonly,
            final PwmEnvironment pwmEnvironment,
            final AppConfig appConfig
    )
            throws Exception
    {
        CREATION_LOCK.lock();
        try
        {
            final AppConfig config = ( appConfig == null && pwmEnvironment != null )
                    ? pwmEnvironment.getConfig()
                    : appConfig;

            final Instant startTime = Instant.now();

            final String className;
            final Map<String, String> initParameters;
            if ( config == null )
            {
                className = AppProperty.LOCALDB_IMPLEMENTATION.getDefaultValue();
                final String initStrings = AppProperty.LOCALDB_INIT_STRING.getDefaultValue();
                initParameters = StringUtil.convertStringListToNameValuePair( Arrays.asList( initStrings.split( ";;;" ) ), "=" );
            }
            else
            {
                className = config.readAppProperty( AppProperty.LOCALDB_IMPLEMENTATION );
                final String initStrings = config.readAppProperty( AppProperty.LOCALDB_INIT_STRING );
                initParameters = StringUtil.convertStringListToNameValuePair( Arrays.asList( initStrings.split( ";;;" ) ), "=" );
            }

            final Map<LocalDBProvider.Parameter, String> parameters = pwmEnvironment == null
                    ? Collections.emptyMap()
                    : makeParameterMap( pwmEnvironment.getConfig(), readonly );
            final LocalDBProvider dbProvider = createInstance( className );
            LOGGER.debug( () -> "initializing " + className + " localDBProvider instance" );

            final LocalDB localDB = new LocalDBAdaptor( dbProvider );

            initInstance( dbProvider, dbDirectory, initParameters, className, parameters );

            if ( !readonly )
            {
                LOGGER.trace( () -> "clearing TEMP db" );
                localDB.truncate( LocalDB.DB.TEMP );

                final LocalDBUtility localDBUtility = new LocalDBUtility( localDB );
                if ( localDBUtility.readImportInprogressFlag() )
                {
                    LOGGER.error( () -> "previous database import process did not complete successfully, clearing all data" );
                    localDBUtility.cancelImportProcess();
                }
            }

            logInstanceCreation( dbDirectory, readonly, startTime, localDB );

            return localDB;
        }
        finally
        {
            CREATION_LOCK.unlock();
        }
    }

    private static void logInstanceCreation( final File dbDirectory, final boolean readonly, final Instant startTime, final LocalDB localDB )
    {
        LOGGER.info( () ->
        {
            final StringBuilder debugText = new StringBuilder();
            debugText.append( "LocalDB open" );

            if ( readonly )
            {
                debugText.append( " (read-only)" );
            }

            if ( localDB.getFileLocation() != null )
            {
                debugText.append( ", db size: " ).append( StringUtil.formatDiskSize( FileSystemUtility.getFileDirectorySize( localDB.getFileLocation() ) ) );
                debugText.append( " at " ).append( dbDirectory.toString() );
                final long freeSpace = FileSystemUtility.diskSpaceRemaining( localDB.getFileLocation() );
                if ( freeSpace >= 0 )
                {
                    debugText.append( ", " ).append( StringUtil.formatDiskSize( freeSpace ) ).append( " free" );
                }
            }
            return debugText.toString();
        }, TimeDuration.fromCurrent( startTime ) );
    }

    private static LocalDBProvider createInstance( final String className )
            throws Exception
    {
        final LocalDBProvider localDB;
        try
        {
            final Class c = Class.forName( className );
            final Object impl = c.getDeclaredConstructor().newInstance();
            if ( !( impl instanceof LocalDBProvider ) )
            {
                throw new Exception( "unable to createSharedHistoryManager new LocalDB, " + className + " is not instance of " + LocalDBProvider.class.getName() );
            }
            localDB = ( LocalDBProvider ) impl;
        }
        catch ( final Throwable e )
        {
            final String errorMsg = "error creating new LocalDB instance: " + e.getClass().getName() + ":" + e.getMessage();
            LOGGER.error( () -> errorMsg, e );
            throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg ) );
        }

        return localDB;
    }

    private static void initInstance(
            final LocalDBProvider pwmDBProvider,
            final File dbFileLocation,
            final Map<String, String> initParameters,
            final String theClass,
            final Map<LocalDBProvider.Parameter, String> parameters
    )
            throws Exception
    {
        try
        {
            if ( dbFileLocation.mkdir() )
            {
                LOGGER.trace( () -> "created directory at " + dbFileLocation.getAbsolutePath() );
            }


            pwmDBProvider.init( dbFileLocation, initParameters, parameters );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "error creating new LocalDB instance: " + e.getClass().getName() + ":" + e.getMessage();
            LOGGER.error( () -> errorMsg, e );
            throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg ) );
        }

        LOGGER.trace( () -> "db init completed for " + theClass );
    }

    private static Map<LocalDBProvider.Parameter, String> makeParameterMap( final AppConfig appConfig, final boolean readOnly )
    {
        final Map<LocalDBProvider.Parameter, String> parameters = new EnumMap<>( LocalDBProvider.Parameter.class );
        if ( readOnly )
        {
            parameters.put( LocalDBProvider.Parameter.readOnly, Boolean.TRUE.toString() );
        }
        if ( Boolean.parseBoolean( appConfig.readAppProperty( AppProperty.LOCALDB_AGGRESSIVE_COMPACT_ENABLED ) ) )
        {
            parameters.put( LocalDBProvider.Parameter.aggressiveCompact, Boolean.TRUE.toString() );
        }
        return Collections.unmodifiableMap( parameters );
    }
}

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

package password.pwm.util.db;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;


public class DatabaseService implements PwmService
{
    private static final String KEY_TEST = "write-test-key";

    static final String KEY_COLUMN = "id";
    static final String VALUE_COLUMN = "value";

    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseService.class );

    private DBConfiguration dbConfiguration;

    private Driver driver;
    private JDBCDriverLoader.DriverLoader jdbcDriverLoader;

    private ErrorInformation lastError;
    private PwmApplication pwmApplication;

    private STATUS status = STATUS.NEW;

    private AtomicLoopIntIncrementer slotIncrementer;
    private final Map<Integer, DatabaseAccessorImpl> accessors = new ConcurrentHashMap<>();

    private ExecutorService executorService;

    private final Map<DatabaseAboutProperty, String> debugInfo = new LinkedHashMap<>();

    private volatile boolean initialized = false;

    public enum DatabaseAboutProperty
    {
        driverName,
        driverVersion,
        databaseProductName,
        databaseProductVersion,
    }


    @Override
    public STATUS status( )
    {
        return status;
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        this.pwmApplication = pwmApplication;
        init();

        executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );

        final TimeDuration watchdogFrequency = TimeDuration.of(
                Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.DB_CONNECTIONS_WATCHDOG_FREQUENCY_SECONDS ) ),
                TimeDuration.Unit.SECONDS );
        pwmApplication.getPwmScheduler().scheduleFixedRateJob( new ConnectionMonitor(), executorService, watchdogFrequency, watchdogFrequency );
    }

    private synchronized void init( )
    {
        if ( initialized )
        {
            return;
        }

        final Instant startTime = Instant.now();
        status = STATUS.OPENING;

        try
        {
            final Configuration config = pwmApplication.getConfig();
            this.dbConfiguration = DBConfiguration.fromConfiguration( config );

            if ( !dbConfiguration.isEnabled() )
            {
                status = PwmService.STATUS.CLOSED;
                LOGGER.debug( () -> "skipping database connection open, no connection parameters configured" );
                initialized = true;
                return;
            }

            LOGGER.debug( () -> "opening connection to database " + this.dbConfiguration.getConnectionString() );
            slotIncrementer = AtomicLoopIntIncrementer.builder().ceiling( dbConfiguration.getMaxConnections() ).build();

            {
                // make initial connection and establish schema
                clearCurrentAccessors();

                final Connection connection = openConnection( dbConfiguration );
                updateDebugProperties( connection );
                LOGGER.debug( () -> "established initial connection to " + dbConfiguration.getConnectionString() + ", properties: " + JsonUtil.serializeMap( this.debugInfo ) );

                for ( final DatabaseTable table : DatabaseTable.values() )
                {
                    DatabaseUtil.initTable( connection, table, dbConfiguration );
                }

                connection.close();
            }

            accessors.clear();
            {
                // set up connection pool
                final boolean traceLogging = config.readSettingAsBoolean( PwmSetting.DATABASE_DEBUG_TRACE );
                for ( int i = 0; i < dbConfiguration.getMaxConnections(); i++ )
                {
                    final Connection connection = openConnection( dbConfiguration );
                    final DatabaseAccessorImpl accessor = new DatabaseAccessorImpl( this, this.dbConfiguration, connection, traceLogging );
                    accessors.put( i, accessor );
                }
            }

            LOGGER.debug( () -> "successfully connected to remote database (" + TimeDuration.compactFromCurrent( startTime ) + ")" );

            status = STATUS.OPEN;
            initialized = true;
        }
        catch ( final Throwable t )
        {
            final String errorMsg = "exception initializing database service: " + t.getMessage();
            LOGGER.warn( () -> errorMsg );
            initialized = false;
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
            lastError = errorInformation;
        }
    }

    @Override
    public void close( )
    {
        status = PwmService.STATUS.CLOSED;

        if ( executorService != null )
        {
            executorService.shutdown();
        }

        clearCurrentAccessors();

        try
        {
            driver = null;
        }
        catch ( final Exception e )
        {
            LOGGER.debug( () -> "error while de-registering driver: " + e.getMessage() );
        }

        if ( jdbcDriverLoader != null )
        {
            jdbcDriverLoader.unloadDriver();
            jdbcDriverLoader = null;
        }
    }

    private void clearCurrentAccessors( )
    {
        for ( final DatabaseAccessorImpl accessor : accessors.values() )
        {
            accessor.close();
        }
        accessors.clear();
    }

    public List<HealthRecord> healthCheck( )
    {
        if ( status == PwmService.STATUS.CLOSED )
        {
            return Collections.emptyList();
        }

        final List<HealthRecord> returnRecords = new ArrayList<>();

        if ( !initialized )
        {
            returnRecords.add( new HealthRecord( HealthStatus.WARN, HealthTopic.Database, makeUninitializedError().getDetailedErrorMsg() ) );
            return returnRecords;
        }

        try
        {
            final Map<String, String> tempMap = new HashMap<>();
            tempMap.put( "date", JavaHelper.toIsoDate( Instant.now() ) );
            final DatabaseAccessor accessor = getAccessor();
            accessor.put( DatabaseTable.PWM_META, KEY_TEST, JsonUtil.serializeMap( tempMap ) );
        }
        catch ( final PwmException e )
        {
            returnRecords.add( new HealthRecord( HealthStatus.WARN, HealthTopic.Database, "Error writing to database: " + e.getErrorInformation().toDebugStr() ) );
            return returnRecords;
        }

        if ( lastError != null )
        {
            final TimeDuration errorAge = TimeDuration.fromCurrent( lastError.getDate() );

            if ( errorAge.isShorterThan( TimeDuration.HOUR ) )
            {
                final String msg = "Database server was recently unavailable ("
                        + errorAge.asLongString( PwmConstants.DEFAULT_LOCALE )
                        + " ago at " + lastError.getDate().toString() + "): " + lastError.toDebugStr();
                returnRecords.add( new HealthRecord( HealthStatus.CAUTION, HealthTopic.Database, msg ) );
            }
        }

        if ( returnRecords.isEmpty() )
        {
            returnRecords.add( new HealthRecord( HealthStatus.GOOD, HealthTopic.Database, "Database connection to " + this.dbConfiguration.getConnectionString() + " okay" ) );
        }

        return returnRecords;
    }

    private ErrorInformation makeUninitializedError( )
    {

        final String errorMsg;
        if ( dbConfiguration != null && !dbConfiguration.isEnabled() )
        {
            errorMsg = "database is not configured";
        }
        else
        {
            if ( lastError != null )
            {
                errorMsg = "unable to initialize database: " + lastError.getDetailedErrorMsg();
            }
            else
            {
                errorMsg = "database is not yet initialized";
            }
        }
        return new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
    }


    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> debugProperties = new LinkedHashMap<>();
        for ( final Map.Entry<DatabaseAboutProperty, String> entry : debugInfo.entrySet() )
        {
            final DatabaseAboutProperty databaseAboutProperty = entry.getKey();
            debugProperties.put( databaseAboutProperty.name(), entry.getValue() );
        }
        if ( status() == STATUS.OPEN )
        {
            return new ServiceInfoBean( Collections.singletonList( DataStorageMethod.DB ), debugProperties );
        }
        else
        {
            return new ServiceInfoBean( Collections.emptyList(), debugProperties );
        }
    }

    public DatabaseAccessor getAccessor( )
            throws PwmUnrecoverableException
    {
        if ( status == PwmService.STATUS.CLOSED )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, "database connection is not open" ) );
        }

        if ( !initialized )
        {
            throw new PwmUnrecoverableException( makeUninitializedError() );
        }

        return accessors.get( slotIncrementer.next() );
    }

    private Connection openConnection( final DBConfiguration dbConfiguration )
            throws DatabaseException
    {
        final String connectionURL = dbConfiguration.getConnectionString();

        final JDBCDriverLoader.DriverWrapper wrapper = JDBCDriverLoader.loadDriver( pwmApplication, dbConfiguration );
        driver = wrapper.getDriver();
        jdbcDriverLoader = wrapper.getDriverLoader();

        try
        {
            LOGGER.debug( () -> "initiating connecting to database " + connectionURL );
            final Properties connectionProperties = new Properties();
            if ( dbConfiguration.getUsername() != null && !dbConfiguration.getUsername().isEmpty() )
            {
                connectionProperties.setProperty( "user", dbConfiguration.getUsername() );
            }
            if ( dbConfiguration.getPassword() != null )
            {
                connectionProperties.setProperty( "password", dbConfiguration.getPassword().getStringValue() );
            }

            final Connection connection = driver.connect( connectionURL, connectionProperties );
            LOGGER.debug( () -> "connected to database " + connectionURL );

            connection.setAutoCommit( false );
            return connection;
        }
        catch ( final Throwable e )
        {
            final String errorMsg = "error connecting to database: " + JavaHelper.readHostileExceptionMessage( e );
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
            LOGGER.error( errorInformation );
            throw new DatabaseException( errorInformation );
        }
    }


    enum OperationType
    {
        WRITE,
        READ,
    }

    void updateStats( final OperationType operationType )
    {
        if ( pwmApplication != null && pwmApplication.getApplicationMode() == PwmApplicationMode.RUNNING )
        {
            final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
            if ( statisticsManager != null && statisticsManager.status() == PwmService.STATUS.OPEN )
            {
                if ( operationType == OperationType.READ )
                {
                    statisticsManager.updateEps( EpsStatistic.DB_READS, 1 );
                }
                if ( operationType == OperationType.WRITE )
                {
                    statisticsManager.updateEps( EpsStatistic.DB_WRITES, 1 );
                }
            }
        }
    }

    public Map<DatabaseAboutProperty, String> getConnectionDebugProperties( )
    {
        return Collections.unmodifiableMap( debugInfo );
    }

    private void updateDebugProperties( final Connection connection )
    {
        if ( connection != null )
        {
            try
            {
                final Map<DatabaseAboutProperty, String> returnObj = new LinkedHashMap<>();
                final DatabaseMetaData databaseMetaData = connection.getMetaData();
                returnObj.put( DatabaseAboutProperty.driverName, databaseMetaData.getDriverName() );
                returnObj.put( DatabaseAboutProperty.driverVersion, databaseMetaData.getDriverVersion() );
                returnObj.put( DatabaseAboutProperty.databaseProductName, databaseMetaData.getDatabaseProductName() );
                returnObj.put( DatabaseAboutProperty.databaseProductVersion, databaseMetaData.getDatabaseProductVersion() );
                debugInfo.clear();
                debugInfo.putAll( Collections.unmodifiableMap( returnObj ) );
            }
            catch ( final SQLException e )
            {
                LOGGER.error( () -> "error reading jdbc meta data: " + e.getMessage() );
            }
        }
    }

    void setLastError( final ErrorInformation lastError )
    {
        this.lastError = lastError;
    }

    private class ConnectionMonitor implements Runnable
    {
        @Override
        public void run( )
        {
            if ( initialized )
            {
                boolean valid = true;
                for ( final DatabaseAccessorImpl databaseAccessor : accessors.values() )
                {
                    if ( !databaseAccessor.isValid() )
                    {
                        valid = false;
                        break;
                    }
                }
                if ( !valid )
                {
                    LOGGER.warn( () -> "database connection lost; will retry connect periodically" );
                    initialized = false;
                }

            }

            if ( !initialized )
            {
                init();
            }
        }
    }
}

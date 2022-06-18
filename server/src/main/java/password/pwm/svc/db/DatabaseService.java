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

package password.pwm.svc.db;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmTimeUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class DatabaseService extends AbstractPwmService implements PwmService
{
    private static final String KEY_TEST = "write-test-key";

    static final String KEY_COLUMN = "id";
    static final String VALUE_COLUMN = "value";

    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseService.class );

    private DBConfiguration dbConfiguration;

    private Driver driver;
    private JDBCDriverLoader.DriverLoader jdbcDriverLoader;

    private ErrorInformation lastError;

    private AtomicLoopIntIncrementer slotIncrementer;
    private final Map<Integer, DatabaseAccessorImpl> accessors = new ConcurrentHashMap<>();

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
    protected Set<PwmApplication.Condition> openConditions()
    {
        return Collections.emptySet();
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.dbConfiguration = DBConfiguration.fromConfiguration( getPwmApplication().getConfig() );

        final TimeDuration watchdogFrequency = TimeDuration.of(
                Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.DB_CONNECTIONS_WATCHDOG_FREQUENCY_SECONDS ) ),
                TimeDuration.Unit.SECONDS );

        pwmApplication.getPwmScheduler().scheduleFixedRateJob( new ConnectionMonitor(), getExecutorService(), watchdogFrequency, watchdogFrequency );

        return dbInit();
    }

    private STATUS dbInit( )
    {
        if ( initialized )
        {
            return STATUS.OPEN;
        }

        if ( !dbConfiguration.isEnabled() )
        {
            setStatus( STATUS.CLOSED );
            LOGGER.debug( () -> "skipping database connection open, no connection parameters configured" );
            initialized = true;
            return STATUS.CLOSED;
        }

        final Instant startTime = Instant.now();

        try
        {
            LOGGER.debug( () -> "opening connection to database " + this.dbConfiguration.getConnectionString() );
            slotIncrementer = AtomicLoopIntIncrementer.builder().ceiling( dbConfiguration.getMaxConnections() ).build();

            {
                // make initial connection and establish schema
                clearCurrentAccessors();

                final Connection connection = openConnection( dbConfiguration );
                updateDebugProperties( connection );
                LOGGER.debug( () -> "established initial connection to " + dbConfiguration.getConnectionString() + ", properties: "
                        + JsonFactory.get().serializeMap( this.debugInfo ) );

                for ( final DatabaseTable table : DatabaseTable.values() )
                {
                    DatabaseUtil.initTable( connection, table, dbConfiguration );
                }

                connection.close();
            }

            accessors.clear();
            {
                // set up connection pool
                final boolean traceLogging = getPwmApplication().getConfig().readSettingAsBoolean( PwmSetting.DATABASE_DEBUG_TRACE );
                for ( int i = 0; i < dbConfiguration.getMaxConnections(); i++ )
                {
                    final Connection connection = openConnection( dbConfiguration );
                    final DatabaseAccessorImpl accessor = new DatabaseAccessorImpl( this, this.dbConfiguration, connection, traceLogging );
                    accessors.put( i, accessor );
                }
            }

            LOGGER.debug( () -> "successfully connected to remote database (" + TimeDuration.compactFromCurrent( startTime ) + ")" );

        }
        catch ( final Throwable t )
        {
            final String errorMsg = "exception initializing database service: " + t.getMessage();
            LOGGER.warn( () -> errorMsg );
            initialized = false;
            lastError = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
            return STATUS.CLOSED;
        }

        initialized = true;
        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl( )
    {
        setStatus( STATUS.CLOSED );

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

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        if ( status() == PwmService.STATUS.CLOSED )
        {
            return Collections.emptyList();
        }

        final List<HealthRecord> returnRecords = new ArrayList<>();

        if ( !initialized )
        {
            returnRecords.add( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.ServiceClosed,
                    this.getClass().getSimpleName(),
                    makeUninitializedError().getDetailedErrorMsg() ) );
            return returnRecords;
        }

        try
        {
            final Map<String, String> tempMap = new HashMap<>();
            tempMap.put( "date", StringUtil.toIsoDate( Instant.now() ) );
            final DatabaseAccessor accessor = getAccessor();
            accessor.put( DatabaseTable.PWM_META, KEY_TEST, JsonFactory.get().serializeMap( tempMap ) );
        }
        catch ( final PwmException e )
        {
            returnRecords.add( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.ServiceClosed,
                    this.getClass().getSimpleName(),
                    "error writing to database: " + e.getMessage() ) );
            return returnRecords;
        }

        if ( lastError != null )
        {
            final TimeDuration errorAge = TimeDuration.fromCurrent( lastError.getDate() );
            final long cautionDurationMS = Long.parseLong( getPwmApplication().getConfig().readAppProperty( AppProperty.HEALTH_DB_CAUTION_DURATION_MS ) );

            if ( errorAge.isShorterThan( cautionDurationMS ) )
            {
                final String ageString = PwmTimeUtil.asLongString( errorAge );
                final String errorDate = StringUtil.toIsoDate( lastError.getDate() );
                final String errorMsg = lastError.toDebugStr();
                returnRecords.add( HealthRecord.forMessage(
                        DomainID.systemId(),
                        HealthMessage.Database_RecentlyUnreachable,
                        ageString,
                        errorDate,
                        errorMsg
                ) );
            }
        }

        if ( returnRecords.isEmpty() )
        {
            returnRecords.add( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.Database_OK,
                    this.dbConfiguration.getConnectionString() ) );
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
        final Map<String, String> debugProperties = new LinkedHashMap<>( debugInfo.size() );
        for ( final Map.Entry<DatabaseAboutProperty, String> entry : debugInfo.entrySet() )
        {
            final DatabaseAboutProperty databaseAboutProperty = entry.getKey();
            debugProperties.put( databaseAboutProperty.name(), entry.getValue() );
        }

        if ( status() == STATUS.OPEN )
        {
            return ServiceInfoBean.builder()
                    .storageMethod( DataStorageMethod.DB )
                    .debugProperties( debugProperties )
                    .build();
        }

        return ServiceInfoBean.builder().debugProperties( debugProperties ).build();
    }

    public DatabaseAccessor getAccessor( )
            throws PwmUnrecoverableException
    {
        if ( status() == PwmService.STATUS.CLOSED )
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

        final JDBCDriverLoader.DriverWrapper wrapper = JDBCDriverLoader.loadDriver( getPwmApplication(), dbConfiguration );
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
        if ( operationType == OperationType.READ )
        {
            StatisticsClient.updateEps( getPwmApplication(), EpsStatistic.DB_READS, 1 );
        }
        if ( operationType == OperationType.WRITE )
        {
            StatisticsClient.updateEps( getPwmApplication(), EpsStatistic.DB_WRITES, 1 );
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
                dbInit();
            }
        }
    }
}

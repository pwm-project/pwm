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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
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
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class DatabaseService extends AbstractPwmService implements PwmService
{
    private static final String KEY_TEST = "write-test-key";

    static final String KEY_COLUMN = "id";
    static final String VALUE_COLUMN = "value";

    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseService.class );

    private DBConfiguration dbConfiguration;

    private Map<DatabaseDebugProperty, String> initializedDebugData = Map.of();
    private volatile boolean initialized = false;

    private HikariDataSource hikariDataSource;

    private final StatisticCounterBundle<DebugStat> statsBundle = new StatisticCounterBundle<>( DebugStat.class );

    enum DebugStat
    {
        issuedAccessors,
    }

    public enum DatabaseDebugProperty
    {
        driverName,
        driverVersion,
        databaseProductName,
        databaseProductVersion,

        idleConnections,
        activeConnections,
        totalConnections, maxConnections,
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

        if ( !dbShouldOpen() )
        {
            initialized = true;
            return STATUS.CLOSED;
        }

        scheduleJob( new PwmDbInitializer() );

        return STATUS.OPEN;
    }

    private boolean dbShouldOpen()
    {
        if ( getPwmApplication().getPwmEnvironment().isInternalRuntimeInstance() )
        {
            return false;
        }

        if ( !getPwmApplication().getConfig().hasDbConfigured() )
        {
            setStatus( STATUS.CLOSED );
            LOGGER.debug( getSessionLabel(), () -> "skipping database connection open, connection parameters are not configured" );
            return false;
        }

        return true;
    }

    private void dbInit( )
    {
        if ( initialized )
        {
            return;
        }

        final Instant startTime = Instant.now();

        try
        {
            JDBCDriverLoader.loadDriver( getPwmApplication(), dbConfiguration );

            final String poolName = makePoolName( getPwmApplication() );

            hikariDataSource = new HikariDataSource( makeHikariConfig( dbConfiguration, poolName ) );

            LOGGER.debug( getSessionLabel(), () -> "opening connection to database "
                    + this.dbConfiguration.connectionString() );

            try ( Connection connection = getConnection() )
            {
                initTables( connection );

                initializedDebugData = initConnectionDebugData( connection );
            }

            postDbInitLogging();

            LOGGER.debug( getSessionLabel(), () -> "successfully connected to remote database ("
                    + TimeDuration.compactFromCurrent( startTime ) + ")" );

            initialized = true;
            setStartupError( null );
        }
        catch ( final Throwable t )
        {
            final String errorMsg = "exception initializing database service: " + t.getMessage();
            LOGGER.warn( getSessionLabel(), () -> errorMsg );
            initialized = false;
            setStartupError( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg ) );
        }
    }

    private void postDbInitLogging()
            throws DatabaseException

    {
        final DatabaseAccessor databaseAccessor = new DatabaseAccessorImpl(
                this,
                dbConfiguration );

        for ( final DatabaseTable databaseTable : DatabaseTable.values() )
        {
            final int size = databaseAccessor.size( databaseTable );
            LOGGER.trace( getSessionLabel(), () -> "opened table " + databaseTable.name() + " with "
                    + size + " records" );
        }
    }

    private void initTables( final Connection connection )
            throws DatabaseException
    {
        final Instant startTime = Instant.now();

        LOGGER.trace( getSessionLabel(), () -> "beginning check for database table schema" );

        for ( final DatabaseTable table : DatabaseTable.values() )
        {
            DatabaseUtil.initTable( getSessionLabel(), connection, table, dbConfiguration );
        }

        LOGGER.trace( getSessionLabel(), () -> "completed check for database table schema", TimeDuration.fromCurrent( startTime ) );
    }

    private Map<DatabaseDebugProperty, String> initConnectionDebugData( final Connection connection )
    {
        try
        {
            final Map<DatabaseDebugProperty, String> returnObj = new EnumMap<>( DatabaseDebugProperty.class );
            final DatabaseMetaData databaseMetaData = connection.getMetaData();
            returnObj.put( DatabaseDebugProperty.driverName, databaseMetaData.getDriverName() );
            returnObj.put( DatabaseDebugProperty.driverVersion, databaseMetaData.getDriverVersion() );
            returnObj.put( DatabaseDebugProperty.databaseProductName, databaseMetaData.getDatabaseProductName() );
            returnObj.put( DatabaseDebugProperty.databaseProductVersion, databaseMetaData.getDatabaseProductVersion() );
            return returnObj;
        }
        catch ( final SQLException e )
        {
            LOGGER.error( getSessionLabel(), () -> "error reading jdbc meta data: " + e.getMessage() );
        }

        return Map.of();
    }

    @Override
    public void shutdownImpl( )
    {
        setStatus( STATUS.CLOSED );
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

        // final long cautionDurationMS = Long.parseLong( getPwmApplication().getConfig().readAppProperty( AppProperty.HEALTH_DB_CAUTION_DURATION_MS ) );

        if ( returnRecords.isEmpty() )
        {
            returnRecords.add( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.Database_OK,
                    this.dbConfiguration.connectionString() ) );
        }

        return returnRecords;
    }

    private ErrorInformation makeUninitializedError( )
    {

        final String errorMsg;
        if ( dbConfiguration != null && !getPwmApplication().getConfig().hasDbConfigured() )
        {
            errorMsg = "database is not configured";
        }
        else
        {
            final ErrorInformation startupError = getStartupError();
            if ( startupError != null )
            {
                errorMsg = "unable to initialize database: " + startupError.getDetailedErrorMsg();
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
        final Map<String, String> debugProperties = new LinkedHashMap<>(
                CollectionUtil.enumMapToStringMap( makeDebugProperties() )
        );

        if ( status() == STATUS.OPEN )
        {
            return ServiceInfoBean.builder()
                    .storageMethod( DataStorageMethod.DB )
                    .debugProperties( debugProperties )
                    .build();
        }

        return ServiceInfoBean.builder().debugProperties( debugProperties ).build();
    }

    Connection getConnection()
            throws SQLException
    {
        return hikariDataSource.getConnection();
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

        statsBundle.increment( DebugStat.issuedAccessors );
        return new DatabaseAccessorImpl( this, dbConfiguration );
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

    public Map<DatabaseDebugProperty, String> getConnectionDebugProperties( )
    {
        return makeDebugProperties();
    }

    private Map<DatabaseDebugProperty, String> makeDebugProperties()
    {
        final Map<DatabaseDebugProperty, String> returnObj = new EnumMap<>( DatabaseDebugProperty.class );

        try
        {
            if ( hikariDataSource != null )
            {
                final HikariPoolMXBean poolProxy = hikariDataSource.getHikariPoolMXBean();
                if ( poolProxy != null )
                {
                    returnObj.put(
                            DatabaseDebugProperty.idleConnections,
                            String.valueOf( poolProxy.getIdleConnections() ) );
                    returnObj.put(
                            DatabaseDebugProperty.activeConnections,
                            String.valueOf( poolProxy.getActiveConnections() ) );
                    returnObj.put(
                            DatabaseDebugProperty.totalConnections,
                            String.valueOf( poolProxy.getTotalConnections() ) );
                    returnObj.put(
                            DatabaseDebugProperty.maxConnections,
                            String.valueOf( hikariDataSource.getMaximumPoolSize() ) );
                }
            }
        }
        catch ( final Throwable t )
        {
            LOGGER.error( getSessionLabel(), () -> "error reading hikari mxBean during debug property generation: "
                    + t.getMessage() );
        }

        returnObj.putAll( initializedDebugData );

        return Map.copyOf( returnObj );
    }

    private class PwmDbInitializer implements Runnable
    {
        @Override
        public void run( )
        {
            if ( initialized )
            {
                return;
            }

            try
            {
                dbInit();
            }
            catch ( final Throwable t )
            {
                LOGGER.error( getSessionLabel(), () -> "error during database initialization: " + t.getMessage() );
            }

            if ( !initialized )
            {
                final TimeDuration watchdogFrequency = TimeDuration.of(
                        Integer.parseInt( getPwmApplication().getConfig().readAppProperty( AppProperty.DB_CONNECTIONS_WATCHDOG_FREQUENCY_SECONDS ) ),
                        TimeDuration.Unit.SECONDS );
                scheduleJob( this, watchdogFrequency );
            }
        }
    }

    private static String makePoolName( final PwmApplication pwmApplication )
    {
        return "pwm-" + pwmApplication.getInstanceID() + "-hikari-pool";
    }


    private static HikariConfig makeHikariConfig( final DBConfiguration dbConfiguration, final String poolName )
            throws PwmUnrecoverableException
    {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl( dbConfiguration.connectionString() );
        config.setUsername( dbConfiguration.username() );
        config.setPassword( dbConfiguration.password().getStringValue() );
        config.addDataSourceProperty( "registerMbeans", "true" );
        config.setPoolName( poolName );

        if ( dbConfiguration.maxConnections() > 0 )
        {
            config.setMaximumPoolSize( dbConfiguration.maxConnections() );
        }

        return config;
    }
}

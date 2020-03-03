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

import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Jason D. Rivard
 */
class DatabaseAccessorImpl implements DatabaseAccessor
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseAccessorImpl.class, true );

    private final Connection connection;
    private final DatabaseService databaseService;
    private final DBConfiguration dbConfiguration;

    private final boolean traceLogEnabled;

    private static final AtomicInteger ACCESSOR_COUNTER = new AtomicInteger( 0 );
    private final int accessorNumber = ACCESSOR_COUNTER.getAndIncrement();

    private static final AtomicInteger ITERATOR_COUNTER = new AtomicInteger( 0 );
    private final Set<DBIterator> outstandingIterators = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean closed = new AtomicBoolean( false );

    private final ReentrantLock lock = new ReentrantLock();

    DatabaseAccessorImpl(
            final DatabaseService databaseService,
            final DBConfiguration dbConfiguration,
            final Connection connection,
            final boolean traceLogEnabled
    )
    {
        this.connection = connection;
        this.dbConfiguration = dbConfiguration;
        this.traceLogEnabled = traceLogEnabled;
        this.databaseService = databaseService;
    }


    private void processSqlException(
            final DatabaseUtil.DebugInfo debugInfo,
            final SQLException e
    )
            throws DatabaseException
    {
        DatabaseUtil.rollbackTransaction( connection );
        final DatabaseException databaseException = DatabaseUtil.convertSqlException( debugInfo, e );
        databaseService.setLastError( databaseException.getErrorInformation() );
        throw databaseException;
    }

    @Override
    public boolean put(
            final DatabaseTable table,
            final String key,
            final String value
    )
            throws DatabaseException
    {
        preCheck();

        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create( "put", table, key, value );

        return execute( debugInfo, ( ) ->
        {
            boolean exists = false;
            try
            {
                exists = containsImpl( table, key );
            }
            catch ( final SQLException e )
            {
                processSqlException( debugInfo, e );
            }

            if ( exists )
            {
                final String sqlText = "UPDATE " + table.toString()
                        + " SET " + DatabaseService.VALUE_COLUMN + "=? WHERE "
                        + DatabaseService.KEY_COLUMN + "=?";

                // note the value/key are reversed for this statement
                executeUpdate( sqlText, debugInfo, value, key );
            }
            else
            {
                final String sqlText = "INSERT INTO " + table.toString()
                        + "(" + DatabaseService.KEY_COLUMN + ", "
                        + DatabaseService.VALUE_COLUMN + ") VALUES(?,?)";
                executeUpdate( sqlText, debugInfo, key, value );
            }

            return !exists;
        } );
    }

    @Override
    public boolean putIfAbsent(
            final DatabaseTable table,
            final String key,
            final String value
    )
            throws DatabaseException
    {
        preCheck();

        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create( "putIfAbsent", table, key, value );

        return execute( debugInfo, ( ) ->
        {
            boolean valueExists = false;
            try
            {
                valueExists = DatabaseAccessorImpl.this.containsImpl( table, key );
            }
            catch ( final SQLException e )
            {
                DatabaseAccessorImpl.this.processSqlException( debugInfo, e );
            }

            if ( !valueExists )
            {
                final String insertSql = "INSERT INTO " + table.name() + "(" + DatabaseService.KEY_COLUMN + ", " + DatabaseService.VALUE_COLUMN + ") VALUES(?,?)";
                DatabaseAccessorImpl.this.executeUpdate( insertSql, debugInfo, key, value );
            }

            return !valueExists;
        } );
    }


    @Override
    public boolean contains(
            final DatabaseTable table,
            final String key
    )
            throws DatabaseException
    {
        preCheck();

        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create( "contains", table, key, null );

        return execute( debugInfo, ( ) ->
        {
            boolean valueExists = false;
            try
            {
                valueExists = containsImpl( table, key );
            }
            catch ( final SQLException e )
            {
                processSqlException( debugInfo, e );
            }
            return valueExists;
        } );
    }

    @Override
    public String get(
            final DatabaseTable table,
            final String key
    )
            throws DatabaseException
    {
        preCheck();

        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create( "get", table, key, null );

        return execute( debugInfo, ( ) ->
        {
            final String sqlStatement = "SELECT * FROM " + table.name() + " WHERE " + DatabaseService.KEY_COLUMN + " = ?";

            try ( PreparedStatement statement = connection.prepareStatement( sqlStatement ) )
            {
                statement.setString( 1, key );
                statement.setMaxRows( 1 );

                try ( ResultSet resultSet = statement.executeQuery() )
                {
                    if ( resultSet.next() )
                    {
                        return resultSet.getString( DatabaseService.VALUE_COLUMN );
                    }
                }
            }
            catch ( final SQLException e )
            {
                processSqlException( debugInfo, e );
            }
            return null;
        } );
    }

    @Override
    public ClosableIterator<Map.Entry<String, String>> iterator( final DatabaseTable table )
            throws DatabaseException
    {
        try
        {
            lock.lock();
            return new DBIterator( table );
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void remove(
            final DatabaseTable table,
            final String key
    )
            throws DatabaseException
    {
        preCheck();

        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create( "remove", table, key, null );

        execute( debugInfo, ( ) ->
        {


            final String sqlText = "DELETE FROM " + table.name() + " WHERE " + DatabaseService.KEY_COLUMN + "=?";
            executeUpdate( sqlText, debugInfo, key );

            return null;
        } );
    }

    @Override
    public int size( final DatabaseTable table )
            throws DatabaseException
    {
        preCheck();

        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create( "size", table, null, null );

        return execute( debugInfo, ( ) ->
        {
            final String sqlStatement = "SELECT COUNT(" + DatabaseService.KEY_COLUMN + ") FROM " + table.name();

            try ( PreparedStatement statement = connection.prepareStatement( sqlStatement ) )
            {
                try ( ResultSet resultSet = statement.executeQuery() )
                {
                    if ( resultSet.next() )
                    {
                        return resultSet.getInt( 1 );
                    }
                }
            }
            catch ( final SQLException e )
            {
                processSqlException( debugInfo, e );
            }

            return 0;
        } );
    }

    boolean isValid( )
    {
        preCheck();

        if ( connection == null )
        {
            return false;
        }

        try
        {
            if ( connection.isClosed() )
            {
                return false;
            }

            final int connectionTimeout = dbConfiguration.getConnectionTimeout();

            if ( !connection.isValid( connectionTimeout ) )
            {
                return false;
            }

        }
        catch ( final SQLException e )
        {
            LOGGER.debug( () -> "error while checking connection validity: " + e.getMessage() );
        }

        return true;
    }


    public class DBIterator implements ClosableIterator<Map.Entry<String, String>>
    {
        private final DatabaseTable table;
        private ResultSet resultSet;
        private PreparedStatement statement;
        private Map.Entry<String, String> nextValue;
        private boolean finished;
        private int counter = ITERATOR_COUNTER.getAndIncrement();

        DBIterator( final DatabaseTable table )
                throws DatabaseException
        {
            this.table = table;
            init();
            getNextItem();
        }

        private void init( ) throws DatabaseException
        {
            final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create(
                    "iterator #" + counter + " open", table, null, null );
            traceBegin( debugInfo );

            final String sqlText = "SELECT * FROM " + table.name();
            try
            {
                outstandingIterators.add( this );
                statement = connection.prepareStatement( sqlText );
                resultSet = statement.executeQuery();
                connection.commit();
            }
            catch ( final SQLException e )
            {
                processSqlException( null, e );
            }

            traceResult( debugInfo, null );
        }

        public boolean hasNext( )
        {
            return !finished;
        }

        public Map.Entry<String, String> next( )
        {
            if ( finished )
            {
                throw new IllegalStateException( "iterator completed" );
            }
            final Map.Entry<String, String> returnValue = nextValue;
            getNextItem();
            return returnValue;
        }

        public void remove( )
        {
            throw new UnsupportedOperationException( "remove not supported" );
        }

        private void getNextItem( )
        {
            try
            {
                if ( resultSet.next() )
                {
                    final String key = resultSet.getString( DatabaseService.KEY_COLUMN );
                    final String value = resultSet.getString( DatabaseService.VALUE_COLUMN );
                    nextValue = new AbstractMap.SimpleEntry<>( key, value );
                }
                else
                {
                    close();
                }
            }
            catch ( final SQLException e )
            {
                finished = true;
                LOGGER.warn( () -> "unexpected error during result set iteration: " + e.getMessage() );
            }
            databaseService.updateStats( DatabaseService.OperationType.READ );
        }

        public void close( )
        {
            final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create(
                    "iterator #" + counter + " close", table, null, null );
            traceBegin( debugInfo );

            try
            {
                lock.lock();
                outstandingIterators.remove( this );

                if ( resultSet != null )
                {
                    try
                    {
                        resultSet.close();
                        resultSet = null;
                    }
                    catch ( final SQLException e )
                    {
                        LOGGER.error( () -> "error closing inner resultSet in iterator: " + e.getMessage() );
                    }
                }

                if ( statement != null )
                {
                    try
                    {
                        statement.close();
                        statement = null;
                    }
                    catch ( final SQLException e )
                    {
                        LOGGER.error( () -> "error closing inner statement in iterator: " + e.getMessage() );
                    }
                }

                finished = true;
            }
            finally
            {
                lock.unlock();
            }

            traceResult( debugInfo, "outstandingIterators=" + outstandingIterators.size() );
        }
    }

    private void traceBegin( final DatabaseUtil.DebugInfo debugInfo )
    {
        if ( !traceLogEnabled )
        {
            return;
        }

        LOGGER.trace( () -> "accessor #" + accessorNumber + " begin operation: " + JsonUtil.serialize( debugInfo ) );
    }

    private void traceResult(
            final DatabaseUtil.DebugInfo debugInfo,
            final Object result
    )
    {
        if ( !traceLogEnabled )
        {
            return;
        }

        final Map<String, String> map = JsonUtil.deserializeStringMap( JsonUtil.serialize( debugInfo ) );
        map.put( "duration", TimeDuration.fromCurrent( debugInfo.getStartTime() ).asCompactString() );
        if ( result != null )
        {
            map.put( "result", String.valueOf( result ) );
        }
        LOGGER.trace( () -> "accessor #" + accessorNumber + " operation result: " + StringUtil.mapToString( map ) );
    }

    private interface SqlFunction<T>
    {
        T execute( ) throws DatabaseException;
    }

    private <T> T execute( final DatabaseUtil.DebugInfo debugInfo, final SqlFunction<T> sqlFunction )
            throws DatabaseException
    {
        traceBegin( debugInfo );

        try
        {
            lock.lock();

            try
            {
                final T result = sqlFunction.execute();
                traceResult( debugInfo, result );
                databaseService.updateStats( DatabaseService.OperationType.WRITE );
                return result;
            }
            finally
            {
                DatabaseUtil.commit( connection );
            }

        }
        finally
        {
            lock.unlock();
        }

    }

    Connection getConnection( )
    {
        return connection;
    }

    void close( )
    {
        closed.set( true );

        try
        {
            lock.lock();
            try
            {
                if ( !outstandingIterators.isEmpty() )
                {
                    LOGGER.warn( () -> "closing outstanding " + outstandingIterators.size() + " iterators" );
                }
                for ( final DBIterator iterator : new HashSet<>( outstandingIterators ) )
                {
                    iterator.close();
                }
            }
            catch ( final Exception e )
            {
                LOGGER.warn( () -> "error while closing connection: " + e.getMessage() );
            }

            try
            {
                connection.close();
            }
            catch ( final SQLException e )
            {
                LOGGER.warn( () -> "error while closing connection: " + e.getMessage() );
            }
        }
        finally
        {
            lock.unlock();
        }

        LOGGER.trace( () -> "closed accessor #" + accessorNumber );
    }

    private boolean containsImpl( final DatabaseTable table, final String key )
            throws SQLException
    {
        final String sqlStatement = "SELECT COUNT(" + DatabaseService.KEY_COLUMN + ") FROM " + table.name()
                + " WHERE " + DatabaseService.KEY_COLUMN + " = ?";

        try ( PreparedStatement selectStatement = connection.prepareStatement( sqlStatement ); )
        {
            selectStatement.setString( 1, key );
            selectStatement.setMaxRows( 1 );

            try ( ResultSet resultSet = selectStatement.executeQuery() )
            {
                if ( resultSet.next() )
                {
                    return resultSet.getInt( 1 ) > 0;
                }
            }
        }

        return false;
    }

    private void executeUpdate( final String sqlStatement, final DatabaseUtil.DebugInfo debugInfo, final String... params )
            throws DatabaseException
    {
        try ( PreparedStatement statement = connection.prepareStatement( sqlStatement ) )
        {
            for ( int i = 0; i < params.length; i++ )
            {
                statement.setString( i + 1, params[ i ] );
            }
            statement.executeUpdate();
        }
        catch ( final SQLException e )
        {
            processSqlException( debugInfo, e );
        }
    }

    private void preCheck( )
    {
        if ( closed.get() )
        {
            throw new IllegalStateException( "call to perform database operation but accessor has been closed" );
        }
    }

    public boolean isConnected()
    {
        try
        {
            return connection.isValid( 5000 );
        }
        catch ( final SQLException e )
        {
            LOGGER.error( () -> "error while checking database connection: " + e.getMessage() );
        }

        return false;
    }
}

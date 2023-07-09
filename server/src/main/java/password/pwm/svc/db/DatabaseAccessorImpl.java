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

import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Jason D. Rivard
 */
class DatabaseAccessorImpl implements DatabaseAccessor
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseAccessorImpl.class, true );

    private final DatabaseService databaseService;

    private final boolean traceLogEnabled;

    private static final AtomicLoopIntIncrementer ACCESSOR_COUNTER = new AtomicLoopIntIncrementer();
    private static final AtomicLoopIntIncrementer ITERATOR_COUNTER = new AtomicLoopIntIncrementer();

    private final int accessorNumber = ACCESSOR_COUNTER.next();

    private final Set<DBIterator> outstandingIterators = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean( false );

    DatabaseAccessorImpl(
            final DatabaseService databaseService,
            final DBConfiguration dbConfiguration
    )
    {
        this.traceLogEnabled = dbConfiguration.traceLogging();
        this.databaseService = databaseService;
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

        final Boolean result = execute( debugInfo, connection ->
        {
            final boolean exists = containsImpl( table, key, connection );

            if ( exists )
            {
                final String sqlText = "UPDATE " + table
                        + " SET " + DatabaseService.VALUE_COLUMN + "=? WHERE "
                        + DatabaseService.KEY_COLUMN + "=?";

                // note the value/key are reversed for this statement
                executeStatementWithParams( sqlText, connection, value, key );
            }
            else
            {
                final String sqlText = "INSERT INTO " + table
                        + "(" + DatabaseService.KEY_COLUMN + ", "
                        + DatabaseService.VALUE_COLUMN + ") VALUES(?,?)";
                executeStatementWithParams( sqlText, connection, key, value );
            }

            return !exists;
        } );

        return result != null && result;
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

        final Boolean result = execute( debugInfo, connection ->
        {
            final boolean valueExists = DatabaseAccessorImpl.this.containsImpl( table, key, connection );

            if ( !valueExists )
            {
                final String insertSql = "INSERT INTO " + table.name() + "(" + DatabaseService.KEY_COLUMN + ", " + DatabaseService.VALUE_COLUMN + ") VALUES(?,?)";
                executeStatementWithParams( insertSql, connection, key, value );
            }

            return !valueExists;
        } );

        return result != null && result;
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

        final Boolean result = execute( debugInfo, connection ->
        {
            return containsImpl( table, key, connection );
        } );

        return result != null && result;

    }

    @Override
    public Optional<String> get(
            final DatabaseTable table,
            final String key
    )
            throws DatabaseException
    {
        preCheck();

        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create( "get", table, key, null );

        return execute( debugInfo, connection ->
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
                        return Optional.ofNullable( resultSet.getString( DatabaseService.VALUE_COLUMN ) );
                    }
                }
            }
            return Optional.empty();
        } );
    }

    @Override
    public ClosableIterator<Map.Entry<String, String>> iterator( final DatabaseTable table )
            throws DatabaseException
    {
        return new DBIterator( table );
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

        execute( debugInfo, connection ->
        {
            final String sqlText = "DELETE FROM " + table.name() + " WHERE " + DatabaseService.KEY_COLUMN + "=?";
            executeStatementWithParams( sqlText, connection, key );

            return null;
        } );
    }

    @Override
    public int size( final DatabaseTable table )
            throws DatabaseException
    {
        preCheck();

        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create( "size", table, null, null );

        final Integer result = execute( debugInfo, connection ->
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

            return 0;
        } );

        return result == null ? 0 : result;
    }

    public class DBIterator implements ClosableIterator<Map.Entry<String, String>>
    {
        private final DatabaseTable table;
        private ResultSet resultSet;
        private PreparedStatement statement;
        private Map.Entry<String, String> nextValue;
        private boolean finished;
        private final int counter = ITERATOR_COUNTER.next();

        DBIterator( final DatabaseTable table )
                throws DatabaseException
        {
            this.table = table;
            init();
            getNextItem();
        }

        private void init( )
                throws DatabaseException
        {
            final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create(
                    "iterator #" + counter + " open", table, null, null );
            traceBegin( debugInfo );

            final String sqlText = "SELECT * FROM " + table.name();
            try ( Connection connection = databaseService.getConnection() )
            {
                outstandingIterators.add( this );
                statement = connection.prepareStatement( sqlText );
                resultSet = statement.executeQuery();
                connection.commit();
            }
            catch ( final SQLException e )
            {
                throw DatabaseUtil.convertSqlException( debugInfo, e );
            }

            traceResult( debugInfo, null );
        }

        @Override
        public boolean hasNext( )
        {
            return !finished;
        }

        @Override
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

        @Override
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

        @Override
        public void close( )
        {
            final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create(
                    "iterator #" + counter + " close", table, null, null );
            traceBegin( debugInfo );

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

            traceResult( debugInfo, "outstandingIterators=" + outstandingIterators.size() );
        }
    }

    private void traceBegin( final DatabaseUtil.DebugInfo debugInfo )
    {
        if ( !traceLogEnabled )
        {
            return;
        }

        LOGGER.trace( () -> "accessor #" + accessorNumber + " begin operation: " + JsonFactory.get().serialize( debugInfo ) );
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

        final Map<String, String> map = JsonFactory.get().deserializeStringMap( JsonFactory.get().serialize( debugInfo ) );
        map.put( "duration", TimeDuration.fromCurrent( debugInfo.getStartTime() ).asCompactString() );
        if ( result != null )
        {
            map.put( "result", String.valueOf( result ) );
        }
        LOGGER.trace( () -> "accessor #" + accessorNumber + " operation result: " + StringUtil.mapToString( map ) );
    }

    private interface SqlFunction<T>
    {
        T execute( Connection connection ) throws SQLException;
    }

    private <T> T execute( final DatabaseUtil.DebugInfo debugInfo, final SqlFunction<T> sqlFunction )
            throws DatabaseException
    {
        traceBegin( debugInfo );

        try ( Connection connection = databaseService.getConnection() )
        {
            try
            {
                final T result = sqlFunction.execute( connection );
                traceResult( debugInfo, result );
                databaseService.updateStats( DatabaseService.OperationType.WRITE );
                return result;
            }
            catch ( final SQLException sqlException )
            {
                throw DatabaseUtil.convertSqlException( debugInfo, sqlException );
            }
            finally
            {
                DatabaseUtil.commit( connection );
            }

        }
        catch ( final SQLException e )
        {
            throw DatabaseUtil.convertSqlException( debugInfo, e );
        }
    }

    private boolean containsImpl( final DatabaseTable table, final String key, final Connection connection )
            throws SQLException
    {

        final String sqlStatement = "SELECT COUNT(" + DatabaseService.KEY_COLUMN + ") FROM " + table.name()
                + " WHERE " + DatabaseService.KEY_COLUMN + " = ?";

        try ( PreparedStatement selectStatement = connection.prepareStatement( sqlStatement ) )
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

    private void executeStatementWithParams( final String sqlStatement, final Connection connection, final String... params )
            throws SQLException
    {
        try ( PreparedStatement statement = connection.prepareStatement( sqlStatement ) )
        {
            for ( int i = 0; i < params.length; i++ )
            {
                statement.setString( i + 1, params[ i ] );
            }
            statement.executeUpdate();
        }
    }

    private void preCheck( )
    {
        if ( closed.get() )
        {
            throw new IllegalStateException( "call to perform database operation but accessor has been closed" );
        }
    }
}

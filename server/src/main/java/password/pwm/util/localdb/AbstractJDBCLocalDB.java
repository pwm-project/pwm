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

package password.pwm.util.localdb;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.Closeable;
import java.io.File;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class AbstractJDBCLocalDB implements LocalDBProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AbstractJDBCLocalDB.class, true );

    private static final String KEY_COLUMN = "id";
    private static final String VALUE_COLUMN = "value";
    private static final int ITERATOR_LIMIT = 100;

    private static final String WIDTH_KEY = String.valueOf( LocalDB.MAX_KEY_LENGTH );

    protected Driver driver;
    protected File dbDirectory;

    // cache of dbIterators
    private final Set<LocalDB.LocalDBIterator<Map.Entry<String, String>>> dbIterators = Collections.newSetFromMap(
            new ConcurrentHashMap<>() );

    // sql db connection
    protected Connection dbConnection;

    // operation lock
    protected final ReadWriteLock lock = new ReentrantReadWriteLock();

    protected LocalDB.Status status = LocalDB.Status.NEW;
    protected boolean readOnly = false;
    protected boolean aggressiveCompact = false;


    @SuppressFBWarnings( "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE" )
    // sql statement is constructed using constants and enums
    private static void initTable( final Connection connection, final LocalDB.DB db ) throws LocalDBException
    {
        try
        {
            checkIfTableExists( connection, db );
            LOGGER.trace( () -> "table " + db + " appears to exist" );
        }
        catch ( final LocalDBException e )
        {
            // assume error was due to table missing;
            {
                final Instant startTime = Instant.now();
                final String sqlString = "CREATE table " + db.toString() + " (" + "\n"
                        + "  " + KEY_COLUMN + " VARCHAR(" + WIDTH_KEY + ") NOT NULL PRIMARY KEY," + "\n"
                        + "  " + VALUE_COLUMN + " CLOB"
                        + "\n"
                        + ")" + "\n";

                Statement statement = null;
                try
                {
                    statement = connection.createStatement();
                    statement.execute( sqlString );
                    connection.commit();
                    LOGGER.debug( () -> "created table " + db.toString() + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")" );
                }
                catch ( final SQLException ex )
                {
                    LOGGER.error( () -> "error creating new table " + db.toString() + ": " + ex.getMessage() );
                }
                finally
                {
                    close( statement );
                }
            }

            {
                final Instant startTime = Instant.now();
                final String indexName = db.toString() + "_IDX";
                final StringBuilder sqlString = new StringBuilder();
                sqlString.append( "CREATE index " ).append( indexName );
                sqlString.append( " ON " ).append( db.toString() );
                sqlString.append( " (" ).append( KEY_COLUMN ).append( ")" );

                Statement statement = null;
                try
                {
                    statement = connection.createStatement();
                    statement.execute( sqlString.toString() );
                    connection.commit();
                    LOGGER.debug( () -> "created index " + indexName + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")" );
                }
                catch ( final SQLException ex )
                {
                    LOGGER.error( () -> "error creating new index " + indexName + ex.getMessage() );
                }
                finally
                {
                    close( statement );
                }
            }
        }
    }

    private static void checkIfTableExists( final Connection connection, final LocalDB.DB db ) throws LocalDBException
    {
        final StringBuilder sb = new StringBuilder();
        sb.append( "SELECT * FROM  " ).append( db.toString() ).append( " WHERE " + KEY_COLUMN + " = '0'" );
        Statement statement = null;
        ResultSet resultSet = null;
        try
        {
            statement = connection.createStatement();
            resultSet = statement.executeQuery( sb.toString() );
        }
        catch ( final SQLException e )
        {
            final String errorMsg = "table doesn't exist or some other error: " + e.getCause();
            throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg ) );
        }
        finally
        {
            close( statement );
            close( resultSet );
        }
    }

    protected static void close( final Statement statement )
    {
        if ( statement != null )
        {
            try
            {
                statement.close();
            }
            catch ( final SQLException e )
            {
                LOGGER.error( () -> "unexpected error during close statement object " + e.getMessage(), e );
            }
        }
    }

    private static void close( final ResultSet resultSet )
    {
        if ( resultSet != null )
        {
            try
            {
                resultSet.close();
            }
            catch ( final SQLException e )
            {
                LOGGER.error( () -> "unexpected error during close resultSet object " + e.getMessage(), e );
            }
        }
    }

    AbstractJDBCLocalDB( )
            throws Exception
    {
    }

    public void close( )
            throws LocalDBException
    {
        status = LocalDB.Status.CLOSED;
        try
        {
            lock.writeLock().lock();
            if ( dbConnection != null )
            {
                try
                {
                    closeConnection( dbConnection );
                }
                catch ( final Exception e )
                {
                    LOGGER.debug( () -> "error while closing DB: " + e.getMessage() );
                }
            }
        }
        finally
        {
            lock.writeLock().unlock();
        }

        LOGGER.debug( () -> "closed" );
    }

    abstract void closeConnection( Connection connection )
            throws SQLException;

    public LocalDB.Status getStatus( )
    {
        return status;
    }

    public boolean contains( final LocalDB.DB db, final String key )
            throws LocalDBException
    {
        preCheck( false );
        return get( db, key ) != null;
    }

    public String get( final LocalDB.DB db, final String key )
            throws LocalDBException
    {
        preCheck( false );
        final StringBuilder sb = new StringBuilder();
        sb.append( "SELECT * FROM " ).append( db.toString() ).append( " WHERE " + KEY_COLUMN + " = ?" );

        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try
        {
            lock.readLock().lock();
            statement = dbConnection.prepareStatement( sb.toString() );
            statement.setString( 1, key );
            statement.setMaxRows( 1 );
            resultSet = statement.executeQuery();
            if ( resultSet.next() )
            {
                return resultSet.getString( VALUE_COLUMN );
            }
        }
        catch ( final SQLException ex )
        {
            throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, ex.getMessage() ) );
        }
        finally
        {
            close( statement );
            close( resultSet );
            lock.readLock().unlock();
        }
        return null;
    }

    public void init( final File dbDirectory, final Map<String, String> initParams, final Map<Parameter, String> parameters )
            throws LocalDBException
    {
        this.dbDirectory = dbDirectory;

        this.dbConnection = openConnection( dbDirectory, getDriverClasspath(), initParams );

        for ( final LocalDB.DB db : LocalDB.DB.values() )
        {
            initTable( dbConnection, db );
        }

        this.readOnly = LocalDBUtility.hasBooleanParameter( Parameter.readOnly, parameters );
        this.status = LocalDB.Status.OPEN;
    }

    public LocalDB.LocalDBIterator<Map.Entry<String, String>> iterator( final LocalDB.DB db )
            throws LocalDBException
    {
        try
        {
            if ( dbIterators.size() > ITERATOR_LIMIT )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_INTERNAL, "over " + ITERATOR_LIMIT + " iterators are outstanding, maximum limit exceeded" ) );
            }

            final LocalDB.LocalDBIterator iterator = new DbIterator( db );
            dbIterators.add( iterator );
            LOGGER.trace( () -> this.getClass().getSimpleName() + " issued iterator for " + db.toString() + ", outstanding iterators: " + dbIterators.size() );
            return iterator;
        }
        catch ( final Exception e )
        {
            throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, e.getMessage() ) );
        }
    }

    public void putAll( final LocalDB.DB db, final Map<String, String> keyValueMap )
            throws LocalDBException
    {
        preCheck( true );
        PreparedStatement insertStatement = null;
        PreparedStatement removeStatement = null;

        final String removeSqlString = "DELETE FROM " + db.toString() + " WHERE " + KEY_COLUMN + "=?";
        final String insertSqlString = "INSERT INTO " + db.toString() + "(" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES(?,?)";

        try
        {
            lock.writeLock().lock();
            // just in case anyone was unclear: sql does indeed suck.
            removeStatement = dbConnection.prepareStatement( removeSqlString );
            insertStatement = dbConnection.prepareStatement( insertSqlString );

            for ( final Map.Entry<String, String> entry : keyValueMap.entrySet() )
            {
                final String loopKey = entry.getKey();
                removeStatement.clearParameters();
                removeStatement.setString( 1, loopKey );
                removeStatement.addBatch();

                insertStatement.clearParameters();
                insertStatement.setString( 1, loopKey );
                insertStatement.setString( 2, entry.getValue() );
                insertStatement.addBatch();
            }

            removeStatement.executeBatch();
            insertStatement.executeBatch();
            dbConnection.commit();
        }
        catch ( final SQLException ex )
        {
            throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, ex.getMessage() ) );
        }
        finally
        {
            close( removeStatement );
            close( insertStatement );
            lock.writeLock().unlock();
        }
    }

    public boolean put( final LocalDB.DB db, final String key, final String value )
            throws LocalDBException
    {
        preCheck( true );
        if ( !contains( db, key ) )
        {
            final String sqlText = "INSERT INTO " + db.toString() + "(" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES(?,?)";
            executeUpdateStatement( sqlText, key, value );
            return false;
        }

        final String sqlText = "UPDATE " + db.toString() + " SET " + VALUE_COLUMN + "=? WHERE " + KEY_COLUMN + "=?";
        executeUpdateStatement( sqlText, value, key );
        return true;
    }

    private void executeUpdateStatement( final String sqlText, final String... values ) throws LocalDBException
    {
        lock.writeLock().lock();
        try
        {
            PreparedStatement statement = null;
            try
            {
                statement = dbConnection.prepareStatement( sqlText );
                for ( int i = 0; i < values.length; i++ )
                {
                    statement.setString( i + 1, values[ i ] );
                }
                statement.executeUpdate();
                dbConnection.commit();
            }
            catch ( final SQLException ex )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, ex.getMessage() ) );
            }
            finally
            {
                close( statement );
            }
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public boolean putIfAbsent( final LocalDB.DB db, final String key, final String value )
            throws LocalDBException
    {
        preCheck( true );
        final String selectSql = "SELECT * FROM " + db.toString() + " WHERE " + KEY_COLUMN + " = ?";

        PreparedStatement selectStatement = null;
        ResultSet resultSet = null;
        PreparedStatement insertStatement = null;
        try
        {
            lock.writeLock().lock();
            try
            {
                selectStatement = dbConnection.prepareStatement( selectSql );
                selectStatement.setString( 1, key );
                selectStatement.setMaxRows( 1 );
                resultSet = selectStatement.executeQuery();

                final boolean valueExists = resultSet.next();

                if ( !valueExists )
                {
                    final String insertSql = "INSERT INTO " + db.toString() + "(" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES(?,?)";
                    insertStatement = dbConnection.prepareStatement( insertSql );
                    insertStatement.setString( 1, key );
                    insertStatement.setString( 2, value );
                    insertStatement.executeUpdate();
                }

                dbConnection.commit();

                return !valueExists;
            }
            catch ( final SQLException ex )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, ex.getMessage() ) );
            }
            finally
            {
                close( selectStatement );
                close( resultSet );
                close( insertStatement );
            }
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public boolean remove( final LocalDB.DB db, final String key )
            throws LocalDBException
    {
        preCheck( true );
        if ( !contains( db, key ) )
        {
            return false;
        }

        final String sqlText = "DELETE FROM " + db.toString() + " WHERE " + KEY_COLUMN + "=?";
        executeUpdateStatement( sqlText, key );
        return true;
    }

    public long size( final LocalDB.DB db )
            throws LocalDBException
    {
        preCheck( false );
        final StringBuilder sb = new StringBuilder();
        sb.append( "SELECT COUNT(" + KEY_COLUMN + ") FROM " ).append( db.toString() );

        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try
        {
            lock.readLock().lock();
            try
            {
                statement = dbConnection.prepareStatement( sb.toString() );
                resultSet = statement.executeQuery();
                if ( resultSet.next() )
                {
                    return resultSet.getInt( 1 );
                }
            }
            catch ( final SQLException ex )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, ex.getMessage() ) );
            }
            finally
            {
                close( statement );
                close( resultSet );
            }
        }
        finally
        {
            lock.readLock().unlock();
        }

        return 0;
    }

    public void truncate( final LocalDB.DB db )
            throws LocalDBException
    {
        preCheck( true );
        final Instant startTime = Instant.now();
        final StringBuilder sqlText = new StringBuilder();
        sqlText.append( "DROP TABLE " ).append( db.toString() );

        PreparedStatement statement = null;
        try
        {
            lock.writeLock().lock();
            try
            {
                final Set<LocalDB.LocalDBIterator<Map.Entry<String, String>>> copiedIterators = new HashSet<>();
                copiedIterators.addAll( dbIterators );

                for ( final LocalDB.LocalDBIterator dbIterator : copiedIterators )
                {
                    dbIterator.close();
                }

                statement = dbConnection.prepareStatement( sqlText.toString() );
                statement.executeUpdate();
                dbConnection.commit();
                LOGGER.debug( () -> "truncated table " + db.toString() + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")" );

                initTable( dbConnection, db );
            }
            catch ( final SQLException ex )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, ex.getMessage() ) );
            }
            finally
            {
                close( statement );
            }
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public void removeAll( final LocalDB.DB db, final Collection<String> keys )
            throws LocalDBException
    {
        preCheck( true );
        final String sqlString = "DELETE FROM " + db.toString() + " WHERE " + KEY_COLUMN + "=?";
        PreparedStatement statement = null;
        try
        {
            lock.writeLock().lock();
            try
            {
                statement = dbConnection.prepareStatement( sqlString );

                for ( final String loopKey : keys )
                {
                    statement.clearParameters();
                    statement.setString( 1, loopKey );
                    statement.addBatch();
                }
                statement.executeBatch();
                dbConnection.commit();
            }
            catch ( final SQLException ex )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, ex.getMessage() ) );
            }
            finally
            {
                close( statement );
            }
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    abstract Connection openConnection(
            File databaseDirectory,
            String driverClasspath,
            Map<String, String> initParams
    ) throws LocalDBException;


    private class DbIterator implements Closeable, LocalDB.LocalDBIterator<Map.Entry<String, String>>
    {
        private Map.Entry<String, String> nextItem;

        private ResultSet resultSet;
        private final LocalDB.DB db;

        private DbIterator( final LocalDB.DB db ) throws LocalDBException
        {
            this.db = db;
            init();
            fetchNext();
        }

        private void init( ) throws LocalDBException
        {
            final String sqlText = "SELECT * FROM " + db.toString();

            try ( PreparedStatement statement = dbConnection.prepareStatement( sqlText ) )
            {
                resultSet = statement.executeQuery();
            }
            catch ( final SQLException ex )
            {
                throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, ex.getMessage() ) );
            }
        }

        private void fetchNext( )
        {
            try
            {
                if ( resultSet.next() )
                {
                    final String key = resultSet.getString( KEY_COLUMN );
                    final String value = resultSet.getString( VALUE_COLUMN );

                    nextItem = new AbstractMap.SimpleImmutableEntry<>( key, value );
                }
                else
                {
                    nextItem = null;
                }
            }
            catch ( final SQLException e )
            {
                throw new IllegalStateException( "error during db iteration of " + db.toString() + ": " + e.getCause() );
            }
        }

        public boolean hasNext( )
        {
            final boolean hasNext = nextItem != null;
            if ( !hasNext )
            {
                close();
            }
            return hasNext;
        }

        public void close( )
        {
            nextItem = null;
            AbstractJDBCLocalDB.close( resultSet );
            dbIterators.remove( this );
        }

        public Map.Entry<String, String> next( )
        {
            final Map.Entry<String, String> currentItem = nextItem;
            fetchNext();
            return currentItem;
        }
    }

    public File getFileLocation( )
    {
        return dbDirectory;
    }

    private void preCheck( final boolean write ) throws LocalDBException
    {
        if ( status != LocalDB.Status.OPEN )
        {
            throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, "LocalDB is not open, cannot begin a new transaction" ) );
        }

        if ( write && readOnly )
        {
            throw new IllegalStateException( "cannot allow mutation operation; LocalDB is in read-only mode" );
        }
    }

    abstract String getDriverClasspath( );

    @Override
    public Map<String, Serializable> debugInfo( )
    {
        return Collections.emptyMap();
    }

    @Override
    public Set<Flag> flags( )
    {
        return Collections.emptySet();
    }
}

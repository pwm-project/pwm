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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressFBWarnings( value = "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE" )
class DatabaseUtil
{
    private static final AtomicInteger OP_COUNTER = new AtomicInteger();

    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseUtil.class );

    private static final String INDEX_NAME_SUFFIX = "_IDX";

    private DatabaseUtil( )
    {
    }


    static void close( final Statement statement ) throws DatabaseException
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
                throw new DatabaseException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, "statement close failure: " + e.getMessage() ) );
            }
        }
    }

    static void close( final ResultSet resultSet ) throws DatabaseException
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
                throw new DatabaseException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, "resultset close failure: " + e.getMessage() ) );
            }
        }
    }

    static void commit( final Connection connection )
            throws DatabaseException
    {
        try
        {
            connection.commit();
        }
        catch ( final SQLException e )
        {
            LOGGER.warn( () -> "database commit failed: " + e.getMessage() );
            throw new DatabaseException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, "commit failure: " + e.getMessage() ) );
        }
    }

    static DatabaseException convertSqlException(
            final DebugInfo debugInfo,
            final SQLException e
    )
    {
        final String errorMsg = debugInfo.getOpName() + " operation opId=" + debugInfo.getOpId() + " failed, error: " + e.getMessage();
        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
        return new DatabaseException( errorInformation );
    }

    static void initTable(
            final Connection connection,
            final DatabaseTable table,
            final DBConfiguration dbConfiguration
    )
            throws DatabaseException
    {
        boolean tableExists = false;
        try
        {
            checkIfTableExists( connection, table );
            LOGGER.trace( () -> "table " + table + " appears to exist" );
            tableExists = true;
        }
        catch ( final DatabaseException e )
        {
            // assume error was due to table missing;
            LOGGER.trace( () -> "error while checking for table: " + e.getMessage() + ", assuming due to table non-existence" );
        }

        if ( !tableExists )
        {
            createTable( connection, table, dbConfiguration );
        }
    }

    private static void createTable(
            final Connection connection,
            final DatabaseTable table,
            final DBConfiguration dbConfiguration
    )
            throws DatabaseException
    {
        {
            final String sqlString = "CREATE table " + table.toString() + " (" + "\n"
                    + "  " + DatabaseService.KEY_COLUMN + " " + dbConfiguration.getColumnTypeKey() + "("
                    + dbConfiguration.getKeyColumnLength() + ") NOT NULL PRIMARY KEY," + "\n"
                    + "  " + DatabaseService.VALUE_COLUMN + " " + dbConfiguration.getColumnTypeValue() + " " + "\n"
                    + ")" + "\n";

            LOGGER.trace( () ->  "attempting to execute the following sql statement:\n " + sqlString );

            Statement statement = null;
            try
            {
                statement = connection.createStatement();
                statement.execute( sqlString );
                connection.commit();
                LOGGER.debug( () -> "created table " + table.toString() );
            }
            catch ( final SQLException ex )
            {
                final String errorMsg = "error creating new table " + table.toString() + ": " + ex.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
                throw new DatabaseException( errorInformation );
            }
            finally
            {
                DatabaseUtil.close( statement );
            }
        }

        {
            final String indexName = table.toString() + INDEX_NAME_SUFFIX;
            final String sqlString = "CREATE index " + indexName
                    + " ON " + table.toString()
                    + " (" + DatabaseService.KEY_COLUMN + ")";

            Statement statement = null;

            LOGGER.trace( () -> "attempting to execute the following sql statement:\n " + sqlString );

            try
            {
                statement = connection.createStatement();
                statement.execute( sqlString );
                connection.commit();
                LOGGER.debug( () -> "created index " + indexName );
            }
            catch ( final SQLException ex )
            {
                final String errorMsg = "error creating new index " + indexName + ": " + ex.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
                if ( dbConfiguration.isFailOnIndexCreation() )
                {
                    throw new DatabaseException ( errorInformation );
                }
                else
                {
                    LOGGER.warn( () -> errorInformation.toDebugStr() );
                }
            }
            finally
            {
                DatabaseUtil.close( statement );
            }
        }
    }

    private static void checkIfTableExists(
            final Connection connection,
            final DatabaseTable table
    )
            throws DatabaseException
    {
        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create( "checkIfTableExists", null, null, null );
        final String sqlText = "SELECT * FROM  " + table.toString() + " WHERE " + DatabaseService.KEY_COLUMN + " = '0'";

        ResultSet resultSet = null;
        try ( Statement statement = connection.createStatement() )
        {
            resultSet = statement.executeQuery( sqlText );
        }
        catch ( final SQLException e )
        {
            rollbackTransaction( connection );
            throw DatabaseUtil.convertSqlException( debugInfo, e );
        }
        finally
        {
            close( resultSet );
        }
    }

    static void rollbackTransaction( final Connection connection ) throws DatabaseException
    {
        try
        {
            connection.rollback();
        }
        catch ( final SQLException e1 )
        {
            final String errorMsg = "error during transaction rollback: " + e1.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
            throw new DatabaseException( errorInformation );
        }
    }


    @Getter
    @AllArgsConstructor
    static class DebugInfo implements Serializable
    {
        private final Instant startTime = Instant.now();
        private final int opId = OP_COUNTER.incrementAndGet();
        private final String opName;
        private final DatabaseTable table;
        private final String key;
        private final String value;

        static DebugInfo create(
                final String opName,
                final DatabaseTable table,
                final String key,
                final String value
        )
        {
            return new DebugInfo(
                    opName,
                    table,
                    key,
                    value
            );
        }
    }
}

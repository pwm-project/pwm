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

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

/**
 * Apache Derby Wrapper for {@link LocalDB} interface.   Uses a single table per DB, with
 * two columns each.  This class would be easily adaptable for a generic JDBC implementation.
 *
 * @author Jason D. Rivard
 */
public class DerbyLocalDB extends AbstractJDBCLocalDB
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DerbyLocalDB.class, true );

    private static final String DERBY_CLASSPATH = "org.apache.derby.jdbc.EmbeddedDriver";
    private static final String DERBY_DEFAULT_SCHEMA = "APP";

    private Driver driver;

    DerbyLocalDB( )
            throws Exception
    {
        super();
    }

    @Override
    String getDriverClasspath( )
    {
        return DERBY_CLASSPATH;
    }

    @Override
    void
    closeConnection( final Connection connection )
            throws SQLException
    {
        try
        {
            if ( driver != null )
            {
                driver.connect( "jdbc:derby:;shutdown=true", new Properties() );
            }

        }
        catch ( final SQLException e )
        {
            if ( "XJ015".equals( e.getSQLState() ) )
            {
                LOGGER.trace( () -> "Derby shutdown succeeded. SQLState=" + e.getSQLState() + ", message=" + e.getMessage() );
            }
            else
            {
                throw e;
            }
        }

        try
        {
            if ( driver != null )
            {
                DriverManager.deregisterDriver( driver );
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error while de-registering derby driver: " + e.getMessage() );
        }

        driver = null;

        try
        {
            connection.close();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error while closing derby connection: " + e.getMessage() );
        }
    }

    @Override
    Connection openConnection(
            final File databaseDirectory,
            final String driverClasspath,
            final Map<String, String> initOptions
    ) throws LocalDBException
    {
        final String filePath = databaseDirectory.getAbsolutePath() + File.separator + "derby-db";
        final String baseConnectionURL = "jdbc:derby:" + filePath;
        final String connectionURL = baseConnectionURL + ";create=true";

        try
        {
            //load driver.
            driver = ( Driver ) Class.forName( driverClasspath ).newInstance();
            final Connection connection = driver.connect( connectionURL, new Properties() );
            connection.setAutoCommit( false );

            if ( aggressiveCompact )
            {
                reclaimAllSpace( connection );
            }

            return connection;
        }
        catch ( final Throwable e )
        {
            final String errorMsg;
            if ( e instanceof SQLException )
            {
                final SQLException sqlException = ( SQLException ) e;
                final SQLException nextException = sqlException.getNextException();
                if ( nextException != null )
                {
                    if ( "XSDB6".equals( nextException.getSQLState() ) )
                    {
                        errorMsg = "unable to open LocalDB, the LocalDB is already opened in a different instance: " + nextException.getMessage();
                    }
                    else
                    {
                        errorMsg = "unable to open LocalDB, error=" + e.getMessage() + ", nextError=" + nextException.getMessage();
                    }
                }
                else
                {
                    errorMsg = "unable to open LocalDB, error=" + e.getMessage();
                }
            }
            else
            {
                errorMsg = "error opening DB: " + e.getMessage();
            }
            LOGGER.error( () -> errorMsg, e );
            throw new LocalDBException( new ErrorInformation( PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg ) );
        }
    }

    private void reclaimAllSpace( final Connection dbConnection )
    {
        final Instant startTime = Instant.now();
        final long startSize = FileSystemUtility.getFileDirectorySize( dbDirectory );
        LOGGER.debug( () -> "beginning reclaim space in all tables startSize=" + StringUtil.formatDiskSize( startSize ) );
        for ( final LocalDB.DB db : LocalDB.DB.values() )
        {
            reclaimSpace( dbConnection, db );
        }
        final long completeSize = FileSystemUtility.getFileDirectorySize( dbDirectory );
        final long sizeDifference = startSize - completeSize;
        LOGGER.debug( () -> "completed reclaim space in all tables; duration=" + TimeDuration.compactFromCurrent( startTime )
                + ", startSize=" + StringUtil.formatDiskSize( startSize )
                + ", completeSize=" + StringUtil.formatDiskSize( completeSize )
                + ", sizeDifference=" + StringUtil.formatDiskSize( sizeDifference )
        );
    }

    public void truncate( final LocalDB.DB db )
            throws LocalDBException
    {
        super.truncate( db );
        reclaimSpace( this.dbConnection, db );
    }

    private void reclaimSpace( final Connection dbConnection, final LocalDB.DB db )
    {
        if ( readOnly )
        {
            return;
        }

        final long startTime = System.currentTimeMillis();
        CallableStatement statement = null;
        try
        {
            lock.writeLock().lock();
            LOGGER.debug( () -> "beginning reclaim space in table " + db.toString() );
            statement = dbConnection.prepareCall( "CALL SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE(?, ?, ?, ?, ?)" );
            statement.setString( 1, DERBY_DEFAULT_SCHEMA );
            statement.setString( 2, db.toString() );
            statement.setShort( 3, ( short ) 1 );
            statement.setShort( 4, ( short ) 1 );
            statement.setShort( 5, ( short ) 1 );
            statement.execute();
        }
        catch ( final SQLException ex )
        {
            LOGGER.error( () -> "error reclaiming space in table " + db.toString() + ": " + ex.getMessage() );
        }
        finally
        {
            close( statement );
            lock.writeLock().unlock();
        }
        LOGGER.debug( () -> "completed reclaimed space in table " + db.toString() + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")" );
    }
}


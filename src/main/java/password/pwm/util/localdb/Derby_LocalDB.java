/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.util.localdb;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.FileSystemUtility;
import password.pwm.util.Helper;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

/**
 * Apache Derby Wrapper for {@link LocalDB} interface.   Uses a single table per DB, with
 * two columns each.  This class would be easily adaptable for a generic JDBC implementation.
 *
 * @author Jason D. Rivard
 */
public class Derby_LocalDB extends AbstractJDBC_LocalDB {
    private static final PwmLogger LOGGER = PwmLogger.forClass(Derby_LocalDB.class, true);

    private static final String DERBY_CLASSPATH = "org.apache.derby.jdbc.EmbeddedDriver";
    private static final String DERBY_DEFAULT_SCHEMA = "APP";

    private static final String OPTION_KEY_RECLAIM_SPACE = "reclaimAllSpace";

    Derby_LocalDB()
            throws Exception
    {
        super();
    }

    @Override
    String getDriverClasspath()
    {
        return DERBY_CLASSPATH;
    }

    @Override
    void
    closeConnection(final Connection connection)
            throws SQLException
    {
        try {
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (SQLException e) {
            if ("XJ015".equals(e.getSQLState())) {
                LOGGER.trace("Derby shutdown succeeded. SQLState=" + e.getSQLState() + ", message=" + e.getMessage());
            } else {
                throw e;
            }
        }
        connection.close();
    }

    @Override
    Connection openConnection(
            final File databaseDirectory,
            final String driverClasspath,
            final Map<String,String> initOptions
    ) throws LocalDBException {
        final String filePath = databaseDirectory.getAbsolutePath() + File.separator + "derby-db";
        final String baseConnectionURL = "jdbc:derby:" + filePath;
        final String connectionURL = baseConnectionURL + ";create=true";

        try {
            Class.forName(driverClasspath).newInstance(); //load driver.
            final Connection connection = DriverManager.getConnection(connectionURL);
            connection.setAutoCommit(false);

            if (initOptions != null && initOptions.containsKey(OPTION_KEY_RECLAIM_SPACE) && Boolean.parseBoolean(initOptions.get(OPTION_KEY_RECLAIM_SPACE))) {
                reclaimAllSpace(connection);
            }

            return connection;
        } catch (Throwable e) {
            final String errorMsg;
            if (e instanceof SQLException) {
                SQLException sqlException = (SQLException)e;
                SQLException nextException = sqlException.getNextException();
                if (nextException != null) {
                    if ("XSDB6".equals(nextException.getSQLState())) {
                        errorMsg = "unable to open LocalDB, the LocalDB is already opened in a different instance: " + nextException.getMessage();
                    } else {
                        errorMsg = "unable to open LocalDB, error=" + e.getMessage() + ", nextError=" + nextException.getMessage();
                    }
                } else {
                    errorMsg = "unable to open LocalDB, error=" + e.getMessage();
                }
            } else {
                errorMsg = "error opening DB: " + e.getMessage();
            }
            LOGGER.error(errorMsg, e);
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,errorMsg));
        }
    }

    private void reclaimAllSpace(final Connection dbConnection) {
        final java.util.Date startTime = new java.util.Date();
        final long startSize = FileSystemUtility.getFileDirectorySize(dbDirectory);
        LOGGER.debug("beginning reclaim space in all tables startSize=" + Helper.formatDiskSize(startSize));
        for (final LocalDB.DB db : LocalDB.DB.values()) {
            reclaimSpace(dbConnection,db);
        }
        final long completeSize = FileSystemUtility.getFileDirectorySize(dbDirectory);
        final long sizeDifference = startSize - completeSize;
        LOGGER.debug("completed reclaim space in all tables; duration=" + TimeDuration.fromCurrent(startTime).asCompactString()
                + ", startSize=" + Helper.formatDiskSize(startSize)
                + ", completeSize=" + Helper.formatDiskSize(completeSize)
                + ", sizeDifference=" + Helper.formatDiskSize(sizeDifference)
        );
    }

    public void truncate(final LocalDB.DB db)
            throws LocalDBException
    {
        super.truncate(db);
        reclaimSpace(this.dbConnection, db);
    }

    private void reclaimSpace(final Connection dbConnection, final LocalDB.DB db)
    {
        if (getStatus() != LocalDB.Status.OPEN || readOnly) {
            return;
        }

        final long startTime = System.currentTimeMillis();
        CallableStatement statement = null;
        try {
            LOCK.writeLock().lock();
            LOGGER.debug("beginning reclaim space in table " + db.toString());
            statement = dbConnection.prepareCall("CALL SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE(?, ?, ?, ?, ?)");
            statement.setString(1, DERBY_DEFAULT_SCHEMA);
            statement.setString(2, db.toString());
            statement.setShort(3, (short) 1);
            statement.setShort(4, (short) 1);
            statement.setShort(5, (short) 1);
            statement.execute();
        } catch (SQLException ex) {
            LOGGER.error("error reclaiming space in table " + db.toString() + ": " + ex.getMessage());
        } finally {
            close(statement);
            LOCK.writeLock().unlock();
        }
        LOGGER.debug("completed reclaimed space in table " + db.toString() + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
    }
}


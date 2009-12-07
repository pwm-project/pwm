/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.util.db;

import password.pwm.Helper;
import password.pwm.util.PwmLogger;

import java.io.File;
import java.sql.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apache Derby Wrapper for {@link password.pwm.util.db.PwmDB} interface.   Uses a single table per DB, with
 * two columns each.  This class would be easily adaptable for a generic JDBC implementation.
 *
 * @author Jason D. Rivard
 */
public class Derby_PwmDb implements PwmDB {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Derby_PwmDb.class);

    private static final String KEY_COLUMN = "id";
    private static final String VALUE_COLUMN = "value";

    private static final String WIDTH_KEY = String.valueOf(PwmDB.MAX_KEY_LENGTH);
    private static final String WIDTH_VALUE = String.valueOf(PwmDB.MAX_VALUE_LENGTH);

    //private Connection connection;
    private String baseConnectionURL;
    private File dbDirectory;

    // cache of dbIterators
    private final Map<DB, DbIterator> dbIterators = new ConcurrentHashMap<DB, DbIterator>();

    private final Map<DB, Connection> connectionMap = new ConcurrentHashMap<DB, Connection>();

// -------------------------- STATIC METHODS --------------------------

    private static void initTable(final Connection connection, final DB db) throws PwmDBException {
        try {
            checkIfTableExists(connection,db);
            LOGGER.trace("table " + db + " appears to exist");
        } catch (PwmDBException e) { // assume error was due to table missing;
            {
                final StringBuilder sqlString = new StringBuilder();
                sqlString.append("CREATE table ").append(db.toString()).append(" (").append("\n");
                sqlString.append("  " + KEY_COLUMN + " VARCHAR(").append(WIDTH_KEY).append(") NOT NULL PRIMARY KEY,").append("\n");
                sqlString.append("  " + VALUE_COLUMN + " VARCHAR(").append(WIDTH_VALUE).append(") ");
                sqlString.append("\n");
                sqlString.append(")").append("\n");

                Statement statement = null;
                try {
                    statement = connection.createStatement();
                    statement.execute(sqlString.toString());
                    connection.commit();
                    LOGGER.debug("created table " + db.toString());
                } catch (SQLException ex) {
                    LOGGER.error("error creating new table " + db.toString() + ": " + ex.getMessage());
                } finally {
                    close(statement);
                }
            }

            {
                final String indexName = db.toString() + "_IDX";
                final StringBuilder sqlString = new StringBuilder();
                sqlString.append("CREATE index ").append(indexName);
                sqlString.append(" ON ").append(db.toString());
                sqlString.append(" (").append(KEY_COLUMN).append(")");

                Statement statement = null;
                try {
                    statement = connection.createStatement();
                    statement.execute(sqlString.toString());
                    connection.commit();
                    LOGGER.debug("created index " + indexName);
                } catch (SQLException ex) {
                    LOGGER.error("error creating new index " + indexName + ex.getMessage());
                } finally {
                    close(statement);
                }
            }
        }
    }

    private static void checkIfTableExists(final Connection connection, final DB db) throws PwmDBException {
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM  ").append(db.toString()).append(" WHERE " + KEY_COLUMN + " = '0'");
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery(sb.toString());
        } catch (SQLException e) {
            throw new PwmDBException("table doesn't exist or some other error: " + e.getCause());
        } finally {
            close(statement);
            close(resultSet);
        }
    }

    private static void close(final Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                LOGGER.error("unexected error during close statement object " + e.getMessage(),e);
            }
        }
    }

    private static void close(final ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                LOGGER.error("unexected error during close resultSet object " + e.getMessage(),e);
            }
        }
    }

// --------------------------- CONSTRUCTORS ---------------------------

    Derby_PwmDb()
            throws Exception
    {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmDB ---------------------

    public void close()
            throws PwmDBException
    {
        for (final Connection connection : connectionMap.values()) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (connection) {
                if (connection != null) {
                    try {
                        connection.close();
                        final String connectionURL = baseConnectionURL + ";shutdown=true";

                        try {
                            DriverManager.getConnection(connectionURL);
                        }  catch (Throwable e)  {
                            throw new PwmDBException(e.getMessage());
                        }
                    } catch (Exception e) {
                        LOGGER.debug("error while closing DB: " + e.getMessage());
                    }
                    connectionMap.values().remove(connection);
                }
            }
        }
    }

    public boolean contains(final DB db, final String key)
            throws PwmDBException
    {
        return get(db, key) != null;
    }

    public String get(final DB db, final String key)
            throws PwmDBException
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ").append(db.toString()).append(" WHERE " + KEY_COLUMN + " = ?");

        PreparedStatement statement = null;
        ResultSet resultSet = null;
        final Connection connection = connectionMap.get(db);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (connection) {
            try {
                statement = connection.prepareStatement(sb.toString());
                statement.setString(1,key);
                statement.setMaxRows(1);
                resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    return resultSet.getString(VALUE_COLUMN);
                }
            } catch (SQLException ex) {
                throw new PwmDBException(ex.getCause());
            } finally {
                close(statement);
                close(resultSet);
            }
            return null;
        }
    }

    public void init(final File dbDirectory, final String initString)
            throws PwmDBException
    {
        this.dbDirectory = dbDirectory;

        for (final DB db : DB.values()) {
            connectionMap.put(db, openDB(dbDirectory));
        }

        for (final DB db : DB.values()) {
            initTable(connectionMap.get(db), db);
        }
    }

    public synchronized Iterator<TransactionItem> iterator(final DB db)
            throws PwmDBException
    {
        try {
            if (dbIterators.containsKey(db)) {
                throw new IllegalArgumentException("multiple iterators per DB are not permitted");
            }

            final DbIterator iterator = new DbIterator(db);
            dbIterators.put(db,iterator);
            return iterator;
        } catch (Exception e) {
            throw new PwmDBException(e);
        }
    }

    public void putAll(final DB db, final Map<String,String> keyValueMap)
            throws PwmDBException
    {
        PreparedStatement insertStatement = null, removeStatement = null;
        final String removeSqlString = "DELETE FROM " + db.toString() + " WHERE "  + KEY_COLUMN + "=?";
        final String insertSqlString = "INSERT INTO " + db.toString() + "(" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES(?,?)";

        final Connection connection = connectionMap.get(db);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (connection) {
            try {
                // just in case anyone was unclear: sql does indeed suck.
                removeStatement = connection.prepareStatement(removeSqlString);
                insertStatement = connection.prepareStatement(insertSqlString);

                for (final String loopKey : keyValueMap.keySet()) {
                    removeStatement.clearParameters();
                    removeStatement.setString(1,loopKey);
                    removeStatement.addBatch();

                    insertStatement.clearParameters();
                    insertStatement.setString(1,loopKey);
                    insertStatement.setString(2,keyValueMap.get(loopKey));
                    insertStatement.addBatch();
                }

                removeStatement.executeBatch();
                insertStatement.executeBatch();
                connection.commit();
            } catch (SQLException ex) {
                throw new PwmDBException(ex.getCause());
            } finally {
                close(removeStatement);
                close(insertStatement);
            }
        }
    }


    public boolean put(final DB db, final String key, final String value)
            throws PwmDBException
    {
        if (!contains(db,key)) {
            final String sqlText = "INSERT INTO " + db.toString() + "(" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES(?,?)";
            PreparedStatement statement = null;

            final Connection connection = connectionMap.get(db);
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (connection) {
                try {
                    statement = connection.prepareStatement(sqlText);
                    statement.setString(1,key);
                    statement.setString(2,value);
                    statement.executeUpdate();
                    connection.commit();
                } catch (SQLException ex) {
                    throw new PwmDBException(ex.getCause());
                } finally {
                    close(statement);
                }
            }
            return false;
        }

        final String sqlText = "UPDATE " + db.toString() + " SET " + VALUE_COLUMN + "=? WHERE " + KEY_COLUMN + "=?";
        PreparedStatement statement = null;

        final Connection connection = connectionMap.get(db);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (connection) {
            try {
                statement = connection.prepareStatement(sqlText);
                statement.setString(1,value);
                statement.setString(2,key);
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException ex) {
                throw new PwmDBException(ex.getCause());
            } finally {
                close(statement);
            }
        }

        return true;
    }

    public boolean remove(final DB db, final String key)
            throws PwmDBException
    {
        if (!contains(db,key)) {
            return false;
        }

        final StringBuilder sqlText = new StringBuilder();
        sqlText.append("DELETE FROM ").append(db.toString()).append(" WHERE " + KEY_COLUMN + "=?");

        PreparedStatement statement = null;
        final Connection connection = connectionMap.get(db);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (connection) {
            try {
                statement = connection.prepareStatement(sqlText.toString());
                statement.setString(1,key);
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException ex) {
                throw new PwmDBException(ex.getCause());
            } finally {
                close(statement);
            }
        }

        return true;
    }

    public synchronized void returnIterator(final DB db)
            throws PwmDBException
    {
        final DbIterator dbIterator = dbIterators.remove(db);
        if (dbIterator != null) {
            try {
                dbIterator.close();
            } catch (Exception e) {
                throw new PwmDBException("error while closing dbIterator: " + e.getMessage());
            }
        }
    }

    public int size(final DB db)
            throws PwmDBException
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT COUNT(" + KEY_COLUMN + ") FROM ").append(db.toString());

        PreparedStatement statement = null;
        ResultSet resultSet = null;
        final Connection connection = connectionMap.get(db);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (connection) {
            try {
                statement = connection.prepareStatement(sb.toString());
                resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            } catch (SQLException ex) {
                throw new PwmDBException(ex.getCause());
            } finally {
                close(statement);
                close(resultSet);
            }
        }

        return 0;
    }

    public void truncate(final DB db)
            throws PwmDBException
    {
        final StringBuilder sqlText = new StringBuilder();
        sqlText.append("DROP TABLE ").append(db.toString());

        PreparedStatement statement = null;
        final Connection connection = connectionMap.get(db);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (connection) {
            try {
                statement = connection.prepareStatement(sqlText.toString());
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException ex) {
                throw new PwmDBException(ex.getCause());
            } finally {
                close(statement);
            }

            initTable(connection, db);
        }
    }

// -------------------------- OTHER METHODS --------------------------

    private Connection openDB(final File databaseDirectory) throws PwmDBException {
        final String filePath = databaseDirectory.getAbsolutePath() + File.separator + "derby-db";
        baseConnectionURL = "jdbc:derby:" + filePath;
        final String connectionURL = baseConnectionURL + ";create=true";

        try {
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();
            final Connection connection = DriverManager.getConnection(connectionURL);
            connection.setAutoCommit(false);
            return connection;
        }  catch (Throwable e)  {
            LOGGER.error("error opening DB: " + e.getMessage(),e);
            throw new PwmDBException(e.getCause());
        }
    }

// -------------------------- INNER CLASSES --------------------------

    private class DbIterator implements Iterator<TransactionItem> {
        private TransactionItem nextItem;
        private TransactionItem currentItem;

        private ResultSet resultSet;
        private final DB db;

        private DbIterator(final DB db) throws PwmDBException {
            this.db = db;
            init();
            fetchNext();
        }

        private void init() throws PwmDBException {
            final StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM ").append(db.toString());

            try {
                final Connection connection = connectionMap.get(db);
                final PreparedStatement statement = connection.prepareStatement(sb.toString());
                resultSet = statement.executeQuery();
            } catch (SQLException ex) {
                throw new PwmDBException(ex.getCause());
            }
        }

        private void fetchNext() {
            try {
                if (resultSet.next()) {
                    final String key = resultSet.getString(KEY_COLUMN);
                    final String value = resultSet.getString(VALUE_COLUMN);
                    nextItem = new TransactionItem(db, key, value);
                } else {
                    nextItem = null;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("error during db iteration of " + db.toString() + ": " + e.getCause());
            }
        }

        public boolean hasNext() {
            return nextItem != null;
        }

        public void close() {
            nextItem = null;
            Derby_PwmDb.close(resultSet);
            dbIterators.remove(db);
        }

        public TransactionItem next() {
            currentItem = nextItem;
            fetchNext();
            return currentItem;
        }

        public void remove() {
            if (currentItem != null) {
                try {
                    Derby_PwmDb.this.remove(db,currentItem.getKey());
                } catch (PwmDBException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        protected void finalize() throws Throwable {
            super.finalize();
            close();
        }
    }

    public long diskSpaceUsed() {
        try {
            return Helper.getFileDirectorySize(dbDirectory);
        } catch (Exception e) {
            LOGGER.error("error trying to compute db directory size: " + e.getMessage());
        }
        return 0;
    }

    public void removeAll(final DB db, final Collection<String> keys) throws PwmDBException {
        final String sqlString = "DELETE FROM " + db.toString() + " WHERE " + KEY_COLUMN + "=?";
        PreparedStatement statement = null;
        final Connection connection = connectionMap.get(db);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (connection) {

            try {
                statement = connection.prepareStatement(sqlString);

                for (final String loopKey : keys) {
                    statement.clearParameters();
                    statement.setString(1,loopKey);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException ex) {
                throw new PwmDBException(ex.getCause());
            } finally {
                close(statement);
            }
        }
    }
}
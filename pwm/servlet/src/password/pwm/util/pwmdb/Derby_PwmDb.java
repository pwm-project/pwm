/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.util.pwmdb;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.PwmLogger;

import java.io.File;
import java.sql.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static password.pwm.util.pwmdb.PwmDB.DB;

/**
 * Apache Derby Wrapper for {@link password.pwm.util.pwmdb.PwmDB} interface.   Uses a single table per DB, with
 * two columns each.  This class would be easily adaptable for a generic JDBC implementation.
 *
 * @author Jason D. Rivard
 */
public class Derby_PwmDb implements PwmDBProvider {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Derby_PwmDb.class, true);

    private static final String KEY_COLUMN = "id";
    private static final String VALUE_COLUMN = "value";

    private static final String WIDTH_KEY = String.valueOf(PwmDB.MAX_KEY_LENGTH);
    private static final String WIDTH_VALUE = String.valueOf(PwmDB.MAX_VALUE_LENGTH);

    //private Connection connection;
    private String baseConnectionURL;
    private File dbDirectory;

    // cache of dbIterators
    private final Map<DB, DbIterator<String>> dbIterators = new ConcurrentHashMap<DB, DbIterator<String>>();

    private final Map<DB, Connection> connectionMap = new ConcurrentHashMap<DB, Connection>();

    // operation lock
    private final ReadWriteLock LOCK = new ReentrantReadWriteLock();
    
    private PwmDB.Status status = PwmDB.Status.NEW;
    private boolean readOnly = false;


// -------------------------- STATIC METHODS --------------------------

    private static void initTable(final Connection connection, final DB db) throws PwmDBException {
        try {
            checkIfTableExists(connection, db);
            LOGGER.trace("table " + db + " appears to exist");
        } catch (PwmDBException e) { // assume error was due to table missing;
            {
                final StringBuilder sqlString = new StringBuilder();
                sqlString.append("CREATE table ").append(db.toString()).append(" (").append("\n");
                sqlString.append("  " + KEY_COLUMN + " VARCHAR(").append(WIDTH_KEY).append(") NOT NULL PRIMARY KEY,").append("\n");
                sqlString.append("  " + VALUE_COLUMN + " CLOB");
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
            final String errorMsg = "table doesn't exist or some other error: " + e.getCause();
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,errorMsg));
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
                LOGGER.error("unexpected error during close statement object " + e.getMessage(), e);
            }
        }
    }

    private static void close(final ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                LOGGER.error("unexected error during close resultSet object " + e.getMessage(), e);
            }
        }
    }

// --------------------------- CONSTRUCTORS ---------------------------

    Derby_PwmDb()
            throws Exception {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmDB ---------------------

    public void close()
            throws PwmDBException {
        status = PwmDB.Status.CLOSED;
        try {
            LOCK.writeLock().lock();
            for (final Connection connection : connectionMap.values()) {
                if (connection != null) {
                    try {

                        connection.close();
                        final String connectionURL = baseConnectionURL + ";shutdown=true";

                        try {
                            DriverManager.getConnection(connectionURL);
                        } catch (Throwable e) {
                            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
                        }
                    } catch (Exception e) {
                        LOGGER.debug("error while closing DB: " + e.getMessage());
                    }
                }
                connectionMap.values().remove(connection);
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public PwmDB.Status getStatus() {
        return status;
    }

    public boolean contains(final DB db, final String key)
            throws PwmDBException {
        preCheck(false);
        return get(db, key) != null;
    }

    public String get(final DB db, final String key)
            throws PwmDBException {
        preCheck(false);
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ").append(db.toString()).append(" WHERE " + KEY_COLUMN + " = ?");

        PreparedStatement statement = null;
        ResultSet resultSet = null;
        final Connection connection = connectionMap.get(db);
        try {
            LOCK.readLock().lock();
            statement = connection.prepareStatement(sb.toString());
            statement.setString(1, key);
            statement.setMaxRows(1);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString(VALUE_COLUMN);
            }
        } catch (SQLException ex) {
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.readLock().unlock();
            close(statement);
            close(resultSet);
        }
        return null;
    }

    public void init(final File dbDirectory, final Map<String, String> initParams, final boolean readOnly)
            throws PwmDBException {
        this.dbDirectory = dbDirectory;

        for (final DB db : DB.values()) {
            connectionMap.put(db, openDB(dbDirectory));
        }

        for (final DB db : DB.values()) {
            initTable(connectionMap.get(db), db);
        }

        this.readOnly = readOnly;
        this.status = PwmDB.Status.OPEN;
    }

    public synchronized Iterator<String> iterator(final DB db)
            throws PwmDBException {
        try {
            if (dbIterators.containsKey(db)) {
                throw new IllegalArgumentException("multiple iterators per DB are not permitted");
            }

            final DbIterator iterator = new DbIterator(db);
            dbIterators.put(db, iterator);
            return iterator;
        } catch (Exception e) {
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        }
    }

    public void putAll(final DB db, final Map<String, String> keyValueMap)
            throws PwmDBException {
        preCheck(true);
        PreparedStatement insertStatement = null, removeStatement = null;
        final String removeSqlString = "DELETE FROM " + db.toString() + " WHERE " + KEY_COLUMN + "=?";
        final String insertSqlString = "INSERT INTO " + db.toString() + "(" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES(?,?)";

        final Connection connection = connectionMap.get(db);
        try {
            LOCK.writeLock().lock();
            // just in case anyone was unclear: sql does indeed suck.
            removeStatement = connection.prepareStatement(removeSqlString);
            insertStatement = connection.prepareStatement(insertSqlString);

            for (final String loopKey : keyValueMap.keySet()) {
                removeStatement.clearParameters();
                removeStatement.setString(1, loopKey);
                removeStatement.addBatch();

                insertStatement.clearParameters();
                insertStatement.setString(1, loopKey);
                insertStatement.setString(2, keyValueMap.get(loopKey));
                insertStatement.addBatch();
            }

            removeStatement.executeBatch();
            insertStatement.executeBatch();
            connection.commit();
        } catch (SQLException ex) {
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
            close(removeStatement);
            close(insertStatement);
        }
    }


    public boolean put(final DB db, final String key, final String value)
            throws PwmDBException {
        preCheck(true);
        if (!contains(db, key)) {
            final String sqlText = "INSERT INTO " + db.toString() + "(" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES(?,?)";
            PreparedStatement statement = null;

            final Connection connection = connectionMap.get(db);
            try {
                LOCK.writeLock().lock();
                statement = connection.prepareStatement(sqlText);
                statement.setString(1, key);
                statement.setString(2, value);
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException ex) {
                throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
            } finally {
                LOCK.writeLock().unlock();
                close(statement);
            }
            return false;
        }

        final String sqlText = "UPDATE " + db.toString() + " SET " + VALUE_COLUMN + "=? WHERE " + KEY_COLUMN + "=?";
        PreparedStatement statement = null;

        final Connection connection = connectionMap.get(db);
        try {
            LOCK.writeLock().lock();
            statement = connection.prepareStatement(sqlText);
            statement.setString(1, value);
            statement.setString(2, key);
            statement.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
            close(statement);

        }

        return true;
    }

    public boolean remove(final DB db, final String key)
            throws PwmDBException {
        preCheck(true);
        if (!contains(db, key)) {
            return false;
        }

        final StringBuilder sqlText = new StringBuilder();
        sqlText.append("DELETE FROM ").append(db.toString()).append(" WHERE " + KEY_COLUMN + "=?");

        PreparedStatement statement = null;
        final Connection connection = connectionMap.get(db);
        try {
            LOCK.writeLock().lock();
            statement = connection.prepareStatement(sqlText.toString());
            statement.setString(1, key);
            statement.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
            close(statement);
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
                throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
            }
        }
    }

    public int size(final DB db)
            throws PwmDBException
    {
        preCheck(false);
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT COUNT(" + KEY_COLUMN + ") FROM ").append(db.toString());

        PreparedStatement statement = null;
        ResultSet resultSet = null;
        final Connection connection = connectionMap.get(db);
        try {
            LOCK.readLock().lock();
            statement = connection.prepareStatement(sb.toString());
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException ex) {
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.readLock().unlock();
            close(statement);
            close(resultSet);
        }

        return 0;
    }

    public void truncate(final DB db)
            throws PwmDBException
    {
        preCheck(true);
        final StringBuilder sqlText = new StringBuilder();
        sqlText.append("DROP TABLE ").append(db.toString());

        PreparedStatement statement = null;
        final Connection connection = connectionMap.get(db);
        try {
            LOCK.writeLock().lock();
            statement = connection.prepareStatement(sqlText.toString());
            statement.executeUpdate();
            connection.commit();
            initTable(connection, db);
        } catch (SQLException ex) {
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
            close(statement);
        }
    }

    public void removeAll(final DB db, final Collection<String> keys)
            throws PwmDBException
    {
        preCheck(true);
        final String sqlString = "DELETE FROM " + db.toString() + " WHERE " + KEY_COLUMN + "=?";
        PreparedStatement statement = null;
        final Connection connection = connectionMap.get(db);
        try {
            LOCK.writeLock().lock();
            statement = connection.prepareStatement(sqlString);

            for (final String loopKey : keys) {
                statement.clearParameters();
                statement.setString(1, loopKey);
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        } catch (SQLException ex) {
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
            close(statement);
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
        } catch (Throwable e) {
            final String errorMsg = "error opening DB: " + e.getMessage();
            LOGGER.error(errorMsg, e);
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,errorMsg));
        }
    }

// -------------------------- INNER CLASSES --------------------------

    private class DbIterator<K> implements Iterator<String> {
        private String nextItem;
        private String currentItem;

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
                throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
            }
        }

        private void fetchNext() {
            try {
                if (resultSet.next()) {
                    nextItem = resultSet.getString(KEY_COLUMN);
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

        public String next() {
            currentItem = nextItem;
            fetchNext();
            return currentItem;
        }

        public void remove() {
            if (currentItem != null) {
                try {
                    Derby_PwmDb.this.remove(db, currentItem);
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

    public File getFileLocation() {
        return dbDirectory;
    }

    private void preCheck(final boolean write) throws PwmDBException {
        if (status != PwmDB.Status.OPEN) {
            throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,"pwmDB is not open, cannot begin a new transaction"));
        }

        if (write && readOnly) {
            throw new IllegalStateException("cannot allow mutator operation; pwmDB is in read-only mode");
        }
    }
}
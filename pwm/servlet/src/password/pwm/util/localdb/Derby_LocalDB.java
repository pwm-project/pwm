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

package password.pwm.util.localdb;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.PwmLogger;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static password.pwm.util.localdb.LocalDB.DB;

/**
 * Apache Derby Wrapper for {@link LocalDB} interface.   Uses a single table per DB, with
 * two columns each.  This class would be easily adaptable for a generic JDBC implementation.
 *
 * @author Jason D. Rivard
 */
public class Derby_LocalDB implements LocalDBProvider {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Derby_LocalDB.class, true);

    private static final String KEY_COLUMN = "id";
    private static final String VALUE_COLUMN = "value";
    private final static int ITERATOR_LIMIT = 100;

    private static final String WIDTH_KEY = String.valueOf(LocalDB.MAX_KEY_LENGTH);

    private static final String DERBY_CLASSPATH = "org.apache.derby.jdbc.EmbeddedDriver";

    private Driver driver;
    private File dbDirectory;

    // cache of dbIterators
    private final Set<LocalDB.PwmDBIterator<String>> dbIterators = Collections.newSetFromMap(new ConcurrentHashMap<LocalDB.PwmDBIterator<String>, Boolean>());

    // sql db connection
    private Connection dbConnection;

    // operation lock
    private final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    private LocalDB.Status status = LocalDB.Status.NEW;
    private boolean readOnly = false;


// -------------------------- STATIC METHODS --------------------------

    private static void initTable(final Connection connection, final DB db) throws LocalDBException {
        try {
            checkIfTableExists(connection, db);
            LOGGER.trace("table " + db + " appears to exist");
        } catch (LocalDBException e) { // assume error was due to table missing;
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

    private static void checkIfTableExists(final Connection connection, final DB db) throws LocalDBException {
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM  ").append(db.toString()).append(" WHERE " + KEY_COLUMN + " = '0'");
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery(sb.toString());
        } catch (SQLException e) {
            final String errorMsg = "table doesn't exist or some other error: " + e.getCause();
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,errorMsg));
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
                LOGGER.error("unexpected error during close resultSet object " + e.getMessage(), e);
            }
        }
    }

// --------------------------- CONSTRUCTORS ---------------------------

    Derby_LocalDB()
            throws Exception {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmDB ---------------------

    public void close()
            throws LocalDBException {
        status = LocalDB.Status.CLOSED;
        try {
            LOCK.writeLock().lock();
            if (dbConnection != null) {
                try {

                    dbConnection.close();
                    DriverManager.getConnection("jdbc:derby:;shutdown=true");
                } catch (Exception e) {
                    LOGGER.debug("error while closing DB: " + e.getMessage());
                }
            }
        } finally {
            LOCK.writeLock().unlock();
        }

        try {
            LOCK.writeLock().lock();
            DriverManager.deregisterDriver(driver);
            driver = null;
        } catch (SQLException e) {
            LOGGER.error("unable to de-register sql driver: " + e.getMessage());
        } finally {
            LOCK.writeLock().unlock();
        }

        LOGGER.debug("closed");
    }

    public LocalDB.Status getStatus() {
        return status;
    }

    public boolean contains(final DB db, final String key)
            throws LocalDBException {
        preCheck(false);
        return get(db, key) != null;
    }

    public String get(final DB db, final String key)
            throws LocalDBException {
        preCheck(false);
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ").append(db.toString()).append(" WHERE " + KEY_COLUMN + " = ?");

        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            LOCK.readLock().lock();
            statement = dbConnection.prepareStatement(sb.toString());
            statement.setString(1, key);
            statement.setMaxRows(1);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString(VALUE_COLUMN);
            }
        } catch (SQLException ex) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.readLock().unlock();
            close(statement);
            close(resultSet);
        }
        return null;
    }

    public void init(final File dbDirectory, final Map<String, String> initParams, final boolean readOnly)
            throws LocalDBException {
        this.dbDirectory = dbDirectory;

        this.dbConnection = openDB(dbDirectory);

        for (final DB db : DB.values()) {
            initTable(dbConnection, db);
        }

        this.readOnly = readOnly;
        this.status = LocalDB.Status.OPEN;
    }

    public LocalDB.PwmDBIterator<String> iterator(final DB db)
            throws LocalDBException {
        try {
            if (dbIterators.size() > ITERATOR_LIMIT) {
                throw new LocalDBException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"over " + ITERATOR_LIMIT + " iterators are outstanding, maximum limit exceeded"));
            }

            final LocalDB.PwmDBIterator iterator = new DbIterator(db);
            dbIterators.add(iterator);
            return iterator;
        } catch (Exception e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        }
    }

    public void putAll(final DB db, final Map<String, String> keyValueMap)
            throws LocalDBException {
        preCheck(true);
        PreparedStatement insertStatement = null, removeStatement = null;
        final String removeSqlString = "DELETE FROM " + db.toString() + " WHERE " + KEY_COLUMN + "=?";
        final String insertSqlString = "INSERT INTO " + db.toString() + "(" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES(?,?)";

        try {
            LOCK.writeLock().lock();
            // just in case anyone was unclear: sql does indeed suck.
            removeStatement = dbConnection.prepareStatement(removeSqlString);
            insertStatement = dbConnection.prepareStatement(insertSqlString);

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
            dbConnection.commit();
        } catch (SQLException ex) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
            close(removeStatement);
            close(insertStatement);
        }
    }


    public boolean put(final DB db, final String key, final String value)
            throws LocalDBException {
        preCheck(true);
        if (!contains(db, key)) {
            final String sqlText = "INSERT INTO " + db.toString() + "(" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES(?,?)";
            PreparedStatement statement = null;

            try {
                LOCK.writeLock().lock();
                statement = dbConnection.prepareStatement(sqlText);
                statement.setString(1, key);
                statement.setString(2, value);
                statement.executeUpdate();
                dbConnection.commit();
            } catch (SQLException ex) {
                throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
            } finally {
                LOCK.writeLock().unlock();
                close(statement);
            }
            return false;
        }

        final String sqlText = "UPDATE " + db.toString() + " SET " + VALUE_COLUMN + "=? WHERE " + KEY_COLUMN + "=?";
        PreparedStatement statement = null;

        try {
            LOCK.writeLock().lock();
            statement = dbConnection.prepareStatement(sqlText);
            statement.setString(1, value);
            statement.setString(2, key);
            statement.executeUpdate();
            dbConnection.commit();
        } catch (SQLException ex) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
            close(statement);

        }

        return true;
    }

    public boolean remove(final DB db, final String key)
            throws LocalDBException {
        preCheck(true);
        if (!contains(db, key)) {
            return false;
        }

        final StringBuilder sqlText = new StringBuilder();
        sqlText.append("DELETE FROM ").append(db.toString()).append(" WHERE " + KEY_COLUMN + "=?");

        PreparedStatement statement = null;
        try {
            LOCK.writeLock().lock();
            statement = dbConnection.prepareStatement(sqlText.toString());
            statement.setString(1, key);
            statement.executeUpdate();
            dbConnection.commit();
        } catch (SQLException ex) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
            close(statement);
        }

        return true;
    }

    public int size(final DB db)
            throws LocalDBException
    {
        preCheck(false);
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT COUNT(" + KEY_COLUMN + ") FROM ").append(db.toString());

        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            LOCK.readLock().lock();
            statement = dbConnection.prepareStatement(sb.toString());
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException ex) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.readLock().unlock();
            close(statement);
            close(resultSet);
        }

        return 0;
    }

    public void truncate(final DB db)
            throws LocalDBException
    {
        preCheck(true);
        final StringBuilder sqlText = new StringBuilder();
        sqlText.append("DROP TABLE ").append(db.toString());

        PreparedStatement statement = null;
        try {
            LOCK.writeLock().lock();
            statement = dbConnection.prepareStatement(sqlText.toString());
            statement.executeUpdate();
            dbConnection.commit();
            initTable(dbConnection, db);
        } catch (SQLException ex) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
            close(statement);
        }
    }

    public void removeAll(final DB db, final Collection<String> keys)
            throws LocalDBException
    {
        preCheck(true);
        final String sqlString = "DELETE FROM " + db.toString() + " WHERE " + KEY_COLUMN + "=?";
        PreparedStatement statement = null;
        try {
            LOCK.writeLock().lock();
            statement = dbConnection.prepareStatement(sqlString);

            for (final String loopKey : keys) {
                statement.clearParameters();
                statement.setString(1, loopKey);
                statement.addBatch();
            }
            statement.executeBatch();
            dbConnection.commit();
        } catch (SQLException ex) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
            close(statement);
        }
    }

// -------------------------- OTHER METHODS --------------------------

    private Connection openDB(final File databaseDirectory) throws LocalDBException {
        final String filePath = databaseDirectory.getAbsolutePath() + File.separator + "derby-db";
        final String baseConnectionURL = "jdbc:derby:" + filePath;
        final String connectionURL = baseConnectionURL + ";create=true";

        try {
            final Driver driver = (Driver)Class.forName(DERBY_CLASSPATH).newInstance();
            DriverManager.registerDriver(driver);
            final Connection connection = DriverManager.getConnection(connectionURL);
            connection.setAutoCommit(false);
            return connection;
        } catch (Throwable e) {
            final String errorMsg = "error opening DB: " + e.getMessage();
            LOGGER.error(errorMsg, e);
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,errorMsg));
        }
    }

// -------------------------- INNER CLASSES --------------------------

    private class DbIterator implements LocalDB.PwmDBIterator<String> {
        private String nextItem;
        private String currentItem;

        private ResultSet resultSet;
        private final DB db;

        private DbIterator(final DB db) throws LocalDBException {
            this.db = db;
            init();
            fetchNext();
        }

        private void init() throws LocalDBException {
            final StringBuilder sb = new StringBuilder();
            sb.append("SELECT * FROM ").append(db.toString());

            try {
                final PreparedStatement statement = dbConnection.prepareStatement(sb.toString());
                resultSet = statement.executeQuery();
            } catch (SQLException ex) {
                throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,ex.getMessage()));
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
            boolean hasNext = nextItem != null;
            if (!hasNext) {
                close();
            }
            return hasNext;
        }

        public void close() {
            nextItem = null;
            Derby_LocalDB.close(resultSet);
            dbIterators.remove(this);
        }

        public String next() {
            currentItem = nextItem;
            fetchNext();
            return currentItem;
        }

        public void remove() {
            if (currentItem != null) {
                try {
                    Derby_LocalDB.this.remove(db, currentItem);
                } catch (LocalDBException e) {
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

    private void preCheck(final boolean write) throws LocalDBException {
        if (status != LocalDB.Status.OPEN) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,"LocalDB is not open, cannot begin a new transaction"));
        }

        if (write && readOnly) {
            throw new IllegalStateException("cannot allow mutation operation; LocalDB is in read-only mode");
        }
    }
}
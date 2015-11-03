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
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class AbstractJDBC_LocalDB implements LocalDBProvider {
    private static final PwmLogger LOGGER = PwmLogger.forClass(AbstractJDBC_LocalDB.class, true);

    private static final String KEY_COLUMN = "id";
    private static final String VALUE_COLUMN = "value";
    private final static int ITERATOR_LIMIT = 100;

    private static final String WIDTH_KEY = String.valueOf(LocalDB.MAX_KEY_LENGTH);

    protected Driver driver;
    protected File dbDirectory;

    // cache of dbIterators
    private final Set<LocalDB.LocalDBIterator<String>> dbIterators = Collections.newSetFromMap(
            new ConcurrentHashMap<LocalDB.LocalDBIterator<String>, Boolean>());

    // sql db connection
    protected Connection dbConnection;

    // operation lock
    protected final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    protected LocalDB.Status status = LocalDB.Status.NEW;
    protected boolean readOnly = false;


// -------------------------- STATIC METHODS --------------------------

    private static void initTable(final Connection connection, final LocalDB.DB db) throws LocalDBException {
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

    private static void checkIfTableExists(final Connection connection, final LocalDB.DB db) throws LocalDBException {
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM  ").append(db.toString()).append(" WHERE " + KEY_COLUMN + " = '0'");
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery(sb.toString());
        } catch (SQLException e) {
            final String errorMsg = "table doesn't exist or some other error: " + e.getCause();
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,errorMsg));
        } finally {
            close(statement);
            close(resultSet);
        }
    }

    protected static void close(final Statement statement) {
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

    AbstractJDBC_LocalDB()
            throws Exception {
    }

// ------------------------ INTERFACE METHODS ------------------------


    public void close()
            throws LocalDBException {
        status = LocalDB.Status.CLOSED;
        try {
            LOCK.writeLock().lock();
            if (dbConnection != null) {
                try {
                    closeConnection(dbConnection);
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

    abstract void closeConnection(Connection connection)
            throws SQLException;

    public LocalDB.Status getStatus() {
        return status;
    }

    public boolean contains(final LocalDB.DB db, final String key)
            throws LocalDBException {
        preCheck(false);
        return get(db, key) != null;
    }

    public String get(final LocalDB.DB db, final String key)
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            close(statement);
            close(resultSet);
            LOCK.readLock().unlock();
        }
        return null;
    }

    public void init(final File dbDirectory, final Map<String, String> initParams, final boolean readOnly)
            throws LocalDBException {
        this.dbDirectory = dbDirectory;

        this.dbConnection = openConnection(dbDirectory, getDriverClasspath(), initParams);

        for (final LocalDB.DB db : LocalDB.DB.values()) {
            initTable(dbConnection, db);
        }

        this.readOnly = readOnly;
        this.status = LocalDB.Status.OPEN;
    }

    public LocalDB.LocalDBIterator<String> iterator(final LocalDB.DB db)
            throws LocalDBException {
        try {
            if (dbIterators.size() > ITERATOR_LIMIT) {
                throw new LocalDBException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"over " + ITERATOR_LIMIT + " iterators are outstanding, maximum limit exceeded"));
            }

            final LocalDB.LocalDBIterator iterator = new DbIterator(db);
            dbIterators.add(iterator);
            LOGGER.trace(this.getClass().getSimpleName() + " issued iterator for " + db.toString() + ", outstanding iterators: " + dbIterators.size());
            return iterator;
        } catch (Exception e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.getMessage()));
        }
    }

    public void putAll(final LocalDB.DB db, final Map<String, String> keyValueMap)
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            close(removeStatement);
            close(insertStatement);
            LOCK.writeLock().unlock();
        }
    }


    public boolean put(final LocalDB.DB db, final String key, final String value)
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
                throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,ex.getMessage()));
            } finally {
                close(statement);
                LOCK.writeLock().unlock();
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            close(statement);
            LOCK.writeLock().unlock();
        }

        return true;
    }

    public boolean remove(final LocalDB.DB db, final String key)
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            close(statement);
            LOCK.writeLock().unlock();
        }

        return true;
    }

    public int size(final LocalDB.DB db)
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            close(statement);
            close(resultSet);
            LOCK.readLock().unlock();
        }

        return 0;
    }

    public void truncate(final LocalDB.DB db)
            throws LocalDBException
    {
        preCheck(true);
        final StringBuilder sqlText = new StringBuilder();
        sqlText.append("DROP TABLE ").append(db.toString());

        PreparedStatement statement = null;
        try {
            LOCK.writeLock().lock();

            final Set<LocalDB.LocalDBIterator<String>> copiedIterators = new HashSet<>();
            copiedIterators.addAll(dbIterators);

            for (final LocalDB.LocalDBIterator dbIterator : copiedIterators) {
                dbIterator.close();
            }

            statement = dbConnection.prepareStatement(sqlText.toString());
            statement.executeUpdate();
            dbConnection.commit();
            initTable(dbConnection, db);
        } catch (SQLException ex) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            close(statement);
            LOCK.writeLock().unlock();
        }
    }

    public void removeAll(final LocalDB.DB db, final Collection<String> keys)
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,ex.getMessage()));
        } finally {
            close(statement);
            LOCK.writeLock().unlock();
        }
    }

// -------------------------- OTHER METHODS --------------------------

    abstract Connection openConnection(
            final File databaseDirectory,
            final String driverClasspath,
            final Map<String,String> initParams
    ) throws LocalDBException;


// -------------------------- INNER CLASSES --------------------------

    private class DbIterator implements LocalDB.LocalDBIterator<String> {
        private String nextItem;
        private String currentItem;

        private ResultSet resultSet;
        private final LocalDB.DB db;

        private DbIterator(final LocalDB.DB db) throws LocalDBException {
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
                throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,ex.getMessage()));
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
            AbstractJDBC_LocalDB.close(resultSet);
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
                    AbstractJDBC_LocalDB.this.remove(db, currentItem);
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,"LocalDB is not open, cannot begin a new transaction"));
        }

        if (write && readOnly) {
            throw new IllegalStateException("cannot allow mutation operation; LocalDB is in read-only mode");
        }
    }

    abstract String getDriverClasspath();
}

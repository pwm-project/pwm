/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Jason D. Rivard
 */
class DatabaseAccessorImpl implements DatabaseAccessor {

    private static final PwmLogger LOGGER = PwmLogger.forClass(DatabaseAccessorImpl.class, true);

    private final Connection connection;
    private final DatabaseService databaseService;
    private final DBConfiguration dbConfiguration;

    private final boolean traceLogEnabled;

    private final ReentrantLock LOCK = new ReentrantLock();

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
        DatabaseUtil.rollbackTransaction(connection);
        final DatabaseException databaseException = DatabaseUtil.convertSqlException(debugInfo, e);
        databaseService.setLastError(databaseException.getErrorInformation());
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
        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create("put", table, key, value);

        return execute(debugInfo, () -> {
            boolean exists = false;
            try {
                exists = containsImpl(table, key);
            } catch (SQLException e) {
                processSqlException(debugInfo, e);
            }

            if (exists) {
                final String sqlText = "UPDATE " + table.toString()
                        + " SET " + DatabaseService.VALUE_COLUMN + "=? WHERE "
                        + DatabaseService.KEY_COLUMN + "=?";
                executeUpdate(sqlText, debugInfo, value, key); // note the value/key are reversed for this statement
            } else {
                final String sqlText = "INSERT INTO " + table.toString()
                        + "(" + DatabaseService.KEY_COLUMN + ", "
                        + DatabaseService.VALUE_COLUMN + ") VALUES(?,?)";
                executeUpdate(sqlText, debugInfo, key, value);
            }

            return !exists;
        });
    }

    @Override
    public boolean putIfAbsent(
            final DatabaseTable table,
            final String key,
            final String value
    )
            throws DatabaseException
    {
        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create("putIfAbsent", table, key, value);

        return execute(debugInfo, () -> {
            boolean valueExists = false;
            try {
                valueExists = DatabaseAccessorImpl.this.containsImpl(table, key);
            } catch (final SQLException e) {
                DatabaseAccessorImpl.this.processSqlException(debugInfo, e);
            }

            if (!valueExists) {
                final String insertSql = "INSERT INTO " + table.name() + "(" + DatabaseService.KEY_COLUMN + ", " + DatabaseService.VALUE_COLUMN + ") VALUES(?,?)";
                DatabaseAccessorImpl.this.executeUpdate(insertSql, debugInfo, key, value);
            }

            return !valueExists;
        });
    }


    @Override
    public boolean contains(
            final DatabaseTable table,
            final String key
    )
            throws DatabaseException
    {
        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create("contains", table, key, null);

        return execute(debugInfo, () -> {
            boolean valueExists = false;
            try {
                valueExists = containsImpl(table, key);
            } catch (final SQLException e) {
                processSqlException(debugInfo, e);
            }
            return valueExists;
        });
    }

    @Override
    public String get(
            final DatabaseTable table,
            final String key
    )
            throws DatabaseException
    {
        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create("get", table, key, null);

        return execute(debugInfo, () -> {
            final String sqlStatement = "SELECT * FROM " + table.name() + " WHERE " + DatabaseService.KEY_COLUMN + " = ?";

            try (PreparedStatement statement = connection.prepareStatement(sqlStatement)) {
                statement.setString(1, key);
                statement.setMaxRows(1);

                try (ResultSet resultSet= statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString(DatabaseService.VALUE_COLUMN);
                    }
                }
            } catch (SQLException e) {
                processSqlException(debugInfo, e);
            }
            return null;
        });
    }

    @Override
    public ClosableIterator<String> iterator(final DatabaseTable table)
            throws DatabaseException
    {
        try {
            LOCK.lock();
            return new DBIterator(table);
        } finally {
            LOCK.unlock();
        }
    }

    @Override
    public void remove(
            final DatabaseTable table,
            final String key
    )
            throws DatabaseException
    {
        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create("remove", table, key, null);

        execute(debugInfo, () -> {


            final String sqlText = "DELETE FROM " + table.name() + " WHERE " + DatabaseService.KEY_COLUMN + "=?";
            executeUpdate(sqlText, debugInfo, key);

            return null;
        });
    }

    @Override
    public int size(final DatabaseTable table) throws
            DatabaseException
    {
        final DatabaseUtil.DebugInfo debugInfo = DatabaseUtil.DebugInfo.create("size", table, null, null);

        return execute(debugInfo, () -> {
            final String sqlStatement = "SELECT COUNT(" + DatabaseService.KEY_COLUMN + ") FROM " + table.name();

            try (PreparedStatement statement = connection.prepareStatement(sqlStatement)) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt(1);
                    }
                }
            } catch (SQLException e) {
                processSqlException(debugInfo, e);
            }

            return 0;
        });
    }

    boolean isValid() {
        if (connection == null) {
            return false;
        }

        try {
            if (connection.isClosed()) {
                return false;
            }

            final int connectionTimeout = dbConfiguration.getConnectionTimeout();

            if (!connection.isValid(connectionTimeout)) {
                return false;
            }

        } catch (SQLException e) {
            LOGGER.debug("error while checking connection validity: " + e.getMessage());
        }

        return true;
    }



    public class DBIterator implements ClosableIterator<String> {
        private final DatabaseTable table;
        private final ResultSet resultSet;
        private String nextValue;
        private boolean finished;

        DBIterator(final DatabaseTable table)
                throws DatabaseException
        {
            this.table = table;
            this.resultSet = init();
            getNextItem();
        }

        private ResultSet init() throws DatabaseException {
            final String sqlText = "SELECT " + DatabaseService.KEY_COLUMN + " FROM " + table.name();


            try (PreparedStatement statement = connection.prepareStatement(sqlText)) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    connection.commit();
                    return resultSet;
                }
            } catch (SQLException e) {
                processSqlException(null, e);
            }
            return null; // unreachable
        }

        public boolean hasNext() {
            return !finished;
        }

        public String next() {
            if (finished) {
                throw new IllegalStateException("iterator completed");
            }
            final String returnValue = nextValue;
            getNextItem();
            return returnValue;
        }

        public void remove() {
            throw new UnsupportedOperationException("remove not supported");
        }

        private void getNextItem() {
            try {
                if (resultSet.next()) {
                    nextValue = resultSet.getString(DatabaseService.KEY_COLUMN);
                } else {
                    close();
                }
            } catch (SQLException e) {
                finished = true;
                LOGGER.warn("unexpected error during result set iteration: " + e.getMessage());
            }
            databaseService.updateStats(DatabaseService.OperationType.READ);
        }

        public void close() {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    LOGGER.error("error closing inner resultset in iterator: " + e.getMessage());
                }
            }
            finished = true;
        }
    }

    private void traceBegin(final DatabaseUtil.DebugInfo debugInfo) {
        if (!traceLogEnabled) {
            return;
        }

        LOGGER.trace("begin operation: " + StringUtil.mapToString(JsonUtil.deserializeStringMap(JsonUtil.serialize(debugInfo))));
    }

    private void traceResult(
            final DatabaseUtil.DebugInfo debugInfo,
            final Object result
    ) {
        if (!traceLogEnabled) {
            return;
        }

        final Map<String,String> map = JsonUtil.deserializeStringMap(JsonUtil.serialize(debugInfo));
        map.put("duration", TimeDuration.fromCurrent(debugInfo.getStartTime()).asCompactString());
        if (result != null) {
            map.put("result", String.valueOf(result));
        }
        LOGGER.trace("operation result: " + StringUtil.mapToString(map));
    }

    private interface SqlFunction<T>  {
        T execute() throws DatabaseException;
    }

    private <T> T execute(final DatabaseUtil.DebugInfo debugInfo, final SqlFunction<T> sqlFunction) throws DatabaseException
    {
        traceBegin(debugInfo);

        try {
            LOCK.lock();

            try {
                final T result = sqlFunction.execute();
                traceResult(debugInfo, result);
                databaseService.updateStats(DatabaseService.OperationType.WRITE);
                return result;
            } finally {
                DatabaseUtil.commit(connection);
            }

        } finally {
            LOCK.unlock();
        }

    }

    Connection getConnection()
    {
        return connection;
    }

    void close() {
        try {
            LOCK.lock();
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.warn("error while closing connection: " + e.getMessage());
            }
        } finally {
            LOCK.unlock();
        }
    }

    private boolean containsImpl(final DatabaseTable table, final String key) throws SQLException
    {
        final String sqlStatement = "SELECT COUNT(" + DatabaseService.KEY_COLUMN + ") FROM " + table.name()
                + " WHERE " + DatabaseService.KEY_COLUMN + " = ?";

        try (PreparedStatement selectStatement = connection.prepareStatement(sqlStatement);) {
            selectStatement.setString(1, key);
            selectStatement.setMaxRows(1);

            try (ResultSet resultSet = selectStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        }

        return false;
    }

    private void executeUpdate(final String sqlStatement, final DatabaseUtil.DebugInfo debugInfo, final String... params) throws DatabaseException
    {
        try (PreparedStatement statement = connection.prepareStatement(sqlStatement) ){
            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            processSqlException(debugInfo, e);
        }
    }
}

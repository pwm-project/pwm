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

package password.pwm.util.db;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.value.FileValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.util.ClosableIterator;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import java.io.*;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * @author Jason D. Rivard
 */
public class DatabaseAccessorImpl implements PwmService, DatabaseAccessor {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(DatabaseAccessorImpl.class, true);
    private static final String KEY_COLUMN = "id";
    private static final String VALUE_COLUMN = "value";

    private static final int KEY_COLUMN_LENGTH = PwmConstants.DATABASE_ACCESSOR_KEY_LENGTH;

    private static final String KEY_TEST = "write-test-key";
    private static final String KEY_ENGINE_START_PREFIX = "engine-start-";

    private DBConfiguration dbConfiguration;
    private Driver driver;
    private String instanceID;
    private boolean traceLogging;
    private volatile Connection connection;
    private volatile PwmService.STATUS status = PwmService.STATUS.NEW;
    private ErrorInformation lastError;
    private PwmApplication pwmApplication;

// --------------------------- CONSTRUCTORS ---------------------------

    public DatabaseAccessorImpl()
    {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmService ---------------------

    public STATUS status() {
        return status;
    }

    public void init(final PwmApplication pwmApplication) throws PwmException {
        this.pwmApplication = pwmApplication;
        final Configuration config = pwmApplication.getConfig();
        init(config);
    }

    public void init(final Configuration config) throws PwmException {
        final Map<FileValue.FileInformation, FileValue.FileContent> fileValue = config.readSettingAsFile(
                PwmSetting.DATABASE_JDBC_DRIVER);
        final byte[] jdbcDriverBytes;
        if (fileValue != null && !fileValue.isEmpty()) {
            final FileValue.FileInformation fileInformation1 = fileValue.keySet().iterator().next();
            final FileValue.FileContent fileContent = fileValue.get(fileInformation1);
            jdbcDriverBytes = fileContent.getContents();
        } else {
            jdbcDriverBytes = null;
        }

        this.dbConfiguration = new DBConfiguration(
                config.readSettingAsString(PwmSetting.DATABASE_CLASS),
                config.readSettingAsString(PwmSetting.DATABASE_URL),
                config.readSettingAsString(PwmSetting.DATABASE_USERNAME),
                config.readSettingAsPassword(PwmSetting.DATABASE_PASSWORD),
                config.readSettingAsString(PwmSetting.DATABASE_COLUMN_TYPE_KEY),
                config.readSettingAsString(PwmSetting.DATABASE_COLUMN_TYPE_VALUE),
                jdbcDriverBytes
        );

        this.instanceID = pwmApplication == null ? null : pwmApplication.getInstanceID();
        this.traceLogging = config.readSettingAsBoolean(PwmSetting.DATABASE_DEBUG_TRACE);

        if (this.dbConfiguration.isEmpty()) {
            status = PwmService.STATUS.CLOSED;
            LOGGER.debug("skipping database connection open, no connection parameters configured");
        }
    }

    public void close()
    {
        status = PwmService.STATUS.CLOSED;
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                LOGGER.debug("error while closing DB: " + e.getMessage());
            }
        }

        try {
            driver = null;
        } catch (Exception e) {
            LOGGER.debug("error while de-registering driver: " + e.getMessage());
        }

        connection = null;
    }

    public List<HealthRecord> healthCheck() {
        if (status == PwmService.STATUS.CLOSED) {
            return Collections.emptyList();
        }

        final List<HealthRecord> returnRecords = new ArrayList<>();

        try {
            preOperationCheck();
        } catch (DatabaseException e) {
            lastError = e.getErrorInformation();
            returnRecords.add(new HealthRecord(HealthStatus.WARN, HealthTopic.Database, "Database server is not available: " + e.getErrorInformation().toDebugStr()));
            return returnRecords;
        }

        try {
            final Map<String,String> tempMap = new HashMap<>();
            tempMap.put("instance",instanceID);
            tempMap.put("date",(new java.util.Date()).toString());
            this.put(DatabaseTable.PWM_META, DatabaseAccessorImpl.KEY_TEST, JsonUtil.serializeMap(tempMap));
        } catch (PwmException e) {
            returnRecords.add(new HealthRecord(HealthStatus.WARN, HealthTopic.Database, "Error writing to database: " + e.getErrorInformation().toDebugStr()));
            return returnRecords;
        }

        if (lastError != null) {
            final TimeDuration errorAge = TimeDuration.fromCurrent(lastError.getDate().getTime());

            if (errorAge.isShorterThan(TimeDuration.HOUR)) {
                returnRecords.add(new HealthRecord(HealthStatus.CAUTION, HealthTopic.Database, "Database server was recently unavailable (" + errorAge.asLongString(PwmConstants.DEFAULT_LOCALE) + " ago at " + lastError.getDate().toString()+ "): " + lastError.toDebugStr()));
            }
        }

        if (returnRecords.isEmpty()) {
            returnRecords.add(new HealthRecord(HealthStatus.GOOD, HealthTopic.Database, "Database connection to " + this.dbConfiguration.getConnectionString() + " okay"));
        }

        return returnRecords;
    }

// -------------------------- OTHER METHODS --------------------------

    private synchronized void init()
            throws DatabaseException
    {
        status = PwmService.STATUS.OPENING;
        LOGGER.debug("opening connection to database " + this.dbConfiguration.getConnectionString());

        connection = openDB(dbConfiguration);
        for (final DatabaseTable table : DatabaseTable.values()) {
            initTable(connection, table, dbConfiguration);
        }

        status = PwmService.STATUS.OPEN;

        try {
            put(DatabaseTable.PWM_META, KEY_ENGINE_START_PREFIX + instanceID, PwmConstants.DEFAULT_DATETIME_FORMAT.format(new java.util.Date()));
        } catch (DatabaseException e) {
            final String errorMsg = "error writing engine start time value: " + e.getMessage();
            throw new DatabaseException(new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg));
        }
    }

    private Connection openDB(final DBConfiguration dbConfiguration) throws DatabaseException {
        final String connectionURL = dbConfiguration.getConnectionString();
        final String jdbcClassName = dbConfiguration.getDriverClassname();

        try {
            final byte[] jdbcDriverBytes = dbConfiguration.getJdbcDriver();
            if (jdbcDriverBytes != null) {
                LOGGER.debug("loading JDBC database driver stored in configuration");
                final JdbcDriverClassLoader jdbcDriverClassLoader = new JdbcDriverClassLoader(jdbcDriverBytes);
                driver = (Driver) Class.forName(jdbcClassName, true, jdbcDriverClassLoader).newInstance();
                LOGGER.debug("successfully loaded JDBC database driver '" + jdbcClassName + "' stored in configuration");
            }
        } catch (Exception e) {
            final String errorMsg = "error registering JDBC database driver stored in configuration: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg);
            throw new DatabaseException(errorInformation);
        }

        if (driver == null) {
            try {
                LOGGER.debug("loading JDBC database driver from classpath: " + jdbcClassName);
                driver = (Driver) Class.forName(jdbcClassName).newInstance(); //load from normal classpath
                LOGGER.debug("successfully loaded JDBC database driver from classpath: " + jdbcClassName);
            } catch (Throwable e) {
                final String errorMsg = e.getClass().getName() + " error loading JDBC database driver from classpath: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg);
                throw new DatabaseException(errorInformation);
            }
        }

        try {
            LOGGER.debug("opening connection to database " + connectionURL);
            final Properties connectionProperties = new Properties();
            if (dbConfiguration.getUsername() != null && !dbConfiguration.getUsername().isEmpty()) {
                connectionProperties.setProperty("user", dbConfiguration.getUsername());
            }
            if (dbConfiguration.getPassword() != null) {
                connectionProperties.setProperty("password", dbConfiguration.getPassword().getStringValue());
            }
            final Connection connection = driver.connect(connectionURL, connectionProperties);
            LOGGER.debug("successfully opened connection to database " + connectionURL);
            connection.setAutoCommit(true);
            return connection;
        } catch (PwmUnrecoverableException | SQLException e) {
            final String errorMsg = "error connecting to database: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg);
            throw new DatabaseException(errorInformation);
        }
    }

    private static void initTable(final Connection connection, final DatabaseTable table, final DBConfiguration dbConfiguration) throws DatabaseException {
        try {
            checkIfTableExists(connection, table);
            LOGGER.trace("table " + table + " appears to exist");
        } catch (SQLException e) { // assume error was due to table missing;
            {
                final StringBuilder sqlString = new StringBuilder();
                sqlString.append("CREATE table ").append(table.toString()).append(" (").append("\n");
                sqlString.append("  " + KEY_COLUMN + " ").append(dbConfiguration.getColumnTypeKey()).append("(").append(
                        KEY_COLUMN_LENGTH).append(") NOT NULL PRIMARY KEY,").append("\n");
                sqlString.append("  " + VALUE_COLUMN + " ").append(dbConfiguration.getColumnTypeValue()).append(" ");
                sqlString.append("\n");
                sqlString.append(")").append("\n");

                LOGGER.trace("attempting to execute the following sql statement:\n " + sqlString.toString());

                Statement statement = null;
                try {
                    statement = connection.createStatement();
                    statement.execute(sqlString.toString());
                    LOGGER.debug("created table " + table.toString());
                } catch (SQLException ex) {
                    LOGGER.error("error creating new table " + table.toString() + ": " + ex.getMessage());
                } finally {
                    close(statement);
                }
            }

            {
                final String indexName = table.toString() + "_IDX";
                final StringBuilder sqlString = new StringBuilder();
                sqlString.append("CREATE index ").append(indexName);
                sqlString.append(" ON ").append(table.toString());
                sqlString.append(" (").append(KEY_COLUMN).append(")");
                Statement statement = null;

                LOGGER.trace("attempting to execute the following sql statement:\n " + sqlString.toString());

                try {
                    statement = connection.createStatement();
                    statement.execute(sqlString.toString());
                    LOGGER.debug("created index " + indexName);
                } catch (SQLException ex) {
                    LOGGER.error("error creating new index " + indexName + ": " + ex.getMessage());
                } finally {
                    close(statement);
                }
            }
        }
    }

    private static void checkIfTableExists(final Connection connection, final DatabaseTable table) throws SQLException {
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM  ").append(table.toString()).append(" WHERE " + KEY_COLUMN + " = '0'");
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery(sb.toString());
        } finally {
            close(statement);
            close(resultSet);
        }
    }

    @Override
    public boolean put(
            final DatabaseTable table,
            final String key,
            final String value
    )
            throws DatabaseException {

        preOperationCheck();
        if (traceLogging) {
            LOGGER.trace("attempting put operation for table=" + table + ", key=" + key);
        }
        if (!contains(table, key)) {
            final String sqlText = "INSERT INTO " + table.toString() + "(" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES(?,?)";
            PreparedStatement statement = null;

            try {
                statement = connection.prepareStatement(sqlText);
                statement.setString(1, key);
                statement.setString(2, value);
                statement.executeUpdate();
            } catch (SQLException e) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,"put operation failed: " + e.getMessage());
                lastError = errorInformation;
                throw new DatabaseException(errorInformation);
            } finally {
                close(statement);
            }
            return false;
        }

        final String sqlText = "UPDATE " + table.toString() + " SET " + VALUE_COLUMN + "=? WHERE " + KEY_COLUMN + "=?";
        PreparedStatement statement = null;

        try {
            statement = connection.prepareStatement(sqlText);
            statement.setString(1, value);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,"put operation failed: " + e.getMessage());
            lastError = errorInformation;
            throw new DatabaseException(errorInformation);
        } finally {
            close(statement);
        }

        if (traceLogging) {
            final Map<String,Object> debugOutput = new LinkedHashMap<>();
            debugOutput.put("table",table);
            debugOutput.put("key",key);
            debugOutput.put("value",value);
            LOGGER.trace("put operation result: " + JsonUtil.serializeMap(debugOutput, JsonUtil.Flag.PrettyPrint));
        }

        updateStats(false,true);
        return true;
    }

    private synchronized void preOperationCheck() throws DatabaseException {
        if (status == PwmService.STATUS.CLOSED) {
            throw new DatabaseException(new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,"database connection is not open"));
        }

        if (status == PwmService.STATUS.NEW) {
            init();
        }

        if (!isValid(connection)) {
            init();
        }
    }

    private boolean isValid(final Connection connection) {
        if (connection == null) {
            return false;
        }

        if (status != PwmService.STATUS.OPEN) {
            return false;
        }

        try {
            final Method getFreeSpaceMethod = File.class.getMethod("isValid");
            final Object rawResult = getFreeSpaceMethod.invoke(connection,10);
            return (Boolean) rawResult;
        } catch (NoSuchMethodException e) {
            /* no error, pre java 1.6 doesn't have this method */
        } catch (Exception e) {
            LOGGER.debug("error checking for isValid for " + connection.toString() + ",: " + e.getMessage());
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ").append(DatabaseTable.PWM_META.toString()).append(" WHERE " + KEY_COLUMN + " = ?");
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            statement = connection.prepareStatement(sb.toString());
            statement.setString(1, KEY_ENGINE_START_PREFIX + instanceID);
            statement.setMaxRows(1);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                resultSet.getString(VALUE_COLUMN);
            }
        } catch (SQLException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,"isValid operation failed: " + e.getMessage());
            lastError = errorInformation;
            LOGGER.error(errorInformation.toDebugStr());
            return false;
        } finally {
            close(statement);
            close(resultSet);
        }
        return true;
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

    @Override
    public boolean contains(
            final DatabaseTable table,
            final String key
    )
            throws DatabaseException
    {
        final boolean result = get(table, key) != null;
        if (traceLogging) {
            final Map<String,Object> debugOutput = new LinkedHashMap<>();
            debugOutput.put("table",table);
            debugOutput.put("key",key);
            debugOutput.put("result",result);
            LOGGER.trace("contains operation result: " + JsonUtil.serializeMap(debugOutput, JsonUtil.Flag.PrettyPrint));
        }
        updateStats(true,false);
        return result;
    }

    @Override
    public String get(
            final DatabaseTable table,
            final String key
    )
            throws DatabaseException
    {
        if (traceLogging) {
            LOGGER.trace("attempting get operation for table=" + table + ", key=" + key);
        }
        preOperationCheck();
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ").append(table.toString()).append(" WHERE " + KEY_COLUMN + " = ?");

        PreparedStatement statement = null;
        ResultSet resultSet = null;
        String returnValue = null;
        try {
            statement = connection.prepareStatement(sb.toString());
            statement.setString(1, key);
            statement.setMaxRows(1);
            resultSet = statement.executeQuery();

            if (resultSet.next()) {
                returnValue = resultSet.getString(VALUE_COLUMN);
            }
        } catch (SQLException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,"get operation failed: " + e.getMessage());
            lastError = errorInformation;
            throw new DatabaseException(errorInformation);
        } finally {
            close(statement);
            close(resultSet);
        }

        if (traceLogging) {
            final LinkedHashMap<String,Object> debugOutput = new LinkedHashMap<>();
            debugOutput.put("table",table);
            debugOutput.put("key",key);
            debugOutput.put("result",returnValue);
            LOGGER.trace("get operation result: " + JsonUtil.serializeMap(debugOutput, JsonUtil.Flag.PrettyPrint));
        }

        updateStats(true,false);
        return returnValue;
    }

    @Override
    public ClosableIterator<String> iterator(final DatabaseTable table)
            throws DatabaseException
    {
        preOperationCheck();
        return new DBIterator(table);
    }

    @Override
    public boolean remove(
            final DatabaseTable table,
            final String key
    )
            throws DatabaseException
    {
        if (traceLogging) {
            LOGGER.trace("attempting remove operation for table=" + table + ", key=" + key);
        }

        boolean result = contains(table, key);
        if (result) {
            final StringBuilder sqlText = new StringBuilder();
            sqlText.append("DELETE FROM ").append(table.toString()).append(" WHERE " + KEY_COLUMN + "=?");

            PreparedStatement statement = null;
            try {
                statement = connection.prepareStatement(sqlText.toString());
                statement.setString(1, key);
                statement.executeUpdate();
                LOGGER.trace("remove operation succeeded for table=" + table + ", key=" + key);
            } catch (SQLException e) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,"remove operation failed: " + e.getMessage());
                lastError = errorInformation;
                throw new DatabaseException(errorInformation);
            } finally {
                close(statement);
            }
        }

        if (traceLogging) {
            final Map<String,Object> debugOutput = new LinkedHashMap<>();
            debugOutput.put("table",table);
            debugOutput.put("key",key);
            debugOutput.put("result",result);
            LOGGER.trace("remove operation result: " + JsonUtil.serializeMap(debugOutput, JsonUtil.Flag.PrettyPrint));
        }

        updateStats(true, false);
        return result;
    }

    @Override
    public int size(final DatabaseTable table) throws
            DatabaseException {
        preOperationCheck();

        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT COUNT(" + KEY_COLUMN + ") FROM ").append(table.toString());

        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            statement = connection.prepareStatement(sb.toString());
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,"size operation failed: " + e.getMessage());
            lastError = errorInformation;
            throw new DatabaseException(errorInformation);
        } finally {
            close(statement);
            close(resultSet);
        }

        updateStats(true,false);
        return 0;
    }

// -------------------------- ENUMERATIONS --------------------------

    // -------------------------- INNER CLASSES --------------------------

    public class DBIterator implements ClosableIterator<String> {
        private final DatabaseTable table;
        private final ResultSet resultSet;
        private java.lang.String nextValue;
        private boolean finished;

        public DBIterator(final DatabaseTable table)
                throws DatabaseException
        {
            this.table = table;
            this.resultSet = init();
            getNextItem();
        }

        private ResultSet init() throws DatabaseException {
            final StringBuilder sb = new StringBuilder();
            sb.append("SELECT " + KEY_COLUMN + " FROM ").append(table.toString());

            try {
                final PreparedStatement statement = connection.prepareStatement(sb.toString());
                return statement.executeQuery();
            } catch (SQLException e) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,"get iterator failed: " + e.getMessage());
                lastError = errorInformation;
                throw new DatabaseException(errorInformation);
            }
        }

        public boolean hasNext() {
            return !finished;
        }

        public java.lang.String next() {
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
                    nextValue = resultSet.getString(KEY_COLUMN);
                } else {
                    close();
                }
            } catch (SQLException e) {
                finished = true;
                LOGGER.warn("unexpected error during result set iteration: " + e.getMessage());
            }
            updateStats(true,false);
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

    public static class DBConfiguration implements Serializable {
        private final String driverClassname;
        private final String connectionString;
        private final String username;
        private final PasswordData password;
        private final String columnTypeKey;
        private final String columnTypeValue;
        private final byte[] jdbcDriver;

        public DBConfiguration(
                final String driverClassname,
                final String connectionString,
                final String username,
                final PasswordData password,
                final String columnTypeKey,
                final String columnTypeValue,
                final byte[] jdbcDriver
        ) {
            this.driverClassname = driverClassname;
            this.connectionString = connectionString;
            this.username = username;
            this.password = password;
            this.columnTypeKey = columnTypeKey;
            this.columnTypeValue = columnTypeValue;
            this.jdbcDriver = jdbcDriver;
        }

        public String getDriverClassname() {
            return driverClassname;
        }

        public String getConnectionString() {
            return connectionString;
        }

        public String getUsername() {
            return username;
        }

        public PasswordData getPassword() {
            return password;
        }

        public String getColumnTypeKey() {
            return columnTypeKey;
        }

        public String getColumnTypeValue() {
            return columnTypeValue;
        }


        public byte[] getJdbcDriver()
        {
            return jdbcDriver;
        }

        public boolean isEmpty() {
            if (driverClassname == null || driverClassname.length() < 1) {
                if (connectionString == null || connectionString.length() < 1) {
                    if (username == null || username.length() < 1) {
                        if (password == null) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    public ServiceInfo serviceInfo()
    {
        if (status() == STATUS.OPEN) {
            return new ServiceInfo(Collections.singletonList(DataStorageMethod.DB));
        } else {
            return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
        }
    }

    private void updateStats(boolean readOperation, boolean writeOperation) {
        if (pwmApplication != null && pwmApplication.getApplicationMode() == PwmApplication.MODE.RUNNING) {
            final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
            if (statisticsManager != null && statisticsManager.status() == STATUS.OPEN) {
                if (readOperation) {
                    statisticsManager.updateEps(Statistic.EpsType.DB_READS,1);
                }
                if (writeOperation) {
                    statisticsManager.updateEps(Statistic.EpsType.DB_WRITES,1);
                }
            }
        }
    }

    public static class JdbcDriverClassLoader extends ClassLoader {
        private static final String CLASS_FILE_SEPERATOR = "/";
        private final byte[] jdbcDriverJar;

        public JdbcDriverClassLoader(byte[] jdbcDriverJar) {
            this.jdbcDriverJar = jdbcDriverJar;
        }

        public Class findClass(String name)
                throws ClassNotFoundException
        {
            try {
                byte[] b = loadClassData(name);
                if (b == null) {
                    throw new ClassNotFoundException("unable to discover file from in-memory jar classloader: " + name);
                }
                return defineClass(name, b, 0, b.length);
            } catch (IOException e) {
                e.printStackTrace();
                throw new ClassNotFoundException("IOException reading from in-memory jar classloader: " + e.getMessage());
            }
        }

        private byte[] loadClassData(String name)
                throws IOException
        {
            final String literalFileName = name.replace(".", CLASS_FILE_SEPERATOR) + ".class";
            final JarInputStream jarInputStream = new JarInputStream(new ByteArrayInputStream(jdbcDriverJar));
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                if (!jarEntry.isDirectory()) {
                    if (literalFileName.equals(jarEntry.getName())) {
                        final int fileLength = (int) jarEntry.getSize();
                        final byte[] buffer = new byte[1024];
                        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int length;
                        while ((length = jarInputStream.read(buffer)) > 0) {
                            baos.write(buffer, 0, length);
                        }
                        final byte[] outputFile = baos.toByteArray();
                        LOGGER.trace("loaded class file name=" + name + ", found=" + jarEntry.getName()
                                + ", fileLength=" + fileLength + " returnLength=" + outputFile.length);
                        return outputFile;
                    }
                }
            }
            return null;
        }
    }
}
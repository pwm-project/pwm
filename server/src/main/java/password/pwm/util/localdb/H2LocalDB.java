/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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


public class H2LocalDB
{
}


// No longer used, commented in case it may be resurrected some day.
/*

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.StringUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


public class H2LocalDB extends AbstractJDBCLocalDB {

    private static final PwmLogger LOGGER = PwmLogger.forClass(H2LocalDB.class, true);

    private static final String H2_CLASSPATH = "org.h2.Driver";
    private static final Map<String,String> DEFAULT_INIT_PARAMS;
    static {
        final Map<String,String> defaultInitParams = new HashMap<>();
        defaultInitParams.put("DB_CLOSE_ON_EXIT","FALSE");
        defaultInitParams.put("COMPRESS","TRUE");
        //defaultInitParams.put("TRACE_LEVEL_FILE","2");
        DEFAULT_INIT_PARAMS = Collections.unmodifiableMap(defaultInitParams);
    }

    private Driver driver;

    H2LocalDB()
            throws Exception
    {
        super();
    }

    @Override
    String getDriverClasspath()
    {
        return H2_CLASSPATH;
    }

    @Override
    void
    closeConnection(final Connection connection)
            throws SQLException
    {

        if (aggressiveCompact) {
            CallableStatement statement = null;
            try {
                LOCK.writeLock().lock();
                final java.util.Date start = new java.util.Date();
                LOGGER.trace("beginning shutdown compact");
                statement = dbConnection.prepareCall("SHUTDOWN COMPACT");
                statement.execute();
                LOGGER.trace("completed shutdown compact in " + TimeDuration.fromCurrent(start).asCompactString());
            } catch (SQLException ex) {
                LOGGER.error("error during shutdown compact: " + ex.getMessage());
            } finally {
                close(statement);
                LOCK.writeLock().unlock();
            }
        }

        try {
            connection.close();
            if (driver != null) {
                DriverManager.deregisterDriver(driver);
                driver = null;
            }
        } catch (Exception e) {
            LOGGER.error("error during H2 shutdown: " + e.getMessage());
        }
    }

    @Override
    Connection openConnection(
            final File databaseDirectory,
            final String driverClasspath,
            final Map<String,String> initParams
    ) throws LocalDBException {
        final String filePath = databaseDirectory.getAbsolutePath() + File.separator + "localdb-h2";
        final String connectionString = "jdbc:h2:split:" + filePath + ";" + makeInitStringParams(initParams);

        try {
            driver = (Driver)Class.forName(H2_CLASSPATH).newInstance();
            final Properties connectionProps = new Properties();
            final Connection connection = driver.connect(connectionString, connectionProps);
            connection.setAutoCommit(true);
            return connection;
        } catch (Throwable e) {
            final String errorMsg = "error opening DB: " + e.getMessage();
            LOGGER.error(errorMsg, e);
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,errorMsg));
        }
    }

    private static String makeInitStringParams(final Map<String,String> initParams) {
        final Map<String,String> params = new HashMap<>();
        params.putAll(DEFAULT_INIT_PARAMS);
        params.putAll(initParams);
        return StringUtil.mapToString(params,"=",";");
    }
}
*/

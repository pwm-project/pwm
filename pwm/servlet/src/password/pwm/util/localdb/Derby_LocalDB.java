/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Apache Derby Wrapper for {@link LocalDB} interface.   Uses a single table per DB, with
 * two columns each.  This class would be easily adaptable for a generic JDBC implementation.
 *
 * @author Jason D. Rivard
 */
public class Derby_LocalDB extends AbstractJDBC_LocalDB {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(Derby_LocalDB.class, true);

    private static final String DERBY_CLASSPATH = "org.apache.derby.jdbc.EmbeddedDriver";

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
        connection.close();
        DriverManager.getConnection("jdbc:derby:;shutdown=true");
    }

    @Override
    Connection openConnection(
            final File databaseDirectory,
            final String driverClasspath
    ) throws LocalDBException {
        final String filePath = databaseDirectory.getAbsolutePath() + File.separator + "derby-db";
        final String baseConnectionURL = "jdbc:derby:" + filePath;
        final String connectionURL = baseConnectionURL + ";create=true";

        try {
            final Driver driver = (Driver)Class.forName(driverClasspath).newInstance();
            DriverManager.registerDriver(driver);
            final Connection connection = DriverManager.getConnection(connectionURL);
            connection.setAutoCommit(false);
            return connection;
        } catch (Throwable e) {
            final String errorMsg = "error opening DB: " + e.getMessage();
            LOGGER.error(errorMsg, e);
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,errorMsg));
        }
    }
}


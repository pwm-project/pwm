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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.JclObjectFactory;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JDBCDriverLoader {

    private static final PwmLogger LOGGER = PwmLogger.forClass(JDBCDriverLoader.class, true);

    static DriverWrapper loadDriver(
            final PwmApplication pwmApplication,
            final DBConfiguration dbConfiguration
    )
            throws DatabaseException
    {
        final List<ClassLoaderStrategy> strategies = dbConfiguration.getClassLoaderStrategies();
        LOGGER.trace("attempting to load jdbc driver using strategies: " + JsonUtil.serializeCollection(strategies));
        final List<String> errorMsgs = new ArrayList<>();
        for (final ClassLoaderStrategy strategy : strategies) {
            final DriverLoader loader = strategy.getJdbcDriverDriverLoader();
            try {
                final Driver driver = loader.loadDriver(pwmApplication, dbConfiguration);
                if (driver != null) {
                    return new DriverWrapper(driver, loader);
                }
            } catch (DatabaseException e) {
                errorMsgs.add(strategy + " error: " + e.getMessage());
            }
        }
        final String errorMsg = " unable to load database driver: " + JsonUtil.serializeCollection(errorMsgs);
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg);
        LOGGER.error(errorMsg);
        throw new DatabaseException(errorInformation);
    }

    public enum ClassLoaderStrategy {
        XeusLoader(new XeusJarClassDriverLoader()),
        AppPathFileLoader(new AppPathDriverLoader()),
        TempFile(new TempFileDriverLoader()),
        Classpath(new JavaClasspathLoader()),

        ;

        private final DriverLoader jdbcDriverDriverLoader;

        ClassLoaderStrategy(final DriverLoader jdbcDriverDriverLoader) {
            this.jdbcDriverDriverLoader = jdbcDriverDriverLoader;
        }

        private DriverLoader getJdbcDriverDriverLoader() {
            return jdbcDriverDriverLoader;
        }
    }

    interface DriverLoader {
        Driver loadDriver(PwmApplication pwmApplication, DBConfiguration dbConfiguration) throws DatabaseException;

        void unloadDriver();
    }

    private static class JavaClasspathLoader implements DriverLoader {

        private static final PwmLogger LOGGER = PwmLogger.forClass(XeusJarClassDriverLoader.class, true);

        @Override
        public Driver loadDriver(final PwmApplication pwmApplication, final DBConfiguration dbConfiguration)
                throws DatabaseException
        {
            final String jdbcClassName = dbConfiguration.getDriverClassname();

            try {
                LOGGER.debug("loading JDBC database driver from classpath: " + jdbcClassName);
                final Driver driver = (Driver) Class.forName(jdbcClassName).newInstance();

                LOGGER.debug("successfully loaded JDBC database driver from classpath: " + jdbcClassName);
                return driver;
            } catch (Throwable e) {
                final String errorMsg = e.getClass().getName() + " error loading JDBC database driver from classpath: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg);
                throw new DatabaseException(errorInformation);
            }
        }

        @Override
        public void unloadDriver() {
        }
    }


    private static class XeusJarClassDriverLoader implements DriverLoader {

        private static final PwmLogger LOGGER = PwmLogger.forClass(XeusJarClassDriverLoader.class, true);

        @Override
        public Driver loadDriver(final PwmApplication pwmApplication, final DBConfiguration dbConfiguration)
                throws DatabaseException
        {
            final String jdbcClassName = dbConfiguration.getDriverClassname();
            final byte[] jdbcDriverBytes = dbConfiguration.getJdbcDriver();
            try {
                LOGGER.debug("loading JDBC database driver stored in configuration");
                final JarClassLoader jarClassLoader = new JarClassLoader();
                jarClassLoader.add(new ByteArrayInputStream(jdbcDriverBytes));
                final JclObjectFactory jclObjectFactory = JclObjectFactory.getInstance(true);

                //Create object of loaded class
                final Driver driver = (Driver)jclObjectFactory.create(jarClassLoader, jdbcClassName);

                LOGGER.debug("successfully loaded JDBC database driver '" + jdbcClassName + "' from application configuration");

                return driver;
            } catch (Throwable e) {
                final String errorMsg = "error registering JDBC database driver stored in configuration: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg);
                throw new DatabaseException(errorInformation);
            }
        }

        @Override
        public void unloadDriver() {

        }
    }

    private static class TempFileDriverLoader implements DriverLoader {

        private static final PwmLogger LOGGER = PwmLogger.forClass(TempFileDriverLoader.class, true);

        private File tempFile;

        @Override
        @SuppressFBWarnings("DP_CREATE_CLASSLOADER_INSIDE_DO_PRIVILEGED") // not clear if this is worth it to fix
        public Driver loadDriver(final PwmApplication pwmApplication, final DBConfiguration dbConfiguration)
                throws DatabaseException
        {
            final String jdbcClassName = dbConfiguration.getDriverClassname();
            final byte[] jdbcDriverBytes = dbConfiguration.getJdbcDriver();

            if (jdbcDriverBytes == null || jdbcDriverBytes.length < 1) {
                final String errorMsg = "jdbc driver file not configured, skipping";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg);
                throw new DatabaseException(errorInformation);
            }
            try {
                LOGGER.debug("loading JDBC database driver stored in configuration");

                if (tempFile == null) {
                    final String prefixName = PwmConstants.PWM_APP_NAME.toLowerCase() + "_jdbcJar_";
                    tempFile = File.createTempFile(prefixName, "jar");
                    LOGGER.trace("created temp file " + tempFile.getAbsolutePath());
                }

                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(jdbcDriverBytes);
                    fos.close();
                }

                final URLClassLoader urlClassLoader = new URLClassLoader(
                        new URL[] {tempFile.toURI().toURL()},
                        this.getClass().getClassLoader()
                );

                //Create object of loaded class
                final Class jdbcDriverClass = urlClassLoader.loadClass(jdbcClassName);
                final Driver driver = (Driver)jdbcDriverClass.newInstance();

                LOGGER.debug("successfully loaded JDBC database driver '" + jdbcClassName + "' from application configuration");

                return driver;
            } catch (Throwable e) {
                final String errorMsg = "error registering JDBC database driver stored in configuration: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg);
                throw new DatabaseException(errorInformation);
            }
        }

        @Override
        public void unloadDriver() {
            if (tempFile != null) {
                if (tempFile.delete()) {
                    LOGGER.trace("removed temporary file " + tempFile.getAbsolutePath());
                }
            }
            tempFile = null;
        }
    }

    private static class AppPathDriverLoader implements DriverLoader {

        private final PwmLogger LOGGER = PwmLogger.forClass(AppPathDriverLoader.class, true);

        // static ccache of classloader to prevent classloader memory leak
        private static Map<String,ClassLoader> driverCache = new ConcurrentHashMap<>();

        @SuppressFBWarnings("DP_CREATE_CLASSLOADER_INSIDE_DO_PRIVILEGED") // not clear if this is worth it to fix
        @Override
        public Driver loadDriver(final PwmApplication pwmApplication, final DBConfiguration dbConfiguration)
                throws DatabaseException
        {
            final String jdbcClassName = dbConfiguration.getDriverClassname();
            final byte[] jdbcDriverBytes = dbConfiguration.getJdbcDriver();

            if (jdbcDriverBytes == null || jdbcDriverBytes.length < 1) {
                final String errorMsg = "jdbc driver file not configured, skipping";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg);
                throw new DatabaseException(errorInformation);
            }

            final String jdbcDriverHash;
            try {
                jdbcDriverHash = pwmApplication.getSecureService().hash(jdbcDriverBytes);
            } catch (PwmUnrecoverableException e) {
                throw new DatabaseException(e.getErrorInformation());
            }

            final ClassLoader urlClassLoader;
            if (driverCache.containsKey(jdbcDriverHash)) {
                urlClassLoader = driverCache.get(jdbcDriverHash);
                LOGGER.trace("loaded classloader from static cache");
            } else {
                try {
                    LOGGER.debug("loading JDBC database driver stored in configuration");
                    final File tempFile = createOrGetTempJarFile(pwmApplication, jdbcDriverBytes);
                    urlClassLoader = new URLClassLoader(
                            new URL[]{tempFile.toURI().toURL()},
                            this.getClass().getClassLoader()
                    );
                    driverCache.put(jdbcDriverHash, urlClassLoader);
                } catch (Throwable e) {
                    final String errorMsg = "error establishing classloader for driver: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE, errorMsg);
                    throw new DatabaseException(errorInformation);
                }
            }

            try {
                //Create object of loaded class
                final Class jdbcDriverClass = urlClassLoader.loadClass(jdbcClassName);
                final Driver driver = (Driver)jdbcDriverClass.newInstance();
                LOGGER.debug("successfully loaded JDBC database driver '" + jdbcClassName + "' from application configuration");
                return driver;
            } catch (Throwable e) {
                final String errorMsg = "error registering JDBC database driver stored in configuration: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,errorMsg);
                throw new DatabaseException(errorInformation);
            }
        }

        @Override
        public void unloadDriver() {
        }

        File createOrGetTempJarFile(final PwmApplication pwmApplication, final byte[] jarBytes) throws PwmUnrecoverableException, IOException {
            final File file = pwmApplication.getTempDirectory();
            final String jarHash = pwmApplication.getSecureService().hash(jarBytes);
            final String tempFileName = "jar-" + jarHash + ".jar";
            final File tempFile = new File(file.getAbsolutePath() + File.separator + tempFileName);
            if (tempFile.exists()) {
                final String fileHash = pwmApplication.getSecureService().hash(tempFile);
                if (!jarHash.equals(fileHash)) {
                    LOGGER.debug("existing temp jar file " + tempFile.getAbsolutePath() + " has wrong contents, will delete");
                    if (!tempFile.delete()) {
                        throw new IOException("unable to delete temp file " + jarHash);
                    }
                }
            }
            if (!tempFile.exists()) {
                LOGGER.debug("creating temp jar file " + tempFile.getAbsolutePath());
                final OutputStream fos = new BufferedOutputStream(new FileOutputStream(tempFile));
                fos.write(jarBytes);
                fos.close();
            } else {
                LOGGER.trace("reusing existing temp jar file " + tempFile.getAbsolutePath());
            }

            return tempFile;
        }
    }

    static class DriverWrapper {
        private final Driver driver;
        private final DriverLoader driverLoader;

        DriverWrapper(final Driver driver, final DriverLoader driverLoader) {
            this.driver = driver;
            this.driverLoader = driverLoader;
        }

        public Driver getDriver() {
            return driver;
        }

        public DriverLoader getDriverLoader() {
            return driverLoader;
        }
    }
}

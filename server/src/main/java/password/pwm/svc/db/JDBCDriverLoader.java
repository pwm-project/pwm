/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.svc.db;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import password.pwm.PwmApplication;
import password.pwm.data.ImmutableByteArray;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

public class JDBCDriverLoader
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( JDBCDriverLoader.class, true );

    static Driver loadDriver(
            final PwmApplication pwmApplication,
            final DBConfiguration dbConfiguration
    )
            throws DatabaseException
    {
        final Set<ClassLoaderStrategy> strategies = Set.of( ClassLoaderStrategy.AppPathFileLoader, ClassLoaderStrategy.Classpath );
        LOGGER.trace( () -> "attempting to load jdbc driver using strategies: " + JsonFactory.get().serializeCollection( strategies ) );
        final List<String> errorMsgs = new ArrayList<>();
        for ( final ClassLoaderStrategy strategy : strategies )
        {
            try
            {
                final DriverLoader loader = strategy.getJdbcDriverDriverLoader();
                final Driver driver = loader.loadDriver( pwmApplication, dbConfiguration );
                if ( driver != null )
                {
                    return driver;
                }
            }
            catch ( final PwmUnrecoverableException | DatabaseException e )
            {
                errorMsgs.add( strategy + " error: " + e.getMessage() );
            }
        }
        final String errorMsg = " unable to load database driver: " + JsonFactory.get().serializeCollection( errorMsgs );
        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
        LOGGER.error( () -> errorMsg );
        throw new DatabaseException( errorInformation );
    }

    public enum ClassLoaderStrategy
    {
        AppPathFileLoader( AppPathDriverLoader.class ),
        Classpath( JavaClasspathLoader.class ),;

        private final Class<? extends DriverLoader> jdbcDriverDriverLoaderClass;

        ClassLoaderStrategy( final Class<? extends DriverLoader> jdbcDriverDriverLoaderClass )
        {
            this.jdbcDriverDriverLoaderClass = jdbcDriverDriverLoaderClass;
        }

        private DriverLoader getJdbcDriverDriverLoader( )
                throws PwmUnrecoverableException
        {
            try
            {
                final Constructor<? extends DriverLoader> constructor = jdbcDriverDriverLoaderClass.getDeclaredConstructor();
                constructor.setAccessible( true );
                return constructor.newInstance();
            }
            catch ( final InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "unable to load jdbc driver loader: " + e.getMessage() );
            }
        }
    }

    interface DriverLoader
    {
        Driver loadDriver( PwmApplication pwmApplication, DBConfiguration dbConfiguration ) throws DatabaseException;
    }

    private static class JavaClasspathLoader implements DriverLoader
    {

        private static final PwmLogger LOGGER = PwmLogger.forClass( JavaClasspathLoader.class, true );

        @Override
        public Driver loadDriver( final PwmApplication pwmApplication, final DBConfiguration dbConfiguration )
                throws DatabaseException
        {
            final String jdbcClassName = dbConfiguration.getDriverClassname();

            try
            {
                LOGGER.debug( () -> "loading JDBC database driver from classpath: " + jdbcClassName );
                final Driver driver = DriverManager.getDriver( dbConfiguration.getConnectionString() );

                LOGGER.debug( () -> "successfully loaded JDBC database driver from classpath: " + jdbcClassName );
                return driver;
            }
            catch ( final Throwable e )
            {
                final String errorMsg = e.getClass().getName() + " error loading JDBC database driver from classpath: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
                throw new DatabaseException( errorInformation );
            }
        }
    }


    private static class AppPathDriverLoader implements DriverLoader
    {
        private static final PwmLogger LOGGER = PwmLogger.forClass( AppPathDriverLoader.class, true );

        public Driver loadDriver( final PwmApplication pwmApplication, final DBConfiguration dbConfiguration )
                throws DatabaseException
        {
            final ImmutableByteArray jdbcDriverBytes = dbConfiguration.getJdbcDriver();

            if ( jdbcDriverBytes == null || jdbcDriverBytes.size() < 1 )
            {
                final String errorMsg = "jdbc driver file not configured, skipping";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
                throw new DatabaseException( errorInformation );
            }

            try
            {
                if ( !createAndRegisterDriverJar( pwmApplication, dbConfiguration ) )
                {
                    return null;
                }
            }
            catch ( final Throwable e )
            {
                final String errorMsg = "error establishing classloader for driver: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
                throw new DatabaseException( errorInformation );
            }

            try
            {
                return DriverManager.getDriver( dbConfiguration.getConnectionString() );
            }
            catch ( final Throwable e )
            {
                final String errorMsg = "error registering JDBC database driver stored in configuration: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
                throw new DatabaseException( errorInformation );
            }
        }

        @SuppressFBWarnings( "DP_CREATE_CLASSLOADER_INSIDE_DO_PRIVILEGED" )
        boolean createAndRegisterDriverJar(
                final PwmApplication pwmApplication,
                final DBConfiguration dbConfiguration
        )
                throws PwmUnrecoverableException, IOException, ClassNotFoundException, NoSuchMethodException,
                InvocationTargetException, InstantiationException, IllegalAccessException, SQLException
        {
            final Optional<Path> pwmTempDir = pwmApplication.getTempDirectory();
            if ( pwmTempDir.isEmpty() )
            {
                return false;
            }

            final byte[] jarBytes = dbConfiguration.getJdbcDriver().copyOf();
            final String jarHash = pwmApplication.getSecureService().hash( jarBytes );
            final String tempFileName = "jar-" + jarHash + ".jar";
            final Path tempFile = pwmTempDir.get().resolve( tempFileName );

            if ( Files.exists( tempFile ) )
            {
                LOGGER.trace( () -> "reusing existing temp jar file and registration: " + tempFile );
                return true;
            }

            LOGGER.debug( () -> "creating temp jar file " + tempFile );
            Files.write( tempFile, jarBytes );

            // load into classloader
            final URLClassLoader urlClassLoader = new URLClassLoader(
                    new URL[]
                            {
                                    tempFile.toUri().toURL(),
                            },
                    this.getClass().getClassLoader() );

            //Create object of loaded class
            final Class<?> jdbcDriverClass = urlClassLoader.loadClass( dbConfiguration.getDriverClassname() );
            final Driver driver = new DriverShim( ( Driver ) jdbcDriverClass.getDeclaredConstructor().newInstance() );
            LOGGER.debug( () -> "successfully loaded JDBC database driver '" + dbConfiguration.getDriverClassname() + "' from application configuration" );
            DriverManager.registerDriver( driver );
            return true;

        }
    }

    private static class DriverShim implements Driver
    {
        private final Driver driver;

        DriverShim( final Driver driver )
        {
            this.driver = driver;
        }

        @Override
        public Connection connect( final String url, final Properties info )
                throws SQLException
        {
            return driver.connect( url, info );
        }

        @Override
        public boolean acceptsURL( final String url )
                throws SQLException
        {
            return driver.acceptsURL( url );
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo( final String url, final Properties info )
                throws SQLException
        {
            return driver.getPropertyInfo( url, info );
        }

        @Override
        public int getMajorVersion()
        {
            return driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion()
        {
            return driver.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant()
        {
            return driver.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger()
                throws SQLFeatureNotSupportedException
        {
            return driver.getParentLogger();
        }
    }
}

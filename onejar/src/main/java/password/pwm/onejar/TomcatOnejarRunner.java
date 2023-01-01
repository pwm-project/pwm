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

package password.pwm.onejar;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.ServerInfo;
import org.apache.coyote.http2.Http2Protocol;

import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class TomcatOnejarRunner
{
    private final OnejarMain onejarMain;
    private Tomcat tomcat;

    public TomcatOnejarRunner( final OnejarMain onejarMain )
    {
        this.onejarMain = onejarMain;
    }

    void startTomcat( final OnejarConfig onejarConfig )
            throws ServletException, IOException, OnejarException
    {
        final Instant startTime = Instant.now();

        final Properties tlsProperties;
        try
        {
            tlsProperties = this.executeOnejarHelper( onejarConfig );
            out( "keystore generated" );
        }
        catch ( final Exception e )
        {
            e.printStackTrace();
            if ( e instanceof InvocationTargetException )
            {
                throw new OnejarException( "error generating keystore: " + e.getCause().getMessage() );
            }
            throw new OnejarException( "error generating keystore: " + e.getMessage() );
        }

        setupEnv( onejarConfig );

        tomcat = new Tomcat();
        tomcat.setSilent( true );

        {
            final Path basePath = onejarConfig.getWorkingPath().resolve( "b" );
            Files.createDirectories( basePath );
            tomcat.setBaseDir( basePath.toString() );
        }
        {
            final Path basePath = onejarConfig.getWorkingPath().resolve( "a" );
            Files.createDirectories( basePath );
            tomcat.getServer().setCatalinaBase( basePath.toFile() );
            tomcat.getServer().setCatalinaHome( basePath.toFile() );
        }
        {
            final Path workPath = onejarConfig.getWorkingPath().resolve( "w" );
            Files.createDirectories( workPath );
            tomcat.getHost().setAppBase( workPath.toString() );
        }

        tomcat.getHost().setAutoDeploy( false );
        tomcat.getHost().setDeployOnStartup( false );

        deployRedirectConnector( tomcat, onejarConfig );

        final String warPath = onejarConfig.getWarFolder().toString();
        tomcat.addWebapp( "/" + onejarConfig.getContext(), warPath );

        try
        {
            tomcat.setConnector( makeConnector( onejarConfig, tlsProperties ) );
            tomcat.start();
            out( "tomcat started, listening on port " + onejarConfig.getPort(), startTime );
        }
        catch ( final Exception e )
        {
            throw new OnejarException( "unable to start tomcat: " + e.getMessage() );
        }
        tomcat.getServer().await();
    }

    private void deployRedirectConnector( final Tomcat tomcat, final OnejarConfig onejarConfig )
            throws IOException
    {
        final String srcRootWebXml = "ROOT-redirect-webapp/WEB-INF/web.xml";
        final String srcRootIndex = "ROOT-redirect-webapp/WEB-INF/index.jsp";

        final Path redirBase = onejarConfig.getWorkingPath().resolve( "redirectBase" );
        Files.createDirectories( redirBase );
        {
            final Path webInfPath = redirBase.resolve( "WEB-INF" );
            Files.createDirectories( webInfPath );
            copyFileAndReplace(
                    srcRootWebXml,
                    webInfPath.resolve( "web.xml" ).toString(),
                    onejarConfig.getContext() );
            copyFileAndReplace(
                    srcRootIndex,
                    webInfPath.resolve( "index.jsp" ).toString(),
                    onejarConfig.getContext() );
        }

        tomcat.addWebapp( "", redirBase.toString() );
    }


    private Connector makeConnector( final OnejarConfig onejarConfig, final Properties tlsProperties )
            throws Exception
    {
        final Connector connector = new Connector( "HTTP/1.1" );
        connector.setPort( onejarConfig.getPort() );

        if ( onejarConfig.getLocalAddress() != null && !onejarConfig.getLocalAddress().isEmpty() )
        {
            connector.setProperty( "address", onejarConfig.getLocalAddress() );
        }
        connector.setSecure( true );
        connector.setScheme( "https" );
        connector.addUpgradeProtocol( new Http2Protocol() );
        connector.setProperty( "SSLEnabled", "true" );
        connector.setProperty( "keystoreFile", onejarConfig.getKeystoreFile().toString() );
        connector.setProperty( "keystorePass", onejarConfig.getKeystorePass() );
        connector.setProperty( "keyAlias", OnejarMain.KEYSTORE_ALIAS );
        connector.setProperty( "clientAuth", "false" );

        out( "connector maxThreads=" + connector.getProperty( "maxThreads" ) );
        out( "connector maxConnections=" + connector.getProperty( "maxConnections" ) );

        if ( tlsProperties != null )
        {
            for ( final String key : tlsProperties.stringPropertyNames() )
            {
                final String value = tlsProperties.getProperty( key );
                connector.setProperty( key, value );
            }
        }

        return connector;
    }

    static String getVersion( ) throws OnejarException
    {
        try
        {
            final Class clazz = TomcatOnejarRunner.class;
            final String className = clazz.getSimpleName() + ".class";
            final String classPath = clazz.getResource( className ).toString();
            if ( !classPath.startsWith( "jar" ) )
            {
                // Class not from JAR
                return "version missing, not running inside jar";
            }
            final String manifestPath = classPath.substring( 0, classPath.lastIndexOf( '!' ) + 1 )
                    + "/META-INF/MANIFEST.MF";
            final Manifest manifest = new Manifest( new URL( manifestPath ).openStream() );
            final Attributes attr = manifest.getMainAttributes();
            return attr.getValue( "Implementation-Version-Display" )
                    + "  [" + ServerInfo.getServerInfo() + "]";
        }
        catch ( final IOException e )
        {
            throw new OnejarException( "error reading internal version info: " + e.getMessage() );
        }
    }

    void out( final String output )
    {
        onejarMain.out( output );
    }

    void out( final String output, final Instant startTime )
    {
        onejarMain.out( output, startTime );
    }

    Properties executeOnejarHelper( final OnejarConfig onejarConfig )
            throws IOException, ClassNotFoundException, IllegalAccessException, NoSuchMethodException, InvocationTargetException
    {
        final Instant startTime = Instant.now();

        try ( URLClassLoader classLoader = warClassLoaderFromConfig( onejarConfig ) )
        {
            final Class<?> pwmMainClass = classLoader.loadClass( "password.pwm.util.OnejarHelper" );
            final String keystoreFile = onejarConfig.getKeystoreFile().toString();
            final Method mainMethod = pwmMainClass.getMethod(
                    "onejarHelper",
                    String.class,
                    String.class,
                    String.class,
                    String.class
            );

            final String[] arguments = new String[] {
                    onejarConfig.getApplicationPath().toString(),
                    keystoreFile,
                    OnejarMain.KEYSTORE_ALIAS,
                    onejarConfig.getKeystorePass(),
            };

            final Object returnObjValue = mainMethod.invoke( null, ( Object[] ) arguments );
            final Properties returnProps = ( Properties ) returnObjValue;
            out( "completed read of tlsProperties", startTime );
            return returnProps;
        }
    }

    private void setupEnv( final OnejarConfig onejarConfig )
    {
        final String envVarPrefix = Resource.envVarPrefix.getValue() + ".";
        System.setProperty( envVarPrefix + "applicationPath", onejarConfig.getApplicationPath().toString() );
        System.setProperty( envVarPrefix + "ManageHttps", Boolean.TRUE.toString() );
        System.setProperty( envVarPrefix + "OnejarInstance", Boolean.TRUE.toString() );
        System.setProperty( envVarPrefix + "AutoExportHttpsKeyStoreFile", onejarConfig.getKeystoreFile().toString() );
        System.setProperty( envVarPrefix + "AutoExportHttpsKeyStorePassword", onejarConfig.getKeystorePass() );
        System.setProperty( envVarPrefix + "AutoExportHttpsKeyStoreAlias", OnejarMain.KEYSTORE_ALIAS );
    }


    private void copyFileAndReplace(
            final String srcPath,
            final String destPath,
            final String rootcontext
    )
            throws IOException
    {
        try ( InputStream inputStream = TomcatOnejarRunner.class.getClassLoader().getResourceAsStream( srcPath ) )
        {
            try ( BufferedReader reader = new BufferedReader( new InputStreamReader( inputStream, StandardCharsets.UTF_8 ) ) )
            {
                String contents = reader.lines().collect( Collectors.joining( "\n" ) );
                contents = contents.replace( "[[[ROOT_CONTEXT]]]", rootcontext );
                Files.write( Path.of( destPath ), contents.getBytes( StandardCharsets.UTF_8 ) );
            }
        }
    }

    URLClassLoader warClassLoaderFromConfig( final OnejarConfig onejarConfig )
            throws IOException
    {
        final Path warPath = onejarConfig.getWarFolder();
        final Path webInfLibPath = warPath.resolve( "WEB-INF" ).resolve( "lib" );
        final List<Path> jarFiles = Files.list( webInfLibPath )
                .filter( path -> path.getFileName().toString().endsWith( ".jar" ) )
                .collect( Collectors.toList() );

        final List<URL> jarURLList = new ArrayList<>( jarFiles.size() );
        for ( final Path jarFile : jarFiles )
        {
            jarURLList.add( jarFile.toUri().toURL() );
        }
        return URLClassLoader.newInstance( jarURLList.toArray( new URL[0] ) );
    }


    public void shutdown() throws LifecycleException
    {
        if ( tomcat != null )
        {
            final Tomcat localTomcat = tomcat;
            localTomcat.stop();
            localTomcat.destroy();
            tomcat = null;
        }
    }
}

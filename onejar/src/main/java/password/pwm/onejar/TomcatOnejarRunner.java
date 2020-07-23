/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.ServerInfo;
import org.apache.coyote.http2.Http2Protocol;

import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
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
            throw new OnejarException( "error generating keystore: " + e.getMessage() );
        }

        outputPwmAppProperties( onejarConfig );

        setupEnv( onejarConfig );

        final Tomcat tomcat = new Tomcat();
        tomcat.setSilent( true );

        {
            final File basePath = new File( onejarConfig.getWorkingPath().getPath() + File.separator + "b" );
            ArgumentParser.mkdirs( basePath );
            tomcat.setBaseDir( basePath.getAbsolutePath() );
        }
        {
            final File basePath = new File( onejarConfig.getWorkingPath().getPath() + File.separator + "a" );
            ArgumentParser.mkdirs( basePath );
            tomcat.getServer().setCatalinaBase( basePath );
            tomcat.getServer().setCatalinaHome( basePath );
        }
        {
            final File workPath = new File( onejarConfig.getWorkingPath().getPath() + File.separator + "w" );
            ArgumentParser.mkdirs( workPath );
            tomcat.getHost().setAppBase( workPath.getAbsolutePath() );
        }

        tomcat.getHost().setAutoDeploy( false );
        tomcat.getHost().setDeployOnStartup( false );

        deployRedirectConnector( tomcat, onejarConfig );

        final String warPath = onejarConfig.getWarFolder().getAbsolutePath();
        tomcat.addWebapp( "/" + onejarConfig.getContext(), warPath );

        try
        {
            tomcat.setConnector( makeConnector( onejarConfig, tlsProperties ) );
            tomcat.start();
            out( "tomcat started in " + Duration.between( Instant.now(), startTime ).toString() );
        }
        catch ( final Exception e )
        {
            throw new OnejarException( "unable to start tomcat: " + e.getMessage() );
        }
        tomcat.getServer().await();

        System.out.println( "\nexiting..." );
    }

    private void deployRedirectConnector( final Tomcat tomcat, final OnejarConfig onejarConfig )
            throws IOException, ServletException
    {
        final String srcRootWebXml = "ROOT-redirect-webapp/WEB-INF/web.xml";
        final String srcRootIndex = "ROOT-redirect-webapp/WEB-INF/index.jsp";

        final File redirBase = new File( onejarConfig.getWorkingPath().getAbsoluteFile() + File.separator + "redirectBase" );
        ArgumentParser.mkdirs( redirBase );
        {
            ArgumentParser.mkdirs( new File ( redirBase.getAbsolutePath() + File.separator + "WEB-INF" ) );
            copyFileAndReplace(
                    srcRootWebXml,
                    redirBase.getPath() + File.separator + "WEB-INF" + File.separator + "web.xml",
                    onejarConfig.getContext() );
            copyFileAndReplace(
                    srcRootIndex,
                    redirBase.getPath() + File.separator +  "WEB-INF" + File.separator + "index.jsp",
                    onejarConfig.getContext() );
        }

        tomcat.addWebapp( "", redirBase.getAbsolutePath() );
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
        connector.setAttribute( "SSLEnabled", "true" );
        connector.setAttribute( "keystoreFile", onejarConfig.getKeystoreFile().getAbsolutePath() );
        connector.setAttribute( "keystorePass", onejarConfig.getKeystorePass() );
        connector.setAttribute( "keyAlias", OnejarMain.KEYSTORE_ALIAS );
        connector.setAttribute( "clientAuth", "false" );

        out( "connector maxThreads=" + connector.getAttribute( "maxThreads" ) );
        out( "connector maxConnections=" + connector.getAttribute( "maxConnections" ) );

        if ( tlsProperties != null )
        {
            for ( final String key : tlsProperties.stringPropertyNames() )
            {
                final String value = tlsProperties.getProperty( key );
                connector.setAttribute( key, value );
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
            final String manifestPath = classPath.substring( 0, classPath.lastIndexOf( "!" ) + 1 )
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


    Properties executeOnejarHelper( final OnejarConfig onejarConfig )
            throws IOException, ClassNotFoundException, IllegalAccessException, NoSuchMethodException, InvocationTargetException
    {
        try ( URLClassLoader classLoader = warClassLoaderFromConfig( onejarConfig ) )
        {
            final Class pwmMainClass = classLoader.loadClass( "password.pwm.util.OnejarHelper" );
            final String keystoreFile = onejarConfig.getKeystoreFile().getAbsolutePath();
            final Method mainMethod = pwmMainClass.getMethod(
                    "onejarHelper",
                    String.class,
                    String.class,
                    String.class,
                    String.class
            );

            final String[] arguments = new String[] {
                    onejarConfig.getApplicationPath().getAbsolutePath(),
                    keystoreFile,
                    OnejarMain.KEYSTORE_ALIAS,
                    onejarConfig.getKeystorePass(),
            };

            final Object returnObjValue = mainMethod.invoke( null, arguments );
            final Properties returnProps = ( Properties ) returnObjValue;
            out( "completed read of tlsProperties " );
            return returnProps;
        }
    }

    private void setupEnv( final OnejarConfig onejarConfig )
    {
        final String envVarPrefix = Resource.envVarPrefix.getValue();
        System.setProperty( envVarPrefix + "_APPLICATIONPATH", onejarConfig.getApplicationPath().getAbsolutePath() );
        System.setProperty( envVarPrefix + "_APPLICATIONFLAGS", "ManageHttps" );
        System.setProperty( envVarPrefix + "_APPLICATIONPARAMFILE", onejarConfig.getPwmAppPropertiesFile().getAbsolutePath() );
    }

    private void outputPwmAppProperties( final OnejarConfig onejarConfig ) throws IOException
    {
        final Properties properties = new Properties();
        properties.setProperty( "AutoExportHttpsKeyStoreFile", onejarConfig.getKeystoreFile().getAbsolutePath() );
        properties.setProperty( "AutoExportHttpsKeyStorePassword", onejarConfig.getKeystorePass() );
        properties.setProperty( "AutoExportHttpsKeyStoreAlias", OnejarMain.KEYSTORE_ALIAS );
        final File propFile = onejarConfig.getPwmAppPropertiesFile( );
        try ( Writer writer = new OutputStreamWriter( new FileOutputStream( propFile ), StandardCharsets.UTF_8 ) )
        {
            properties.store( writer, "auto-generated file" );
        }
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
            try ( BufferedReader reader = new BufferedReader( new InputStreamReader( inputStream, "UTF8" ) ) )
            {
                String contents = reader.lines().collect( Collectors.joining( "\n" ) );
                contents = contents.replace( "[[[ROOT_CONTEXT]]]", rootcontext );
                Files.write( Paths.get( destPath ), contents.getBytes( "UTF8" ) );
            }
        }
    }

    URLClassLoader warClassLoaderFromConfig( final OnejarConfig onejarConfig )
            throws IOException
    {
        final File warPath = onejarConfig.getWarFolder();
        final File webInfPath = new File( warPath.getAbsolutePath() + File.separator + "WEB-INF" + File.separator + "lib" );
        final File[] jarFiles = webInfPath.listFiles();
        final List<URL> jarURLList = new ArrayList<>();
        if ( jarFiles != null )
        {
            for ( final File jarFile : jarFiles )
            {
                jarURLList.add( jarFile.toURI().toURL() );
            }
        }
        return URLClassLoader.newInstance( jarURLList.toArray( new URL[ jarURLList.size() ] ) );
    }
}

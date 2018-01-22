package password.pwm;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.ServerInfo;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TomcatOneJarMain
{
    //private static final String TEMP_WAR_FILE_NAME = "embed.war";
    private static final String KEYSTORE_ALIAS = "https";

    public static void main( final String[] args )
    {
        final ArgumentParser argumentParser = new ArgumentParser();
        TomcatConfig tomcatConfig = null;
        try
        {
            tomcatConfig = argumentParser.parseArguments( args );
        }
        catch ( ArgumentParserException | TomcatOneJarException e )
        {
            out( "error parsing command line: " + e.getMessage() );
        }
        if ( tomcatConfig != null )
        {
            try
            {
                startTomcat( tomcatConfig );
            }
            catch ( TomcatOneJarException | ServletException | IOException e )
            {
                out( "error starting tomcat: " + e.getMessage() );
            }
        }
    }

    private static File getWarFolder( final TomcatConfig tomcatConfig ) throws IOException
    {
        return new File( tomcatConfig.getWorkingPath().getAbsoluteFile() + File.separator + "war" );
    }

    private static File getKeystoreFile( final TomcatConfig tomcatConfig )
    {
        return new File( tomcatConfig.getWorkingPath().getAbsoluteFile() + File.separator + "keystore" );
    }

    private static File getPwmAppPropertiesFile( final TomcatConfig tomcatConfig )
    {
        return new File( tomcatConfig.getWorkingPath().getAbsoluteFile() + File.separator + "application.properties" );
    }


    private static void explodeWar( final TomcatConfig tomcatConfig ) throws IOException
    {
        final InputStream warSource = tomcatConfig.getWar();
        final ZipInputStream zipInputStream = new ZipInputStream( warSource );
        final File outputFolder = getWarFolder( tomcatConfig );
        outputFolder.mkdir();

        ZipEntry zipEntry = zipInputStream.getNextEntry();

        while ( zipEntry != null )
        {
            final String fileName = zipEntry.getName();
            final File newFile = new File( outputFolder + File.separator + fileName );

            if ( !zipEntry.isDirectory() )
            {
                newFile.getParentFile().mkdirs();
                Files.copy( zipInputStream, newFile.toPath() );
            }
            zipEntry = zipInputStream.getNextEntry();
        }

    }

    private static void startTomcat( final TomcatConfig tomcatConfig ) throws ServletException, IOException, TomcatOneJarException
    {
        final Instant startTime = Instant.now();

        purgeDirectory( tomcatConfig.getWorkingPath().toPath() );

        explodeWar( tomcatConfig );
        out( "deployed war" );

        try
        {
            generatePwmKeystore( tomcatConfig );
            out( "keystore generated" );
        }
        catch ( Exception e )
        {
            throw new TomcatOneJarException( "error generating keystore: " + e.getMessage() );
        }

        outputPwmAppProperties( tomcatConfig );

        setupEnv( tomcatConfig );

        final Tomcat tomcat = new Tomcat();

        {
            final File basePath = new File( tomcatConfig.getWorkingPath().getPath() + File.separator + "b" );
            basePath.mkdir();
            tomcat.setBaseDir( basePath.getAbsolutePath() );
        }
        {
            final File basePath = new File( tomcatConfig.getWorkingPath().getPath() + File.separator + "a" );
            basePath.mkdir();
            tomcat.getServer().setCatalinaBase( basePath );
            tomcat.getServer().setCatalinaHome( basePath );
        }
        {
            final File workPath = new File( tomcatConfig.getWorkingPath().getPath() + File.separator + "w" );
            workPath.mkdir();
            tomcat.getHost().setAppBase( workPath.getAbsolutePath() );
        }


        tomcat.getHost().setAutoDeploy( false );
        tomcat.getHost().setDeployOnStartup( false );

        final String warPath = getWarFolder( tomcatConfig ).getAbsolutePath();
        tomcat.addWebapp( "/" + tomcatConfig.getContext(), warPath );

        try
        {
            tomcat.start();

            tomcat.setConnector( makeConnector( tomcatConfig ) );

            out( "tomcat started in " + Duration.between( Instant.now(), startTime ).toString() );
        }
        catch ( LifecycleException e )
        {
            throw new TomcatOneJarException( "unable to start tomcat: " + e.getMessage() );
        }

        tomcat.getServer().await();

        System.out.println( "\n" );
    }

    private static Connector makeConnector( final TomcatConfig tomcatConfig )
    {
        final Connector connector = new Connector( "HTTP/1.1" );
        connector.setPort( tomcatConfig.getPort() );

        if ( tomcatConfig.getLocalAddress() != null && !tomcatConfig.getLocalAddress().isEmpty() )
        {
            connector.setProperty( "address", tomcatConfig.getLocalAddress() );
        }
        connector.setSecure( true );
        connector.setScheme( "https" );
        connector.setAttribute( "SSLEnabled", "true" );
        connector.setAttribute( "keystoreFile", getKeystoreFile( tomcatConfig ).getAbsolutePath() );
        connector.setAttribute( "keystorePass", tomcatConfig.getKeystorePass() );
        connector.setAttribute( "keyAlias", KEYSTORE_ALIAS );
        connector.setAttribute( "clientAuth", "false" );

        return connector;
    }

    static String getVersion( ) throws TomcatOneJarException
    {
        try
        {
            final Class clazz = TomcatOneJarMain.class;
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
        catch ( IOException e )
        {
            throw new TomcatOneJarException( "error reading internal version info: " + e.getMessage() );
        }
    }

    private static void purgeDirectory( final Path rootPath )
            throws IOException
    {
        System.out.println( "purging work directory: " + rootPath );
        Files.walk( rootPath, FileVisitOption.FOLLOW_LINKS )
                .sorted( Comparator.reverseOrder() )
                .map( Path::toFile )
                .filter( file -> !rootPath.toString().equals( file.getPath() ) )
                .forEach( File::delete );
    }


    static void out( final String output )
    {
        System.out.println( output );
    }


    static void generatePwmKeystore( final TomcatConfig tomcatConfig )
            throws IOException, ClassNotFoundException, IllegalAccessException, NoSuchMethodException, InvocationTargetException
    {
        final File warPath = getWarFolder( tomcatConfig );
        final String keystoreFile = getKeystoreFile( tomcatConfig ).getAbsolutePath();
        final File webInfPath = new File( warPath.getAbsolutePath() + File.separator + "WEB-INF" + File.separator + "lib" );
        final File[] jarFiles = webInfPath.listFiles();
        final List<URL> jarURLList = new ArrayList<>();
        for ( final File jarFile : jarFiles )
        {
            jarURLList.add( jarFile.toURI().toURL() );
        }
        final URLClassLoader classLoader = URLClassLoader.newInstance( jarURLList.toArray( new URL[ jarURLList.size() ] ) );
        final Class pwmMainClass = classLoader.loadClass( "password.pwm.util.cli.MainClass" );
        final Method mainMethod = pwmMainClass.getMethod( "main", String[].class );
        final String[] arguments = new String[] {
                "-applicationPath=" + tomcatConfig.getApplicationPath().getAbsolutePath(),
                "ExportHttpsKeyStore",
                keystoreFile,
                KEYSTORE_ALIAS,
                tomcatConfig.getKeystorePass(),
        };

        mainMethod.invoke( null, ( Object ) arguments );
        classLoader.close();
    }

    static void setupEnv( final TomcatConfig tomcatConfig )
    {
        System.setProperty( "PWM_APPLICATIONPATH", tomcatConfig.getApplicationPath().getAbsolutePath() );
        System.setProperty( "PWM_APPLICATIONFLAGS", "ManageHttps" );
        System.setProperty( "PWM_APPLICATIONPARAMFILE", getPwmAppPropertiesFile( tomcatConfig ).getAbsolutePath() );
    }

    static void outputPwmAppProperties( final TomcatConfig tomcatConfig ) throws IOException
    {
        final Properties properties = new Properties();
        properties.setProperty( "AutoExportHttpsKeyStoreFile", getKeystoreFile( tomcatConfig ).getAbsolutePath() );
        properties.setProperty( "AutoExportHttpsKeyStorePassword", tomcatConfig.getKeystorePass() );
        properties.setProperty( "AutoExportHttpsKeyStoreAlias", KEYSTORE_ALIAS );
        final File propFile = getPwmAppPropertiesFile( tomcatConfig );
        properties.store( new FileWriter( propFile ), "auto-generated file" );
    }
}

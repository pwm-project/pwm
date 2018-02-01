package password.pwm;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.CoyoteAdapter;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.ServerInfo;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URL;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Logger;

public class TomcatOneJarMain
{
    private static final  Logger LOGGER = Logger.getLogger(TomcatOneJarMain.class.getName());

    private static final String ARG_APP_PATH = "applicationPath";
    private static final String ARG_WORK_PATH = "workPath";
    private static final String ARG_VERSION = "version";
    private static final String ARG_HELP = "help";
    private static final String ARG_WARFILE = "war";
    private static final String ARG_PORT = "port";
    private static final String ARG_CONTEXT = "context";

    private static final String DEFAULT_CONTEXT = "pwm";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_WORK_DIR_NAME = ".pwm-workpath";

    private static final String EMBED_WAR_NAME = "embed.war";

    public static void main(final String[] args) throws ServletException, IOException, LifecycleException
    {
        if (args == null || args.length == 0) {
            outputHelp();
        } else
        {
            final CommandLine commandLine = parseOptions( args );
            if ( commandLine.hasOption( ARG_VERSION ) ) {
                System.out.println( getVersion() );
            } else if ( commandLine.hasOption( ARG_HELP ) ) {
                outputHelp();
            } else {
                final TomcatConfig tomcatConfig = makeTomcatConfig( commandLine );
                startTomcat(tomcatConfig);
            }
        }
    }

    private static TomcatConfig makeTomcatConfig(final CommandLine commandLine) throws IOException
    {
        final TomcatConfig tomcatConfig = new TomcatConfig();
        tomcatConfig.setApplicationPath( parseFileOption( commandLine, ARG_APP_PATH ) );

        if (commandLine.hasOption( ARG_CONTEXT )) {
            tomcatConfig.setContext( commandLine.getOptionValue( ARG_CONTEXT ) );
        } else {
            tomcatConfig.setContext( DEFAULT_CONTEXT );
        }

        if (commandLine.hasOption( ARG_WARFILE )) {
            final File inputWarFile = new File (commandLine.getOptionValue( ARG_WARFILE ));
            if (!inputWarFile.exists()) {
                System.out.println( "output war file " + inputWarFile.getAbsolutePath() + "does not exist" );
                System.exit( -1 );
                return null;
            }
            tomcatConfig.setWar( new FileInputStream( inputWarFile ) );
        } else {
            tomcatConfig.setWar( getEmbeddedWar() );
        }

        tomcatConfig.setPort( DEFAULT_PORT );
        if (commandLine.hasOption( ARG_PORT )) {
            try {
                tomcatConfig.setPort( Integer.parseInt( commandLine.getOptionValue( ARG_PORT ) ) );
            } catch (NumberFormatException e) {
                System.out.println( ARG_PORT + " argument must be numeric" );
                System.exit( -1 );
            }
        }

        if ( checkIfPortInUse( tomcatConfig.getPort())) {
            System.out.println( "port " + tomcatConfig.getPort() +  " is in use" );
            System.exit( -1 );
        }

        if (commandLine.hasOption( ARG_WORK_PATH )) {
            tomcatConfig.setWorkingPath( parseFileOption( commandLine, ARG_WORK_PATH ) );
        } else {
            tomcatConfig.setWorkingPath( figureDefaultWorkPath(tomcatConfig) );
        }

        return tomcatConfig;
    }

    private static File parseFileOption(final  CommandLine commandLine, final String argName) {
        if (!commandLine.hasOption( argName )) {
            outAndExit(  "option " + argName + " required");
        }
        final File file = new File(commandLine.getOptionValue( argName ));
        if (!file.isAbsolute()) {
            outAndExit( "a fully qualified file path name is required for " + argName);
        }
        if (!file.exists()) {
            outAndExit( "path specified by " + argName + " must exist");
        }
        return file;
    }

    private static void outputHelp() throws IOException
    {
        final HelpFormatter formatter = new HelpFormatter();
        System.out.println( getVersion() );
        System.out.println( "usage:" );
        formatter.printOptions( System.console().writer(), HelpFormatter.DEFAULT_WIDTH, makeOptions(),3 , 8);
    }

    private static void startTomcat( final TomcatConfig tomcatConfig) throws ServletException, IOException
    {
        purgeDirectory( tomcatConfig.getWorkingPath().toPath() );
        {
            final Path outputPath = tomcatConfig.getWorkingPath().toPath().resolve( EMBED_WAR_NAME );
            final InputStream warSource = tomcatConfig.getWar();
            Files.copy(warSource, outputPath);
        }
        System.setProperty( "PWM_APPLICATIONPATH", tomcatConfig.getApplicationPath().getAbsolutePath() );

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

        tomcat.setConnector( makeConnector( tomcatConfig ) );

        tomcat.setPort( DEFAULT_PORT );
        tomcat.getConnector();

        tomcat.getHost().setAutoDeploy(false);
        tomcat.getHost().setDeployOnStartup(false);

        final String warPath = tomcatConfig.getWorkingPath().getAbsolutePath() + File.separator + EMBED_WAR_NAME;
        final Context pwmContext = tomcat.addWebapp( "/" + tomcatConfig.getContext(), warPath );
        LOGGER.info("Deployed " + pwmContext.getBaseName() + " as " + pwmContext.getBaseName());

        try {
            tomcat.start();
        } catch (LifecycleException e) {
            outAndExit( "unable to start tomcat: " + e.getMessage() );
        }
        LOGGER.info("tomcat started on " + tomcat.getHost());

        tomcat.getServer().await();
    }

    private static Connector makeConnector(final TomcatConfig tomcatConfig) {
        final Connector connector = new Connector("HTTP/1.1");
        connector.setPort(tomcatConfig.getPort());
        return connector;
    }

    private static CommandLine parseOptions(final String[] args) {
        final CommandLineParser parser = new DefaultParser();
        try {
            return parser.parse( makeOptions(), args);
        } catch ( ParseException e ) {
            outAndExit(  "error parsing commandline: " + e.getMessage() );
        }
        return null;
    }

    private static Options makeOptions() {
        final Map<String,Option> optionMap = new TreeMap<>(  );

        optionMap.put(ARG_APP_PATH,Option.builder(ARG_APP_PATH)
                .desc( "application path (required)" )
                .numberOfArgs( 1 )
                .required(false)
                .build());

        optionMap.put(ARG_WORK_PATH,Option.builder(ARG_WORK_PATH)
                .desc( "temporary work path" )
                .numberOfArgs( 1 )
                .required(false)
                .build());

        optionMap.put(ARG_VERSION,Option.builder(ARG_VERSION)
                .desc( "show version" )
                .numberOfArgs( 0 )
                .required(false)
                .build());

        optionMap.put(ARG_PORT,Option.builder()
                .longOpt( ARG_PORT )
                .desc( "web server port (default " + DEFAULT_PORT + ")" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put(ARG_CONTEXT,Option.builder()
                .longOpt( ARG_CONTEXT )
                .desc( "context (url path) name (default " + DEFAULT_CONTEXT + ")" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put(ARG_HELP,Option.builder(ARG_HELP)
                .desc( "show this help" )
                .numberOfArgs( 0 )
                .required(false)
                .build());

        optionMap.put(ARG_WARFILE,Option.builder(ARG_WARFILE)
                .desc( "source war file (default embedded)" )
                .numberOfArgs( 1 )
                .required(false)
                .build());

        final Options options = new Options();
        optionMap.values().forEach( options::addOption );
        return options;
    }

    private static InputStream getEmbeddedWar() throws IOException
    {
        final Class clazz = TomcatOneJarMain.class;
        final String className = clazz.getSimpleName() + ".class";
        final String classPath = clazz.getResource(className).toString();
        if (!classPath.startsWith("jar")) {
            outAndExit("not running from war, war option must be specified");
            return null;
        }
        final String warPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                "/" + EMBED_WAR_NAME;
        return new URL(warPath).openStream();
    }

    private static String getVersion() throws IOException
    {
        final Class clazz = TomcatOneJarMain.class;
        final String className = clazz.getSimpleName() + ".class";
        final String classPath = clazz.getResource(className).toString();
        if (!classPath.startsWith("jar")) {
            // Class not from JAR
            return "--version missing--";
        }
        final String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                "/META-INF/MANIFEST.MF";
        final Manifest manifest = new Manifest(new URL(manifestPath).openStream());
        final Attributes attr = manifest.getMainAttributes();
        return attr.getValue("Implementation-Version-Display")
                + ", " + ServerInfo.getServerInfo();
    }

    private static void purgeDirectory(final Path rootPath)
            throws IOException
    {
        System.out.println("purging work directory: " + rootPath);
        Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
                .sorted( Comparator.reverseOrder())
                .map(Path::toFile)
                .filter( file -> !rootPath.toString().equals( file.getPath() ) )
                .forEach(File::delete);
    }

    private static boolean checkIfPortInUse( final int portNumber) {
        boolean result;

        try {
            Socket s = new Socket("localhost", portNumber);
            s.close();
            result = true;
        } catch(IOException e) {
            result = false;
        }

        return(result);
    }

    private static File figureDefaultWorkPath(final TomcatConfig tomcatConfig) {
        final String userHomePath = System.getProperty( "user.home" );
        if (userHomePath != null && !userHomePath.isEmpty()) {
            final File basePath = new File(userHomePath + File.separator
                    + "." + DEFAULT_CONTEXT);
            basePath.mkdir();
            final File workPath = new File( basePath.getPath() + File.separator
                    + "work-" + tomcatConfig.getContext() + "-" + Integer.toString( tomcatConfig.getPort() ));
            workPath.mkdir();
            System.out.println( "using work directory: " + workPath.getAbsolutePath() );
            return workPath;
        }

        System.out.println( "cant locate user home directory" );
        System.exit( -1 );
        return null;
    }

    private static Object outAndExit(final String output) {
        System.out.println(output);
        System.exit( -1 );
        return null;
    }
}

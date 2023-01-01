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

package password.pwm.util.cli;

import password.pwm.AppProperty;
import password.pwm.EnvironmentProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.stored.ConfigurationFileManager;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.cli.commands.ClearResponsesCommand;
import password.pwm.util.cli.commands.CliCommand;
import password.pwm.util.cli.commands.ConfigDeleteCommand;
import password.pwm.util.cli.commands.ConfigLockCommand;
import password.pwm.util.cli.commands.ConfigNewCommand;
import password.pwm.util.cli.commands.ConfigResetHttpsCommand;
import password.pwm.util.cli.commands.ConfigSetPasswordCommand;
import password.pwm.util.cli.commands.ConfigUnlockCommand;
import password.pwm.util.cli.commands.ExportAuditCommand;
import password.pwm.util.cli.commands.ExportHttpsKeyStoreCommand;
import password.pwm.util.cli.commands.ExportHttpsTomcatConfigCommand;
import password.pwm.util.cli.commands.ExportLocalDBCommand;
import password.pwm.util.cli.commands.ExportLogsCommand;
import password.pwm.util.cli.commands.ExportResponsesCommand;
import password.pwm.util.cli.commands.ExportStatsCommand;
import password.pwm.util.cli.commands.ExportWordlistCommand;
import password.pwm.util.cli.commands.HelpCommand;
import password.pwm.util.cli.commands.ImportHttpsKeyStoreCommand;
import password.pwm.util.cli.commands.ImportLocalDBCommand;
import password.pwm.util.cli.commands.ImportPropertyConfigCommand;
import password.pwm.util.cli.commands.ImportResponsesCommand;
import password.pwm.util.cli.commands.LdapSchemaExtendCommand;
import password.pwm.util.cli.commands.LocalDBInfoCommand;
import password.pwm.util.cli.commands.ResetInstanceIDCommand;
import password.pwm.util.cli.commands.ResponseStatsCommand;
import password.pwm.util.cli.commands.ShellCommand;
import password.pwm.util.cli.commands.TokenInfoCommand;
import password.pwm.util.cli.commands.UserReportCommand;
import password.pwm.util.cli.commands.VersionCommand;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.logging.PwmLogger;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainClass
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( MainClass.class );

    private static final String LOGGING_PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ssX}{GMT}, %-5p, %c{2}, %m%n";

    private static MainOptions mainOptions;

    public static final Map<String, CliCommand> COMMANDS = Map.copyOf(
            new TreeMap<>( Stream.of(
                    new LocalDBInfoCommand(),
                    new ExportLogsCommand(),
                    new UserReportCommand(),
                    new ExportLocalDBCommand(),
                    new ImportLocalDBCommand(),
                    new ExportAuditCommand(),
                    new ConfigUnlockCommand(),
                    new ConfigLockCommand(),
                    new ConfigSetPasswordCommand(),
                    new ExportStatsCommand(),
                    new ExportResponsesCommand(),
                    new ClearResponsesCommand(),
                    new ImportResponsesCommand(),
                    new TokenInfoCommand(),
                    new ConfigNewCommand(),
                    new VersionCommand(),
                    new LdapSchemaExtendCommand(),
                    new ConfigDeleteCommand(),
                    new ResponseStatsCommand(),
                    new ImportHttpsKeyStoreCommand(),
                    new ExportHttpsKeyStoreCommand(),
                    new ExportHttpsTomcatConfigCommand(),
                    new ShellCommand(),
                    new ConfigResetHttpsCommand(),
                    new HelpCommand(),
                    new ImportPropertyConfigCommand(),
                    new ResetInstanceIDCommand(),
                    new ExportWordlistCommand() ).collect( Collectors.toMap(
                    command -> command.getCliParameters().commandName,
                    Function.identity() ) ) ) );


    public static String helpTextFromCommands( final Collection<CliCommand> commands )
    {
        final StringBuilder output = new StringBuilder();
        for ( final CliCommand command : commands )
        {
            output.append( command.getCliParameters().commandName );
            if ( command.getCliParameters().options != null )
            {
                for ( final CliParameters.Option option : command.getCliParameters().options )
                {
                    output.append( ' ' );
                    if ( option.isOptional() )
                    {
                        output.append( '<' ).append( option.getName() ).append( '>' );
                    }
                    else
                    {
                        output.append( '[' ).append( option.getName() ).append( ']' );
                    }
                }
            }
            output.append( '\n' );
            output.append( "       " ).append( command.getCliParameters().description );
            output.append( '\n' );
        }
        return output.toString();
    }

    private static String makeHelpTextOutput( )
    {
        final StringBuilder output = new StringBuilder();
        output.append( helpTextFromCommands( COMMANDS.values() ) );
        output.append( '\n' );
        output.append( "options:\n" );
        output.append( " -force                force operations skipping any confirmation\n" );
        output.append( " -debugLevel=x         set the debug level where x is TRACE, DEBUG, INFO, ERROR, WARN or FATAL\n" );
        output.append( " -applicationPath=x    set the application path, default is current path\n" );
        output.append( '\n' );
        output.append( "usage: \n" );
        output.append( " command[.bat/.sh] <options> CommandName <command options>" );

        return output.toString();
    }

    private static CliEnvironment createEnv(
            final CliParameters parameters,
            final List<String> args
    )
            throws Exception
    {

        final Map<String, Object> options = parseCommandOptions( parameters, args );
        final Path applicationPath = figureApplicationPath( mainOptions );
        out( "applicationPath=" + applicationPath );
        PwmEnvironment.verifyApplicationPath( applicationPath );

        final Path configurationFile = locateConfigurationFile( applicationPath );

        final ConfigurationFileManager configReader = loadConfiguration( configurationFile );
        final AppConfig config = configReader.getConfiguration();

        final PwmApplication pwmApplication;
        final LocalDB localDB;

        if ( parameters.needsPwmApplication )
        {
            pwmApplication = loadPwmApplication( applicationPath, config, configurationFile, parameters.readOnly );
            localDB = pwmApplication.getLocalDB();
        }
        else if ( parameters.needsLocalDB )
        {
            pwmApplication = null;
            localDB = loadPwmDB( config, parameters.readOnly, applicationPath );
        }
        else
        {
            pwmApplication = null;
            localDB = null;
        }

        out( PwmConstants.PWM_APP_NAME + " environment initialized" );
        out( "" );

        final Writer outputStream = new OutputStreamWriter( System.out, PwmConstants.DEFAULT_CHARSET );
        return CliEnvironment.builder()
                .configurationFileManager( configReader )
                .configurationFile( configurationFile )
                .config( config )
                .applicationPath( applicationPath )
                .pwmApplication( pwmApplication )
                .localDB( localDB )
                .debugWriter( outputStream )
                .options( options )
                .mainOptions( mainOptions )
                .build();
    }

    public static Map<String, Object> parseCommandOptions(
            final CliParameters cliParameters,
            final List<String> args
    )
            throws CliException
    {
        final Queue<String> argQueue = new LinkedList<>( args );
        final Map<String, Object> returnObj = new LinkedHashMap<>();

        if ( cliParameters.options != null )
        {
            for ( final CliParameters.Option option : cliParameters.options )
            {
                if ( !option.isOptional() && argQueue.isEmpty() )
                {
                    throw new CliException( "missing required option '" + option.getName() + "'" );
                }

                if ( !argQueue.isEmpty() )
                {
                    final String argument = argQueue.poll();
                    switch ( option.getType() )
                    {
                        case NEW_FILE:
                            try
                            {
                                final Path theFile = Path.of( argument );
                                if ( Files.exists( theFile ) )
                                {
                                    throw new CliException( "file for option '" + option.getName() + "' at '" + theFile + "' already exists" );
                                }
                                returnObj.put( option.getName(), theFile );
                            }
                            catch ( final Exception e )
                            {
                                if ( e instanceof CliException )
                                {
                                    throw ( CliException ) e;
                                }
                                throw new CliException( "cannot access file for option '" + option.getName() + "', " + e.getMessage(), e );
                            }
                            break;

                        case EXISTING_FILE:
                            try
                            {
                                final Path theFile = Path.of( argument );
                                if ( !Files.exists( theFile ) )
                                {
                                    throw new CliException( "file for option '" + option.getName() + "' at '" + theFile + "' does not exist" );
                                }
                                returnObj.put( option.getName(), theFile );
                            }
                            catch ( final Exception e )
                            {
                                if ( e instanceof CliException )
                                {
                                    throw ( CliException ) e;
                                }
                                throw new CliException( "cannot access file for option '" + option.getName() + "', " + e.getMessage(), e );
                            }
                            break;

                        case STRING:
                            returnObj.put( option.getName(), argument );
                            break;

                        default:
                            PwmUtil.unhandledSwitchStatement( option.getType() );
                    }
                }
            }
        }

        if ( !argQueue.isEmpty() )
        {
            throw new CliException( "unknown option '" + argQueue.poll() + "'" );
        }

        return returnObj;
    }

    public static void main( final String[] args )
            throws Exception
    {
        out( PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " Command Line Utility" );
        mainOptions = MainOptions.parseMainCommandLineOptions( args, new OutputStreamWriter( System.out, PwmConstants.DEFAULT_CHARSET ) );
        final List<String> workingArgs = mainOptions.getRemainingArguments();

        final String commandStr = workingArgs == null || workingArgs.size() < 1 ? null : workingArgs.iterator().next();

        boolean commandExceuted = false;
        if ( commandStr == null )
        {
            out( "\n" );
            out( makeHelpTextOutput() );
        }
        else
        {
            for ( final CliCommand command : COMMANDS.values() )
            {
                if ( commandStr.equalsIgnoreCase( command.getCliParameters().commandName ) )
                {
                    commandExceuted = true;
                    executeCommand( command, commandStr, workingArgs.toArray( new String[0] ) );
                    break;
                }
            }
            if ( !commandExceuted )
            {
                out( "unknown command '" + workingArgs.get( 0 ) + "'" );
                out( "use 'help' for command list" );
            }
        }
    }

    private static void executeCommand(
            final CliCommand command,
            final String commandStr,
            final String[] args
    )
    {
        final List<String> argList = new ArrayList<>( Arrays.asList( args ) );
        argList.remove( 0 );

        final CliEnvironment cliEnvironment;
        try
        {
            cliEnvironment = createEnv( command.getCliParameters(), argList );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unable to establish operating environment: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_ENVIRONMENT_ERROR, errorMsg );
            LOGGER.error( SessionLabel.CLI_SESSION_LABEL, errorInformation::toDebugStr, e );
            out( "unable to establish operating environment: " + e.getMessage() );
            System.exit( -1 );
            return;
        }

        try
        {
            command.execute( commandStr, cliEnvironment );
        }
        catch ( final Exception e )
        {
            System.out.println( e.getMessage() );
            //System.exit(-1);
            return;
        }

        if ( cliEnvironment.getPwmApplication() != null )
        {
            try
            {
                cliEnvironment.getPwmApplication().shutdown();
            }
            catch ( final Exception e )
            {
                out( "error closing operating environment: " + e.getMessage() );
            }
        }
        if ( cliEnvironment.getLocalDB() != null )
        {
            try
            {
                cliEnvironment.getLocalDB().close();
            }
            catch ( final Exception e )
            {
                out( "error closing LocalDB environment: " + e.getMessage() );
            }
        }
    }

    private static LocalDB loadPwmDB(
            final AppConfig config,
            final boolean readonly,
            final Path applicationPath
    )
            throws Exception
    {
        final Path databaseDirectory;
        final String pwmDBLocationSetting = config.readAppProperty( AppProperty.LOCALDB_LOCATION );
        databaseDirectory = FileSystemUtility.figureFilepath( pwmDBLocationSetting, applicationPath );
        return LocalDBFactory.getInstance( databaseDirectory, readonly, null, config );
    }

    private static ConfigurationFileManager loadConfiguration( final Path configurationFile ) throws Exception
    {
        final ConfigurationFileManager reader = new ConfigurationFileManager( configurationFile, SessionLabel.CLI_SESSION_LABEL );

        if ( reader.getConfigMode() == PwmApplicationMode.ERROR )
        {
            final String errorMsg = reader.getConfigFileError() == null ? "error" : reader.getConfigFileError().toDebugStr();
            out( "unable to load configuration: " + errorMsg );
            System.exit( -1 );
        }

        return reader;
    }

    private static PwmApplication loadPwmApplication(
            final Path applicationPath,
            final AppConfig config,
            final Path configurationFile,
            final boolean readonly
    )
            throws PwmUnrecoverableException
    {
        final PwmApplicationMode mode = readonly ? PwmApplicationMode.READ_ONLY : PwmApplicationMode.RUNNING;
        System.setProperty(
                PwmConstants.PWM_APP_NAME.toLowerCase() + "." + EnvironmentProperty.CommandLineInstance.name(),
                Boolean.TRUE.toString() );

        final PwmEnvironment pwmEnvironment = PwmEnvironment.builder()
                .config( config )
                .applicationPath( applicationPath )
                .applicationMode( mode )
                .configurationFile( configurationFile )
                .build();

        final PwmApplication pwmApplication = PwmApplication.createPwmApplication( pwmEnvironment );
        final PwmApplicationMode runningMode = pwmApplication.getApplicationMode();

        if ( runningMode != mode )
        {
            out( "application is in non running state: " + runningMode );
        }

        return pwmApplication;
    }

    private static Path locateConfigurationFile( final Path applicationPath )
    {
        return applicationPath.resolve( PwmConstants.DEFAULT_CONFIG_FILE_FILENAME );
    }

    private static void out( final CharSequence txt )
    {
        System.out.println( txt );
    }

    private static Path figureApplicationPath( final MainOptions mainOptions ) throws PwmUnrecoverableException
    {
        final Path applicationPath;
        if ( mainOptions != null && mainOptions.getApplicationPath() != null )
        {
            applicationPath = mainOptions.getApplicationPath();
        }
        else
        {
            final Optional<Path> appPathStr = EnvironmentProperty.readApplicationPath( null );
            if ( appPathStr.isPresent() )
            {
                applicationPath = appPathStr.get();
            }
            else
            {
                final String errorMsg = "unable to locate applicationPath.  Specify using -applicationPath option, java option "
                        + "\"" + EnvironmentProperty.applicationPath.conicalJavaOptionSystemName( null ) + "\""
                        + ", or system environment setting "
                        + "\"" + EnvironmentProperty.applicationPath.conicalEnvironmentSystemName( null ) + "\"";
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg ) );
            }
        }

        LOGGER.debug( () -> "using applicationPath " + applicationPath );
        return applicationPath;
    }
}

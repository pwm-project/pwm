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

package password.pwm.util.cli;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.config.stored.ConfigurationReader;
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
import password.pwm.util.java.JavaHelper;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogManager;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

public class MainClass
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( MainClass.class );

    private static final String LOGGING_PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ssX}{GMT}, %-5p, %c{2}, %m%n";

    private static MainOptions mainOptions;

    public static final Map<String, CliCommand> COMMANDS;

    static
    {
        final List<CliCommand> commandList = new ArrayList<>();
        commandList.add( new LocalDBInfoCommand() );
        commandList.add( new ExportLogsCommand() );
        commandList.add( new UserReportCommand() );
        commandList.add( new ExportLocalDBCommand() );
        commandList.add( new ImportLocalDBCommand() );
        commandList.add( new ExportAuditCommand() );
        commandList.add( new ConfigUnlockCommand() );
        commandList.add( new ConfigLockCommand() );
        commandList.add( new ConfigSetPasswordCommand() );
        commandList.add( new ExportStatsCommand() );
        commandList.add( new ExportResponsesCommand() );
        commandList.add( new ClearResponsesCommand() );
        commandList.add( new ImportResponsesCommand() );
        commandList.add( new TokenInfoCommand() );
        commandList.add( new ConfigNewCommand() );
        commandList.add( new VersionCommand() );
        commandList.add( new LdapSchemaExtendCommand() );
        commandList.add( new ConfigDeleteCommand() );
        commandList.add( new ResponseStatsCommand() );
        commandList.add( new ImportHttpsKeyStoreCommand() );
        commandList.add( new ExportHttpsKeyStoreCommand() );
        commandList.add( new ExportHttpsTomcatConfigCommand() );
        commandList.add( new ShellCommand() );
        commandList.add( new ConfigResetHttpsCommand() );
        commandList.add( new HelpCommand() );
        commandList.add( new ImportPropertyConfigCommand() );
        commandList.add( new ResetInstanceIDCommand() );
        commandList.add( new ExportWordlistCommand() );

        final Map<String, CliCommand> sortedMap = new TreeMap<>();
        for ( final CliCommand command : commandList )
        {
            sortedMap.put( command.getCliParameters().commandName, command );
        }
        COMMANDS = Collections.unmodifiableMap( sortedMap );
    }

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
                    output.append( " " );
                    if ( option.isOptional() )
                    {
                        output.append( "<" ).append( option.getName() ).append( ">" );
                    }
                    else
                    {
                        output.append( "[" ).append( option.getName() ).append( "]" );
                    }
                }
            }
            output.append( "\n" );
            output.append( "       " ).append( command.getCliParameters().description );
            output.append( "\n" );
        }
        return output.toString();
    }

    private static String makeHelpTextOutput( )
    {
        final StringBuilder output = new StringBuilder();
        output.append( helpTextFromCommands( COMMANDS.values() ) );
        output.append( "\n" );
        output.append( "options:\n" );
        output.append( " -force                force operations skipping any confirmation\n" );
        output.append( " -debugLevel=x         set the debug level where x is TRACE, DEBUG, INFO, ERROR, WARN or FATAL\n" );
        output.append( " -applicationPath=x    set the application path, default is current path\n" );
        output.append( "\n" );
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
        final File applicationPath = figureApplicationPath( mainOptions );
        out( "applicationPath=" + applicationPath.getAbsolutePath() );
        PwmEnvironment.verifyApplicationPath( applicationPath );

        final File configurationFile = locateConfigurationFile( applicationPath );

        final ConfigurationReader configReader = loadConfiguration( configurationFile );
        final Configuration config = configReader.getConfiguration();

        final PwmApplication pwmApplication;
        final LocalDB localDB;

        if ( parameters.needsPwmApplication )
        {
            pwmApplication = loadPwmApplication( applicationPath, mainOptions.getApplicationFlags(), config, configurationFile, parameters.readOnly );
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

        out( "environment initialized" );
        out( "" );

        final Writer outputStream = new OutputStreamWriter( System.out, PwmConstants.DEFAULT_CHARSET );
        return CliEnvironment.builder()
                .configurationReader( configReader )
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
                                final File theFile = new File( argument );
                                if ( theFile.exists() )
                                {
                                    throw new CliException( "file for option '" + option.getName() + "' at '" + theFile.getAbsolutePath() + "' already exists" );
                                }
                                returnObj.put( option.getName(), theFile );
                            }
                            catch ( final Exception e )
                            {
                                if ( e instanceof CliException )
                                {
                                    throw ( CliException ) e;
                                }
                                throw new CliException( "cannot access file for option '" + option.getName() + "', " + e.getMessage() );

                            }
                            break;

                        case EXISTING_FILE:
                            try
                            {
                                final File theFile = new File( argument );
                                if ( !theFile.exists() )
                                {
                                    throw new CliException( "file for option '" + option.getName() + "' at '" + theFile.getAbsolutePath() + "' does not exist" );
                                }
                                returnObj.put( option.getName(), theFile );
                            }
                            catch ( final Exception e )
                            {
                                if ( e instanceof CliException )
                                {
                                    throw ( CliException ) e;
                                }
                                throw new CliException( "cannot access file for option '" + option.getName() + "', " + e.getMessage() );
                            }
                            break;

                        case STRING:
                            returnObj.put( option.getName(), argument );
                            break;

                        default:
                            JavaHelper.unhandledSwitchStatement( option.getType() );
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

        initLog4j( mainOptions.getPwmLogLevel() );

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
                    executeCommand( command, commandStr, workingArgs.toArray( new String[ workingArgs.size() ] ) );
                    break;
                }
            }
            if ( !commandExceuted )
            {
                out( "unknown command '" + workingArgs.iterator().next() + "'" );
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
        final List<String> argList = new LinkedList<>( Arrays.asList( args ) );
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
            LOGGER.error( () -> errorInformation.toDebugStr(), e );
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
                e.printStackTrace();
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

        //System.exit(0);
        return;

    }

    private static void initLog4j( final PwmLogLevel logLevel )
    {
        if ( logLevel == null )
        {
            PwmLogger.disableAllLogging();
            return;
        }

        final Layout patternLayout = new EnhancedPatternLayout( LOGGING_PATTERN );
        final ConsoleAppender consoleAppender = new ConsoleAppender( patternLayout );
        for ( final Package logPackage : PwmLogManager.LOGGING_PACKAGES )
        {
            if ( logPackage != null )
            {
                final Logger logger = Logger.getLogger( logPackage.getName() );
                logger.addAppender( consoleAppender );
                logger.setLevel( logLevel.getLog4jLevel() );
            }
        }
        PwmLogger.markInitialized();
    }

    private static LocalDB loadPwmDB(
            final Configuration config,
            final boolean readonly,
            final File applicationPath
    )
            throws Exception
    {
        final File databaseDirectory;
        final String pwmDBLocationSetting = config.readAppProperty( AppProperty.LOCALDB_LOCATION );
        databaseDirectory = FileSystemUtility.figureFilepath( pwmDBLocationSetting, applicationPath );
        return LocalDBFactory.getInstance( databaseDirectory, readonly, null, config );
    }

    private static ConfigurationReader loadConfiguration( final File configurationFile ) throws Exception
    {
        final ConfigurationReader reader = new ConfigurationReader( configurationFile );

        if ( reader.getConfigMode() == PwmApplicationMode.ERROR )
        {
            final String errorMsg = reader.getConfigFileError() == null ? "error" : reader.getConfigFileError().toDebugStr();
            out( "unable to load configuration: " + errorMsg );
            System.exit( -1 );
        }

        return reader;
    }

    private static PwmApplication loadPwmApplication(
            final File applicationPath,
            final Collection<PwmEnvironment.ApplicationFlag> flags,
            final Configuration config,
            final File configurationFile,
            final boolean readonly
    )
            throws LocalDBException, PwmUnrecoverableException
    {
        final PwmApplicationMode mode = readonly ? PwmApplicationMode.READ_ONLY : PwmApplicationMode.RUNNING;
        final Collection<PwmEnvironment.ApplicationFlag> applicationFlags = new HashSet<>();
        if ( flags == null )
        {
            applicationFlags.addAll( PwmEnvironment.ParseHelper.readApplicationFlagsFromSystem( null ) );
        }
        else
        {
            applicationFlags.addAll( flags );
        }
        applicationFlags.add( PwmEnvironment.ApplicationFlag.CommandLineInstance );
        final PwmEnvironment pwmEnvironment = new PwmEnvironment.Builder( config, applicationPath )
                .setApplicationMode( mode )
                .setConfigurationFile( configurationFile )
                .setFlags( applicationFlags )
                .createPwmEnvironment();
        final PwmApplication pwmApplication = PwmApplication.createPwmApplication( pwmEnvironment );
        final PwmApplicationMode runningMode = pwmApplication.getApplicationMode();

        if ( runningMode != mode )
        {
            out( "application is in non running state: " + runningMode );
        }

        return pwmApplication;
    }

    private static File locateConfigurationFile( final File applicationPath )
    {
        return new File( applicationPath + File.separator + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME );
    }

    private static void out( final CharSequence txt )
    {
        System.out.println( txt );
    }

    private static File figureApplicationPath( final MainOptions mainOptions ) throws IOException, PwmUnrecoverableException
    {
        final File applicationPath;
        if ( mainOptions != null && mainOptions.getApplicationPath() != null )
        {
            applicationPath = mainOptions.getApplicationPath();
        }
        else
        {
            final String appPathStr = PwmEnvironment.ParseHelper.readValueFromSystem( PwmEnvironment.EnvironmentParameter.applicationPath, null );
            if ( appPathStr != null && !appPathStr.isEmpty() )
            {
                applicationPath = new File( appPathStr );
            }
            else
            {
                final String errorMsg = "unable to locate applicationPath.  Specify using -applicationPath option, java option "
                        + "\"" + PwmEnvironment.EnvironmentParameter.applicationPath.conicalJavaOptionSystemName() + "\""
                        + ", or system environment setting "
                        + "\"" + PwmEnvironment.EnvironmentParameter.applicationPath.conicalEnvironmentSystemName() + "\"";
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg ) );
            }
        }

        LOGGER.debug( () -> "using applicationPath " + applicationPath.getAbsolutePath() );
        return applicationPath;
    }
}

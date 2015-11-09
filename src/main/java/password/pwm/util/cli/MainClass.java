/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.util.cli;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.varia.NullAppender;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.FileSystemUtility;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogManager;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

public class MainClass {
    private static final PwmLogger LOGGER = PwmLogger.forClass(MainClass.class);

    private static final String LOGGING_PATTERN = "%d{yyyy-MM-dd HH:mm:ss}, %-5p, %c{2}, %m%n";

    private static MainOptions MAIN_OPTIONS = new MainOptions();

    public static final Map<String,CliCommand> COMMANDS;
    static {
        final List<CliCommand> commandList = new ArrayList<>();
        commandList.add(new LocalDBInfoCommand());
        commandList.add(new ExportLogsCommand());
        commandList.add(new UserReportCommand());
        commandList.add(new ExportLocalDBCommand());
        commandList.add(new ImportLocalDBCommand());
        commandList.add(new ExportAuditCommand());
        commandList.add(new ConfigUnlockCommand());
        commandList.add(new ConfigLockCommand());
        commandList.add(new ConfigSetPasswordCommand());
        commandList.add(new ExportStatsCommand());
        commandList.add(new ExportResponsesCommand());
        commandList.add(new ClearResponsesCommand());
        commandList.add(new ImportResponsesCommand());
        commandList.add(new TokenInfoCommand());
        commandList.add(new ConfigNewCommand());
        commandList.add(new VersionCommand());
        commandList.add(new LdapSchemaExtendCommand());
        commandList.add(new ConfigDeleteCommand());
        commandList.add(new ResponseStatsCommand());
        commandList.add(new ExportHttpsKeyStoreCommand());
        commandList.add(new ExportHttpsTomcatConfigCommand());
        commandList.add(new ShellCommand());
        commandList.add(new ConfigResetHttpsCommand());

        final Map<String,CliCommand> sortedMap = new TreeMap<>();
        for (CliCommand command : commandList) {
            sortedMap.put(command.getCliParameters().commandName,command);
        }
        COMMANDS = Collections.unmodifiableMap(sortedMap);
    }

    public static String helpTextFromCommands(Collection<CliCommand> commands) {
        final StringBuilder output = new StringBuilder();
        for (CliCommand command : commands) {
            output.append(command.getCliParameters().commandName);
            if (command.getCliParameters().options != null) {
                for (CliParameters.Option option : command.getCliParameters().options) {
                    output.append(" ");
                    if (option.isOptional()) {
                        output.append("<").append(option.getName()).append(">");
                    } else {
                        output.append("[").append(option.getName()).append("]");
                    }
                }
            }
            output.append("\n");
            output.append("       ").append(command.getCliParameters().description);
            output.append("\n");
        }
        return output.toString();
    }

    public static String makeHelpTextOutput() {
        final StringBuilder output = new StringBuilder();
        output.append(helpTextFromCommands(COMMANDS.values()));
        output.append("\n");
        output.append("options:\n");
        output.append(" -force                force operations skipping any confirmation\n");
        output.append(" -debugLevel=x         set the debug level where x is TRACE, DEBUG, INFO, WARN or FATAL\n");
        output.append(" -applicationPath=x    set the application path, default is current path\n");
        output.append("\n");
        output.append("usage: \n");
        output.append(" command[.bat/.sh] <options> CommandName <command options>");

        return output.toString();
    }

    private static CliEnvironment createEnv(
            CliParameters parameters,
            final List<String> args
    )
            throws Exception
    {

        final Map<String,Object> options = parseCommandOptions(parameters, args);
        final File applicationPath = figureApplicationPath(MAIN_OPTIONS);
        out("applicationPath=" + applicationPath.getAbsolutePath());
        PwmEnvironment.verifyApplicationPath(applicationPath);

        final File configurationFile = locateConfigurationFile(applicationPath);

        final ConfigurationReader configReader = loadConfiguration(configurationFile);
        final Configuration config = configReader.getConfiguration();

        final PwmApplication pwmApplication;
        final LocalDB localDB;

        if (parameters.needsPwmApplication) {
            pwmApplication = loadPwmApplication(applicationPath, MAIN_OPTIONS.applicationFlags, config, configurationFile, parameters.readOnly);
            localDB = pwmApplication.getLocalDB();
        } else if (parameters.needsLocalDB) {
            pwmApplication = null;
            localDB = loadPwmDB(config, parameters.readOnly, applicationPath);
        } else {
            pwmApplication = null;
            localDB = null;
        }


        return new CliEnvironment(
                configReader,
                configurationFile,
                config,
                applicationPath,
                pwmApplication,
                localDB,
                new OutputStreamWriter(System.out),
                options,
                MAIN_OPTIONS
        );
    }

    static Map<String,Object> parseCommandOptions(
            final CliParameters cliParameters,
            final List<String> args
    )
            throws CliException
    {
        final Queue<String> argQueue = new LinkedList<>(args);
        final Map<String,Object> returnObj = new LinkedHashMap<>();

        if (cliParameters.options != null) {
            for (CliParameters.Option option : cliParameters.options) {
                if (!option.isOptional() && argQueue.isEmpty()) {
                    throw new CliException("missing required option '" + option.getName() + "'");
                }

                if (!argQueue.isEmpty()) {
                    final String argument = argQueue.poll();
                    switch (option.getType()) {
                        case NEW_FILE:
                            try {
                                File theFile = new File(argument);
                                if (theFile.exists()) {
                                    throw new CliException("file for option '" + option.getName() + "' at '" + theFile.getAbsolutePath() + "' already exists");
                                }
                                returnObj.put(option.getName(),theFile);
                            } catch (Exception e) {
                                if (e instanceof CliException) {
                                    throw (CliException)e;
                                }
                                throw new CliException("cannot access file for option '" + option.getName() + "', " + e.getMessage());

                            }
                            break;

                        case EXISTING_FILE:
                            try {
                                File theFile = new File(argument);
                                if (!theFile.exists()) {
                                    throw new CliException("file for option '" + option.getName() + "' at '" + theFile.getAbsolutePath() + "' does not exist");
                                }
                                returnObj.put(option.getName(),theFile);
                            } catch (Exception e) {
                                if (e instanceof CliException) {
                                    throw (CliException)e;
                                }
                                throw new CliException("cannot access file for option '" + option.getName() + "', " + e.getMessage());
                            }
                            break;

                        case STRING:
                            returnObj.put(option.getName(), argument);
                            break;
                    }
                }
            }
        }

        if (!argQueue.isEmpty()) {
            throw new CliException("unknown option '" + argQueue.poll() + "'");
        }

        return returnObj;
    }

    public static void main(String[] args)
            throws Exception
    {
        System.out.println(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " Command Line Utility");
        args = parseMainCommandLineOptions(args);

        initLog4j(MAIN_OPTIONS.pwmLogLevel);

        final String commandStr = args == null || args.length < 1 ? null : args[0];

        if (commandStr == null) {
            System.out.println("");
            System.out.println(makeHelpTextOutput());
        } else {
            for (CliCommand command : COMMANDS.values()) {
                if (commandStr.equalsIgnoreCase(command.getCliParameters().commandName)) {

                    final List<String> argList = new LinkedList<>(Arrays.asList(args));
                    argList.remove(0);

                    final CliEnvironment cliEnvironment;
                    try {
                        cliEnvironment = createEnv(command.getCliParameters(), argList);
                    } catch (Exception e) {
                        System.out.println("unable to establish operating environment: " + e.getMessage());
                        System.exit(-1);
                        return;
                    }

                    try {
                        command.execute(commandStr, cliEnvironment);
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        //System.exit(-1);
                        return;
                    }

                    if (cliEnvironment.getPwmApplication() != null) {
                        try {
                            cliEnvironment.getPwmApplication().shutdown();
                        } catch (Exception e) {
                            System.out.println("error closing operating environment: " + e.getMessage());
                        }
                    }
                    if (cliEnvironment.getLocalDB() != null) {
                        try {
                            cliEnvironment.getLocalDB().close();
                        } catch (Exception e) {
                            System.out.println("error closing LocalDB environment: " + e.getMessage());
                        }
                    }

                    System.exit(0);
                    return;
                }
            }
            out("unknown command '" + args[0] + "'");
        }
    }

    static String[] parseMainCommandLineOptions(String[] args) {
        final String OPT_DEBUG_LEVEL = "-debugLevel";
        final String OPT_APP_PATH = "-applicationPath";
        final String OPT_APP_FLAGS= "-applicationFlags";
        final String OPT_FORCE = "-force";

        if (args == null || args.length < 1) {
            return args;
        }

        final List<String> outputArgs = new ArrayList<>();
        for (final String arg : args) {
            if (arg != null) {
                if (arg.startsWith(OPT_DEBUG_LEVEL)) {
                    if (arg.length() < OPT_DEBUG_LEVEL.length() + 2) {
                        out(OPT_DEBUG_LEVEL + " option must include level (example: -" + OPT_DEBUG_LEVEL + "=TRACE");
                        System.exit(-1);
                    } else {
                        final String levelStr = arg.substring(OPT_DEBUG_LEVEL.length() + 1, arg.length());
                        final PwmLogLevel pwmLogLevel;
                        try {
                            pwmLogLevel = PwmLogLevel.valueOf(levelStr.toUpperCase());
                            MAIN_OPTIONS.pwmLogLevel = pwmLogLevel;
                        } catch (IllegalArgumentException e) {
                            out(" unknown log level value: " + levelStr);
                            System.exit(-1);
                        }
                    }
                } else  if (arg.startsWith(OPT_APP_PATH)) {
                    if (arg.length() < OPT_APP_PATH.length() + 2) {
                        out(OPT_APP_PATH + " option must include value (example: -" + OPT_APP_PATH + "=/tmp/applicationPath");
                        System.exit(-1);
                    } else {
                        final String pathStr = arg.substring(OPT_APP_PATH.length() + 1, arg.length());
                        MAIN_OPTIONS.applicationPath = new File(pathStr);
                    }
                } else if (arg.equals(OPT_FORCE)) {
                    MAIN_OPTIONS.forceFlag = true;
                } else if (arg.startsWith(OPT_APP_FLAGS)){
                    if (arg.length() < OPT_APP_FLAGS.length() + 2) {
                        out(OPT_APP_FLAGS + " option must include value (example: -" + OPT_APP_FLAGS + "=Flag1,Flag2");
                        System.exit(-1);
                    } else {
                        final String flagStr = arg.substring(OPT_APP_PATH.length() + 1, arg.length());
                        MAIN_OPTIONS.applicationFlags = PwmEnvironment.ParseHelper.parseApplicationFlagValueParameter(flagStr);
                    }
                    outputArgs.add(arg);
                } else {
                    outputArgs.add(arg);
                }
            }
        }
        return outputArgs.toArray(new String[outputArgs.size()]);
    }

    static void initLog4j(PwmLogLevel logLevel) {
        if (logLevel == null) {
            Logger.getRootLogger().removeAllAppenders();
            Logger.getRootLogger().addAppender(new NullAppender());
            PwmLogger.markInitialized();
            return;
        }

        final Layout patternLayout = new PatternLayout(LOGGING_PATTERN);
        final ConsoleAppender consoleAppender = new ConsoleAppender(patternLayout);
        for (final Package logPackage : PwmLogManager.LOGGING_PACKAGES) {
            if (logPackage != null) {
                final Logger logger = Logger.getLogger(logPackage.getName());
                logger.addAppender(consoleAppender);
                logger.setLevel(logLevel.getLog4jLevel());
            }
        }
        PwmLogger.markInitialized();
    }

    static LocalDB loadPwmDB(
            final Configuration config,
            final boolean readonly,
            final File applicationPath
    )
            throws Exception
    {
        final File databaseDirectory;
        final String pwmDBLocationSetting = config.readSettingAsString(PwmSetting.PWMDB_LOCATION);
        databaseDirectory = FileSystemUtility.figureFilepath(pwmDBLocationSetting, applicationPath);
        return LocalDBFactory.getInstance(databaseDirectory, readonly, null, config);
    }

    static ConfigurationReader loadConfiguration(final File configurationFile) throws Exception {
        final ConfigurationReader reader = new ConfigurationReader(configurationFile);

        if (reader.getConfigMode() == PwmApplication.MODE.ERROR) {
            final String errorMsg = reader.getConfigFileError() == null ? "error" : reader.getConfigFileError().toDebugStr();
            out("unable to load configuration: " + errorMsg);
            System.exit(-1);
        }

        return reader;
    }

    static PwmApplication loadPwmApplication(
            final File applicationPath,
            final Collection<PwmEnvironment.ApplicationFlag> flags,
            final Configuration config,
            final File configurationFile,
            final boolean readonly
    )
            throws LocalDBException, PwmUnrecoverableException
    {
        final PwmApplication.MODE mode = readonly ? PwmApplication.MODE.READ_ONLY : PwmApplication.MODE.RUNNING;
        final Collection<PwmEnvironment.ApplicationFlag> applicationFlags = flags == null
                ? PwmEnvironment.ParseHelper.readApplicationFlagsFromSystem(null)
                : flags;
        applicationFlags.add(PwmEnvironment.ApplicationFlag.CommandLineInstance);
        final PwmEnvironment pwmEnvironment = new PwmEnvironment.Builder(config, applicationPath)
                .setApplicationMode(mode)
                .setConfigurationFile(configurationFile)
                .setFlags(applicationFlags)
                .createPwmEnvironment();
        final PwmApplication pwmApplication = new PwmApplication(pwmEnvironment);
        final PwmApplication.MODE runningMode = pwmApplication.getApplicationMode();

        if (runningMode != mode) {
            out("unable to start application in required state '" + mode + "', current state: " + runningMode);
            System.exit(-1);
        }

        return pwmApplication;
    }

    static File locateConfigurationFile(File applicationPath) {
        return new File(applicationPath + File.separator + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME);
    }

    private static void out(CharSequence txt) {
        System.out.println(txt + "\n");
    }

    public static class MainOptions {
        private PwmLogLevel pwmLogLevel = null;
        private File applicationPath = null;
        private boolean forceFlag = false;
        private Collection<PwmEnvironment.ApplicationFlag> applicationFlags;

        public PwmLogLevel getPwmLogLevel() {
            return pwmLogLevel;
        }

        public File getApplicationPath() {
            return applicationPath;
        }

        public boolean isForceFlag() {
            return forceFlag;
        }
    }

    private static File figureApplicationPath(final MainOptions mainOptions) throws IOException, PwmUnrecoverableException {
        final File applicationPath;
        if (mainOptions != null && mainOptions.applicationPath != null) {
            applicationPath = mainOptions.applicationPath;
        } else {
            final String appPathStr = PwmEnvironment.ParseHelper.readValueFromSystem(PwmEnvironment.EnvironmentParameter.applicationPath,null);
            if (appPathStr != null && !appPathStr.isEmpty()) {
                applicationPath = new File(appPathStr);
            } else {
                final String errorMsg = "unable to locate applicationPath.  Specify using -applicationPath option, java option "
                        + "\"" + PwmEnvironment.EnvironmentParameter.applicationPath.conicalJavaOptionSystemName() + "\""
                        + ", or system environment setting "
                        + "\"" + PwmEnvironment.EnvironmentParameter.applicationPath.conicalEnvironmentSystemName() + "\"";
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_STARTUP_ERROR,errorMsg));
            }
        }

        LOGGER.debug("using applicationPath " + applicationPath.getAbsolutePath());
        return applicationPath;
    }
}
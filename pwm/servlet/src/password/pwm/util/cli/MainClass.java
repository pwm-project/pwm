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

package password.pwm.util.cli;

import com.novell.ldapchai.ChaiUser;
import org.apache.log4j.*;
import org.apache.log4j.varia.NullAppender;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.PasswordValue;
import password.pwm.util.Helper;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.*;

public class MainClass {

    public static final Map<String,CliCommand> COMMANDS;
    static {
        final List<CliCommand> commandList = new ArrayList<CliCommand>();
        commandList.add(new LocalDBInfoCommand());
        commandList.add(new ExportLogsCommand());
        commandList.add(new UserReportCommand());
        commandList.add(new ExportLocalDBCommand());
        commandList.add(new ImportLocalDBCommand());
        commandList.add(new ExportAuditCommand());
        commandList.add(new UnlockConfigCommand());
        commandList.add(new SetConfigPasswordCommand());
        commandList.add(new ExportStatsCommand());
        commandList.add(new ExportResponsesCommand());
        commandList.add(new ClearResponsesCommand());
        commandList.add(new ImportResponsesCommand());
        commandList.add(new TokenInfoCommand());
        commandList.add(new IntegrityReportCommand());

        final Map<String,CliCommand> sortedMap = new TreeMap<String,CliCommand>();
        for (CliCommand command : commandList) {
            sortedMap.put(command.getCliParameters().commandName,command);
        }
        COMMANDS = Collections.unmodifiableMap(sortedMap);
    }

    private static String makeHelpTextOutput() {
        final StringBuilder output = new StringBuilder();
        for (CliCommand command : COMMANDS.values()) {
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

    private static CliEnvironment createEnv(
            CliParameters parameters,
            final List<String> args
    )
            throws Exception
    {
        final Map<String,Object> options = parseCommandOptions(parameters, args);
        final File workingFolder = new File(".").getCanonicalFile();
        final Configuration config = loadConfiguration();
        final PwmApplication pwmApplication = parameters.needsPwmApplication
                ? loadPwmApplication(config,workingFolder,parameters.readOnly)
                : null;
        final LocalDB localDB = parameters.needsLocalDB
                ? pwmApplication == null
                ? loadPwmDB(config, parameters.readOnly)
                : pwmApplication.getLocalDB()
                : null;

        return new CliEnvironment(
                config,
                pwmApplication,
                localDB,
                new OutputStreamWriter(System.out),
                options
        );
    }

    private static Map<String,Object> parseCommandOptions(
            final CliParameters cliParameters,
            final List<String> args
    )
            throws CliException
    {
        final Queue<String> argQueue = new LinkedList<String>(args);
        final Map<String,Object> returnObj = new LinkedHashMap<String, Object>();

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

    public static void main(final String[] args)
            throws Exception
    {
        initLog4j(false);

        final String commandStr = args == null || args.length < 1 ? null : args[0];

        if (commandStr == null) {
            System.out.println(PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " Command Line Utility");
            System.out.println("");
            System.out.println(makeHelpTextOutput());
        } else {
            for (CliCommand command : COMMANDS.values()) {
                if (commandStr.equalsIgnoreCase(command.getCliParameters().commandName)) {

                    final List<String> argList = new LinkedList<String>(Arrays.asList(args));
                    argList.remove(0);

                    CliEnvironment cliEnvironment;
                    try {
                        cliEnvironment = createEnv(command.getCliParameters(), argList);
                        command.execute(commandStr, cliEnvironment);
                    } catch (CliException e) {
                        System.out.println(e.getMessage());
                        System.exit(-1);
                    }

                    System.exit(0);
                    return;
                }
            }
            out("unknown command '" + args[0] + "'");
        }
    }


    static void initLog4j(boolean show) {
        if (!show) {
            Logger.getRootLogger().removeAllAppenders();
            Logger.getRootLogger().addAppender(new NullAppender());
            return;
        }


        // clear all existing package loggers
        final String pwmPackageName = PwmApplication.class.getPackage().getName();
        final Logger pwmPackageLogger = Logger.getLogger(pwmPackageName);
        final String chaiPackageName = ChaiUser.class.getPackage().getName();
        final Logger chaiPackageLogger = Logger.getLogger(chaiPackageName);
        final Layout patternLayout = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss}, %-5p, %c{2}, %m%n");
        final ConsoleAppender consoleAppender = new ConsoleAppender(patternLayout);
        final Level level = Level.toLevel(Level.INFO_INT);
        pwmPackageLogger.addAppender(consoleAppender);
        pwmPackageLogger.setLevel(level);
        chaiPackageLogger.addAppender(consoleAppender);
        chaiPackageLogger.setLevel(level);
    }

    static LocalDB loadPwmDB(final Configuration config, final boolean readonly) throws Exception {
        final File databaseDirectory;
        final String pwmDBLocationSetting = config.readSettingAsString(PwmSetting.PWMDB_LOCATION);
        databaseDirectory = Helper.figureFilepath(pwmDBLocationSetting, new File("."));
        return LocalDBFactory.getInstance(databaseDirectory, readonly, null, config);
    }

    static Configuration loadConfiguration() throws Exception {
        return (new ConfigurationReader(new File(PwmConstants.CONFIG_FILE_FILENAME))).getConfiguration();
    }

    static PwmApplication loadPwmApplication(final Configuration config, final File workingDirectory, final boolean readonly)
            throws LocalDBException
    {
        final PwmApplication.MODE mode = readonly ? PwmApplication.MODE.READ_ONLY : PwmApplication.MODE.RUNNING;
        return new PwmApplication(config, mode, workingDirectory, false);
    }

    static void handleEncryptConfigPassword(final String[] args) throws Exception {
        if (args.length < 2) {
            out("must specify a password value");
            System.exit(-1);
        }

        if (args.length < 3) {
            out("must specify file to write password value to");
            System.exit(-1);
        }

        final File outputFile = new File(args[2]);
        if (outputFile.exists()) {
            out("outputFile '" + outputFile.getAbsolutePath() + "' already exists");
            System.exit(-1);
        }

        final String input = args[1];
        final ConfigurationReader configurationReader = new ConfigurationReader(new File(PwmConstants.CONFIG_FILE_FILENAME));
        final String key = configurationReader.getStoredConfiguration().getKey();
        final String output = PasswordValue.encryptValue(key,input);
        final FileWriter writer = new FileWriter(outputFile,true);
        writer.write(output);
        writer.close();
    }

    private static void out(CharSequence txt) {
        System.out.println(txt + "\n");
    }

}
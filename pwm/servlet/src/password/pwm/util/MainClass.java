/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm.util;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiResponseSet;
import com.novell.ldapchai.provider.ChaiProvider;
import org.apache.log4j.*;
import password.pwm.ContextManager;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBFactory;
import password.pwm.util.pwmdb.PwmDBStoredQueue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;

public class MainClass {

    private static final String RESPONSE_FILE_ENCODING = "UTF-8";

    public static void main(final String[] args)
            throws Exception {
        initLog4j();
        out("PWM Command Line - " + PwmConstants.PWM_VERSION + " b" + PwmConstants.BUILD_NUMBER);
        if (args == null || args.length < 1) {
            out("");
            out(" [command] option option");
            out("  | PwmDbInfo                     Report information about the PwmDB");
            out("  | ExportLogs      [outputFile]  Export all logs in the PwmDB");
            out("  | ExportResponses [location]    Export all saved responses in the PwmDB");
            out("  | ImportResponses [location]    Import responses from files into the PwmDB");
            out("  | ClearResponses                Clear all responses from the PwmDB");
            out("  | UserReport      [outputFile]  Dump a user report to the output file (csv format)");
        } else {
            if ("PwmDbInfo".equalsIgnoreCase(args[0])) {
                handlePwmDbInfo();
            } else if ("ExportLogs".equalsIgnoreCase(args[0])) {
                handleExportLogs(args);
            } else if ("ExportResponses".equalsIgnoreCase(args[0])) {
                handleExportResponses(args);
            } else if ("ImportResponses".equalsIgnoreCase(args[0])) {
                handleImportResponses(args);
            } else if ("ClearResponses".equalsIgnoreCase(args[0])) {
                handleClearResponses();
            } else if ("UserReport".equalsIgnoreCase(args[0])) {
                handleUserReport();
            } else {
                out("unknown command");
            }
        }
    }

    static void handleUserReport() throws Exception {
        final Configuration config = loadConfiguration();
        final ChaiProvider provider;
        {
            final String proxyDN = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
            final String proxyPW = config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD);
            provider = Helper.createChaiProvider(config,proxyDN,proxyPW);
        }
        final UserReport userReport = new UserReport(config,provider);


        //@todo output to file..
        System.out.println("log file output:");

        for (final Iterator<UserReport.UserInformation> resultIterator = userReport.resultIterator(); resultIterator.hasNext(); ) {
            final UserReport.UserInformation userInformation = resultIterator.next();
            System.out.println(userInformation.toCsvLine());
        }

        System.out.println("report complete.");
    }

    static void handlePwmDbInfo() throws Exception {
        final Configuration config = loadConfiguration();
        final PwmDB pwmDB = loadPwmDB(config, true);
        final long pwmDBdiskSpace = Helper.getFileDirectorySize(pwmDB.getFileLocation());
        out("PwmDB Total Disk Space = " + pwmDBdiskSpace + " (" + Helper.formatDiskSize(pwmDBdiskSpace) + ")");
        out("Checking row counts, this may take a moment.... ");
        for (final PwmDB.DB db : PwmDB.DB.values()) {
            out("  " + db.toString() + "=" + pwmDB.size(db));
        }
    }

    static void handleExportLogs(final String[] args) throws Exception {
        final Configuration config = loadConfiguration();
        final PwmDB pwmDB = loadPwmDB(config, true);
        final PwmDBStoredQueue logQueue = PwmDBStoredQueue.createPwmDBStoredQueue(pwmDB, PwmDB.DB.EVENTLOG_EVENTS);

        if (args.length < 2) {
            out("must specify file to write log data to");
            return;
        }

        if (logQueue.isEmpty()) {
            out("no logs present");
            return;
        }

        final File outputFile = new File(args[1]);
        out("outputting " + logQueue.size() + " log events to " + outputFile.getAbsolutePath() + "....");

        Writer outputWriter = null;
        try {
            outputWriter = new OutputStreamWriter(new FileOutputStream(outputFile));
            for (final Iterator<String> iter = logQueue.descendingIterator(); iter.hasNext();) {
                final String loopString = iter.next();
                final PwmLogEvent logEvent = PwmLogEvent.fromEncodedString(loopString);
                if (logEvent != null) {
                    outputWriter.write(logEvent.toLogString(false));
                    outputWriter.write("\n");
                }
            }
        } finally {
            if (outputWriter != null) {
                outputWriter.close();
            }
        }

        out("output complete");
    }

    static void handleExportResponses(final String[] args) throws Exception {
        final Configuration config = loadConfiguration();
        final PwmDB pwmDB = loadPwmDB(config, true);

        if (args.length < 2) {
            out("must specify a directory to write responses to");
            return;
        }

        if (pwmDB.size(PwmDB.DB.RESPONSE_STORAGE) == 0) {
            out("no stored responses");
            return;
        }

        final File outputDirectory = new File(args[1]);

        if (!outputDirectory.isDirectory()) {
            out(outputDirectory.getAbsolutePath() + " is not a valid directory");
            return;
        }

        out("outputting " + pwmDB.size(PwmDB.DB.RESPONSE_STORAGE) + " stored responses to " + outputDirectory.getAbsolutePath() + "....");

        for (final Iterator<String> iter = pwmDB.iterator(PwmDB.DB.RESPONSE_STORAGE); iter.hasNext();) {
            final String key = iter.next();
            final String value = pwmDB.get(PwmDB.DB.RESPONSE_STORAGE, key);
            final File outputFile = new File(outputDirectory.getAbsolutePath() + File.separator + key + ".xml");

            Helper.writeFileAsString(outputFile, value, RESPONSE_FILE_ENCODING);
        }

        out("output complete");
    }

    static void handleImportResponses(final String[] args) throws Exception {
        final Configuration config = loadConfiguration();
        final PwmDB pwmDB = loadPwmDB(config, false);

        if (args.length < 2) {
            out("must specify a directory to read responses from");
            return;
        }

        final File outputDirectory = new File(args[1]);

        if (!outputDirectory.isDirectory()) {
            out(outputDirectory.getAbsolutePath() + " is not a valid directory");
            return;
        }

        out("importing stored responses from " + outputDirectory.getAbsolutePath() + "....");

        int counter = 0;
        for (final File loopFile : outputDirectory.listFiles()) {
            final String fileContents = Helper.readFileAsString(loopFile, PwmDB.MAX_VALUE_LENGTH, RESPONSE_FILE_ENCODING);
            boolean validResponses = false;

            try {
                ChaiResponseSet.parseChaiResponseSetXML(fileContents, null);
                validResponses = true;
            } catch (Exception e) {
                out("file " + loopFile.getAbsolutePath() + " has invalid responses: " + e.getMessage());
            }

            if (validResponses) {
                String keyName = loopFile.getName();
                if (keyName.contains(".")) {
                    keyName = keyName.substring(0, keyName.indexOf("."));
                }

                if (pwmDB.contains(PwmDB.DB.RESPONSE_STORAGE, keyName)) {
                    out("updating value for PwmGUID " + keyName);
                } else {
                    out("importing value for PwmGUID " + keyName);
                }
                pwmDB.put(PwmDB.DB.RESPONSE_STORAGE, keyName, fileContents);

                counter++;
            }
        }

        out("finished import of " + counter + " stored responses");
    }

    static void handleClearResponses() throws Exception {
        final Configuration config = loadConfiguration();

        out("Proceeding with this operation will clear all stored responses from the PwmDB.");
        out("Please consider exporting the responses before proceeding. ");
        out("");
        out("To proceed, type 'continue'");
        final Scanner scanner = new Scanner(System.in);
        final String input = scanner.nextLine();

        if (!"continue".equalsIgnoreCase(input)) {
            out("exiting...");
            return;
        }

        final PwmDB pwmDB = loadPwmDB(config, false);

        if (pwmDB.size(PwmDB.DB.RESPONSE_STORAGE) == 0) {
            out("The PwmDB response database is already empty");
            return;
        }

        out("clearing " + pwmDB.size(PwmDB.DB.RESPONSE_STORAGE) + " responses");
        pwmDB.truncate(PwmDB.DB.RESPONSE_STORAGE);
        out("all saved responses are now removed from PwmDB");
    }

    static void out(final CharSequence out) {
        System.out.println(out);
    }

    static PwmDB loadPwmDB(final Configuration config, final boolean readonly) throws Exception {
        final File databaseDirectory;
        final String pwmDBLocationSetting = config.readSettingAsString(PwmSetting.PWMDB_LOCATION);
        databaseDirectory = Helper.figureFilepath(pwmDBLocationSetting, "WEB-INF", new File(".").toString());

        final String classname = config.readSettingAsString(PwmSetting.PWMDB_IMPLEMENTATION);
        final List<String> initStrings = config.readSettingAsStringArray(PwmSetting.PWMDB_INIT_STRING);
        final Map<String, String> initParamers = Configuration.convertStringListToNameValuePair(initStrings, "=");
        return PwmDBFactory.getInstance(databaseDirectory, classname, initParamers, readonly);
    }

    static Configuration loadConfiguration() throws Exception {
        return (new ConfigurationReader(new File("PwmConfiguration.xml"))).getConfiguration();
    }

    static void initLog4j() {
        // clear all existing package loggers
        final String pwmPackageName = ContextManager.class.getPackage().getName();
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
}


/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import org.apache.log4j.*;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmPasswordPolicy;
import password.pwm.TokenManager;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmOperationalException;
import password.pwm.event.AuditManager;
import password.pwm.util.operations.UserSearchEngine;
import password.pwm.util.localdb.*;
import password.pwm.util.stats.StatisticsManager;
import password.pwm.ws.server.rest.RestChallengesServer;

import java.io.*;
import java.util.*;

public class MainClass {

    public static void main(final String[] args)
            throws Exception {
        initLog4j();
        out(PwmConstants.PWM_APP_NAME + " Command Line - v" + PwmConstants.PWM_VERSION + " b" + PwmConstants.BUILD_NUMBER);
        if (args == null || args.length < 1) {
            out("");
            out(" [command] option option");
            out("  | LocalDbInfo                   Report information about the LocalDB");
            out("  | ExportLogs      [outputFile]  Export all logs in the LocalDB");
            out("  | ExportResponses [location]    Export all saved responses");
            out("  | ImportResponses [location]    Import responses from files");
            out("  | ClearLocalResponses           Clear all responses from the LocalDB");
            out("  | UserReport      [outputFile]  Dump a user report to the output file (csv format)");
            out("  | ExportLocalDB   [outputFile]  Export the entire LocalDB contents to a backup file");
            out("  | ImportLocalDB   [inputFile]   Import the entire LocalDB contents from a backup file");
            out("  | TokenInfo       [tokenKey]    Get information about a PWM issued token");
            out("  | ExportStats     [outputFile]  Dump all statistics in the LocalDB to a csv file");
            out("  | ExportAudit     [outputFile]  Dump all audit records in the LocalDB to a csv file");
            out("");
        } else {
            if ("LocalDbInfo".equalsIgnoreCase(args[0])) {
                handlePwmDbInfo();
            } else if ("ExportLogs".equalsIgnoreCase(args[0])) {
                handleExportLogs(args);
            } else if ("ExportResponses".equalsIgnoreCase(args[0])) {
                handleExportResponses(args);
            } else if ("ImportResponses".equalsIgnoreCase(args[0])) {
                handleImportResponses(args);
            } else if ("ClearLocalResponses".equalsIgnoreCase(args[0])) {
                handleClearLocalResponses();
            } else if ("UserReport".equalsIgnoreCase(args[0])) {
                handleUserReport(args);
            } else if ("ExportLocalDB".equalsIgnoreCase(args[0])) {
                handleExportLocalDB(args);
            } else if ("ImportLocalDB".equalsIgnoreCase(args[0])) {
                handleImportLocalDB(args);
            } else if ("TokenInfo".equalsIgnoreCase(args[0])) {
                handleTokenKey(args);
            } else if ("ExportStats".equalsIgnoreCase(args[0])) {
                handleExportStats(args);
            } else if ("ExportAudit".equalsIgnoreCase(args[0])) {
                handleExportAudit(args);
            } else {
                out("unknown command '" + args[0] + "'");
            }
        }
    }

    static void handleUserReport(final String[] args) throws Exception {
        if (args.length < 2) {
            out("output filename required");
            System.exit(-1);
        }

        final OutputStream outputFileStream;
        try {
            final File outputFile = new File(args[1]).getCanonicalFile();
            outputFileStream = new BufferedOutputStream(new FileOutputStream(outputFile));
        } catch (Exception e) {
            out("unable to open file '" + args[1] + "' for writing");
            System.exit(-1);
            throw new Exception();
        }

        final Configuration config = loadConfiguration();
        final File workingFolder = new File(".").getCanonicalFile();
        final PwmApplication pwmApplication = loadPwmApplication(config, workingFolder, true);

        final UserReport userReport = new UserReport(pwmApplication);
        userReport.outputToCsv(outputFileStream,true,50*1000);

        try { outputFileStream.close(); } catch (Exception e) { /* nothing */ }
        out("report complete.");
    }

    static void handlePwmDbInfo() throws Exception {
        final Configuration config = loadConfiguration();
        final LocalDB pwmDB = loadPwmDB(config, true);
        final long pwmDBdiskSpace = Helper.getFileDirectorySize(pwmDB.getFileLocation());
        out("LocalDB Total Disk Space = " + pwmDBdiskSpace + " (" + Helper.formatDiskSize(pwmDBdiskSpace) + ")");
        out("Checking row counts, this may take a moment.... ");
        for (final LocalDB.DB db : LocalDB.DB.values()) {
            out("  " + db.toString() + "=" + pwmDB.size(db));
        }
    }

    static void handleExportLogs(final String[] args) throws Exception {
        final Configuration config = loadConfiguration();
        final LocalDB pwmDB = loadPwmDB(config, true);
        final LocalDBStoredQueue logQueue = LocalDBStoredQueue.createPwmDBStoredQueue(pwmDB, LocalDB.DB.EVENTLOG_EVENTS);

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

        if (args.length < 2) {
            out("must specify a file to write responses to");
            return;
        }

        final Configuration config = loadConfiguration();
        final File workingFolder = new File(".").getCanonicalFile();
        final PwmApplication pwmApplication = loadPwmApplication(config, workingFolder, true);

        final File outputFile = new File(args[1]);
        if (outputFile.exists()) {
            out(outputFile.getAbsolutePath() + " already exists");
            return;
        }

        Helper.pause(2000);

        final long startTime = System.currentTimeMillis();
        final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setChaiProvider(pwmApplication.getProxyChaiProvider());
        searchConfiguration.setEnableValueEscaping(false);
        searchConfiguration.setUsername("*");

        final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        final String systemRecordDelimiter = System.getProperty("line.separator");
        final Writer writer = new BufferedWriter(new PrintWriter(outputFile,"UTF-8"));
        final Map<ChaiUser,Map<String,String>> results = userSearchEngine.performMultiUserSearch(null,searchConfiguration, Integer.MAX_VALUE, Collections.<String>emptyList());
        out("searching " + results.size() + " users for stored responses to write to " + outputFile.getAbsolutePath() + "....");
        int counter = 0;
        for (final ChaiUser user : results.keySet()) {
            final ResponseSet responseSet = pwmApplication.getCrService().readUserResponseSet(null, user);
            if (responseSet != null) {
                counter++;
                out("found responses for '" + user.getEntryDN() + "', writing to output.");
                final RestChallengesServer.JsonChallengesData outputData = new RestChallengesServer.JsonChallengesData();
                outputData.challenges = responseSet.asChallengeBeans(true);
                outputData.helpdeskChallenges = responseSet.asHelpdeskChallengeBeans(true);
                outputData.minimumRandoms = responseSet.getChallengeSet().minimumResponses();
                outputData.username = user.getEntryDN();
                writer.write(gson.toJson(outputData));
                writer.write(systemRecordDelimiter);
            } else {
                out("skipping '" + user.getEntryDN() + "', no stored responses.");
            }
        }
        writer.close();

        out("output complete, " + counter + " responses exported in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    static void handleImportResponses(final String[] args) throws Exception {
        final Configuration config = loadConfiguration();
        final File workingFolder = new File(".").getCanonicalFile();
        final PwmApplication pwmApplication = loadPwmApplication(config, workingFolder, false);

        if (args.length < 2) {
            out("must specify a file to read responses from");
            return;
        }

        final File inputFile = new File(args[1]);

        if (!inputFile.exists()) {
            out(inputFile.getAbsolutePath() + " does not exist");
            return;
        }

        final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile),"UTF-8"));
        out("importing stored responses from " + inputFile.getAbsolutePath() + "....");

        int counter = 0;
        String line;
        final Gson gson = new Gson();
        final long startTime = System.currentTimeMillis();
        while ((line = reader.readLine()) != null) {
            counter++;
            final RestChallengesServer.JsonChallengesData inputData;
            try {
                inputData = gson.fromJson(line, RestChallengesServer.JsonChallengesData.class);
            } catch (JsonSyntaxException e) {
                out("error parsing line " + counter + ", error: " + e.getMessage());
                return;
            }

            final ChaiUser user = ChaiFactory.createChaiUser(inputData.username,pwmApplication.getProxyChaiProvider());
            if (user.isValid()) {
                out("writing responses to user '" + user.getEntryDN() + "'");
                try {
                    final ChallengeSet challengeSet = pwmApplication.getCrService().readUserChallengeSet(user, PwmPasswordPolicy.defaultPolicy(), PwmConstants.DEFAULT_LOCALE);
                    final String userGuid = Helper.readLdapGuidValue(pwmApplication, user.getEntryDN());
                    final ResponseInfoBean responseInfoBean = inputData.toResponseInfoBean(PwmConstants.DEFAULT_LOCALE,challengeSet.getIdentifier());
                    pwmApplication.getCrService().writeResponses(user, userGuid, responseInfoBean );
                } catch (Exception e) {
                    out("error writing responses to user '" + user.getEntryDN() + "', error: " + e.getMessage());
                    return;
                }
            } else {
                out("user '" + user.getEntryDN() + "' is not a valid userDN");
                return;
            }
        }

        out("output complete, " + counter + " responses imported in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    static void handleClearLocalResponses() throws Exception {
        final Configuration config = loadConfiguration();

        out("Proceeding with this operation will clear all stored responses from the LocalDB.");
        out("Please consider exporting the responses before proceeding. ");
        out("");
        out("The application must be stopped for this operation to succeed.");
        out("");
        out("To proceed, type 'continue'");
        final Scanner scanner = new Scanner(System.in);
        final String input = scanner.nextLine();

        if (!"continue".equalsIgnoreCase(input)) {
            out("exiting...");
            return;
        }

        final LocalDB pwmDB = loadPwmDB(config, false);

        if (pwmDB.size(LocalDB.DB.RESPONSE_STORAGE) == 0) {
            out("The LocalDB response database is already empty");
            return;
        }

        out("clearing " + pwmDB.size(LocalDB.DB.RESPONSE_STORAGE) + " responses");
        pwmDB.truncate(LocalDB.DB.RESPONSE_STORAGE);
        out("all saved responses are now removed from PwmDB");
    }

    static void out(final CharSequence out) {
        //LOGGER.info(out);
        System.out.println(out);
    }

    static LocalDB loadPwmDB(final Configuration config, final boolean readonly) throws Exception {
        final File databaseDirectory;
        final String pwmDBLocationSetting = config.readSettingAsString(PwmSetting.PWMDB_LOCATION);
        databaseDirectory = Helper.figureFilepath(pwmDBLocationSetting, new File("."));

        final String classname = config.readSettingAsString(PwmSetting.PWMDB_IMPLEMENTATION);
        final List<String> initStrings = config.readSettingAsStringArray(PwmSetting.PWMDB_INIT_STRING);
        final Map<String, String> initParamers = Configuration.convertStringListToNameValuePair(initStrings, "=");
        return LocalDBFactory.getInstance(databaseDirectory, classname, initParamers, readonly, null);
    }

    static Configuration loadConfiguration() throws Exception {
        return (new ConfigurationReader(new File(PwmConstants.CONFIG_FILE_FILENAME))).getConfiguration();
    }

    static void initLog4j() {
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

    static PwmApplication loadPwmApplication(final Configuration config, final File workingDirectory, final boolean readonly)
            throws LocalDBException
    {
        final PwmApplication.MODE mode = readonly ? PwmApplication.MODE.READ_ONLY : PwmApplication.MODE.RUNNING;
        return new PwmApplication(config, mode, workingDirectory);
    }

    static void handleExportLocalDB(final String[] args) throws Exception {
        final Configuration config = loadConfiguration();
        final LocalDB pwmDB = loadPwmDB(config, true);

        if (args.length < 2) {
            out("must specify file to write LocalDB data to");
            return;
        }

        final File outputFile = new File(args[1]);
        final LocalDBUtility pwmDBUtility = new LocalDBUtility(pwmDB);
        try {
            pwmDBUtility.exportLocalDB(outputFile, System.out);
        } catch (PwmOperationalException e) {
            out("error during export: " + e.getMessage());
        }
    }

    static void handleImportLocalDB(final String[] args) throws Exception {
        final Configuration config = loadConfiguration();
        final LocalDB pwmDB = loadPwmDB(config, false);

        if (args.length < 2) {
            out("must specify file to read LocalDB data from");
            return;
        }

        out("Proceeding with this operation will clear ALL data from the LocalDB.");
        out("Please consider backing up the LocalDB before proceeding. ");
        out("");
        out("The application must be stopped for this operation to succeed.");
        out("");
        out("To proceed, type 'continue'");
        final Scanner scanner = new Scanner(System.in);
        final String input = scanner.nextLine();

        if (!"continue".equalsIgnoreCase(input)) {
            out("exiting...");
            return;
        }

        final LocalDBUtility pwmDBUtility = new LocalDBUtility(pwmDB);
        final File inputFile = new File(args[1]);
        try {
            pwmDBUtility.importLocalDB(inputFile, System.out);
        } catch (PwmOperationalException e) {
            out("error during import: " + e.getMessage());
        }
    }

    static void handleTokenKey(final String[] args)
            throws Exception
    {
        if (args.length < 2) {
            out("first argument must be tokenKey");
            return;
        }

        final String tokenKey = args[1];

        final Configuration config = loadConfiguration();
        final File workingFolder = new File(".").getCanonicalFile();
        final PwmApplication pwmApplication = loadPwmApplication(config, workingFolder, true);

        final TokenManager tokenManager = pwmApplication.getTokenManager();
        TokenManager.TokenPayload tokenPayload = null;
        Exception lookupError = null;
        try {
            tokenPayload = tokenManager.retrieveTokenData(tokenKey);
        } catch (Exception e) {
            lookupError = e;
        }

        pwmApplication.shutdown();
        Helper.pause(1000);

        final StringBuilder output = new StringBuilder();
        output.append("\n");
        output.append("token: ").append(tokenKey);
        output.append("\n");

        if (lookupError != null) {
            output.append("result: error during token lookup: ").append(lookupError.toString());
        } else if (tokenPayload == null) {
            output.append("result: token not found");
            return;
        } else {
            output.append("  name: ").append(tokenPayload.getName());
            output.append("userDN: ").append(tokenPayload.getUserDN());
            output.append("issued: ").append(PwmConstants.DEFAULT_DATETIME_FORMAT.format(tokenPayload.getIssueDate()));
            for (final String key : tokenPayload.getPayloadData().keySet()) {
                final String value = tokenPayload.getPayloadData().get(key);
                output.append("  payload key: ").append(key).append(", value:").append(value);
            }
        }
        out(output.toString());
    }

    static void handleExportStats(final String[] args) throws Exception {
        final Configuration config = loadConfiguration();
        final File workingFolder = new File(".").getCanonicalFile();
        final PwmApplication pwmApplication = loadPwmApplication(config, workingFolder, true);
        StatisticsManager statsManger = pwmApplication.getStatisticsManager();
        Helper.pause(1000);

        if (args.length < 2) {
            out("must specify file to write stats data to");
            return;
        }

        final File outputFile = new File(args[1]);
        if (outputFile.exists()) {
            out("outputFile '" + outputFile.getAbsolutePath() + "' already exists");
            return;
        }

        final long startTime = System.currentTimeMillis();
        out("beginning output to " + outputFile.getAbsolutePath());
        final FileWriter fileWriter = new FileWriter(outputFile,true);
        final int counter = statsManger.outputStatsToCsv(fileWriter,false);
        fileWriter.close();
        out("completed writing " + counter + " rows of stats output in " + TimeDuration.fromCurrent(startTime).asLongString());
    }

    static void handleExportAudit(final String[] args) throws Exception {
        final Configuration config = loadConfiguration();
        final File workingFolder = new File(".").getCanonicalFile();
        final PwmApplication pwmApplication = loadPwmApplication(config, workingFolder, true);
        final AuditManager auditManager = pwmApplication.getAuditManager();
        Helper.pause(1000);

        if (args.length < 2) {
            out("must specify file to write audit data to");
            return;
        }

        final File outputFile = new File(args[1]);
        if (outputFile.exists()) {
            out("outputFile '" + outputFile.getAbsolutePath() + "' already exists");
            return;
        }

        final long startTime = System.currentTimeMillis();
        out("beginning output to " + outputFile.getAbsolutePath());
        final FileWriter fileWriter = new FileWriter(outputFile,true);
        final int counter = auditManager.outputLocalDBToCsv(fileWriter,false);
        fileWriter.close();
        out("completed writing " + counter + " rows of audit output in " + TimeDuration.fromCurrent(startTime).asLongString());
    }
}
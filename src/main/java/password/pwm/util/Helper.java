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

package password.pwm.util;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmAboutProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.i18n.Display;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.secure.PwmRandom;

import java.io.*;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Pattern;

/**
 * A collection of static methods used throughout PWM
 *
 * @author Jason D. Rivard
 */
public class
        Helper {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(Helper.class);

    // -------------------------- STATIC METHODS --------------------------

    private Helper() {
    }


    /**
     * Convert a byte[] array to readable string format. This makes the "hex" readable
     *
     * @param in byte[] buffer to convert to string format
     * @return result String buffer in String format
     */
    public static String byteArrayToHexString(final byte in[]) {
        final String pseudo[] = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};

        if (in == null || in.length <= 0) {
            return "";
        }

        final StringBuilder out = new StringBuilder(in.length * 2);

        for (final byte b : in) {
            byte ch = (byte) (b & 0xF0);    // strip off high nibble
            ch = (byte) (ch >>> 4);         // shift the bits down
            ch = (byte) (ch & 0x0F);        // must do this is high order bit is on!
            out.append(pseudo[(int) ch]);   // convert the nibble to a String Character
            ch = (byte) (b & 0x0F);         // strip off low nibble
            out.append(pseudo[(int) ch]);   // convert the nibble to a String Character
        }

        return out.toString();
    }

    /**
     * Pause the calling thread the specified amount of time.
     *
     * @param sleepTimeMS - a time duration in milliseconds
     * @return time actually spent sleeping
     */
    public static long pause(final long sleepTimeMS) {
        final long startTime = System.currentTimeMillis();
        do {
            try {
                final long sleepTime = sleepTimeMS - (System.currentTimeMillis() - startTime);
                Thread.sleep(sleepTime > 0 ? sleepTime : 5);
            } catch (InterruptedException e) {
                //who cares
            }
        } while ((System.currentTimeMillis() - startTime) < sleepTimeMS);

        return System.currentTimeMillis() - startTime;
    }


    /**
     * Writes a Map of form values to ldap onto the supplied user object.
     * The map key must be a string of attribute names.
     * <p/>
     * Any ldap operation exceptions are not reported (but logged).
     *
     * @param pwmSession       for looking up session info
     * @param theUser          User to write to
     * @param formValues       A map with {@link password.pwm.config.FormConfiguration} keys and String values.
     * @throws ChaiUnavailableException if the directory is unavailable
     * @throws PwmOperationalException if their is an unexpected ldap problem
     */
    public static void writeFormValuesToLdap(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final Map<FormConfiguration,String> formValues,
            final boolean expandMacros
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        final Map<String,String> tempMap = new HashMap<>();

        for (final FormConfiguration formItem : formValues.keySet()) {
            if (!formItem.isReadonly()) {
                tempMap.put(formItem.getName(),formValues.get(formItem));
            }
        }

        final MacroMachine macroMachine = pwmSession.getSessionManager().getMacroMachine(pwmApplication);
        writeMapToLdap(theUser, tempMap, macroMachine, expandMacros);
    }

    /**
     * Writes a Map of values to ldap onto the supplied user object.
     * The map key must be a string of attribute names.
     * <p/>
     * Any ldap operation exceptions are not reported (but logged).
     *
     * @param theUser          User to write to
     * @param valueMap       A map with String keys and String values.
     * @throws ChaiUnavailableException if the directory is unavailable
     * @throws PwmOperationalException if their is an unexpected ldap problem
     */
    public static void writeMapToLdap(
            final ChaiUser theUser,
            final Map<String,String> valueMap,
            final MacroMachine macroMachine,
            final boolean expandMacros
    )
            throws PwmOperationalException, ChaiUnavailableException
    {
        final Map<String,String> currentValues;
        try {
            currentValues = theUser.readStringAttributes(valueMap.keySet());
        } catch (ChaiOperationException e) {
            final String errorMsg = "error reading existing values on user " + theUser.getEntryDN() + " prior to replacing values, error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            final PwmOperationalException newException = new PwmOperationalException(errorInformation);
            newException.initCause(e);
            throw newException;
        }

        for (final String attrName : valueMap.keySet()) {
            String attrValue = valueMap.get(attrName) != null ? valueMap.get(attrName) : "";
            if (expandMacros) {
                attrValue = macroMachine.expandMacros(attrValue);
            }
            if (!attrValue.equals(currentValues.get(attrName))) {
                if (attrValue.length() > 0) {
                    try {
                        theUser.writeStringAttribute(attrName, attrValue);
                        LOGGER.info("set attribute on user " + theUser.getEntryDN() + " (" + attrName + "=" + attrValue + ")");
                    } catch (ChaiOperationException e) {
                        final String errorMsg = "error setting '" + attrName + "' attribute on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                        final PwmOperationalException newException = new PwmOperationalException(errorInformation);
                        newException.initCause(e);
                        throw newException;
                    }
                } else {
                    if (currentValues.get(attrName) != null && currentValues.get(attrName).length() > 0) {
                        try {
                            theUser.deleteAttribute(attrName, null);
                            LOGGER.info("deleted attribute value on user " + theUser.getEntryDN() + " (" + attrName + ")");
                        } catch (ChaiOperationException e) {
                            final String errorMsg = "error removing '" + attrName + "' attribute value on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                            final PwmOperationalException newException = new PwmOperationalException(errorInformation);
                            newException.initCause(e);
                            throw newException;
                        }
                    }
                }
            } else {
                LOGGER.debug("skipping attribute modify for attribute '" + attrName + "', no change in value");
            }
        }
    }

    public static String binaryArrayToHex(final byte[] buf) {
        final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();
        final char[] chars = new char[2 * buf.length];
        for (int i = 0; i < buf.length; ++i) {
            chars[2 * i] = HEX_CHARS[(buf[i] & 0xF0) >>> 4];
            chars[2 * i + 1] = HEX_CHARS[buf[i] & 0x0F];
        }
        return new String(chars);
    }

    public static String formatDiskSize(final long diskSize) {
        final float COUNT = 1000;
        if (diskSize < 1) {
            return "n/a";
        }

        if (diskSize == 0) {
            return "0";
        }

        final NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);

        if (diskSize > COUNT * COUNT * COUNT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(nf.format(diskSize / COUNT / COUNT / COUNT));
            sb.append(" GB");
            return sb.toString();
        }

        if (diskSize > COUNT * COUNT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(nf.format(diskSize / COUNT / COUNT));
            sb.append(" MB");
            return sb.toString();
        }

        return NumberFormat.getInstance().format(diskSize) + " bytes";
    }


    static public String buildPwmFormID(final SessionStateBean ssBean) {
        return ssBean.getSessionVerificationKey() + ssBean.getRequestVerificationKey();
    }

    public static void rotateBackups(final File inputFile, final int maxRotate) {
        if (maxRotate < 1) {
            return;
        }
        for (int i = maxRotate; i >= 0; i--) {
            final File thisFile = (i == 0) ? inputFile : new File(inputFile.getAbsolutePath() + "-" + i);
            final File youngerFile = (i <= 1) ? inputFile : new File(inputFile.getAbsolutePath() + "-" + (i - 1));

            if (i == maxRotate) {
                if (thisFile.exists()) {
                    LOGGER.debug("deleting old backup file: " + thisFile.getAbsolutePath());
                    if (!thisFile.delete()) {
                        LOGGER.error("unable to delete old backup file: " + thisFile.getAbsolutePath());
                    }
                }
            } else if (i == 0 || youngerFile.exists()) {
                final File destFile = new File(inputFile.getAbsolutePath() + "-" + (i + 1));
                LOGGER.debug("backup file " + thisFile.getAbsolutePath() + " renamed to " + destFile.getAbsolutePath());
                if (!thisFile.renameTo(destFile)) {
                    LOGGER.debug("unable to rename file " + thisFile.getAbsolutePath() + " to " + destFile.getAbsolutePath());
                }
            }
        }
    }

    public static Date nextZuluZeroTime() {
        final Calendar nextZuluMidnight = GregorianCalendar.getInstance(TimeZone.getTimeZone("Zulu"));
        nextZuluMidnight.set(Calendar.HOUR_OF_DAY,0);
        nextZuluMidnight.set(Calendar.MINUTE,0);
        nextZuluMidnight.set(Calendar.SECOND, 0);
        nextZuluMidnight.add(Calendar.HOUR, 24);
        return nextZuluMidnight.getTime();
    }



    public static String makeThreadName(final PwmApplication pwmApplication, final Class theClass) {
        String instanceName = "-";
        if (pwmApplication != null && pwmApplication.getInstanceID() != null) {
            instanceName = pwmApplication.getInstanceID();
        }

        return PwmConstants.PWM_APP_NAME + "-" + instanceName + "-" + theClass.getSimpleName();
    }

    public static void checkUrlAgainstWhitelist(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final String inputURL
    )
            throws PwmOperationalException
    {
        LOGGER.trace(sessionLabel, "beginning test of requested redirect URL: " + inputURL);
        if (inputURL == null || inputURL.isEmpty()) {
            return;
        }

        final URI inputURI;
        try {
            inputURI = URI.create(inputURL);
        } catch (IllegalArgumentException e) {
            LOGGER.error(sessionLabel, "unable to parse requested redirect url '" + inputURL + "', error: " + e.getMessage());
            // dont put input uri in error response
            final String errorMsg = "unable to parse url: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_REDIRECT_ILLEGAL,errorMsg));
        }

        { // check to make sure we werent handed a non-http uri.
            final String scheme = inputURI.getScheme();
            if (scheme != null && !scheme.isEmpty() && !scheme.equalsIgnoreCase("http") && !scheme.equals("https")) {
                final String errorMsg = "unsupported url scheme";
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_REDIRECT_ILLEGAL,errorMsg));
            }
        }

        if (inputURI.getHost() != null && !inputURI.getHost().isEmpty()) { // disallow localhost uri
            try {
                InetAddress inetAddress = InetAddress.getByName(inputURI.getHost());
                if (inetAddress.isLoopbackAddress()) {
                    final String errorMsg = "redirect to loopback host is not permitted";
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_REDIRECT_ILLEGAL,errorMsg));
                }
            } catch (UnknownHostException e) {
                /* noop */
            }
        }

        final StringBuilder sb = new StringBuilder();
        if (inputURI.getScheme() != null) {
            sb.append(inputURI.getScheme());
            sb.append("://");
        }
        if (inputURI.getHost() != null) {
            sb.append(inputURI.getHost());
        }
        if (inputURI.getPort() != -1) {
            sb.append(":");
            sb.append(inputURI.getPort());
        }
        if (inputURI.getPath() != null) {
            sb.append(inputURI.getPath());
        }

        final String testURI = sb.toString();
        LOGGER.trace(sessionLabel, "preparing to whitelist test parsed and decoded URL: " + testURI);

        final String REGEX_PREFIX = "regex:";
        final List<String> whiteList = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.SECURITY_REDIRECT_WHITELIST);
        for (final String loopFragment : whiteList) {
            if (loopFragment.startsWith(REGEX_PREFIX)) {
                try {
                    final String strPattern = loopFragment.substring(REGEX_PREFIX.length(), loopFragment.length());
                    final Pattern pattern = Pattern.compile(strPattern);
                    if (pattern.matcher(testURI).matches()) {
                        LOGGER.debug(sessionLabel, "positive URL match for regex pattern: " + strPattern);
                        return;
                    } else {
                        LOGGER.trace(sessionLabel, "negative URL match for regex pattern: " + strPattern);
                    }
                } catch (Exception e) {
                    LOGGER.error(sessionLabel, "error while testing URL match for regex pattern: '" + loopFragment + "', error: " + e.getMessage());;
                }

            } else {
                if (testURI.startsWith(loopFragment)) {
                    LOGGER.debug(sessionLabel, "positive URL match for pattern: " + loopFragment);
                    return;
                } else {
                    LOGGER.trace(sessionLabel, "negative URL match for pattern: " + loopFragment);
                }
            }
        }

        final String errorMsg = testURI + " is not a match for any configured redirect whitelist, see setting: " + PwmSetting.SECURITY_REDIRECT_WHITELIST.toMenuLocationDebug(null,PwmConstants.DEFAULT_LOCALE);
        throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_REDIRECT_ILLEGAL,errorMsg));
    }

    public static boolean determineIfDetailErrorMsgShown(final PwmApplication pwmApplication) {
        if (pwmApplication == null) {
            return false;
        }
        PwmApplication.MODE mode = pwmApplication.getApplicationMode();
        if (mode == PwmApplication.MODE.CONFIGURATION || mode == PwmApplication.MODE.NEW) {
            return true;
        }
        if (mode == PwmApplication.MODE.RUNNING) {
            if (pwmApplication.getConfig() != null) {
                if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static CSVPrinter makeCsvPrinter(final OutputStream outputStream)
            throws IOException
    {
        return new CSVPrinter(new OutputStreamWriter(outputStream,PwmConstants.DEFAULT_CHARSET), PwmConstants.DEFAULT_CSV_FORMAT);
    }

    private static String dateFormatForInfoBean(final Date date) {
        if (date != null) {
            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(date);
        } else {
            return LocaleHelper.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE, Display.Value_NotApplicable, null);
        }

    }

    public static Map<PwmAboutProperty,String> makeInfoBean(
            final PwmApplication pwmApplication
    ) {
        final Map<PwmAboutProperty,String> aboutMap = new TreeMap<>();

        // about page
        aboutMap.put(PwmAboutProperty.app_version,                  PwmConstants.SERVLET_VERSION);
        aboutMap.put(PwmAboutProperty.app_currentTime,              dateFormatForInfoBean(new Date()));
        aboutMap.put(PwmAboutProperty.app_startTime,                dateFormatForInfoBean(pwmApplication.getStartupTime()));
        aboutMap.put(PwmAboutProperty.app_installTime,              dateFormatForInfoBean(pwmApplication.getInstallTime()));
        aboutMap.put(PwmAboutProperty.app_siteUrl,                  pwmApplication.getConfig().readSettingAsString(PwmSetting.PWM_SITE_URL));
        aboutMap.put(PwmAboutProperty.app_instanceID,               pwmApplication.getInstanceID());
        aboutMap.put(PwmAboutProperty.app_chaiApiVersion,           PwmConstants.CHAI_API_VERSION);

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) {
            if (pwmApplication.getVersionChecker() != null) {
                aboutMap.put(PwmAboutProperty.app_currentPublishedVersion, pwmApplication.getVersionChecker().currentVersion());
                aboutMap.put(PwmAboutProperty.app_currentPublishedVersionCheckTime, dateFormatForInfoBean(pwmApplication.getVersionChecker().lastReadTimestamp()));
            }
        }

        aboutMap.put(PwmAboutProperty.app_secureBlockAlgorithm,     pwmApplication.getSecureService().getDefaultBlockAlgorithm().getLabel());
        aboutMap.put(PwmAboutProperty.app_secureHashAlgorithm,      pwmApplication.getSecureService().getDefaultHashAlgorithm().toString());

        aboutMap.put(PwmAboutProperty.app_wordlistSize,             Integer.toString(pwmApplication.getWordlistManager().size()));
        aboutMap.put(PwmAboutProperty.app_seedlistSize,             Integer.toString(pwmApplication.getSeedlistManager().size()));
        if (pwmApplication.getSharedHistoryManager() != null) {
            aboutMap.put(PwmAboutProperty.app_sharedHistorySize,    Integer.toString(pwmApplication.getSharedHistoryManager().size()));
            aboutMap.put(PwmAboutProperty.app_sharedHistoryOldestTime, dateFormatForInfoBean(pwmApplication.getSharedHistoryManager().getOldestEntryTime()));
        }


        if (pwmApplication.getEmailQueue() != null) {
            aboutMap.put(PwmAboutProperty.app_emailQueueSize,       Integer.toString(pwmApplication.getEmailQueue().queueSize()));
            aboutMap.put(PwmAboutProperty.app_emailQueueOldestTime, dateFormatForInfoBean(pwmApplication.getEmailQueue().eldestItem()));
        }

        if (pwmApplication.getSmsQueue() != null) {
            aboutMap.put(PwmAboutProperty.app_smsQueueSize,         Integer.toString(pwmApplication.getSmsQueue().queueSize()));
            aboutMap.put(PwmAboutProperty.app_smsQueueOldestTime,   dateFormatForInfoBean(pwmApplication.getSmsQueue().eldestItem()));
        }

        if (pwmApplication.getAuditManager() != null) {
            aboutMap.put(PwmAboutProperty.app_syslogQueueSize,      Integer.toString(pwmApplication.getAuditManager().syslogQueueSize()));
        }

        if (pwmApplication.getLocalDB() != null) {
            aboutMap.put(PwmAboutProperty.app_localDbLogSize,       Integer.toString(pwmApplication.getLocalDBLogger().getStoredEventCount()));
            aboutMap.put(PwmAboutProperty.app_localDbLogOldestTime, dateFormatForInfoBean(pwmApplication.getLocalDBLogger().getTailDate()));

            aboutMap.put(PwmAboutProperty.app_localDbStorageSize,   formatDiskSize(FileSystemUtility.getFileDirectorySize(pwmApplication.getLocalDB().getFileLocation())));
            aboutMap.put(PwmAboutProperty.app_localDbFreeSpace,     formatDiskSize(FileSystemUtility.diskSpaceRemaining(pwmApplication.getLocalDB().getFileLocation())));
        }


        { // java info
            final Runtime runtime = Runtime.getRuntime();
            aboutMap.put(PwmAboutProperty.java_memoryFree,          Long.toString(runtime.freeMemory()));
            aboutMap.put(PwmAboutProperty.java_memoryAllocated,     Long.toString(runtime.totalMemory()));
            aboutMap.put(PwmAboutProperty.java_memoryMax,           Long.toString(runtime.maxMemory()));
            aboutMap.put(PwmAboutProperty.java_threadCount,         Integer.toString(Thread.activeCount()));

            aboutMap.put(PwmAboutProperty.java_vmVendor,            System.getProperty("java.vm.vendor"));

            aboutMap.put(PwmAboutProperty.java_runtimeVersion,      System.getProperty("java.runtime.version"));
            aboutMap.put(PwmAboutProperty.java_vmVersion,           System.getProperty("java.vm.version"));
            aboutMap.put(PwmAboutProperty.java_vmName,              System.getProperty("java.vm.name"));
            aboutMap.put(PwmAboutProperty.java_vmLocation,          System.getProperty("java.home"));

            aboutMap.put(PwmAboutProperty.java_osName,              System.getProperty("os.name"));
            aboutMap.put(PwmAboutProperty.java_osVersion,           System.getProperty("os.version"));
            aboutMap.put(PwmAboutProperty.java_randomAlgorithm,     PwmRandom.getInstance().getAlgorithm());
        }

        { // build info
            aboutMap.put(PwmAboutProperty.build_Time,               PwmConstants.BUILD_TIME);
            aboutMap.put(PwmAboutProperty.build_Number,             PwmConstants.BUILD_NUMBER);
            aboutMap.put(PwmAboutProperty.build_Type,               PwmConstants.BUILD_TYPE);
            aboutMap.put(PwmAboutProperty.build_User,               PwmConstants.BUILD_USER);
            aboutMap.put(PwmAboutProperty.build_Revision,           PwmConstants.BUILD_REVISION);
            aboutMap.put(PwmAboutProperty.build_JavaVendor,         PwmConstants.BUILD_JAVA_VENDOR);
            aboutMap.put(PwmAboutProperty.build_JavaVersion,        PwmConstants.BUILD_JAVA_VERSION);
            aboutMap.put(PwmAboutProperty.build_Version,            PwmConstants.BUILD_VERSION);
        }

        return Collections.unmodifiableMap(aboutMap);
    }

    public static int portForUriSchema(final URI uri) {
        final int port = uri.getPort();
        if (port < 1) {
            return portForUriScheme(uri.getScheme());
        }
        return port;
    }

    private static int portForUriScheme(final String scheme) {
        if (scheme == null) {
            throw new NullPointerException("scheme cannot be null");
        }
        switch (scheme) {
            case "http": return 80;
            case "https": return 443;
            case "ldap": return 389;
            case "ldaps": return 636;
        }
        throw new IllegalArgumentException("unknown scheme: " + scheme);
    }

    public static <E extends Enum<E>> E readEnumFromString(Class<E> enumClass, E defaultValue, String input) {
        if (input == null) {
            return defaultValue;
        }

        try {
            Method valueOfMethod = enumClass.getMethod("valueOf",String.class);
            Object result = valueOfMethod.invoke(null,input);
            return (E)result;
        } catch (Exception e) {
            LOGGER.warn("unexpected error translating input=" + input + " to enumClass=" + enumClass.getSimpleName(),e);
        }

        return defaultValue;
    }

    public static Properties newSortedProperties() {
        return new Properties() {
            public synchronized Enumeration<Object> keys() {
                return Collections.enumeration(new TreeSet<>(super.keySet()));
            }
        };
    }

    public static ThreadFactory makePwmThreadFactory(final String namePrefix, final boolean daemon) {
        return new ThreadFactory() {
            private final ThreadFactory realThreadFactory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(final Runnable r) {
                final Thread t = realThreadFactory.newThread(r);
                t.setDaemon(daemon);
                if (namePrefix != null) {
                    final String newName = namePrefix + t.getName();
                    t.setName(newName);
                }
                return t;
            }
        };
    }

    public static String throwableToString(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

}

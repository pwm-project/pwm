/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm;

import password.pwm.config.PwmSetting;
import password.pwm.i18n.Display;
import password.pwm.util.FileSystemUtility;
import password.pwm.util.Helper;
import password.pwm.util.LocaleHelper;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

public enum PwmAboutProperty {

    app_version,
    app_chaiApiVersion,
    app_currentTime,
    app_startTime,
    app_installTime,
    app_currentPublishedVersion,
    app_currentPublishedVersionCheckTime,
    app_siteUrl,
    app_instanceID,
    app_wordlistSize,
    app_seedlistSize,
    app_sharedHistorySize,
    app_sharedHistoryOldestTime,
    app_emailQueueSize,
    app_emailQueueOldestTime,
    app_smsQueueSize,
    app_smsQueueOldestTime,
    app_syslogQueueSize,
    app_localDbLogSize,
    app_localDbLogOldestTime,
    app_localDbStorageSize,
    app_localDbFreeSpace,
    app_configurationRestartCounter,
    app_secureBlockAlgorithm,
    app_secureHashAlgorithm,

    build_Time,
    build_Number,
    build_Type,
    build_User,
    build_Revision,
    build_JavaVendor,
    build_JavaVersion,
    build_Version,

    java_memoryFree,
    java_memoryAllocated,
    java_memoryMax,
    java_threadCount,
    java_vmVendor,
    java_vmLocation,
    java_vmVersion,
    java_runtimeVersion,
    java_vmName,
    java_osName,
    java_osVersion,
    java_randomAlgorithm,

    database_driverName,
    database_driverVersion,
    database_databaseProductName,
    database_databaseProductVersion,

    ;

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmAboutProperty.class);

    public static Map<PwmAboutProperty,String> makeInfoBean(
            final PwmApplication pwmApplication
    ) {
        final Map<PwmAboutProperty,String> aboutMap = new TreeMap<>();

        // about page
        aboutMap.put(app_version,                  PwmConstants.SERVLET_VERSION);
        aboutMap.put(app_currentTime,              dateFormatForInfoBean(new Date()));
        aboutMap.put(app_startTime,                dateFormatForInfoBean(pwmApplication.getStartupTime()));
        aboutMap.put(app_installTime,              dateFormatForInfoBean(pwmApplication.getInstallTime()));
        aboutMap.put(app_siteUrl,                  pwmApplication.getConfig().readSettingAsString(PwmSetting.PWM_SITE_URL));
        aboutMap.put(app_instanceID,               pwmApplication.getInstanceID());
        aboutMap.put(app_chaiApiVersion,           PwmConstants.CHAI_API_VERSION);

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) {
            if (pwmApplication.getVersionChecker() != null) {
                aboutMap.put(app_currentPublishedVersion, pwmApplication.getVersionChecker().currentVersion());
                aboutMap.put(app_currentPublishedVersionCheckTime, dateFormatForInfoBean(pwmApplication.getVersionChecker().lastReadTimestamp()));
            }
        }

        aboutMap.put(app_secureBlockAlgorithm,     pwmApplication.getSecureService().getDefaultBlockAlgorithm().getLabel());
        aboutMap.put(app_secureHashAlgorithm,      pwmApplication.getSecureService().getDefaultHashAlgorithm().toString());

        aboutMap.put(app_wordlistSize,             Integer.toString(pwmApplication.getWordlistManager().size()));
        aboutMap.put(app_seedlistSize,             Integer.toString(pwmApplication.getSeedlistManager().size()));
        if (pwmApplication.getSharedHistoryManager() != null) {
            aboutMap.put(app_sharedHistorySize,    Integer.toString(pwmApplication.getSharedHistoryManager().size()));
            aboutMap.put(app_sharedHistoryOldestTime, dateFormatForInfoBean(pwmApplication.getSharedHistoryManager().getOldestEntryTime()));
        }


        if (pwmApplication.getEmailQueue() != null) {
            aboutMap.put(app_emailQueueSize,       Integer.toString(pwmApplication.getEmailQueue().queueSize()));
            aboutMap.put(app_emailQueueOldestTime, dateFormatForInfoBean(pwmApplication.getEmailQueue().eldestItem()));
        }

        if (pwmApplication.getSmsQueue() != null) {
            aboutMap.put(app_smsQueueSize,         Integer.toString(pwmApplication.getSmsQueue().queueSize()));
            aboutMap.put(app_smsQueueOldestTime,   dateFormatForInfoBean(pwmApplication.getSmsQueue().eldestItem()));
        }

        if (pwmApplication.getAuditManager() != null) {
            aboutMap.put(app_syslogQueueSize,      Integer.toString(pwmApplication.getAuditManager().syslogQueueSize()));
        }

        if (pwmApplication.getLocalDB() != null) {
            aboutMap.put(app_localDbLogSize,       Integer.toString(pwmApplication.getLocalDBLogger().getStoredEventCount()));
            aboutMap.put(app_localDbLogOldestTime, dateFormatForInfoBean(pwmApplication.getLocalDBLogger().getTailDate()));

            aboutMap.put(app_localDbStorageSize,   Helper.formatDiskSize(FileSystemUtility.getFileDirectorySize(pwmApplication.getLocalDB().getFileLocation())));
            aboutMap.put(app_localDbFreeSpace,     Helper.formatDiskSize(FileSystemUtility.diskSpaceRemaining(pwmApplication.getLocalDB().getFileLocation())));
        }


        { // java info
            final Runtime runtime = Runtime.getRuntime();
            aboutMap.put(java_memoryFree,          Long.toString(runtime.freeMemory()));
            aboutMap.put(java_memoryAllocated,     Long.toString(runtime.totalMemory()));
            aboutMap.put(java_memoryMax,           Long.toString(runtime.maxMemory()));
            aboutMap.put(java_threadCount,         Integer.toString(Thread.activeCount()));

            aboutMap.put(java_vmVendor,            System.getProperty("java.vm.vendor"));

            aboutMap.put(java_runtimeVersion,      System.getProperty("java.runtime.version"));
            aboutMap.put(java_vmVersion,           System.getProperty("java.vm.version"));
            aboutMap.put(java_vmName,              System.getProperty("java.vm.name"));
            aboutMap.put(java_vmLocation,          System.getProperty("java.home"));

            aboutMap.put(java_osName,              System.getProperty("os.name"));
            aboutMap.put(java_osVersion,           System.getProperty("os.version"));
            aboutMap.put(java_randomAlgorithm,     PwmRandom.getInstance().getAlgorithm());
        }

        { // build info
            aboutMap.put(build_Time,               PwmConstants.BUILD_TIME);
            aboutMap.put(build_Number,             PwmConstants.BUILD_NUMBER);
            aboutMap.put(build_Type,               PwmConstants.BUILD_TYPE);
            aboutMap.put(build_User,               PwmConstants.BUILD_USER);
            aboutMap.put(build_Revision,           PwmConstants.BUILD_REVISION);
            aboutMap.put(build_JavaVendor,         PwmConstants.BUILD_JAVA_VENDOR);
            aboutMap.put(build_JavaVersion,        PwmConstants.BUILD_JAVA_VERSION);
            aboutMap.put(build_Version,            PwmConstants.BUILD_VERSION);
        }

        { // database info
            try {
                final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
                if (databaseAccessor != null) {
                    final Map<PwmAboutProperty,String> debugData = databaseAccessor.getConnectionDebugProperties();
                    aboutMap.putAll(debugData);
                }
            } catch (Throwable t) {
                LOGGER.error("error reading database debug properties");
            }
        }

        return Collections.unmodifiableMap(aboutMap);
    }

    private static String dateFormatForInfoBean(final Date date) {
        if (date != null) {
            return PwmConstants.DEFAULT_DATETIME_FORMAT.format(date);
        } else {
            return LocaleHelper.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE, Display.Value_NotApplicable, null);
        }

    }
}
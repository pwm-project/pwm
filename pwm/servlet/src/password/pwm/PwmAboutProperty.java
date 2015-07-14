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

package password.pwm;

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
}
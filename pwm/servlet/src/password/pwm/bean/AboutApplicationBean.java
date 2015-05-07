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

package password.pwm.bean;

import java.io.Serializable;
import java.util.Date;

public class AboutApplicationBean implements Serializable {
    
    private String version;
    private String chaiApiVersion;

    private Date currentTime;
    private Date startTime;
    private Date installTime;
    
    private String currentPublishedVersion;
    private Date currentPublishedVersionCheckTime;
    
    private String siteUrl;
    private String instanceID;
    
    private int wordlistSize;
    private int seedlistSize;
    private int sharedHistorySize;
    private Date sharedHistoryOldestTime;

    private int emailQueueSize;
    private Date emailQueueOldestTime;
    private int smsQueueSize;
    private Date smsQueueOldestTime;
    private int syslogQueueSize;
    private Date syslogQueueOldestTime;
    
    private int localDbLogSize;
    private Date localDbLogOldestTime;

    private String localDbStorageSize;
    private String localDbFreeSpace;
    
    private int configurationRestartCounter;

    private JavaInformation javaInformation;
    private BuildInformation buildInformation;

    public static class BuildInformation implements Serializable {
        private String buildTime;
        private String buildNumber;
        private String buildType;
        private String buildUser;
        private String buildRevision;
        private String buildJavaVendor;
        private String buildJavaVersion;
        private String buildVersion;

        public String getBuildTime() {
            return buildTime;
        }

        public void setBuildTime(String buildTime) {
            this.buildTime = buildTime;
        }

        public String getBuildNumber() {
            return buildNumber;
        }

        public void setBuildNumber(String buildNumber) {
            this.buildNumber = buildNumber;
        }

        public String getBuildType() {
            return buildType;
        }

        public void setBuildType(String buildType) {
            this.buildType = buildType;
        }

        public String getBuildUser() {
            return buildUser;
        }

        public void setBuildUser(String buildUser) {
            this.buildUser = buildUser;
        }

        public String getBuildRevision() {
            return buildRevision;
        }

        public void setBuildRevision(String buildRevision) {
            this.buildRevision = buildRevision;
        }

        public String getBuildJavaVendor() {
            return buildJavaVendor;
        }

        public void setBuildJavaVendor(String buildJavaVendor) {
            this.buildJavaVendor = buildJavaVendor;
        }

        public String getBuildJavaVersion() {
            return buildJavaVersion;
        }

        public void setBuildJavaVersion(String buildJavaVersion) {
            this.buildJavaVersion = buildJavaVersion;
        }

        public String getBuildVersion() {
            return buildVersion;
        }

        public void setBuildVersion(String buildVersion) {
            this.buildVersion = buildVersion;
        }
    }

    public static class JavaInformation implements Serializable {
        private long memoryFree;
        private long memoryAllocated;
        private long memoryMax;
        private long threadCount;
        private String vmVendor;
        private String vmLocation;
        private String vmVersion;
        private String runtimeVersion;
        private String vmName;
        private String osName;
        private String osVersion;
        private String randomAlgorithm;

        public JavaInformation() {
        }

        public long getMemoryFree() {
            return memoryFree;
        }

        public void setMemoryFree(long memoryFree) {
            this.memoryFree = memoryFree;
        }

        public long getMemoryAllocated() {
            return memoryAllocated;
        }

        public void setMemoryAllocated(long memoryAllocated) {
            this.memoryAllocated = memoryAllocated;
        }

        public long getMemoryMax() {
            return memoryMax;
        }

        public void setMemoryMax(long memoryMax) {
            this.memoryMax = memoryMax;
        }

        public long getThreadCount() {
            return threadCount;
        }

        public void setThreadCount(long threadCount) {
            this.threadCount = threadCount;
        }

        public String getVmVendor() {
            return vmVendor;
        }

        public void setVmVendor(String vmVendor) {
            this.vmVendor = vmVendor;
        }

        public String getVmLocation() {
            return vmLocation;
        }

        public void setVmLocation(String vmLocation) {
            this.vmLocation = vmLocation;
        }

        public String getVmVersion() {
            return vmVersion;
        }

        public void setVmVersion(String vmVersion) {
            this.vmVersion = vmVersion;
        }

        public String getRuntimeVersion() {
            return runtimeVersion;
        }

        public void setRuntimeVersion(String runtimeVersion) {
            this.runtimeVersion = runtimeVersion;
        }

        public String getVmName() {
            return vmName;
        }

        public void setVmName(String vmName) {
            this.vmName = vmName;
        }

        public String getOsName() {
            return osName;
        }

        public void setOsName(String osName) {
            this.osName = osName;
        }

        public String getOsVersion() {
            return osVersion;
        }

        public void setOsVersion(String osVersion) {
            this.osVersion = osVersion;
        }

        public String getRandomAlgorithm() {
            return randomAlgorithm;
        }

        public void setRandomAlgorithm(String randomAlgorithm) {
            this.randomAlgorithm = randomAlgorithm;
        }
    }
    
    ///////////////////////////////

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getChaiApiVersion() {
        return chaiApiVersion;
    }

    public void setChaiApiVersion(String chaiApiVersion) {
        this.chaiApiVersion = chaiApiVersion;
    }

    public Date getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(Date currentTime) {
        this.currentTime = currentTime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getInstallTime() {
        return installTime;
    }

    public void setInstallTime(Date installTime) {
        this.installTime = installTime;
    }

    public String getCurrentPublishedVersion() {
        return currentPublishedVersion;
    }

    public void setCurrentPublishedVersion(String currentPublishedVersion) {
        this.currentPublishedVersion = currentPublishedVersion;
    }

    public Date getCurrentPublishedVersionCheckTime() {
        return currentPublishedVersionCheckTime;
    }

    public void setCurrentPublishedVersionCheckTime(Date currentPublishedVersionCheckTime) {
        this.currentPublishedVersionCheckTime = currentPublishedVersionCheckTime;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public String getInstanceID() {
        return instanceID;
    }

    public void setInstanceID(String instanceID) {
        this.instanceID = instanceID;
    }

    public int getWordlistSize() {
        return wordlistSize;
    }

    public void setWordlistSize(int wordlistSize) {
        this.wordlistSize = wordlistSize;
    }

    public int getSeedlistSize() {
        return seedlistSize;
    }

    public void setSeedlistSize(int seedlistSize) {
        this.seedlistSize = seedlistSize;
    }

    public int getSharedHistorySize() {
        return sharedHistorySize;
    }

    public void setSharedHistorySize(int sharedHistorySize) {
        this.sharedHistorySize = sharedHistorySize;
    }

    public int getEmailQueueSize() {
        return emailQueueSize;
    }

    public void setEmailQueueSize(int emailQueueSize) {
        this.emailQueueSize = emailQueueSize;
    }

    public Date getEmailQueueOldestTime() {
        return emailQueueOldestTime;
    }

    public void setEmailQueueOldestTime(Date emailQueueOldestTime) {
        this.emailQueueOldestTime = emailQueueOldestTime;
    }

    public int getSmsQueueSize() {
        return smsQueueSize;
    }

    public void setSmsQueueSize(int smsQueueSize) {
        this.smsQueueSize = smsQueueSize;
    }

    public Date getSmsQueueOldestTime() {
        return smsQueueOldestTime;
    }

    public void setSmsQueueOldestTime(Date smsQueueOldestTime) {
        this.smsQueueOldestTime = smsQueueOldestTime;
    }

    public int getSyslogQueueSize() {
        return syslogQueueSize;
    }

    public void setSyslogQueueSize(int syslogQueueSize) {
        this.syslogQueueSize = syslogQueueSize;
    }

    public Date getSyslogQueueOldestTime() {
        return syslogQueueOldestTime;
    }

    public void setSyslogQueueOldestTime(Date syslogQueueOldestTime) {
        this.syslogQueueOldestTime = syslogQueueOldestTime;
    }

    public int getLocalDbLogSize() {
        return localDbLogSize;
    }

    public void setLocalDbLogSize(int localDbLogSize) {
        this.localDbLogSize = localDbLogSize;
    }

    public Date getLocalDbLogOldestTime() {
        return localDbLogOldestTime;
    }

    public void setLocalDbLogOldestTime(Date localDbLogOldestTime) {
        this.localDbLogOldestTime = localDbLogOldestTime;
    }

    public Date getSharedHistoryOldestTime() {
        return sharedHistoryOldestTime;
    }

    public void setSharedHistoryOldestTime(Date sharedHistoryOldestTime) {
        this.sharedHistoryOldestTime = sharedHistoryOldestTime;
    }

    public String getLocalDbStorageSize() {
        return localDbStorageSize;
    }

    public void setLocalDbStorageSize(String localDbStorageSize) {
        this.localDbStorageSize = localDbStorageSize;
    }

    public String getLocalDbFreeSpace() {
        return localDbFreeSpace;
    }

    public void setLocalDbFreeSpace(String localDbFreeSpace) {
        this.localDbFreeSpace = localDbFreeSpace;
    }

    public int getConfigurationRestartCounter() {
        return configurationRestartCounter;
    }

    public void setConfigurationRestartCounter(int configurationRestartCounter) {
        this.configurationRestartCounter = configurationRestartCounter;
    }

    public JavaInformation getJavaInformation() {
        return javaInformation;
    }

    public void setJavaInformation(JavaInformation javaInformation) {
        this.javaInformation = javaInformation;
    }

    public BuildInformation getBuildInformation() {
        return buildInformation;
    }

    public void setBuildInformation(BuildInformation buildInformation) {
        this.buildInformation = buildInformation;
    }
}

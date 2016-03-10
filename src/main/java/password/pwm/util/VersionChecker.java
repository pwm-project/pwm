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

package password.pwm.util;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.i18n.Display;
import password.pwm.svc.PwmService;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class VersionChecker implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(VersionChecker.class);

    private static final String KEY_VERSION = "version";
    private static final String KEY_BUILD = "build";
    private static final String LOCALDB_KEY_VERSION_CHECK_INFO_CACHE = "versionCheckInfoCache";
    
    private static final String VERSION_CHECK_URL = PwmConstants.PWM_URL_CLOUD + "/rest/pwm/current-version";

    private PwmApplication pwmApplication;
    private VersionCheckInfoCache versionCheckInfoCache;
    private STATUS status = STATUS.CLOSED;

    public VersionChecker() {
    }

    public void init(final PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) {
            status = STATUS.CLOSED;
            return;
        }

        if (pwmApplication.getLocalDB() != null && pwmApplication.getLocalDB().status() == LocalDB.Status.OPEN) {
            try {
                final String versionChkInfoJson = pwmApplication.getLocalDB().get(LocalDB.DB.PWM_META,
                        LOCALDB_KEY_VERSION_CHECK_INFO_CACHE);
                if (versionChkInfoJson != null && versionChkInfoJson.length() > 0) {
                    versionCheckInfoCache = JsonUtil.deserialize(versionChkInfoJson, VersionCheckInfoCache.class);
                }
            } catch (LocalDBException e) {
                LOGGER.error("error reading version check info out of LocalDB: " + e.getMessage());
            }
        }

        if (pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING && pwmApplication.getApplicationMode() != PwmApplicationMode.CONFIGURATION ) {
            LOGGER.trace("skipping init due to application mode");
            return;
        }

        if (versionCheckInfoCache != null && versionCheckInfoCache.getLastError() != null) {
            versionCheckInfoCache = null;
        }

        if (!isVersionCurrent()) {
            LOGGER.warn("this version of PWM is outdated, please check the project website for the current version");
        }

        status = STATUS.OPEN;
    }

    public String currentVersion() {
        if (status() != STATUS.OPEN) {
            return Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE, Display.Value_NotApplicable, pwmApplication.getConfig());
        }

        try {
            final VersionCheckInfoCache versionCheckInfo = getVersionCheckInfo();
            if (versionCheckInfo != null) {
                return versionCheckInfo.getCurrentVersion();
            }
        } catch (Exception e) {
            LOGGER.error("unable to retrieve current version data from cloud: " + e.toString());
        }
        return Display.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE, Display.Value_NotApplicable, pwmApplication.getConfig());
    }

    public Date lastReadTimestamp() {
        if (status() != STATUS.OPEN) {
            return null;
        }

        try {
            final VersionCheckInfoCache versionCheckInfo = getVersionCheckInfo();
            if (versionCheckInfo != null) {
                return versionCheckInfo.getLastCheckTimestamp();
            }
        } catch (Exception e) {
            LOGGER.error("unable to determine last read timestamp: " + e.toString());
        }
        return null;
    }

    public boolean isVersionCurrent() {
        if (status() != STATUS.OPEN) {
            return true;
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) {
            return true;
        }

        if (PwmConstants.BUILD_NUMBER == null || PwmConstants.BUILD_NUMBER.length() < 1) {
            return true;
        }

        try {
            final VersionCheckInfoCache versionCheckInfo = getVersionCheckInfo();
            final String currentBuild = versionCheckInfo.getCurrentBuild();
            final int currentBuildNumber = Integer.parseInt(currentBuild);
            int localBuildNumber;
            try {
                localBuildNumber = Integer.parseInt(PwmConstants.BUILD_NUMBER);
            } catch (NumberFormatException e) {
                localBuildNumber = 0;
            }
            if (localBuildNumber < currentBuildNumber) {
                LOGGER.trace("current build " + currentBuildNumber + " is newer than local build (" + localBuildNumber + ")");
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("unable to retrieve current version data from cloud: " + e.toString());
        }
        return true;
    }

    private synchronized VersionCheckInfoCache getVersionCheckInfo() {
        if (versionCheckInfoCache != null) {
            if (versionCheckInfoCache.getLastError() != null) {
                if (TimeDuration.fromCurrent(versionCheckInfoCache.getLastCheckTimestamp()).isLongerThan(PwmConstants.VERSION_CHECK_FAIL_RETRY_MS)) {
                    versionCheckInfoCache = null;
                }                
            } else if (TimeDuration.fromCurrent(versionCheckInfoCache.getLastCheckTimestamp()).isLongerThan(PwmConstants.VERSION_CHECK_FREQUENCEY_MS)) {
                versionCheckInfoCache = null;
            }
        }

        if (versionCheckInfoCache != null) {
            return versionCheckInfoCache;
        }

        try {
            final Map<String,String> responseMap = doCurrentVersionFetch();
            versionCheckInfoCache = new VersionCheckInfoCache(null, responseMap.get(KEY_VERSION), responseMap.get(KEY_BUILD));
            LOGGER.info("version check to " + VERSION_CHECK_URL +" completed, current-build=" + versionCheckInfoCache.getCurrentBuild() + ", current-version=" + versionCheckInfoCache.getCurrentVersion());
        } catch (Exception e) {
            final String errorMsg = "unable to reach version check service '" + VERSION_CHECK_URL + "', error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNREACHABLE_CLOUD_SERVICE, errorMsg);
            LOGGER.error(errorInformation.toDebugStr());
            versionCheckInfoCache = new VersionCheckInfoCache(errorInformation,"","");
        }

        if (pwmApplication.getLocalDB() != null && pwmApplication.getLocalDB().status() == LocalDB.Status.OPEN) {
            try {
                final String jsonVersionInfo = JsonUtil.serialize(versionCheckInfoCache);
                pwmApplication.getLocalDB().put(LocalDB.DB.PWM_META, LOCALDB_KEY_VERSION_CHECK_INFO_CACHE,jsonVersionInfo);
            } catch (LocalDBException e) {
                LOGGER.error("error writing version check info out of LocalDB: " + e.getMessage());
            }
        }

        return versionCheckInfoCache;
    }

    private Map<String,String> doCurrentVersionFetch() throws IOException, URISyntaxException, PwmUnrecoverableException {
        final URI requestURI = new URI(VERSION_CHECK_URL);
        final HttpGet httpGet = new HttpGet(requestURI.toString());
        httpGet.setHeader("Accept", PwmConstants.ContentTypeValue.json.getHeaderValue());
        LOGGER.trace("sending cloud version request to: " + VERSION_CHECK_URL);

        final HttpResponse httpResponse = PwmHttpClient.getHttpClient(pwmApplication.getConfig()).execute(httpGet);
        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new IOException("http response error code: " + httpResponse.getStatusLine().getStatusCode());
        }
        final String responseBody = EntityUtils.toString(httpResponse.getEntity());
        return JsonUtil.deserializeStringMap(responseBody);
    }

    public STATUS status() {
        return status;
    }

    public void close() {
    }

    public List<HealthRecord> healthCheck() {
        final ArrayList<HealthRecord> returnRecords = new ArrayList<>();

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) { // version checking
            VersionCheckInfoCache checkInfoCache = getVersionCheckInfo();
            if (checkInfoCache.getLastError() == null) {
                if (!isVersionCurrent()) {
                    returnRecords.add(new HealthRecord(HealthStatus.CAUTION, HealthTopic.Application,
                            "This version of " + PwmConstants.PWM_APP_NAME + " is out of date." + "  The current version is "
                                    + versionCheckInfoCache.getCurrentVersion() + " (b" + versionCheckInfoCache.getCurrentBuild() + ")."
                                    + "  Check the project page for more information."));
                }
            } else {
                returnRecords.add(new HealthRecord(HealthStatus.WARN,HealthTopic.Application,"Unable to check current version: " + versionCheckInfoCache.getLastError().toDebugStr()));
            }
        }

        return returnRecords;
    }

    private static class VersionCheckInfoCache implements Serializable {
        private Date lastCheckTimestamp;
        private ErrorInformation lastError;
        private String currentVersion;
        private String currentBuild;

        private VersionCheckInfoCache(ErrorInformation lastError, String currentVersion, String currentBuild) {
            this.lastCheckTimestamp = new Date();
            this.lastError = lastError;
            this.currentVersion = currentVersion;
            this.currentBuild = currentBuild;
        }

        public Date getLastCheckTimestamp() {
            return lastCheckTimestamp;
        }

        public ErrorInformation getLastError() {
            return lastError;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getCurrentBuild() {
            return currentBuild;
        }
    }

    public ServiceInfo serviceInfo()
    {
        return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
    }
}
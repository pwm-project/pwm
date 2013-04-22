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

package password.pwm;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VersionChecker implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(VersionChecker.class);

    private static final String KEY_VERSION = "version";
    private static final String KEY_BUILD = "build";
    private static final String PWMDB_KEY_VERSION_CHECK_INFO_CACHE = "versionCheckInfoCache";
    
    private static final String VERSION_CHECK_URL = PwmConstants.PWM_URL_CLOUD + "/rest/pwm/current-version";

    private PwmApplication pwmApplication;
    private VersionCheckInfoCache versionCheckInfoCache;

    public VersionChecker() {
    }

    public void init(final PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
        if (pwmApplication.getLocalDB() != null && pwmApplication.getLocalDB().status() == LocalDB.Status.OPEN) {
            try {
                final String versionChkInfoJson = pwmApplication.getLocalDB().get(LocalDB.DB.PWM_META,PWMDB_KEY_VERSION_CHECK_INFO_CACHE);
                if (versionChkInfoJson != null && versionChkInfoJson.length() > 0) {
                    final Gson gson = new Gson();
                    versionCheckInfoCache = gson.fromJson(versionChkInfoJson, VersionCheckInfoCache.class);
                }
            } catch (LocalDBException e) {
                LOGGER.error("error reading version check info out of PwmDB: " + e.getMessage());
            }
        }

        if (versionCheckInfoCache != null && versionCheckInfoCache.getLastError() != null) {
            versionCheckInfoCache = null;
        }

        if (!isVersionCurrent()) {
            LOGGER.warn("this version of PWM is outdated, please check the project website for the current version");
        }
    }

    public String currentVersion() {
        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) {
            return "n/a";
        }

        try {
            final VersionCheckInfoCache versionCheckInfo = getVersionCheckInfo();
            if (versionCheckInfo != null) {
                return versionCheckInfo.getCurrentVersion();
            }
        } catch (Exception e) {
            LOGGER.error("unable to retrieve current version data from cloud: " + e.toString());
        }
        return "n/a";
    }

    public boolean isVersionCurrent() {
        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) {
            return true;
        }

        try {
            final VersionCheckInfoCache versionCheckInfo = getVersionCheckInfo();
            final String currentBuild = versionCheckInfo.getCurrentBuild();
            final int currentBuildNumber = Integer.parseInt(currentBuild);
            final int localBuildNumber = Integer.parseInt(PwmConstants.BUILD_NUMBER);
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
                final Gson gson = new Gson();
                final String gsonVersionInfo = gson.toJson(versionCheckInfoCache);
                pwmApplication.getLocalDB().put(LocalDB.DB.PWM_META,PWMDB_KEY_VERSION_CHECK_INFO_CACHE,gsonVersionInfo);
            } catch (LocalDBException e) {
                LOGGER.error("error writing version check info out of PwmDB: " + e.getMessage());
            }
        }

        return versionCheckInfoCache;
    }

    private Map<String,String> doCurrentVersionFetch() throws IOException, URISyntaxException {
        final URI requestURI = new URI(VERSION_CHECK_URL);
        final HttpGet httpGet = new HttpGet(requestURI.toString());
        httpGet.setHeader("Accept", "application/json");
        LOGGER.trace("sending cloud version request to: " + VERSION_CHECK_URL);

        final HttpResponse httpResponse = Helper.getHttpClient(pwmApplication.getConfig()).execute(httpGet);
        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new IOException("http response error code: " + httpResponse.getStatusLine().getStatusCode());
        }
        final String responseBody = EntityUtils.toString(httpResponse.getEntity());
        Gson gson = new Gson();
        return gson.fromJson(responseBody, new TypeToken<Map<String, String>>() {}.getType());
    }

    public STATUS status() {
        return STATUS.OPEN;
    }

    public void close() {
    }

    public List<HealthRecord> healthCheck() {
        final ArrayList<HealthRecord> returnRecords = new ArrayList<HealthRecord>();

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) { // version checking
            VersionCheckInfoCache checkInfoCache = getVersionCheckInfo();
            if (checkInfoCache.getLastError() == null) {
                if (!isVersionCurrent()) {
                    final StringBuilder healthMsg = new StringBuilder();
                    healthMsg.append("This version of PWM is out of date.");
                    healthMsg.append("  The current version is ").append(versionCheckInfoCache.getCurrentVersion());
                    healthMsg.append(" (b").append(versionCheckInfoCache.getCurrentBuild()).append(").");
                    healthMsg.append("  Check the PWM project page for more information.");
                    returnRecords.add(new HealthRecord(HealthStatus.CAUTION,"Version",healthMsg.toString()));
                }
            } else {
                returnRecords.add(new HealthRecord(HealthStatus.WARN,"Version","Unable to check PWM current version: " + versionCheckInfoCache.getLastError().toDebugStr()));
            }
        }

        return returnRecords;
    }

    private static class VersionCheckInfoCache {
        private long lastCheckTimestamp;
        private ErrorInformation lastError;
        private String currentVersion;
        private String currentBuild;

        private VersionCheckInfoCache(ErrorInformation lastError, String currentVersion, String currentBuild) {
            this.lastCheckTimestamp = System.currentTimeMillis();
            this.lastError = lastError;
            this.currentVersion = currentVersion;
            this.currentBuild = currentBuild;
        }

        public long getLastCheckTimestamp() {
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
}
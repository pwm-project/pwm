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

package password.pwm.svc.report;

import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ReportSettings implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ReportSettings.class);
    
    private TimeDuration minCacheAge = TimeDuration.DAY;
    private TimeDuration maxCacheAge = new TimeDuration(TimeDuration.DAY.getTotalMilliseconds() * 90);
    private TimeDuration restTime = new TimeDuration(100);
    private boolean autoCalcRest = true;
    private String searchFilter = null;
    private int jobOffsetSeconds = 0;
    private int maxSearchSize = 100 * 1000;
    private List<Integer> trackDays = new ArrayList<>();

    public static ReportSettings readSettingsFromConfig(final Configuration config) {
        ReportSettings settings = new ReportSettings();
        settings.minCacheAge = new TimeDuration(config.readSettingAsLong(PwmSetting.REPORTING_MIN_CACHE_AGE) * 1000);
        settings.maxCacheAge = new TimeDuration(config.readSettingAsLong(PwmSetting.REPORTING_MAX_CACHE_AGE) * 1000);
        settings.searchFilter = config.readSettingAsString(PwmSetting.REPORTING_SEARCH_FILTER);
        settings.maxSearchSize = (int)config.readSettingAsLong(PwmSetting.REPORTING_MAX_QUERY_SIZE);

        if (settings.searchFilter == null || settings.searchFilter.isEmpty()) {
            settings.searchFilter = null;
        }

        final int configuredRestTimeMs = (int)config.readSettingAsLong(PwmSetting.REPORTING_REST_TIME_MS);
        settings.autoCalcRest = configuredRestTimeMs == -1;
        if (!settings.autoCalcRest) {
            settings.restTime = new TimeDuration(configuredRestTimeMs);
        }

        settings.jobOffsetSeconds = (int)config.readSettingAsLong(PwmSetting.REPORTING_JOB_TIME_OFFSET);
        if (settings.jobOffsetSeconds > 60 * 60 * 24) {
            settings.jobOffsetSeconds = 0;
        }

        settings.trackDays = parseDayIntervalStr(config);

        return settings;
    }

    private static List<Integer> parseDayIntervalStr(final Configuration configuration) {
        final List<String> configuredValues = new ArrayList<>();
        if (configuration != null) {
            configuredValues.addAll(configuration.readSettingAsStringArray(PwmSetting.REPORTING_SUMMARY_DAY_VALUES));
        }
        if (configuredValues.isEmpty()) {
            configuredValues.add("1");
        }
        final List<Integer> returnValue = new ArrayList<>();
        for (final String splitDay : configuredValues) {
            try {
                final int dayValue = Integer.parseInt(splitDay);
                returnValue.add(dayValue);
            } catch (NumberFormatException e) {
                LOGGER.error("error parsing reporting summary day value '" + splitDay + "', error: " + e.getMessage());
            }
        }
        Collections.sort(returnValue);
        return Collections.unmodifiableList(returnValue);
    }

    public TimeDuration getMinCacheAge() {
        return minCacheAge;
    }

    public TimeDuration getMaxCacheAge() {
        return maxCacheAge;
    }

    public TimeDuration getRestTime() {
        return restTime;
    }

    public boolean isAutoCalcRest() {
        return autoCalcRest;
    }

    public String getSearchFilter() {
        return searchFilter;
    }

    public int getJobOffsetSeconds() {
        return jobOffsetSeconds;
    }

    public int getMaxSearchSize() {
        return maxSearchSize;
    }

    public List<Integer> getTrackDays() {
        return trackDays;
    }
    
    public String getSettingsHash() 
            throws PwmUnrecoverableException 
    {
        return SecureEngine.hash(JsonUtil.serialize(this), PwmConstants.SETTING_CHECKSUM_HASH_METHOD);
    }
}

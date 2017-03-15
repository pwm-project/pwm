/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ReportSettings implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ReportSettings.class);
    
    private TimeDuration maxCacheAge = new TimeDuration(TimeDuration.DAY.getTotalMilliseconds() * 90);
    private String searchFilter = null;
    private int jobOffsetSeconds;
    private int maxSearchSize = 100 * 1000;
    private List<Integer> trackDays = new ArrayList<>();
    private int reportJobThreads = 1;
    private JobIntensity reportJobIntensity = JobIntensity.LOW;

    public enum JobIntensity {
        LOW,
        MEDIUM,
        HIGH,
    }

    public static ReportSettings readSettingsFromConfig(final Configuration config) {
        final ReportSettings settings = new ReportSettings();
        settings.maxCacheAge = new TimeDuration(config.readSettingAsLong(PwmSetting.REPORTING_MAX_CACHE_AGE) * 1000);
        settings.searchFilter = config.readSettingAsString(PwmSetting.REPORTING_SEARCH_FILTER);
        settings.maxSearchSize = (int)config.readSettingAsLong(PwmSetting.REPORTING_MAX_QUERY_SIZE);

        if (settings.searchFilter == null || settings.searchFilter.isEmpty()) {
            settings.searchFilter = null;
        }

        settings.jobOffsetSeconds = (int)config.readSettingAsLong(PwmSetting.REPORTING_JOB_TIME_OFFSET);
        if (settings.jobOffsetSeconds > 60 * 60 * 24) {
            settings.jobOffsetSeconds = 0;
        }

        settings.trackDays = parseDayIntervalStr(config);

        settings.reportJobThreads = Integer.parseInt(config.readAppProperty(AppProperty.REPORTING_LDAP_SEARCH_THREADS));

        settings.reportJobIntensity = config.readSettingAsEnum(PwmSetting.REPORTING_JOB_INTENSITY, JobIntensity.class);

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

    public TimeDuration getMaxCacheAge() {
        return maxCacheAge;
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

    public int getReportJobThreads() {
        return reportJobThreads;
    }

    public JobIntensity getReportJobIntensity() {
        return reportJobIntensity;
    }

    public String getSettingsHash()
            throws PwmUnrecoverableException 
    {
        return SecureEngine.hash(JsonUtil.serialize(this), PwmConstants.SETTING_CHECKSUM_HASH_METHOD);
    }
}

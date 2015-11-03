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

package password.pwm.ws.server.rest.bean;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.i18n.Admin;
import password.pwm.i18n.Display;
import password.pwm.util.LocaleHelper;
import password.pwm.util.TimeDuration;

import java.io.Serializable;
import java.util.*;

public class AppDashboardData implements Serializable {
    public static class DataElement {
        public String key;
        public Type type;
        public String label;
        public Object value;

        public DataElement(
                String key,
                Type type,
                String label,
                Object value
        )
        {
            this.key = key;
            this.type = type;
            this.label = label;
            this.value = value;
        }
    }

    public enum Type {
        string,
        timestamp,
        number,
    }

    public Map<String,DataElement> about = new LinkedHashMap<>();
    public Map<String,DataElement> appStats = new LinkedHashMap<>();

    public AppDashboardData()
    {
    }

    public static AppDashboardData makeDashboardData(
            final PwmApplication pwmApplication,
            final Locale locale
    )
    {
        if (pwmApplication == null) {
            return new AppDashboardData();
        }
        final Configuration config = pwmApplication.getConfig();
        final String NA_VALUE = Display.getLocalizedMessage(locale, Display.Value_NotApplicable, config);
        final LocaleHelper.DisplayMaker l = new LocaleHelper.DisplayMaker(locale, Admin.class, pwmApplication);

        AppDashboardData appDashboardData = new AppDashboardData();
        {
            final List<DataElement> data = new ArrayList<>();

            data.add(new DataElement(
                    "appVersion",
                    Type.string,
                    l.forKey("Field_AppVersion", PwmConstants.PWM_APP_NAME),
                    PwmConstants.SERVLET_VERSION
            ));
            if (config.readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) {
                if (pwmApplication.getVersionChecker() != null) {
                    final Date readDate = pwmApplication.getVersionChecker().lastReadTimestamp();
                    if (readDate != null) {
                        data.add(new DataElement(
                                "currentPubVersion",
                                Type.timestamp,
                                l.forKey("Field_CurrentPubVersion"),
                                readDate
                        ));
                    } else {
                        data.add(new DataElement(
                                "currentPubVersion",
                                Type.string,
                                l.forKey("Field_CurrentPubVersion"),
                                NA_VALUE
                        ));
                    }
                }
            }
            data.add(new DataElement(
                    "currentTime",
                    Type.timestamp,
                    l.forKey("Field_CurrentTime"),
                    new Date()
            ));
            data.add(new DataElement(
                    "startupTime",
                    Type.timestamp,
                    l.forKey("Field_StartTime"),
                    pwmApplication.getStartupTime()
            ));
            data.add(new DataElement(
                    "runningDuration",
                    Type.string,
                    l.forKey("Field_UpTime"),
                    TimeDuration.fromCurrent(pwmApplication.getStartupTime()).asLongString(locale)
            ));
            data.add(new DataElement(
                    "installTime",
                    Type.timestamp,
                    l.forKey("Field_InstallTime"),
                    pwmApplication.getInstallTime()
            ));
            data.add(new DataElement(
                    "siteURL",
                    Type.string,
                    l.forKey("Field_SiteURL"),
                    pwmApplication.getConfig().readSettingAsString(PwmSetting.PWM_SITE_URL)
            ));
            data.add(new DataElement(
                    "instanceID",
                    Type.string,
                    l.forKey("Field_InstanceID"),
                    pwmApplication.getInstanceID()
            ));
            data.add(new DataElement(
                    "chaiApiVersion",
                    Type.string,
                    l.forKey("Field_ChaiAPIVersion"),
                    com.novell.ldapchai.ChaiConstant.CHAI_API_VERSION
            ));


            final Map<String, DataElement> aboutMap = new LinkedHashMap<>();
            for (DataElement dataElement : data) {
                aboutMap.put(dataElement.key, dataElement);
            }
            appDashboardData.about = aboutMap;
        }

        {
            final List<DataElement> data = new ArrayList<>();

            data.add(new DataElement(
                    "appVersion",
                    Type.string,
                    l.forKey("Field_AppVersion", PwmConstants.PWM_APP_NAME),
                    PwmConstants.SERVLET_VERSION
            ));

            final Map<String, DataElement> statsMap = new LinkedHashMap<>();
            for (DataElement dataElement : data) {
                statsMap.put(dataElement.key, dataElement);
            }
            appDashboardData.appStats = statsMap;
        }


        return appDashboardData;
    }


}

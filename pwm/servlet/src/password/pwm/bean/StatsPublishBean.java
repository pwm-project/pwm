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

package password.pwm.bean;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class StatsPublishBean implements Serializable {
    private String instanceID;
    private Date timestamp;
    private Map<String,String> totalStatistics;
    private List<String> configuredSettings;
    private String versionBuild;
    private String versionVersion;
    private Map<String,String> otherInfo;

    public enum KEYS {
        SITE_URL,
        SITE_DESCRIPTION,
        INSTALL_DATE,
        LDAP_VENDOR
    }

    public StatsPublishBean() {
    }

    public StatsPublishBean(
            final String instanceID,
            final Date timestamp,
            final Map<String, String> totalStatistics,
            final List<String> configuredSettings,
            final String versionBuild,
            final String versionVersion,
            final Map<String,String> otherInfo
    ) {
        this.instanceID = instanceID;
        this.timestamp = timestamp;
        this.totalStatistics = totalStatistics;
        this.configuredSettings = configuredSettings;
        this.versionBuild = versionBuild;
        this.versionVersion = versionVersion;
        this.otherInfo = otherInfo;
    }

    public String getInstanceID() {
        return instanceID;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public Map<String, String> getTotalStatistics() {
        return totalStatistics;
    }

    public List<String> getConfiguredSettings() {
        return configuredSettings;
    }

    public String getVersionBuild() {
        return versionBuild;
    }

    public String getVersionVersion() {
        return versionVersion;
    }

    public Map<String, String> getOtherInfo() {
        return otherInfo;
    }
}

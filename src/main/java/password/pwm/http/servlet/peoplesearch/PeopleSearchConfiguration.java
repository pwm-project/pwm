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

package password.pwm.http.servlet.peoplesearch;

import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;

class PeopleSearchConfiguration {
    private final String photoAttribute;
    private final String photoUrlOverride;
    private final boolean photosEnabled;
    private final boolean orgChartEnabled;
    private final String orgChartParentAttr;
    private final String orgChartChildAttr;

    PeopleSearchConfiguration(final Configuration configuration) {
        photoAttribute = configuration.readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_ATTRIBUTE);
        photoUrlOverride = configuration.readSettingAsString(PwmSetting.PEOPLE_SEARCH_PHOTO_URL_OVERRIDE);
        photosEnabled = (photoAttribute != null && !photoAttribute.isEmpty())
                || (photoUrlOverride != null && !photoUrlOverride.isEmpty());

        orgChartParentAttr = configuration.readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_PARENT_ATTRIBUTE);
        orgChartChildAttr = configuration.readSettingAsString(PwmSetting.PEOPLE_SEARCH_ORGCHART_CHILD_ATTRIBUTE);
        orgChartEnabled = orgChartParentAttr != null && !orgChartParentAttr.isEmpty() && orgChartChildAttr != null && !orgChartChildAttr.isEmpty();
    }

    public String getPhotoAttribute() {
        return photoAttribute;
    }

    public String getPhotoUrlOverride() {
        return photoUrlOverride;
    }

    public boolean isPhotosEnabled() {
        return photosEnabled;
    }

    public boolean isOrgChartEnabled() {
        return orgChartEnabled;
    }

    public String getOrgChartParentAttr() {
        return orgChartParentAttr;
    }

    public String getOrgChartChildAttr() {
        return orgChartChildAttr;
    }
}

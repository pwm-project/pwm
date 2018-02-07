/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import lombok.Getter;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;

@Getter
public class PeopleSearchConfiguration
{
    private String photoAttribute;
    private String photoUrlOverride;
    private boolean photosEnabled;
    private boolean orgChartEnabled;
    private String orgChartParentAttr;
    private String orgChartChildAttr;
    private String orgChartAssistantAttr;
    private boolean orgChartShowChildCount;
    private int orgChartMaxParents;

    public static PeopleSearchConfiguration fromConfiguration( final Configuration configuration )
    {
        final PeopleSearchConfiguration config = new PeopleSearchConfiguration();
        config.photoAttribute = configuration.readSettingAsString( PwmSetting.PEOPLE_SEARCH_PHOTO_ATTRIBUTE );
        config.photoUrlOverride = configuration.readSettingAsString( PwmSetting.PEOPLE_SEARCH_PHOTO_URL_OVERRIDE );
        config.photosEnabled = ( config.photoAttribute != null && !config.photoAttribute.isEmpty() )
                || ( config.photoUrlOverride != null && !config.photoUrlOverride.isEmpty() );

        config.orgChartAssistantAttr = configuration.readSettingAsString( PwmSetting.PEOPLE_SEARCH_ORGCHART_ASSISTANT_ATTRIBUTE );
        config.orgChartParentAttr = configuration.readSettingAsString( PwmSetting.PEOPLE_SEARCH_ORGCHART_PARENT_ATTRIBUTE );
        config.orgChartChildAttr = configuration.readSettingAsString( PwmSetting.PEOPLE_SEARCH_ORGCHART_CHILD_ATTRIBUTE );
        config.orgChartEnabled = config.orgChartParentAttr != null
                && !config.orgChartParentAttr.isEmpty()
                && config.orgChartChildAttr != null
                && !config.orgChartChildAttr.isEmpty();

        config.orgChartShowChildCount = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.PEOPLESEARCH_ORGCHART_ENABLE_CHILD_COUNT ) );
        config.orgChartMaxParents = Integer.parseInt( configuration.readAppProperty( AppProperty.PEOPLESEARCH_ORGCHART_MAX_PARENTS ) );

        return config;
    }
}

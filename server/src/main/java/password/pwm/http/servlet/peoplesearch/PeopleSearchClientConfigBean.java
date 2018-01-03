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

package password.pwm.http.servlet.peoplesearch;

import lombok.Getter;
import lombok.Setter;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.FormConfiguration;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
public class PeopleSearchClientConfigBean implements Serializable
{

    private Map<String, String> peoplesearch_search_columns;
    private boolean peoplesearch_enablePhoto;
    private boolean peoplesearch_orgChartEnabled;
    private boolean orgChartShowChildCount;
    private int orgChartMaxParents;


    static PeopleSearchClientConfigBean fromConfig(
            final Configuration configuration,
            final PeopleSearchConfiguration peopleSearchConfiguration,
            final Locale locale
    )
    {
        final Map<String, String> searchColumns = new LinkedHashMap<>();
        final List<FormConfiguration> searchForm = configuration.readSettingAsForm( PwmSetting.PEOPLE_SEARCH_RESULT_FORM );
        for ( final FormConfiguration formConfiguration : searchForm )
        {
            searchColumns.put( formConfiguration.getName(),
                    formConfiguration.getLabel( locale ) );
        }

        final PeopleSearchClientConfigBean peopleSearchClientConfigBean = new PeopleSearchClientConfigBean();
        peopleSearchClientConfigBean.setPeoplesearch_search_columns( searchColumns );
        peopleSearchClientConfigBean.setPeoplesearch_enablePhoto( peopleSearchConfiguration.isPhotosEnabled() );
        peopleSearchClientConfigBean.setPeoplesearch_orgChartEnabled( peopleSearchConfiguration.isOrgChartEnabled() );
        peopleSearchClientConfigBean.setOrgChartShowChildCount( peopleSearchConfiguration.isOrgChartShowChildCount() );
        peopleSearchClientConfigBean.setOrgChartMaxParents( peopleSearchClientConfigBean.getOrgChartMaxParents() );

        return peopleSearchClientConfigBean;
    }
}

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

import lombok.Builder;
import lombok.Value;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class PeopleSearchClientConfigBean implements Serializable
{
    private Map<String, String> searchColumns;
    private boolean enablePhoto;
    private boolean orgChartEnabled;
    private boolean orgChartShowChildCount;
    private int orgChartMaxParents;
    private boolean enableExport;
    private int exportMaxDepth;


    static PeopleSearchClientConfigBean fromConfig(
            final PwmRequest pwmRequest,
            final PeopleSearchConfiguration peopleSearchConfiguration,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Configuration configuration = pwmRequest.getConfig();
        final Map<String, String> searchColumns = new LinkedHashMap<>();
        final List<FormConfiguration> searchForm = configuration.readSettingAsForm( PwmSetting.PEOPLE_SEARCH_RESULT_FORM );
        for ( final FormConfiguration formConfiguration : searchForm )
        {
            searchColumns.put( formConfiguration.getName(),
                    formConfiguration.getLabel( pwmRequest.getLocale() ) );
        }

        return PeopleSearchClientConfigBean.builder()
                .searchColumns( searchColumns )
                .enablePhoto( peopleSearchConfiguration.isPhotosEnabled( userIdentity, pwmRequest.getSessionLabel() ) )
                .orgChartEnabled( peopleSearchConfiguration.isOrgChartEnabled() )
                .orgChartShowChildCount( peopleSearchConfiguration.isOrgChartShowChildCount() )
                .orgChartMaxParents( peopleSearchConfiguration.getOrgChartMaxParents() )
                .enableExport( peopleSearchConfiguration.isEnableExportCsv() )
                .exportMaxDepth( peopleSearchConfiguration.getExportCsvMaxDepth() )
                .build();
    }
}

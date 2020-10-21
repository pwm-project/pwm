/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http.servlet.peoplesearch.bean;

import lombok.Builder;
import lombok.Value;
import password.pwm.bean.UserIdentity;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.peoplesearch.PeopleSearchConfiguration;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Value
@Builder
public class PeopleSearchClientConfigBean implements Serializable
{
    private Map<String, String> searchColumns;
    private boolean enableAdvancedSearch;
    private boolean enablePhoto;
    private boolean orgChartEnabled;
    private boolean orgChartShowChildCount;
    private int orgChartMaxParents;
    private int maxAdvancedSearchAttributes;
    private List<SearchAttributeBean> advancedSearchAttributes;
    private boolean enableOrgChartPrinting;
    private boolean enableExport;
    private int exportMaxDepth;
    private boolean enableMailtoLinks;
    private int mailtoLinkMaxDepth;


    public static PeopleSearchClientConfigBean fromConfig(
            final PwmRequest pwmRequest,
            final PeopleSearchConfiguration peopleSearchConfiguration,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Locale locale = pwmRequest.getLocale();

        final Map<String, String> searchColumns = new LinkedHashMap<>();
        final List<FormConfiguration> searchForm = peopleSearchConfiguration.getSearchResultForm();
        for ( final FormConfiguration formConfiguration : searchForm )
        {
            searchColumns.put( formConfiguration.getName(),
                    formConfiguration.getLabel( locale ) );
        }

        final List<SearchAttributeBean> searchAttributeBeans = SearchAttributeBean.searchAttributesFromForm(
                locale,
                peopleSearchConfiguration.getSearchForm() );

        return PeopleSearchClientConfigBean.builder()
                .searchColumns( searchColumns )
                .enablePhoto( peopleSearchConfiguration.isPhotosEnabled() )
                .orgChartEnabled( peopleSearchConfiguration.isOrgChartEnabled() )
                .orgChartShowChildCount( peopleSearchConfiguration.isOrgChartShowChildCount() )
                .orgChartMaxParents( peopleSearchConfiguration.getOrgChartMaxParents() )

                .enableAdvancedSearch( peopleSearchConfiguration.isEnableAdvancedSearch() )
                .enableOrgChartPrinting( peopleSearchConfiguration.isEnablePrinting() )

                .maxAdvancedSearchAttributes( 3 )
                .advancedSearchAttributes( searchAttributeBeans )

                .mailtoLinkMaxDepth( peopleSearchConfiguration.getMailtoLinksMaxDepth() )
                .enableMailtoLinks( peopleSearchConfiguration.isEnableMailtoLinks() )

                .enableExport( peopleSearchConfiguration.isEnableExportCsv() )
                .exportMaxDepth( peopleSearchConfiguration.getExportCsvMaxDepth() )

                .build();
    }
}

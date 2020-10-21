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

package password.pwm.http.servlet.peoplesearch;

import password.pwm.AppProperty;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.PeopleSearchProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.TimeDuration;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PeopleSearchConfiguration
{
    private final PeopleSearchProfile peopleSearchProfile;
    private final Configuration configuration;

    PeopleSearchConfiguration( final Configuration configuration, final PeopleSearchProfile peopleSearchProfile )
            throws PwmUnrecoverableException
    {
        this.configuration = configuration;
        this.peopleSearchProfile = peopleSearchProfile;
    }

    String getEmailAttribute( final UserIdentity userIdentity )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( configuration );
        return ldapProfile.readSettingAsString( PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE );
    }

    public boolean isPhotosEnabled( )
    {
        return peopleSearchProfile.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_PHOTO );
    }

    public boolean isOrgChartEnabled()
    {
        return peopleSearchProfile.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_ORGCHART );
    }

    String getOrgChartParentAttr( final UserIdentity userIdentity )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( configuration );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHART_PARENT );
    }

    String getOrgChartChildAttr( final UserIdentity userIdentity  )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( configuration );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHART_CHILD );
    }

    String getOrgChartAssistantAttr( final UserIdentity userIdentity  )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( configuration );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHART_ASSISTANT );
    }

    String getOrgChartWorkforceIDAttr( final UserIdentity userIdentity  )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( configuration );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHART_WORKFORCEID );
    }

    public boolean isOrgChartShowChildCount()
    {
        return Boolean.parseBoolean( configuration.readAppProperty( AppProperty.PEOPLESEARCH_ORGCHART_ENABLE_CHILD_COUNT ) );
    }

    public int getOrgChartMaxParents()
    {
        return Integer.parseInt( configuration.readAppProperty( AppProperty.PEOPLESEARCH_ORGCHART_MAX_PARENTS ) );
    }

    public boolean isEnableExportCsv()
    {
        return peopleSearchProfile.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_EXPORT );
    }

    public int getExportCsvMaxDepth()
    {
        return Integer.parseInt( configuration.readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_DEPTH ) );
    }

    public boolean isEnableMailtoLinks()
    {
        return peopleSearchProfile.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_TEAM_MAILTO );
    }

    public int getMailtoLinksMaxDepth( )
    {
        return Integer.parseInt( configuration.readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_DEPTH ) );
    }

    TimeDuration getMaxCacheTime()
    {
        final long seconds = peopleSearchProfile.readSettingAsLong( PwmSetting.PEOPLE_SEARCH_MAX_CACHE_SECONDS );
        return TimeDuration.of( seconds, TimeDuration.Unit.SECONDS );
    }

    TimeDuration getExportCsvMaxDuration( )
    {
        final int seconds = Integer.parseInt( configuration.readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_SECONDS ) );
        return TimeDuration.of( seconds, TimeDuration.Unit.SECONDS );
    }

    int getExportCsvMaxThreads( )
    {
        return Integer.parseInt( configuration.readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_THREADS ) );
    }

    int getExportCsvMaxItems( )
    {
        return Integer.parseInt( configuration.readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_ITEMS ) );
    }

    public String getSearchFilter()
    {
        return peopleSearchProfile.readSettingAsString( PwmSetting.PEOPLE_SEARCH_SEARCH_FILTER );
    }

    public List<UserPermission> getSearchPhotoFilter()
    {
        return peopleSearchProfile.readSettingAsUserPermission( PwmSetting.PEOPLE_SEARCH_PHOTO_QUERY_FILTER );
    }

    public List<FormConfiguration> getSearchForm()
    {
        return peopleSearchProfile.readSettingAsForm( PwmSetting.PEOPLE_SEARCH_SEARCH_FORM );
    }

    public List<FormConfiguration> getSearchResultForm()
    {
        return peopleSearchProfile.readSettingAsForm( PwmSetting.PEOPLE_SEARCH_RESULT_FORM );
    }

    public List<FormConfiguration> getSearchDetailForm()
    {
        return peopleSearchProfile.readSettingAsForm( PwmSetting.PEOPLE_SEARCH_DETAIL_FORM );
    }

    public boolean isUseProxy()
    {
        return peopleSearchProfile.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_USE_PROXY );
    }

    public List<String> getLdapBase()
    {
        return peopleSearchProfile.readSettingAsStringArray( PwmSetting.PEOPLE_SEARCH_SEARCH_BASE );
    }

    public List<String> getDisplayNameCardLables()
    {
        return peopleSearchProfile.readSettingAsStringArray( PwmSetting.PEOPLE_SEARCH_DISPLAY_NAMES_CARD_LABELS );
    }

    public String getDisplayName()
    {
        return peopleSearchProfile.readSettingAsString( PwmSetting.PEOPLE_SEARCH_DISPLAY_NAME );
    }

    Set<String> getSearchAttributes()
    {
        final List<FormConfiguration> searchForm = getSearchForm();
        return Collections.unmodifiableSet( new LinkedHashSet<>( FormConfiguration.convertToListOfNames( searchForm ) ) );
    }

    List<FormConfiguration> getResultForm()
    {
        return peopleSearchProfile.readSettingAsForm( PwmSetting.PEOPLE_SEARCH_RESULT_FORM );
    }

    int getResultLimit()
    {
        return ( int ) peopleSearchProfile.readSettingAsLong( PwmSetting.PEOPLE_SEARCH_RESULT_LIMIT );
    }

    public boolean isEnablePrinting()
    {
        return peopleSearchProfile.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_PRINTING );
    }

    public boolean isEnableAdvancedSearch()
    {
        return peopleSearchProfile.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_ADVANCED_SEARCH );
    }
}

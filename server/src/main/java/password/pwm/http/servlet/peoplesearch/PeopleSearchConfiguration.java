/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PeopleSearchConfiguration
{
    private final PwmRequest pwmRequest;
    private final PwmApplication pwmApplication;


    private PeopleSearchConfiguration( final PwmRequest pwmRequest )
    {
        this.pwmRequest = pwmRequest;
        this.pwmApplication = pwmRequest.getPwmApplication();
    }

    public String getPhotoAttribute( final UserIdentity userIdentity )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_PHOTO );
    }

    String getPhotoUrlOverride( final UserIdentity userIdentity )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_PHOTO_URL_OVERRIDE );
    }

    public boolean isPhotosEnabled( final UserIdentity actor, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        if ( actor == null )
        {
            return false;
        }

        final boolean settingEnabled = pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_PHOTO );
        final String photoAttribute = getPhotoAttribute( actor );
        final String photoUrl = getPhotoUrlOverride( actor );
        return settingEnabled
                && ( !StringUtil.isEmpty( photoAttribute ) || !StringUtil.isEmpty( photoUrl ) );
    }

    public boolean isOrgChartEnabled()
    {
        final Configuration config = pwmApplication.getConfig();
        return config.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_ORGCHART );
    }

    String getOrgChartParentAttr( final UserIdentity userIdentity )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHART_PARENT );
    }

    String getOrgChartChildAttr( final UserIdentity userIdentity  )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHART_CHILD );
    }

    String getOrgChartAssistantAttr( final UserIdentity userIdentity  )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHART_ASSISTANT );
    }

    String getOrgChartWorkforceIDAttr( final UserIdentity userIdentity  )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHART_WORKFORCEID );
    }

    public boolean isOrgChartShowChildCount()
    {
        return Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_ORGCHART_ENABLE_CHILD_COUNT ) );
    }

    public int getOrgChartMaxParents()
    {
        return Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_ORGCHART_MAX_PARENTS ) );
    }

    public boolean isEnableExportCsv()
    {
        return pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_EXPORT );
    }

    public int getExportCsvMaxDepth()
    {
        return Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_DEPTH ) );
    }

    public boolean isEnableMailtoLinks()
    {
        return pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_TEAM_MAILTO );
    }

    public int getMailtoLinksMaxDepth( )
    {
        return Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_DEPTH ) );
    }

    TimeDuration getExportCsvMaxDuration( )
    {
        final int seconds = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_SECONDS ) );
        return TimeDuration.of( seconds, TimeDuration.Unit.SECONDS );
    }

    int getExportCsvMaxThreads( )
    {
        return Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_THREADS ) );
    }

    int getExportCsvMaxItems( )
    {
        return Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_ITEMS ) );
    }

    public List<FormConfiguration> getSearchForm()
    {
        return pwmRequest.getConfig().readSettingAsForm( PwmSetting.PEOPLE_SEARCH_SEARCH_FORM );
    }

    Set<String> getSearchAttributes()
    {
        final List<FormConfiguration> searchForm = getSearchForm();

        return Collections.unmodifiableSet( new LinkedHashSet<>( FormConfiguration.convertToListOfNames( searchForm ) ) );
    }

    List<FormConfiguration> getResultForm()
    {
        return pwmRequest.getConfig().readSettingAsForm( PwmSetting.PEOPLE_SEARCH_RESULT_FORM );
    }

    int getResultLimit()
    {
        return ( int ) pwmRequest.getConfig().readSettingAsLong( PwmSetting.PEOPLE_SEARCH_RESULT_LIMIT );
    }

    public boolean isEnablePrinting()
    {
        return pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_PRINTING );
    }

    public static PeopleSearchConfiguration forRequest(
            final PwmRequest pwmRequest
    )
    {
        return new PeopleSearchConfiguration( pwmRequest );
    }

    public boolean isEnableAdvancedSearch()
    {
        return pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_ADVANCED_SEARCH );
    }
}

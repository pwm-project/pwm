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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.LdapPermissionTester;
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

    boolean isPhotosEnabled( final UserIdentity actor, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        if ( actor == null )
        {
            return false;
        }

        final List<UserPermission> permissions =  pwmApplication.getConfig().readSettingAsUserPermission( PwmSetting.PEOPLE_SEARCH_PHOTO_QUERY_FILTER );
        return LdapPermissionTester.testUserPermissions( pwmApplication, sessionLabel, actor, permissions );
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

    boolean isOrgChartShowChildCount( )
    {
        return Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_ORGCHART_ENABLE_CHILD_COUNT ) );
    }

    int getOrgChartMaxParents( )
    {
        return Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_ORGCHART_MAX_PARENTS ) );
    }

    boolean isEnableExportCsv( )
    {
        return pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_EXPORT );
    }

    int getExportCsvMaxDepth( )
    {
        return Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.PEOPLESEARCH_EXPORT_CSV_MAX_DEPTH ) );
    }

    boolean isEnableMailtoLinks( )
    {
        return pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_TEAM_MAILTO );
    }

    int getMailtoLinksMaxDepth( )
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

    List<FormConfiguration> getSearchForm()
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

    boolean isEnablePrinting()
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

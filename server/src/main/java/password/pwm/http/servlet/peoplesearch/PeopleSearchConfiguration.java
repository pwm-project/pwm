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
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.util.java.StringUtil;

import java.util.List;

public class PeopleSearchConfiguration
{
    private final PwmApplication pwmApplication;

    private boolean orgChartEnabled;
    private String orgChartParentAttr;
    private String orgChartChildAttr;
    private String orgChartAssistantAttr;
    private boolean orgChartShowChildCount;
    private int orgChartMaxParents;

    private PeopleSearchConfiguration( final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
    }

    public String getPhotoAttribute( final UserIdentity userIdentity )
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        return ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_PHOTO );
    }

    public String getPhotoUrlOverride( final UserIdentity userIdentity )
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

        final List<UserPermission> permissions =  pwmApplication.getConfig().readSettingAsUserPermission( PwmSetting.PEOPLE_SEARCH_PHOTO_QUERY_FILTER );
        return LdapPermissionTester.testUserPermissions( pwmApplication, sessionLabel, actor, permissions );

    }

    public boolean isOrgChartEnabled( )
    {
        return orgChartEnabled;
    }

    public String getOrgChartParentAttr( )
    {
        return orgChartParentAttr;
    }

    public String getOrgChartChildAttr( )
    {
        return orgChartChildAttr;
    }

    public String getOrgChartAssistantAttr( )
    {
        return orgChartAssistantAttr;
    }

    public boolean isOrgChartShowChildCount( )
    {
        return orgChartShowChildCount;
    }

    public int getOrgChartMaxParents( )
    {
        return orgChartMaxParents;
    }

    public static PeopleSearchConfiguration forRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final LdapProfile ldapProfile = pwmRequest.isAuthenticated()
                ? pwmRequest.getUserInfoIfLoggedIn().getLdapProfile( pwmRequest.getConfig() )
                : pwmRequest.getConfig().getDefaultLdapProfile();

        final Configuration configuration = pwmApplication.getConfig();
        final PeopleSearchConfiguration config = new PeopleSearchConfiguration( pwmApplication );
        config.orgChartAssistantAttr = ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHART_ASSISTANT );
        config.orgChartParentAttr = ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHART_PARENT );
        config.orgChartChildAttr = ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_ORGCHARD_CHILD );
        config.orgChartEnabled = configuration.readSettingAsBoolean( PwmSetting.PEOPLE_SEARCH_ENABLE_ORGCHART )
                && !StringUtil.isEmpty( config.orgChartParentAttr )
                && !StringUtil.isEmpty( config.orgChartChildAttr );

        config.orgChartShowChildCount = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.PEOPLESEARCH_ORGCHART_ENABLE_CHILD_COUNT ) );
        config.orgChartMaxParents = Integer.parseInt( configuration.readAppProperty( AppProperty.PEOPLESEARCH_ORGCHART_MAX_PARENTS ) );

        return config;
    }
}

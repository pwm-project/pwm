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

package password.pwm.config.profile;

import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.util.logging.PwmLogger;

import java.util.List;
import java.util.Map;

public class ProfileUtility
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ProfileUtility.class );

    public static String discoverProfileIDforUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ProfileType profileType
    )
            throws PwmUnrecoverableException
    {
        final Map<String, Profile> profileMap = pwmApplication.getConfig().profileMap( profileType );
        for ( final Profile profile : profileMap.values() )
        {
            final List<UserPermission> queryMatches = profile.getPermissionMatches();
            final boolean match = LdapPermissionTester.testUserPermissions( pwmApplication, sessionLabel, userIdentity, queryMatches );
            if ( match )
            {
                return profile.getIdentifier();
            }
        }
        return null;
    }

    public static List<String> profileIDsForCategory( final Configuration configuration, final PwmSettingCategory pwmSettingCategory )
    {
        final PwmSetting profileSetting = pwmSettingCategory.getProfileSetting();
        return configuration.readSettingAsStringArray( profileSetting );
    }


}

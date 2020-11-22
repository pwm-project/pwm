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

package password.pwm.config.profile;

import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequestContext;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.util.logging.PwmLogger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProfileUtility
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ProfileUtility.class );

    public static Optional<String> discoverProfileIDForUser(
            final PwmRequestContext pwmRequestContext,
            final UserIdentity userIdentity,
            final ProfileDefinition profileDefinition
    )
            throws PwmUnrecoverableException
    {
        return discoverProfileIDForUser( pwmRequestContext.getPwmApplication(), pwmRequestContext.getSessionLabel(), userIdentity, profileDefinition );
    }

    public static <T extends Profile> T profileForUser(
            final PwmRequestContext pwmRequestContext,
            final UserIdentity userIdentity,
            final ProfileDefinition profileDefinition,
            final Class<T> classOfT
    )
            throws PwmUnrecoverableException
    {
        final Optional<String> profileID = discoverProfileIDForUser( pwmRequestContext, userIdentity, profileDefinition );
        if ( !profileID.isPresent() )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_NO_PROFILE_ASSIGNED, "profile of type " + profileDefinition + " is required but not assigned" );
        }
        final Profile profileImpl = pwmRequestContext.getConfig().profileMap( profileDefinition ).get( profileID.get() );
        return ( T ) profileImpl;
    }


    public static Optional<String> discoverProfileIDForUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ProfileDefinition profileDefinition
    )
            throws PwmUnrecoverableException
    {
        final Map<String, Profile> profileMap = pwmApplication.getConfig().profileMap( profileDefinition );
        for ( final Profile profile : profileMap.values() )
        {
            final List<UserPermission> queryMatches = profile.profilePermissions();
            final boolean match = UserPermissionUtility.testUserPermission( pwmApplication, sessionLabel, userIdentity, queryMatches );
            if ( match )
            {
                return Optional.of( profile.getIdentifier() );
            }
        }
        return Optional.empty();
    }

    public static List<String> profileIDsForCategory( final Configuration configuration, final PwmSettingCategory pwmSettingCategory )
    {
        final PwmSetting profileSetting = pwmSettingCategory.getProfileSetting().orElseThrow( IllegalStateException::new );
        return configuration.readSettingAsStringArray( profileSetting );
    }


}

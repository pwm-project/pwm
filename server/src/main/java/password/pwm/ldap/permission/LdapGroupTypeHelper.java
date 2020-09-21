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

package password.pwm.ldap.permission;

import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;

class LdapGroupTypeHelper implements PermissionTypeHelper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapGroupTypeHelper.class );

    @Override
    public boolean testMatch(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final UserPermission userPermission
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final String groupDN = userPermission.getLdapQuery();

        if ( userIdentity == null )
        {
            return false;
        }

        LOGGER.trace( sessionLabel, () -> "begin check for ldapGroup match for " + userIdentity + " using queryMatch: " + groupDN );

        boolean result = false;
        if ( StringUtil.isEmpty( groupDN ) )
        {
            LOGGER.trace( sessionLabel, () -> "missing groupDN value, skipping check" );
        }
        else
        {
            final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
            final String filterString = "(" + ldapProfile.readSettingAsString( PwmSetting.LDAP_USER_GROUP_ATTRIBUTE ) + "=" + groupDN + ")";
            LOGGER.trace( sessionLabel, () -> "checking ldap to see if " + userIdentity + " matches group '" + groupDN + "' using filter '" + filterString + "'" );
            result = LdapQueryHelper.selfUserSearch( pwmApplication, sessionLabel, userIdentity, filterString );

        }

        {
            final boolean finalResult = result;
            LOGGER.debug( sessionLabel, () -> "user " + userIdentity.toDisplayString() + " is "
                    + ( finalResult ? "" : "not " )
                    + "a match for group '" + groupDN + "'"
                    + " (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
        }

        return result;
    }

    @Override
    public SearchConfiguration searchConfigurationFromPermission( final UserPermission userPermission )
            throws PwmUnrecoverableException
    {
        return SearchConfiguration.builder()
                .groupDN( userPermission.getLdapBase() )
                .ldapProfile( UserPermissionUtility.profileIdForPermission( userPermission ) )
                .build();
    }

    @Override
    public void validatePermission( final UserPermission userPermission ) throws PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( userPermission.getLdapBase() ) )
        {
            throw PwmUnrecoverableException.newException(
                    PwmError.CONFIG_FORMAT_ERROR,
                    "userPermission of type " + UserPermissionType.ldapGroup + " must have a ldapBase value" );
        }
    }
}

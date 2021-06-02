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
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.Objects;

class LdapUserDNTypeHelper implements PermissionTypeHelper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapUserDNTypeHelper.class );

    @Override
    public boolean testMatch(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final UserPermission userPermission
    )
            throws PwmUnrecoverableException
    {
        final String groupDN = userPermission.getLdapQuery();

        if ( userIdentity == null )
        {
            return false;
        }

        LOGGER.trace( sessionLabel, () -> "begin check for userDN match for " + userIdentity + " using compare DN" );
        if ( StringUtil.isEmpty( groupDN ) )
        {
            LOGGER.trace( sessionLabel, () -> "missing userDN value, skipping check" );
        }

        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        final String userCanonicalDN = ldapProfile.readCanonicalDN( pwmApplication, userIdentity.getUserDN() );
        final String configuredCanonicalDN = ldapProfile.readCanonicalDN( pwmApplication, userPermission.getLdapBase() );
        return Objects.equals( userCanonicalDN, configuredCanonicalDN );
    }

    @Override
    public SearchConfiguration searchConfigurationFromPermission( final UserPermission userPermission ) throws PwmUnrecoverableException
    {
        return SearchConfiguration.builder()
                .username( "*" )
                .enableContextValidation( false )
                .enableValueEscaping( false )
                .ldapProfile( UserPermissionUtility.profileIdForPermission( userPermission ) )
                .contexts( Collections.singletonList( userPermission.getLdapBase() ) )
                .searchScope( SearchConfiguration.SearchScope.base )
                .build();
    }

    @Override
    public void validatePermission( final UserPermission userPermission ) throws PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( userPermission.getLdapBase() ) )
        {
            throw PwmUnrecoverableException.newException(
                    PwmError.CONFIG_FORMAT_ERROR,
                    "userPermission of type " + UserPermissionType.ldapUser + " must have a ldapBase value" );
        }
    }
}

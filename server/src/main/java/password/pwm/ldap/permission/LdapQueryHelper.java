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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.provider.SearchScope;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.Map;

class LdapQueryHelper implements PermissionTypeHelper
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( UserPermissionUtility.class );

    @Override
    public boolean testMatch(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final UserPermission userPermission
    )
            throws PwmUnrecoverableException
    {
        if ( userPermission.getLdapBase() != null && !userPermission.getLdapBase().trim().isEmpty() )
        {
            final String canonicalBaseDN = pwmApplication.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() )
                    .readCanonicalDN( pwmApplication, userPermission.getLdapBase() );

            if ( !UserPermissionUtility.testBaseDnMatch( pwmApplication, canonicalBaseDN, userIdentity ) )
            {
                return false;
            }
        }

        if ( userIdentity == null )
        {
            return false;
        }

        final String filterString = userPermission.getLdapQuery();
        LOGGER.trace( sessionLabel, () -> "begin check for ldapQuery match for " + userIdentity + " using queryMatch: " + filterString );

        if ( StringUtil.isEmpty( filterString ) )
        {
            LOGGER.trace( sessionLabel, () -> "missing queryMatch value, skipping check" );
            return false;
        }

        if ( "(objectClass=*)".equalsIgnoreCase( filterString ) || "objectClass=*".equalsIgnoreCase( filterString ) )
        {
            LOGGER.trace( sessionLabel, () -> "queryMatch check is guaranteed to be true, skipping ldap query" );
            return true;
        }

        LOGGER.trace( sessionLabel, () -> "checking ldap to see if " + userIdentity + " matches '" + filterString + "'" );
        return selfUserSearch( pwmApplication, sessionLabel, userIdentity, filterString );
    }

    static boolean selfUserSearch(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final String searchFilter
    )
            throws PwmUnrecoverableException
    {
        try
        {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
            final Map<String, Map<String, String>> results = theUser.getChaiProvider().search(
                    theUser.getEntryDN(),
                    searchFilter,
                    Collections.emptySet(),
                    SearchScope.BASE );

            if ( results.size() == 1 && results.containsKey( theUser.getEntryDN() ) )
            {
                return true;
            }
        }
        catch ( final ChaiException e )
        {
            LOGGER.warn( sessionLabel, () -> "LDAP error during check for " + userIdentity + " using " + searchFilter + ", error:" + e.getMessage() );
        }

        return false;
    }




    @Override
    public SearchConfiguration searchConfigurationFromPermission( final UserPermission userPermission )
            throws PwmUnrecoverableException
    {
        return SearchConfiguration.builder()
                .filter( userPermission.getLdapQuery() )
                .ldapProfile( UserPermissionUtility.profileIdForPermission( userPermission ) )
                .build();
    }

    @Override
    public void validatePermission( final UserPermission userPermission ) throws PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( userPermission.getLdapQuery() ) )
        {
            throw PwmUnrecoverableException.newException(
                    PwmError.CONFIG_FORMAT_ERROR,
                    "userPermission of type " + UserPermissionType.ldapQuery + " must have a ldapQuery value" );
        }

        StringUtil.validateLdapSearchFilter( userPermission.getLdapQuery() );
    }
}

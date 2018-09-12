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

package password.pwm.ldap;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.provider.SearchScope;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LdapPermissionTester
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapPermissionTester.class );

    public static boolean testUserPermissions(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final List<UserPermission> userPermissions
    )
            throws PwmUnrecoverableException
    {
        if ( userPermissions == null )
        {
            return false;
        }

        for ( final UserPermission userPermission : userPermissions )
        {
            if ( testUserPermission( pwmApplication, sessionLabel, userIdentity, userPermission ) )
            {
                return true;
            }
        }
        return false;
    }

    private static boolean testUserPermission(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final UserPermission userPermission
    )
            throws PwmUnrecoverableException
    {
        if ( userPermission == null || userIdentity == null )
        {
            return false;
        }

        boolean profileAppliesToUser = false;
        if ( userPermission.getLdapProfileID() == null
                || userPermission.getLdapProfileID().isEmpty()
                || userPermission.getLdapProfileID().equals( PwmConstants.PROFILE_ID_ALL ) )
        {
            profileAppliesToUser = true;
        }
        else if ( userIdentity.getLdapProfileID().equals( userPermission.getLdapProfileID() ) )
        {
            profileAppliesToUser = true;
        }
        if ( !profileAppliesToUser )
        {
            return false;
        }

        switch ( userPermission.getType() )
        {
            case ldapQuery:
            {
                if ( userPermission.getLdapBase() != null && !userPermission.getLdapBase().trim().isEmpty() )
                {
                    if ( !testUserDNmatch( pwmApplication, sessionLabel, userPermission.getLdapBase(), userIdentity ) )
                    {
                        return false;
                    }
                }

                return testQueryMatch( pwmApplication, sessionLabel, userIdentity, userPermission.getLdapQuery() );
            }

            case ldapGroup:
            {
                return testGroupMatch( pwmApplication, sessionLabel, userIdentity, userPermission.getLdapBase() );
            }

            default:
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNKNOWN, "unknown permission type: " + userPermission.getType() ) );
        }
    }

    public static boolean testGroupMatch(
            final PwmApplication pwmApplication,
            final SessionLabel pwmSession,
            final UserIdentity userIdentity,
            final String groupDN
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        if ( userIdentity == null )
        {
            return false;
        }

        LOGGER.trace( pwmSession, "begin check for ldapGroup match for " + userIdentity + " using queryMatch: " + groupDN );

        boolean result = false;
        if ( groupDN == null || groupDN.length() < 1 )
        {
            LOGGER.trace( pwmSession, "missing groupDN value, skipping check" );
        }
        else
        {
            final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
            final String filterString = "(" + ldapProfile.readSettingAsString( PwmSetting.LDAP_USER_GROUP_ATTRIBUTE ) + "=" + groupDN + ")";
            try
            {
                LOGGER.trace( pwmSession, "checking ldap to see if " + userIdentity + " matches group '" + groupDN + "' using filter '" + filterString + "'" );
                final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
                final Map<String, Map<String, String>> results = theUser.getChaiProvider().search(
                        theUser.getEntryDN(),
                        filterString,
                        Collections.<String>emptySet(), SearchScope.BASE
                );
                if ( results.size() == 1 && results.keySet().contains( theUser.getEntryDN() ) )
                {
                    result = true;
                }
            }
            catch ( ChaiException e )
            {
                LOGGER.warn( pwmSession, "LDAP error during group for " + userIdentity + " using " + filterString + ", error:" + e.getMessage() );
            }
        }

        final String logMsg = "user " + userIdentity.toDisplayString() + " is "
                + ( result ? "" : "not " )
                + "a match for group '" + groupDN + "'"
                + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")";

        LOGGER.debug( pwmSession, logMsg );

        return result;
    }

    public static boolean testQueryMatch(
            final PwmApplication pwmApplication,
            final SessionLabel pwmSession,
            final UserIdentity userIdentity,
            final String filterString
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        if ( userIdentity == null )
        {
            return false;
        }

        LOGGER.trace( pwmSession, "begin check for ldapQuery match for " + userIdentity + " using queryMatch: " + filterString );

        boolean result = false;
        if ( filterString == null || filterString.length() < 1 )
        {
            LOGGER.trace( pwmSession, "missing queryMatch value, skipping check" );
        }
        else if ( "(objectClass=*)".equalsIgnoreCase( filterString ) || "objectClass=*".equalsIgnoreCase( filterString ) )
        {
            LOGGER.trace( pwmSession, "queryMatch check is guaranteed to be true, skipping ldap query" );
            result = true;
        }
        else
        {
            try
            {
                LOGGER.trace( pwmSession, "checking ldap to see if " + userIdentity + " matches '" + filterString + "'" );
                final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
                final Map<String, Map<String, String>> results = theUser.getChaiProvider().search( theUser.getEntryDN(), filterString, Collections.emptySet(), SearchScope.BASE );
                if ( results.size() == 1 && results.keySet().contains( theUser.getEntryDN() ) )
                {
                    result = true;
                }
            }
            catch ( ChaiException e )
            {
                LOGGER.warn( pwmSession, "LDAP error during check for " + userIdentity + " using " + filterString + ", error:" + e.getMessage() );
            }
        }

        final String logMsg = "user " + userIdentity.toDisplayString() + " is "
                + ( result ? "" : "not " )
                + "a match for filter '" + filterString + "'"
                + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")";

        LOGGER.debug( pwmSession, logMsg );

        return result;
    }

    public static Map<UserIdentity, Map<String, String>> discoverMatchingUsers(
            final PwmApplication pwmApplication,
            final int maxResultSize,
            final List<UserPermission> userPermissions,
            final SessionLabel sessionLabel
    )
            throws Exception
    {
        final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();

        final Map<UserIdentity, Map<String, String>> results = new TreeMap<>();
        for ( final UserPermission userPermission : userPermissions )
        {
            if ( ( maxResultSize ) - results.size() > 0 )
            {

                final SearchConfiguration.SearchConfigurationBuilder builder = SearchConfiguration.builder();

                switch ( userPermission.getType() )
                {
                    case ldapQuery:
                    {
                        builder.filter( userPermission.getLdapQuery() );
                        if ( userPermission.getLdapBase() != null && !userPermission.getLdapBase().isEmpty() )
                        {
                            builder.enableContextValidation( false );
                            builder.contexts( Collections.singletonList( userPermission.getLdapBase() ) );
                        }
                    }
                    break;

                    case ldapGroup:
                    {
                        builder.groupDN( userPermission.getLdapBase() );
                    }
                    break;

                    default:
                        throw new PwmUnrecoverableException( new ErrorInformation(
                                PwmError.ERROR_UNKNOWN,
                                "unknown permission type: " + userPermission.getType() )
                        );
                }

                if ( userPermission.getLdapProfileID() != null
                        && !userPermission.getLdapProfileID().isEmpty()
                        && !userPermission.getLdapProfileID().equals( PwmConstants.PROFILE_ID_ALL ) )
                {
                    builder.ldapProfile( userPermission.getLdapProfileID() );
                }

                final SearchConfiguration searchConfiguration = builder.build();

                try
                {
                    results.putAll( userSearchEngine.performMultiUserSearch(
                            searchConfiguration,
                            ( maxResultSize ) - results.size(),
                            Collections.emptyList(),
                            sessionLabel
                    ) );
                }
                catch ( PwmUnrecoverableException e )
                {
                    LOGGER.error( "error reading matching users: " + e.getMessage() );
                    throw new PwmOperationalException( e.getErrorInformation() );
                }
            }
        }

        return results;
    }

    private static boolean testUserDNmatch(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final String baseDN,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        if ( baseDN == null || baseDN.trim().isEmpty() )
        {
            return true;
        }

        final LdapProfile ldapProfile = userIdentity.getLdapProfile( pwmApplication.getConfig() );
        final String canonicalBaseDN = ldapProfile.readCanonicalDN( pwmApplication, baseDN );
        final String userDN = userIdentity.getUserDN();
        return userDN.endsWith( canonicalBaseDN );
    }
}

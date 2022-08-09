/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequestContext;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchService;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class UserPermissionUtility
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserPermissionUtility.class );

    public static boolean testUserPermission(
            final PwmRequestContext pwmRequestContext,
            final UserIdentity userIdentity,
            final UserPermission userPermissions
    )
            throws PwmUnrecoverableException
    {
        return testUserPermission(
                pwmRequestContext.getPwmDomain(),
                pwmRequestContext.getSessionLabel(),
                userIdentity,
                Collections.singletonList( userPermissions ) );
    }

    public static boolean testUserPermission(
            final PwmDomain pwmDomain,
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

        final List<UserPermission> sortedList = new ArrayList<>( userPermissions );
        Collections.sort( sortedList );

        for ( final UserPermission userPermission : sortedList )
        {
            if ( testUserPermission( pwmDomain, sessionLabel, userIdentity, userPermission ) )
            {
                return true;
            }
        }
        return false;
    }

    private static boolean checkIfProfileAppliesToUser(
            final UserIdentity userIdentity,
            final UserPermission userPermission
    )
    {
        return userPermission.getLdapProfileID() == null
                || userPermission.getLdapProfileID().isEmpty()
                || PwmConstants.PROFILE_ID_ALL.equals( userPermission.getLdapProfileID() )
                || userIdentity.getLdapProfileID().equals( userPermission.getLdapProfileID() );
    }

    private static boolean testUserPermission(
            final PwmDomain pwmDomain,
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

        if ( !checkIfProfileAppliesToUser( userIdentity, userPermission ) )
        {
            return false;
        }

        final PermissionTypeHelper permissionTypeHelper = userPermission.getType().getPermissionTypeTester();
        final Instant startTime = Instant.now();
        final boolean match = permissionTypeHelper.testMatch( pwmDomain, sessionLabel, userIdentity, userPermission );
        LOGGER.debug( sessionLabel, () -> "user " + userIdentity.toDisplayString() + " is "
                        + ( match ? "" : "not " )
                        + "a match for permission '" + userPermission + "'",
                TimeDuration.fromCurrent( startTime ) );
        return match;
    }

    public static Iterator<UserIdentity> discoverMatchingUsers(
            final PwmDomain pwmDomain,
            final List<UserPermission> userPermissions,
            final SessionLabel sessionLabel,
            final int maxResultSize,
            final TimeDuration maxSearchTime
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        if ( userPermissions == null )
        {
            return Collections.emptyIterator();
        }

        final List<UserPermission> sortedPermissions = new ArrayList<>( userPermissions );
        Collections.sort( sortedPermissions );

        final UserSearchService userSearchService = pwmDomain.getUserSearchEngine();
        final List<UserIdentity> resultSet = new ArrayList<>();

        for ( final UserPermission userPermission : sortedPermissions )
        {
            if ( ( maxResultSize ) - resultSet.size() > 0 )
            {
                final PermissionTypeHelper permissionTypeHelper = userPermission.getType().getPermissionTypeTester();
                final SearchConfiguration searchConfiguration = permissionTypeHelper.searchConfigurationFromPermission( userPermission )
                        .toBuilder()
                        .searchTimeout( maxSearchTime )
                        .build();

                try
                {
                    final Map<UserIdentity, Map<String, String>> results = userSearchService.performMultiUserSearch(
                            searchConfiguration,
                            ( maxResultSize ) - resultSet.size(),
                            Collections.emptyList(),
                            sessionLabel
                    );

                    resultSet.addAll( results.keySet() );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    LOGGER.error( () -> "error reading matching users: " + e.getMessage() );
                    throw new PwmOperationalException( e.getErrorInformation() );
                }
            }
        }

        final List<UserIdentity> strippedResults = stripUserMatchesOutsideUserContexts( sessionLabel, pwmDomain.getPwmApplication(), resultSet );
        return strippedResults.stream()
                .distinct()
                .sorted()
                .iterator();
    }

    static Optional<String> profileIdForPermission( final UserPermission userPermission )
    {
        if ( userPermission.getLdapProfileID() != null
                && !userPermission.getLdapProfileID().isEmpty()
                && !PwmConstants.PROFILE_ID_ALL.equals( userPermission.getLdapProfileID() ) )
        {
            return Optional.of( userPermission.getLdapProfileID() );
        }

        return Optional.empty();
    }

    public static void validatePermissionSyntax( final UserPermission userPermission )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( userPermission );

        if ( userPermission.getType() == null )
        {
            throw PwmUnrecoverableException.newException( PwmError.CONFIG_FORMAT_ERROR, "userPermission must have a type value" );
        }

        final PermissionTypeHelper permissionTypeHelper = userPermission.getType().getPermissionTypeTester();
        permissionTypeHelper.validatePermission( userPermission );
    }

    static List<UserIdentity> stripUserMatchesOutsideUserContexts(
            final SessionLabel sessionLabel,
            final PwmApplication pwmApplication,
            final List<UserIdentity> userIdentities
    )
    {
        final Instant startTime = Instant.now();
        final List<UserIdentity> output = userIdentities
                .stream()
                .filter( ( u ) -> testUserWithinConfiguredUserContexts( sessionLabel, pwmApplication, u ) )
                .collect( Collectors.toList() );

        final int removedValues = userIdentities.size() - output.size();
        if ( removedValues > 0 )
        {
            LOGGER.debug(
                    sessionLabel,
                    () -> "stripped " + removedValues + " user(s) from set of " + userIdentities.size() + " permission matches",
                    TimeDuration.fromCurrent( startTime ) );
        }
        return Collections.unmodifiableList( output );
    }

    public static boolean testUserWithinConfiguredUserContexts(
            final SessionLabel sessionLabel,
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity
    )
    {
        final String ldapProfileID = userIdentity.getLdapProfileID();
        final PwmDomain pwmDomain = pwmApplication.domains().get( userIdentity.getDomainID() );
        final LdapProfile ldapProfile = pwmDomain.getConfig().getLdapProfiles().get( ldapProfileID );

        try
        {
            final List<String> rootContexts = ldapProfile.getRootContexts( sessionLabel, pwmDomain );

            for ( final String rootContext : rootContexts )
            {
                if ( testBaseDnMatch( sessionLabel, pwmDomain, rootContext, userIdentity ) )
                {
                    return true;
                }
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.debug( sessionLabel,
                    () -> "unexpected error testing userIdentity " + userIdentity.toDisplayString() + " for configured ldapProfile user context match" );
        }

        LOGGER.trace( sessionLabel, () -> "stripping user " + userIdentity.toDisplayString()
                + " from permission list because it is not contained by configured user contexts in ldapProfile " + ldapProfileID  );

        return false;
    }

    static boolean testBaseDnMatch(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final String canonicalBaseDN,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        if ( StringUtil.isTrimEmpty( canonicalBaseDN ) )
        {
            return false;
        }

        final String userDN = userIdentity.canonicalized( sessionLabel, pwmDomain.getPwmApplication() ).getUserDN();
        return userDN.endsWith( canonicalBaseDN );
    }

    public static boolean isAllProfiles( final String profile )
    {
        return StringUtil.isEmpty( profile ) || PwmConstants.PROFILE_ID_ALL.equalsIgnoreCase( profile );
    }


}

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

package password.pwm.ws.server;

import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.NamedSecretData;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.ldap.auth.AuthenticationResult;
import password.pwm.ldap.auth.SimpleLdapAuthenticator;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RestAuthenticationProcessor
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestAuthentication.class );

    private final PwmApplication pwmApplication;
    private final HttpServletRequest httpServletRequest;
    private final SessionLabel sessionLabel;

    public RestAuthenticationProcessor(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final HttpServletRequest httpServletRequest
    )
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.httpServletRequest = httpServletRequest;
    }

    public RestAuthentication readRestAuthentication( ) throws PwmUnrecoverableException
    {
        {
            // named secret auth
            final String namedSecretName = readNamedSecretName();
            if ( namedSecretName != null )
            {
                LOGGER.trace( sessionLabel, "authenticating with named secret '" + namedSecretName + "'" );
                final Set<WebServiceUsage> usages = new HashSet<>( JavaHelper.readEnumListFromStringCollection(
                        WebServiceUsage.class,
                        pwmApplication.getConfig().readSettingAsNamedPasswords( PwmSetting.WEBSERVICES_EXTERNAL_SECRET ).get( namedSecretName ).getUsage()
                ) );
                return new RestAuthentication(
                        RestAuthenticationType.NAMED_SECRET,
                        namedSecretName,
                        null,
                        Collections.unmodifiableSet( usages ),
                        true,
                        null
                );
            }
        }

        {
            // ldap auth
            final UserIdentity userIdentity = readLdapUserIdentity();
            if ( userIdentity != null )
            {
                {
                    final List<UserPermission> userPermission = pwmApplication.getConfig().readSettingAsUserPermission( PwmSetting.WEBSERVICES_QUERY_MATCH );
                    final boolean result = LdapPermissionTester.testUserPermissions( pwmApplication, sessionLabel, userIdentity, userPermission );
                    if ( !result )
                    {
                        final String errorMsg = "user does not have webservice permission due to setting "
                                + PwmSetting.WEBSERVICES_QUERY_MATCH.toMenuLocationDebug( null, httpServletRequest.getLocale() );
                        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg ) );
                    }
                }

                final boolean thirdParty;
                {
                    final List<UserPermission> userPermission = pwmApplication.getConfig().readSettingAsUserPermission( PwmSetting.WEBSERVICES_THIRDPARTY_QUERY_MATCH );
                    thirdParty = LdapPermissionTester.testUserPermissions( pwmApplication, sessionLabel, userIdentity, userPermission );
                }

                final ChaiProvider chaiProvider = authenticateUser( userIdentity );

                verifyAuthUserIsNotSystemUser( userIdentity );

                return new RestAuthentication(
                        RestAuthenticationType.LDAP,
                        null,
                        userIdentity,
                        Collections.unmodifiableSet( new HashSet<>( Arrays.asList( WebServiceUsage.values() ) ) ),
                        thirdParty,
                        chaiProvider
                );
            }
        }

        final Set<WebServiceUsage> publicUsages;
        if ( pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES ) )
        {
            final WebServiceUsage[] usages = {
                    WebServiceUsage.Health,
                    WebServiceUsage.Statistics,
            };
            publicUsages = Collections.unmodifiableSet( new HashSet<>( Arrays.asList( usages ) ) );
        }
        else
        {
            publicUsages = Collections.emptySet();
        }

        return new RestAuthentication(
                RestAuthenticationType.PUBLIC,
                null,
                null,
                publicUsages,
                false,
                null
        );
    }

    private String readNamedSecretName( ) throws PwmUnrecoverableException
    {
        final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader( pwmApplication, httpServletRequest );
        if ( basicAuthInfo != null )
        {
            final String basicAuthUsername = basicAuthInfo.getUsername();
            final Map<String, NamedSecretData> secrets = pwmApplication.getConfig().readSettingAsNamedPasswords( PwmSetting.WEBSERVICES_EXTERNAL_SECRET );
            final NamedSecretData namedSecretData = secrets.get( basicAuthUsername );
            if ( namedSecretData != null )
            {
                if ( namedSecretData.getPassword().equals( basicAuthInfo.getPassword() ) )
                {
                    return basicAuthUsername;
                }
                throw PwmUnrecoverableException.newException( PwmError.ERROR_WRONGPASSWORD, "incorrect password value for named secret" );
            }
        }
        return null;
    }

    private void verifyAuthUserIsNotSystemUser( final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() );
        if ( ldapProfile != null )
        {
            {
                final UserIdentity testUser = ldapProfile.getTestUser( pwmApplication );
                if ( testUser != null && testUser.canonicalEquals( userIdentity, pwmApplication ) )
                {
                    final String msg = "rest services can not be authenticated using the configured LDAP profile test user";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_REST_INVOCATION_ERROR, msg );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }

            {
                final UserIdentity testUser = ldapProfile.getTestUser( pwmApplication );
                if ( testUser != null && testUser.canonicalEquals( userIdentity, pwmApplication ) )
                {
                    final String msg = "rest services can not be authenticated using the configured LDAP profile proxy user";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_REST_INVOCATION_ERROR, msg );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }
        }
    }

    private UserIdentity readLdapUserIdentity( ) throws PwmUnrecoverableException
    {
        final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader( pwmApplication, httpServletRequest );
        if ( basicAuthInfo == null )
        {
            return null;
        }

        final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
        try
        {
            return userSearchEngine.resolveUsername( basicAuthInfo.getUsername(), null, null, sessionLabel );
        }
        catch ( PwmOperationalException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation().wrapWithNewErrorCode( PwmError.ERROR_WRONGPASSWORD ) );
        }
    }

    private ChaiProvider authenticateUser( final UserIdentity userIdentity ) throws PwmUnrecoverableException
    {
        final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader( pwmApplication, httpServletRequest );

        final AuthenticationResult authenticationResult = SimpleLdapAuthenticator.authenticateUser( pwmApplication, sessionLabel, userIdentity, basicAuthInfo.getPassword() );
        return authenticationResult.getUserProvider();

    }
}

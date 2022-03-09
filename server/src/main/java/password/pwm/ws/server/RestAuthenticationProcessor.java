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

package password.pwm.ws.server;

import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmDomain;
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
import password.pwm.ldap.auth.AuthenticationResult;
import password.pwm.ldap.auth.SimpleLdapAuthenticator;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RestAuthenticationProcessor
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RestAuthentication.class );

    private final PwmDomain pwmDomain;
    private final HttpServletRequest httpServletRequest;
    private final SessionLabel sessionLabel;

    public RestAuthenticationProcessor(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final HttpServletRequest httpServletRequest
    )
    {
        this.pwmDomain = pwmDomain;
        this.sessionLabel = sessionLabel;
        this.httpServletRequest = httpServletRequest;
    }

    public RestAuthentication readRestAuthentication( ) throws PwmUnrecoverableException
    {
        {
            // named secret auth
            final Optional<String> namedSecretName = readNamedSecretName();
            if ( namedSecretName.isPresent() )
            {
                final String name = namedSecretName.get();
                LOGGER.trace( sessionLabel, () -> "authenticating with named secret '" + name + "'" );
                final Set<WebServiceUsage> usages = CollectionUtil.copyToEnumSet( CollectionUtil.readEnumSetFromStringCollection(
                        WebServiceUsage.class,
                        pwmDomain.getConfig().readSettingAsNamedPasswords( PwmSetting.WEBSERVICES_EXTERNAL_SECRET ).get( name ).getUsage()
                ), WebServiceUsage.class );
                return new RestAuthentication(
                        RestAuthenticationType.NAMED_SECRET,
                        name,
                        null,
                        Collections.unmodifiableSet( usages ),
                        true,
                        null
                );
            }
        }

        {
            // ldap auth
            final Optional<UserIdentity> userIdentity = readLdapUserIdentity();
            if ( userIdentity.isPresent() )
            {
                {
                    final List<UserPermission> userPermission = pwmDomain.getConfig().readSettingAsUserPermission( PwmSetting.WEBSERVICES_QUERY_MATCH );
                    final boolean result = UserPermissionUtility.testUserPermission( pwmDomain, sessionLabel, userIdentity.get(), userPermission );
                    if ( !result )
                    {
                        final String errorMsg = "user does not have webservice permission due to setting "
                                + PwmSetting.WEBSERVICES_QUERY_MATCH.toMenuLocationDebug( null, httpServletRequest.getLocale() );
                        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg ) );
                    }
                }

                final boolean thirdParty;
                {
                    final List<UserPermission> userPermission = pwmDomain.getConfig().readSettingAsUserPermission( PwmSetting.WEBSERVICES_THIRDPARTY_QUERY_MATCH );
                    thirdParty = UserPermissionUtility.testUserPermission( pwmDomain, sessionLabel, userIdentity.get(), userPermission );
                }

                final ChaiProvider chaiProvider = authenticateUser( userIdentity.get() );

                verifyAuthUserIsNotSystemUser( userIdentity.get() );

                return new RestAuthentication(
                        RestAuthenticationType.LDAP,
                        null,
                        userIdentity.get(),
                        Collections.unmodifiableSet( CollectionUtil.copyToEnumSet( WebServiceUsage.forType( RestAuthenticationType.LDAP ), WebServiceUsage.class ) ),
                        thirdParty,
                        chaiProvider
                );
            }
        }

        final Set<WebServiceUsage> publicUsages = WebServiceUsage.forType( RestAuthenticationType.PUBLIC );
        final Set<WebServiceUsage> enabledUsages = CollectionUtil.copyToEnumSet(
                pwmDomain.getConfig().readSettingAsOptionList( PwmSetting.WEBSERVICES_PUBLIC_ENABLE, WebServiceUsage.class ), WebServiceUsage.class );
        enabledUsages.retainAll( publicUsages );

        return new RestAuthentication(
                RestAuthenticationType.PUBLIC,
                null,
                null,
                Collections.unmodifiableSet( enabledUsages ),
                false,
                null
        );
    }

    private Optional<String> readNamedSecretName( )
            throws PwmUnrecoverableException
    {
        final Optional<BasicAuthInfo> basicAuthInfo = BasicAuthInfo.parseAuthHeader( pwmDomain, httpServletRequest );
        if ( basicAuthInfo.isPresent() )
        {
            final String basicAuthUsername = basicAuthInfo.get().getUsername();
            final Map<String, NamedSecretData> secrets = pwmDomain.getConfig().readSettingAsNamedPasswords( PwmSetting.WEBSERVICES_EXTERNAL_SECRET );
            final NamedSecretData namedSecretData = secrets.get( basicAuthUsername );
            if ( namedSecretData != null )
            {
                if ( namedSecretData.getPassword().equals( basicAuthInfo.get().getPassword() ) )
                {
                    return Optional.of( basicAuthUsername );
                }
                throw PwmUnrecoverableException.newException( PwmError.ERROR_WRONGPASSWORD, "incorrect password value for named secret" );
            }
        }
        return Optional.empty();
    }

    private void verifyAuthUserIsNotSystemUser( final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = pwmDomain.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() );
        if ( ldapProfile != null )
        {
            {
                final Optional<UserIdentity> optionalTestUser = ldapProfile.getTestUser( sessionLabel, pwmDomain );
                if ( optionalTestUser.isPresent() && optionalTestUser.get().canonicalEquals( sessionLabel, userIdentity, pwmDomain.getPwmApplication() ) )
                {
                    final String msg = "rest services can not be authenticated using the configured LDAP profile test user";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_REST_INVOCATION_ERROR, msg );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }

            {
                final UserIdentity proxyUser = ldapProfile.getProxyUser( sessionLabel, pwmDomain );
                if ( proxyUser.canonicalEquals( sessionLabel, userIdentity, pwmDomain.getPwmApplication() ) )
                {
                    final String msg = "rest services can not be authenticated using the configured LDAP profile proxy user";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_REST_INVOCATION_ERROR, msg );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }
        }
    }

    private Optional<UserIdentity> readLdapUserIdentity( ) throws PwmUnrecoverableException
    {
        final Optional<BasicAuthInfo> basicAuthInfo = BasicAuthInfo.parseAuthHeader( pwmDomain, httpServletRequest );
        if ( basicAuthInfo.isEmpty() )
        {
            return Optional.empty();
        }

        final UserSearchEngine userSearchEngine = pwmDomain.getUserSearchEngine();
        try
        {
            return Optional.of( userSearchEngine.resolveUsername( basicAuthInfo.get().getUsername(), null, null, sessionLabel ) );
        }
        catch ( final PwmOperationalException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation().wrapWithNewErrorCode( PwmError.ERROR_WRONGPASSWORD ) );
        }
    }

    private ChaiProvider authenticateUser( final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        final BasicAuthInfo basicAuthInfo = BasicAuthInfo.parseAuthHeader( pwmDomain, httpServletRequest )
                .orElseThrow();

        final AuthenticationResult authenticationResult = SimpleLdapAuthenticator.authenticateUser( pwmDomain, sessionLabel, userIdentity, basicAuthInfo.getPassword() );
        return authenticationResult.getUserProvider();

    }
}

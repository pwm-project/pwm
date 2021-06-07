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

package password.pwm.config.function;

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.SettingUIFunction;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Display;
import password.pwm.ldap.permission.UserPermissionType;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UserMatchViewerFunction implements SettingUIFunction
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserMatchViewerFunction.class );

    @Override
    public Serializable provideFunction(
            final PwmRequest pwmRequest,
            final StoredConfigurationModifier storedConfiguration,
            final StoredConfigKey key,
            final String extraData )
            throws Exception
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        final Instant startSearchTime = Instant.now();
        final int maxResultSize = Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.CONFIG_EDITOR_USER_PERMISSION_MATCH_LIMIT ) );
        final Collection<UserIdentity> users = discoverMatchingUsers(
                pwmRequest.getLabel(),
                pwmDomain,
                maxResultSize,
                storedConfiguration.newStoredConfiguration(),
                key );
        final TimeDuration searchDuration = TimeDuration.fromCurrent( startSearchTime );

        final String message = LocaleHelper.getLocalizedMessage(
                Display.Display_SearchResultsInfo, pwmRequest,
                String.valueOf( users.size() ),
                searchDuration.asLongString( pwmRequest.getLocale() ) );

        final boolean sizeExceeded = users.size() >= maxResultSize;

        return UserMatchViewerResults.builder()
                .users( users )
                .searchOperationSummary( message )
                .sizeExceeded( sizeExceeded )
                .build();
    }

    public List<UserIdentity> discoverMatchingUsers(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final int maxResultSize,
            final StoredConfiguration storedConfiguration,
            final StoredConfigKey key
    )
            throws Exception
    {
        final AppConfig config = new AppConfig( storedConfiguration );
        final PwmApplication tempApplication = PwmApplication.createPwmApplication( pwmDomain.getPwmApplication().getPwmEnvironment().makeRuntimeInstance( config ) );
        final StoredValue storedValue = StoredConfigurationUtil.getValueOrDefault( storedConfiguration, key );
        final List<UserPermission> permissions = ValueTypeConverter.valueToUserPermissions( storedValue );
        final PwmDomain tempDomain = tempApplication.domains().get( key.getDomainID() );

        validateUserPermissionLdapValues( sessionLabel, tempDomain, permissions );

        final int maxSearchSeconds = Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.CONFIG_EDITOR_USER_PERMISSION_TIMEOUT_SECONDS ) );
        final TimeDuration maxSearchTime = TimeDuration.of( maxSearchSeconds, TimeDuration.Unit.SECONDS );
        final Iterator<UserIdentity> matches =  UserPermissionUtility.discoverMatchingUsers( tempDomain, permissions, SessionLabel.SYSTEM_LABEL, maxResultSize, maxSearchTime );
        final List<UserIdentity> sortedResults = new ArrayList<>( CollectionUtil.iteratorToList( matches ) );
        Collections.sort( sortedResults );
        return Collections.unmodifiableList ( sortedResults );

    }

    private static void validateUserPermissionLdapValues(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final List<UserPermission> permissions
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        for ( final UserPermission userPermission : permissions )
        {
            if ( userPermission.getType() == UserPermissionType.ldapQuery )
            {
                if ( userPermission.getLdapBase() != null && !userPermission.getLdapBase().isEmpty() )
                {
                    testIfLdapDNIsValid( sessionLabel, pwmDomain, userPermission.getLdapBase(), userPermission.getLdapProfileID() );
                }
            }
            else if ( userPermission.getType() == UserPermissionType.ldapGroup )
            {
                testIfLdapDNIsValid( sessionLabel, pwmDomain, userPermission.getLdapBase(), userPermission.getLdapProfileID() );
            }
        }
    }


    private static void testIfLdapDNIsValid(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final String baseDN,
            final String profileID
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Set<String> profileIDsToTest = new LinkedHashSet<>();

        if ( UserPermissionUtility.isAllProfiles( profileID ) )
        {
            profileIDsToTest.addAll( pwmDomain.getConfig().getLdapProfiles().keySet() );
        }
        else
        {
            profileIDsToTest.add( profileID );
        }

        if ( profileIDsToTest.isEmpty() )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED, "invalid ldap profile" ) );
        }

        for ( final String loopID : profileIDsToTest )
        {
            ChaiEntry chaiEntry = null;
            try
            {
                final ChaiProvider proxiedProvider = pwmDomain.getProxyChaiProvider( sessionLabel, loopID );
                chaiEntry = proxiedProvider.getEntryFactory().newChaiEntry( baseDN );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error while testing entry DN for profile '" + profileID + "', error:" + profileID );
            }
            try
            {
                if ( chaiEntry != null && !chaiEntry.exists() )
                {
                    final String errorMsg = "entry DN '" + baseDN + "' is not valid for profile " + loopID;
                    throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_LDAP_DATA_ERROR, errorMsg ) );
                }
            }
            catch ( final ChaiUnavailableException e )
            {
                throw PwmUnrecoverableException.fromChaiException( e );
            }
        }
    }

    @Value
    @Builder
    public static class UserMatchViewerResults implements Serializable
    {
        private Collection<UserIdentity> users;
        private boolean sizeExceeded;
        private String searchOperationSummary;
    }
}

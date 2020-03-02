/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingUIFunction;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Display;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
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
            final PwmSetting setting,
            final String profile,
            final String extraData )
            throws Exception
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final Instant startSearchTime = Instant.now();
        final int maxResultSize = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.CONFIG_EDITOR_QUERY_FILTER_TEST_LIMIT ) );
        final Collection<UserIdentity> users = discoverMatchingUsers( pwmApplication, maxResultSize, storedConfiguration.newStoredConfiguration(), setting, profile );
        final TimeDuration searchDuration = TimeDuration.fromCurrent( startSearchTime );

        final UserMatchViewerResults userMatchViewerResults = new UserMatchViewerResults();
        final boolean sizeExceeded = users.size() >= maxResultSize;

        userMatchViewerResults.setUsers( users );
        userMatchViewerResults.setSearchOperationSummary(
                LocaleHelper.getLocalizedMessage(
                        Display.Display_SearchResultsInfo, pwmRequest,
                        String.valueOf( users.size() ),
                        searchDuration.asLongString( pwmRequest.getLocale() )
                ) );
        userMatchViewerResults.setSizeExceeded( sizeExceeded );
        return userMatchViewerResults;
    }

    public Collection<UserIdentity> discoverMatchingUsers(
            final PwmApplication pwmApplication,
            final int maxResultSize,
            final StoredConfiguration storedConfiguration,
            final PwmSetting setting,
            final String profile
    )
            throws Exception
    {
        final Configuration config = new Configuration( storedConfiguration );
        final PwmApplication tempApplication = PwmApplication.createPwmApplication( pwmApplication.getPwmEnvironment().makeRuntimeInstance( config ) );
        final List<UserPermission> permissions = ( List<UserPermission> ) storedConfiguration.readSetting( setting, profile ).toNativeObject();

        for ( final UserPermission userPermission : permissions )
        {
            if ( userPermission.getType() == UserPermission.Type.ldapQuery )
            {
                if ( userPermission.getLdapBase() != null && !userPermission.getLdapBase().isEmpty() )
                {
                    testIfLdapDNIsValid( tempApplication, userPermission.getLdapBase(), userPermission.getLdapProfileID() );
                }
            }
            else if ( userPermission.getType() == UserPermission.Type.ldapGroup )
            {
                testIfLdapDNIsValid( tempApplication, userPermission.getLdapBase(), userPermission.getLdapProfileID() );
            }
        }

        return LdapPermissionTester.discoverMatchingUsers( tempApplication, maxResultSize, permissions, SessionLabel.SYSTEM_LABEL ).keySet();
    }


    private void testIfLdapDNIsValid( final PwmApplication pwmApplication, final String baseDN, final String profileID )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Set<String> profileIDsToTest = new LinkedHashSet<>();
        if ( profileID == null || profileID.isEmpty() )
        {
            profileIDsToTest.add( pwmApplication.getConfig().getDefaultLdapProfile().getIdentifier() );
        }
        else if ( profileID.equals( PwmConstants.PROFILE_ID_ALL ) )
        {
            profileIDsToTest.addAll( pwmApplication.getConfig().getLdapProfiles().keySet() );
        }
        else
        {
            profileIDsToTest.add( profileID );
        }
        for ( final String loopID : profileIDsToTest )
        {
            ChaiEntry chaiEntry = null;
            try
            {
                final ChaiProvider proxiedProvider = pwmApplication.getProxyChaiProvider( loopID );
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

    public static class UserMatchViewerResults implements Serializable
    {
        private Collection<UserIdentity> users;
        private boolean sizeExceeded;
        private String searchOperationSummary;

        public Collection<UserIdentity> getUsers( )
        {
            return users;
        }

        public void setUsers( final Collection<UserIdentity> users )
        {
            this.users = users;
        }

        public boolean isSizeExceeded( )
        {
            return sizeExceeded;
        }

        public void setSizeExceeded( final boolean sizeExceeded )
        {
            this.sizeExceeded = sizeExceeded;
        }

        public String getSearchOperationSummary( )
        {
            return searchOperationSummary;
        }

        public void setSearchOperationSummary( final String searchOperationSummary )
        {
            this.searchOperationSummary = searchOperationSummary;
        }
    }
}

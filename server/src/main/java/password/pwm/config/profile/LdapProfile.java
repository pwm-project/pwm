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

package password.pwm.config.profile;

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.cache.CacheKey;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LdapProfile extends AbstractProfile implements Profile
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapProfile.class );

    private static final ProfileDefinition PROFILE_TYPE = ProfileDefinition.LdapProfile;

    protected LdapProfile( final String identifier, final Map<PwmSetting, StoredValue> storedValueMap )
    {
        super( identifier, storedValueMap );
    }

    public Map<String, String> getSelectableContexts(
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException
    {
        final List<String> rawValues = readSettingAsStringArray( PwmSetting.LDAP_LOGIN_CONTEXTS );
        final Map<String, String> configuredValues = StringUtil.convertStringListToNameValuePair( rawValues, ":::" );
        final Map<String, String> canonicalValues = new LinkedHashMap<>();
        for ( final Map.Entry<String, String> entry : configuredValues.entrySet() )
        {
            final String dn = entry.getKey();
            final String label = entry.getValue();
            final String canonicalDN = readCanonicalDN( pwmApplication, dn );
            canonicalValues.put( canonicalDN, label );
        }
        return Collections.unmodifiableMap( canonicalValues );
    }

    public List<String> getRootContexts(
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException
    {
        final List<String> rawValues = readSettingAsStringArray( PwmSetting.LDAP_CONTEXTLESS_ROOT );
        final List<String> canonicalValues = new ArrayList<>();
        for ( final String dn : rawValues )
        {
            final String canonicalDN = readCanonicalDN( pwmApplication, dn );
            canonicalValues.add( canonicalDN );
        }
        return Collections.unmodifiableList( canonicalValues );
    }

    public List<String> getLdapUrls(
    )
    {
        return readSettingAsStringArray( PwmSetting.LDAP_SERVER_URLS );
    }

    @Override
    public String getDisplayName( final Locale locale )
    {
        final String displayName = readSettingAsLocalizedString( PwmSetting.LDAP_PROFILE_DISPLAY_NAME, locale );
        return displayName == null || displayName.length() < 1 ? getIdentifier() : displayName;
    }

    public String getUsernameAttribute( )
    {
        final String configUsernameAttr = this.readSettingAsString( PwmSetting.LDAP_USERNAME_ATTRIBUTE );
        final String ldapNamingAttribute = this.readSettingAsString( PwmSetting.LDAP_NAMING_ATTRIBUTE );
        return configUsernameAttr != null && configUsernameAttr.length() > 0 ? configUsernameAttr : ldapNamingAttribute;
    }

    public ChaiProvider getProxyChaiProvider( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        return pwmApplication.getProxyChaiProvider( this.getIdentifier() );
    }

    @Override
    public ProfileDefinition profileType( )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<UserPermission> getPermissionMatches( )
    {
        throw new UnsupportedOperationException();
    }

    public String readCanonicalDN(
            final PwmApplication pwmApplication,
            final String dnValue
    )
            throws PwmUnrecoverableException
    {
        {
            final boolean doCanonicalDnResolve = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_RESOLVE_CANONICAL_DN ) );
            if ( !doCanonicalDnResolve )
            {
                return dnValue;
            }
        }

        final boolean enableCanonicalCache = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_CACHE_CANONICAL_ENABLE ) );

        String canonicalValue = null;
        final CacheKey cacheKey = CacheKey.newKey( LdapProfile.class, null, "canonicalDN-" + this.getIdentifier() + "-" + dnValue );
        if ( enableCanonicalCache )
        {
            final String cachedDN = pwmApplication.getCacheService().get( cacheKey, String.class );
            if ( cachedDN != null )
            {
                canonicalValue = cachedDN;
            }
        }

        if ( canonicalValue == null )
        {
            try
            {
                final ChaiProvider chaiProvider = this.getProxyChaiProvider( pwmApplication );
                final ChaiEntry chaiEntry = chaiProvider.getEntryFactory().newChaiEntry( dnValue );
                canonicalValue = chaiEntry.readCanonicalDN();

                if ( enableCanonicalCache )
                {
                    final long cacheSeconds = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_CACHE_CANONICAL_SECONDS ) );
                    final CachePolicy cachePolicy = CachePolicy.makePolicyWithExpiration( TimeDuration.of( cacheSeconds, TimeDuration.Unit.SECONDS ) );
                    pwmApplication.getCacheService().put( cacheKey, cachePolicy, canonicalValue );
                }

                {
                    final String finalCanonical = canonicalValue;
                    LOGGER.trace( () -> "read and cached canonical ldap DN value for input '" + dnValue + "' as '" + finalCanonical + "'" );
                }
            }
            catch ( final ChaiUnavailableException | ChaiOperationException e )
            {
                LOGGER.error( () -> "error while reading canonicalDN for dn value '" + dnValue + "', error: " + e.getMessage() );
                return dnValue;
            }
        }

        return canonicalValue;
    }

    public UserIdentity getTestUser( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        return readUserIdentity( pwmApplication, PwmSetting.LDAP_TEST_USER_DN );
    }

    public UserIdentity getProxyUser( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        return readUserIdentity( pwmApplication, PwmSetting.LDAP_PROXY_USER_DN );
    }

    private UserIdentity readUserIdentity( final PwmApplication pwmApplication, final PwmSetting pwmSetting ) throws PwmUnrecoverableException
    {
        final String testUserDN = this.readSettingAsString( pwmSetting );

        if ( !StringUtil.isEmpty( testUserDN ) )
        {
            final String canonicalDN = readCanonicalDN( pwmApplication, testUserDN );
            return new UserIdentity( canonicalDN, this.getIdentifier() );
        }

        return null;
    }

    public static class LdapProfileFactory implements ProfileFactory
    {
        @Override
        public Profile makeFromStoredConfiguration( final StoredConfiguration storedConfiguration, final String identifier )
        {
            return new LdapProfile( identifier, makeValueMap( storedConfiguration, identifier, PROFILE_TYPE.getCategory() ) );
        }
    }

    @Override
    public String toString()
    {
        return "LDAPProfile:" + this.getIdentifier();
    }
}

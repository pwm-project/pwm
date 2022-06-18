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

package password.pwm.config.profile;

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.cache.CacheKey;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class LdapProfile extends AbstractProfile implements Profile
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapProfile.class );

    private static final ProfileDefinition PROFILE_TYPE = ProfileDefinition.LdapProfile;

    protected LdapProfile( final DomainID domainID, final String identifier, final StoredConfiguration storedValueMap )
    {
        super( domainID, identifier, storedValueMap );
    }

    public Map<String, String> getSelectableContexts(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain
    )
            throws PwmUnrecoverableException
    {
        final List<String> rawValues = readSettingAsStringArray( PwmSetting.LDAP_LOGIN_CONTEXTS );
        final Map<String, String> configuredValues = StringUtil.convertStringListToNameValuePair( rawValues, ":::" );
        final Map<String, String> canonicalValues = new LinkedHashMap<>( configuredValues.size() );
        for ( final Map.Entry<String, String> entry : configuredValues.entrySet() )
        {
            final String dn = entry.getKey();
            final String label = entry.getValue();
            final String canonicalDN = readCanonicalDN( sessionLabel, pwmDomain, dn );
            canonicalValues.put( canonicalDN, label );
        }
        return Collections.unmodifiableMap( canonicalValues );
    }

    public List<String> getRootContexts(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain
    )
            throws PwmUnrecoverableException
    {
        final List<String> rawValues = readSettingAsStringArray( PwmSetting.LDAP_CONTEXTLESS_ROOT );
        final List<String> canonicalValues = new ArrayList<>( rawValues.size() );
        for ( final String dn : rawValues )
        {
            final String canonicalDN = readCanonicalDN( sessionLabel, pwmDomain, dn );
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

    public ChaiProvider getProxyChaiProvider( final SessionLabel sessionLabel, final PwmDomain pwmDomain ) throws PwmUnrecoverableException
    {
        verifyIsEnabled();
        return pwmDomain.getProxyChaiProvider( sessionLabel, this.getIdentifier() );
    }

    @Override
    public ProfileDefinition profileType( )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<UserPermission> profilePermissions( )
    {
        throw new UnsupportedOperationException();
    }

    public String readCanonicalDN(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final String dnValue
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        {
            final boolean doCanonicalDnResolve = Boolean.parseBoolean( pwmDomain.getConfig().readAppProperty( AppProperty.LDAP_RESOLVE_CANONICAL_DN ) );
            if ( !doCanonicalDnResolve )
            {
                return dnValue;
            }
        }

        final boolean enableCanonicalCache = Boolean.parseBoolean( pwmDomain.getConfig().readAppProperty( AppProperty.LDAP_CACHE_CANONICAL_ENABLE ) );

        String canonicalValue = null;
        final CacheKey cacheKey = CacheKey.newKey( LdapProfile.class, null, "canonicalDN-" + this.getIdentifier() + "-" + dnValue );
        if ( enableCanonicalCache )
        {
            final String cachedDN = pwmDomain.getCacheService().get( cacheKey, String.class );
            if ( cachedDN != null )
            {
                canonicalValue = cachedDN;
            }
        }

        if ( canonicalValue == null )
        {
            try
            {
                final ChaiProvider chaiProvider = this.getProxyChaiProvider( sessionLabel, pwmDomain );
                final ChaiEntry chaiEntry = chaiProvider.getEntryFactory().newChaiEntry( dnValue );
                canonicalValue = chaiEntry.readCanonicalDN();

                if ( enableCanonicalCache )
                {
                    final long cacheSeconds = Long.parseLong( pwmDomain.getConfig().readAppProperty( AppProperty.LDAP_CACHE_CANONICAL_SECONDS ) );
                    final CachePolicy cachePolicy = CachePolicy.makePolicyWithExpiration( TimeDuration.of( cacheSeconds, TimeDuration.Unit.SECONDS ) );
                    pwmDomain.getCacheService().put( cacheKey, cachePolicy, canonicalValue );
                }

                {
                    final String finalCanonical = canonicalValue;
                    LOGGER.trace( () -> "read and cached canonical ldap DN value for input '" + dnValue + "' as '" + finalCanonical + "'",
                            TimeDuration.fromCurrent( startTime ) );
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

    public Optional<UserIdentity> getTestUser( final SessionLabel sessionLabel, final PwmDomain pwmDomain )
            throws PwmUnrecoverableException
    {
        return readUserIdentity( sessionLabel, pwmDomain, PwmSetting.LDAP_TEST_USER_DN );
    }

    public UserIdentity getProxyUser( final SessionLabel sessionLabel, final PwmDomain pwmDomain )
            throws PwmUnrecoverableException
    {
        return readUserIdentity( sessionLabel, pwmDomain, PwmSetting.LDAP_PROXY_USER_DN )
                .orElseThrow( () ->
                        new PwmUnrecoverableException( new ErrorInformation(
                                PwmError.CONFIG_FORMAT_ERROR,
                                "ldap proxy user is not defined" ) ) );
    }

    private Optional<UserIdentity> readUserIdentity(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final PwmSetting pwmSetting
    )
            throws PwmUnrecoverableException
    {
        final String testUserDN = this.readSettingAsString( pwmSetting );

        if ( StringUtil.notEmpty( testUserDN ) )
        {
            return Optional.of( UserIdentity.create( testUserDN, this.getIdentifier(), pwmDomain.getDomainID() ).canonicalized( sessionLabel, pwmDomain.getPwmApplication() ) );
        }

        return Optional.empty();
    }

    public static class LdapProfileFactory implements ProfileFactory
    {
        @Override
        public Profile makeFromStoredConfiguration( final StoredConfiguration storedConfiguration, final DomainID domainID, final String identifier )
        {
            return new LdapProfile( domainID, identifier, storedConfiguration );
        }
    }

    private void verifyIsEnabled()
            throws PwmUnrecoverableException
    {
        if ( !isEnabled() )
        {
            final String msg = "ldap profile '" + getIdentifier() + "' is not enabled";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, msg ) );
        }
    }

    public boolean isEnabled()
    {
        return readSettingAsBoolean( PwmSetting.LDAP_PROFILE_ENABLED );
    }

    @Override
    public String toString()
    {
        return "LDAPProfile:" + this.getIdentifier();
    }
}

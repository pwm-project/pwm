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

package password.pwm.bean;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.AppConfig;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.cache.CacheKey;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.svc.cache.CacheService;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.StringTokenizer;

@SuppressFBWarnings( "SE_TRANSIENT_FIELD_NOT_RESTORED" )
public class UserIdentity implements Serializable, Comparable<UserIdentity>
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserIdentity.class );
    private static final long serialVersionUID = 1L;

    private static final String CRYPO_HEADER = "ui_C-";
    private static final String DELIM_SEPARATOR = "|";

    private static final Comparator<UserIdentity> COMPARATOR = Comparator.comparing(
            UserIdentity::getDomainID,
            Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    UserIdentity::getLdapProfileID,
                    Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    UserIdentity::getDomainID,
                    Comparator.nullsLast( Comparator.naturalOrder() ) );

    public static final String XML_DOMAIN = "domain";
    public static final String XML_LDAP_PROFILE = "ldapProfile";
    public static final String XML_DISTINGUISHED_NAME = "distinguishedName";
    public static final String XML_USER_IDENTITY = "userIdentity";


    private transient String obfuscatedValue;
    private transient boolean canonical;

    private final String userDN;
    private final String ldapProfile;
    private final DomainID domainID;

    public enum Flag
    {
        PreCanonicalized,
    }

    private UserIdentity( final String userDN, final String ldapProfile, final DomainID domainID )
    {
        this.userDN = JavaHelper.requireNonEmpty( userDN, "UserIdentity: userDN value cannot be empty" );
        this.ldapProfile = JavaHelper.requireNonEmpty( ldapProfile, "UserIdentity: ldapProfile value cannot be empty" );
        this.domainID = Objects.requireNonNull( domainID );
    }

    public UserIdentity( final String userDN, final String ldapProfile, final DomainID domainID, final boolean canonical )
    {
        this( userDN, ldapProfile, domainID );
        this.canonical = canonical;
    }

    public static UserIdentity create(
            final String userDN,
            final String ldapProfile,
            final DomainID domainID,
            final Flag... flags
    )
    {
        final boolean canonical = JavaHelper.enumArrayContainsValue( flags, Flag.PreCanonicalized );
        return new UserIdentity( userDN, ldapProfile, domainID, canonical );
    }

    public String getUserDN( )
    {
        return userDN;
    }

    public DomainID getDomainID()
    {
        return domainID;
    }

    public String getLdapProfileID( )
    {
        return ldapProfile;
    }

    public LdapProfile getLdapProfile( final AppConfig appConfig )
    {
        Objects.requireNonNull( appConfig );
        final LdapProfile ldapProfile = appConfig.getDomainConfigs().get( domainID ).getLdapProfiles().get( this.getLdapProfileID() );
        if ( ldapProfile == null )
        {
            throw new IllegalStateException( "bogus ldapProfileID on userIdentity: "  + this.getLdapProfileID() );
        }
        return ldapProfile;
    }

    public String toString( )
    {
        return toDisplayString();
    }

    public String toObfuscatedKey( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        // use local cache first.
        if ( !StringUtil.isEmpty( obfuscatedValue ) )
        {
            return obfuscatedValue;
        }

        // check app cache.  This is used primarily so that keys are static over some meaningful lifetime, allowing browser caching based on keys.
        final CacheService cacheService = pwmApplication.getCacheService();
        final CacheKey cacheKey = CacheKey.newKey( this.getClass(), this, "obfuscatedKey" );
        final String cachedValue = cacheService.get( cacheKey, String.class );

        if ( !StringUtil.isEmpty( cachedValue ) )
        {
            obfuscatedValue = cachedValue;
            return cachedValue;
        }

        // generate key
        try
        {
            final String jsonValue = JsonUtil.serialize( this );
            final String localValue = CRYPO_HEADER + pwmApplication.getSecureService().encryptToString( jsonValue );
            this.obfuscatedValue = localValue;
            cacheService.put( cacheKey, CachePolicy.makePolicyWithExpiration( TimeDuration.DAY ), localValue );
            return localValue;
        }
        catch ( final Exception e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "unexpected error making obfuscated user key: " + e.getMessage() ) );
        }
    }

    public String toDelimitedKey( )
    {
        return JsonUtil.serialize( this );
    }

    public String toDisplayString( )
    {
        return this.getUserDN() + ( ( this.getLdapProfileID() != null && !this.getLdapProfileID().isEmpty() ) ? " (" + this.getLdapProfileID() + ")" : "" );
    }

    public static UserIdentity fromObfuscatedKey( final String key, final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( pwmApplication );
        JavaHelper.requireNonEmpty( key, "key can not be null or empty" );

        if ( !key.startsWith( CRYPO_HEADER ) )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "cannot reverse obfuscated user key: missing header; value=" + key ) );
        }

        try
        {
            final String input = key.substring( CRYPO_HEADER.length() );
            final String jsonValue = pwmApplication.getSecureService().decryptStringValue( input );
            return JsonUtil.deserialize( jsonValue, UserIdentity.class );
        }
        catch ( final Exception e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "unexpected error reversing obfuscated user key: " + e.getMessage() ) );
        }
    }

    public static UserIdentity fromDelimitedKey( final String key )
            throws PwmUnrecoverableException
    {
        JavaHelper.requireNonEmpty( key );

        try
        {
            return JsonUtil.deserialize( key, UserIdentity.class );
        }
        catch ( final Exception e )
        {
            LOGGER.trace( () -> "unable to deserialize UserIdentity: " + key + " using JSON method: " + e.getMessage() );
        }

        // old style
        final StringTokenizer st = new StringTokenizer( key, DELIM_SEPARATOR );

        DomainID domainID = PwmConstants.DOMAIN_ID_PLACEHOLDER;
        if ( st.countTokens() < 2 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "not enough tokens while parsing delimited identity key" ) );
        }
        else if ( st.countTokens() > 2 )
        {
            String domainStr = "";
            try
            {
                domainStr = st.nextToken();
                domainID = DomainID.create( domainStr );
            }
            catch ( final Exception e )
            {
                final String fDomainStr = domainStr;
                LOGGER.trace( () -> "error decoding DomainID '" + fDomainStr + "' from delimited UserIdentity: " + e.getMessage() );
            }
        }

        if ( st.countTokens() > 3 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "too many string tokens while parsing delimited identity key" ) );
        }
        final String profileID = st.nextToken();
        final String userDN = st.nextToken();
        return create( userDN, profileID, domainID );
    }

    /**
     * Attempt to de-serialize value using delimited or obfuscated key.
     *
     * @deprecated  Should be used by calling {@link #fromDelimitedKey(String)} or {@link #fromObfuscatedKey(String, PwmApplication)}.
     */
    @Deprecated
    public static UserIdentity fromKey( final String key, final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        JavaHelper.requireNonEmpty( key );

        if ( key.startsWith( CRYPO_HEADER ) )
        {
            return fromObfuscatedKey( key, pwmApplication );
        }

        return fromDelimitedKey( key );
    }

    public boolean canonicalEquals( final UserIdentity otherIdentity, final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        if ( otherIdentity == null )
        {
            return false;
        }

        final UserIdentity thisCanonicalIdentity = this.canonicalized( pwmApplication );
        final UserIdentity otherCanonicalIdentity = otherIdentity.canonicalized( pwmApplication );
        return thisCanonicalIdentity.equals( otherCanonicalIdentity );
    }

    @Override
    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }
        final UserIdentity that = ( UserIdentity ) o;
        return Objects.equals( domainID, that.domainID )
                && Objects.equals( ldapProfile, that.ldapProfile )
                && Objects.equals( userDN, that.userDN );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( domainID, ldapProfile, userDN );
    }

    @Override
    public int compareTo( @NotNull final UserIdentity otherIdentity )
    {
        return COMPARATOR.compare( this, otherIdentity );
    }

    public UserIdentity canonicalized( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        if ( this.canonical )
        {
            return this;
        }

        final ChaiUser chaiUser = pwmApplication.getDomains().get( this.getDomainID() ).getProxiedChaiUser( this );
        final String userDN;
        try
        {
            userDN = chaiUser.readCanonicalDN();
        }
        catch ( final ChaiException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        final UserIdentity canonicalziedIdentity = create( userDN, this.getLdapProfileID(), this.getDomainID() );
        canonicalziedIdentity.canonical = true;
        return canonicalziedIdentity;
    }
}

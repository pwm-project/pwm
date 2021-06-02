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
import password.pwm.config.Configuration;
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

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.StringTokenizer;

@SuppressFBWarnings( "SE_TRANSIENT_FIELD_NOT_RESTORED" )
public class UserIdentity implements Serializable, Comparable<UserIdentity>
{
    private static final long serialVersionUID = 1L;

    private static final String CRYPO_HEADER = "ui_C-";
    private static final String DELIM_SEPARATOR = "|";

    private transient String obfuscatedValue;
    private transient boolean canonical;

    private final String userDN;
    private final String ldapProfile;
    private final String domain;

    public enum Flag
    {
        PreCanonicalized,
    }

    private UserIdentity( final String userDN, final String ldapProfile, final String domain )
    {
        this.userDN = JavaHelper.requireNonEmpty( userDN, "UserIdentity: userDN value cannot be empty" );
        this.ldapProfile = JavaHelper.requireNonEmpty( ldapProfile, "UserIdentity: ldapProfile value cannot be empty" );
        this.domain = JavaHelper.requireNonEmpty( domain, "UserIdentity: domain value cannot be empty" );
    }

    public UserIdentity( final String userDN, final String ldapProfile, final String domain, final boolean canonical )
    {
        this( userDN, ldapProfile, domain );
        this.canonical = canonical;
    }

    public static UserIdentity createUserIdentity( final String userDN, final String ldapProfile, final Flag... flags )
    {
        final boolean canonical = JavaHelper.enumArrayContainsValue( flags, Flag.PreCanonicalized );
        return new UserIdentity( userDN, ldapProfile, PwmConstants.DOMAIN_ID_DEFAULT, canonical );
    }

    public String getUserDN( )
    {
        return userDN;
    }

    public String getDomain()
    {
        return domain;
    }

    public String getLdapProfileID( )
    {
        return ldapProfile;
    }

    public LdapProfile getLdapProfile( final Configuration configuration )
    {
        Objects.requireNonNull( configuration );
        final LdapProfile ldapProfile = configuration.getLdapProfiles().get( this.getLdapProfileID() );
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
        return this.getLdapProfileID() + DELIM_SEPARATOR + this.getUserDN();
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

        final StringTokenizer st = new StringTokenizer( key, DELIM_SEPARATOR );
        if ( st.countTokens() < 2 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "not enough tokens while parsing delimited identity key" ) );
        }
        else if ( st.countTokens() > 2 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "too many string tokens while parsing delimited identity key" ) );
        }
        final String profileID = st.nextToken();
        final String userDN = st.nextToken();
        return createUserIdentity( userDN, profileID );
    }

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
        return Objects.equals( domain, that.domain )
                && Objects.equals( ldapProfile, that.ldapProfile )
                && Objects.equals( userDN, that.userDN );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( domain, ldapProfile, userDN );
    }

    @Override
    public int compareTo( @NotNull final UserIdentity otherIdentity )
    {
        return comparator().compare( this, otherIdentity );
    }

    private static Comparator<UserIdentity> comparator( )
    {
        final Comparator<UserIdentity> domainComparator = Comparator.comparing(
                UserIdentity::getDomain,
                Comparator.nullsLast( Comparator.naturalOrder() ) );

        final Comparator<UserIdentity> profileComparator = Comparator.comparing(
                UserIdentity::getLdapProfileID,
                Comparator.nullsLast( Comparator.naturalOrder() ) );

        final Comparator<UserIdentity> userComparator = Comparator.comparing(
                UserIdentity::getDomain,
                Comparator.nullsLast( Comparator.naturalOrder() ) );

        return domainComparator
                .thenComparing( profileComparator )
                .thenComparing( userComparator );
    }


    public UserIdentity canonicalized( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        if ( this.canonical )
        {
            return this;
        }

        final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser( this );
        final String userDN;
        try
        {
            userDN = chaiUser.readCanonicalDN();
        }
        catch ( final ChaiException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        final UserIdentity canonicalziedIdentity = createUserIdentity( userDN, this.getLdapProfileID() );
        canonicalziedIdentity.canonical = true;
        return canonicalziedIdentity;
    }
}

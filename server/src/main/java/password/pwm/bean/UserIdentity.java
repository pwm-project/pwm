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

package password.pwm.bean;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.cache.CacheKey;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.svc.cache.CacheService;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.util.StringTokenizer;

public class UserIdentity implements Serializable, Comparable
{
    private static final String CRYPO_HEADER = "ui_C-";
    private static final String DELIM_SEPARATOR = "|";

    private transient String obfuscatedValue;
    private transient boolean canonicalized;

    private String userDN;
    private String ldapProfile;

    public UserIdentity( final String userDN, final String ldapProfile )
    {
        if ( userDN == null || userDN.length() < 1 )
        {
            throw new IllegalArgumentException( "UserIdentity: userDN value cannot be empty" );
        }
        this.userDN = userDN;
        this.ldapProfile = ldapProfile == null ? "" : ldapProfile;
    }

    public String getUserDN( )
    {
        return userDN;
    }

    public String getLdapProfileID( )
    {
        return ldapProfile;
    }

    public LdapProfile getLdapProfile( final Configuration configuration )
    {
        if ( configuration == null )
        {
            return null;
        }
        return configuration.getLdapProfiles().getOrDefault( this.getLdapProfileID(), null );
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

    public static UserIdentity fromObfuscatedKey( final String key, final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        if ( key == null || key.length() < 1 )
        {
            return null;
        }

        if ( !key.startsWith( CRYPO_HEADER ) )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "cannot reverse obfuscated user key: missing header; value=" + key ) );
        }

        try
        {
            final String input = key.substring( CRYPO_HEADER.length(), key.length() );
            final String jsonValue = pwmApplication.getSecureService().decryptStringValue( input );
            return JsonUtil.deserialize( jsonValue, UserIdentity.class );
        }
        catch ( final Exception e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "unexpected error reversing obfuscated user key: " + e.getMessage() ) );
        }
    }

    public static UserIdentity fromDelimitedKey( final String key ) throws PwmUnrecoverableException
    {
        if ( key == null || key.length() < 1 )
        {
            return null;
        }

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
        return new UserIdentity( userDN, profileID );
    }

    public static UserIdentity fromKey( final String key, final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        if ( key == null || key.length() < 1 )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, "userKey parameter is missing" );
            throw new PwmUnrecoverableException( errorInformation );
        }

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

        if ( !ldapProfile.equals( that.ldapProfile ) )
        {
            return false;
        }
        if ( !userDN.equals( that.userDN ) )
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode( )
    {
        int result = userDN.hashCode();
        result = 31 * result + ldapProfile.hashCode();
        return result;
    }

    @Override
    public int compareTo( final Object o )
    {
        final String thisStr = ( ldapProfile == null ? "_" : ldapProfile ) + userDN;
        final UserIdentity otherIdentity = ( UserIdentity ) o;
        final String otherStr = ( otherIdentity.ldapProfile == null ? "_" : otherIdentity.ldapProfile ) + otherIdentity.userDN;

        return thisStr.compareTo( otherStr );
    }

    public UserIdentity canonicalized( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        if ( this.canonicalized )
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
        final UserIdentity canonicalziedIdentity = new UserIdentity( userDN, this.getLdapProfileID() );
        canonicalziedIdentity.canonicalized = true;
        return canonicalziedIdentity;
    }
}

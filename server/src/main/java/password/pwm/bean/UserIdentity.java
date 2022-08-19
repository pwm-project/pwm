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

package password.pwm.bean;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import password.pwm.PwmApplication;
import password.pwm.config.AppConfig;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonFactory;
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

    private static final String DELIM_SEPARATOR = "|";

    private static final Comparator<UserIdentity> COMPARATOR = Comparator.comparing(
            UserIdentity::getDomainID,
            Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    UserIdentity::getLdapProfileID,
                    ProfileID.comparator()
            )
            .thenComparing(
                    UserIdentity::getDomainID,
                    DomainID.comparator() );

    private transient boolean canonical;

    private final String userDN;
    private final ProfileID ldapProfile;
    private final DomainID domainID;

    public enum Flag
    {
        PreCanonicalized,
    }

    private UserIdentity( final String userDN, final ProfileID ldapProfile, final DomainID domainID )
    {
        this.userDN = JavaHelper.requireNonEmpty( userDN, "UserIdentity: userDN value cannot be empty" );
        this.ldapProfile = Objects.requireNonNull( ldapProfile, "UserIdentity: ldapProfile value cannot be empty" );
        this.domainID = Objects.requireNonNull( domainID );
    }

    public UserIdentity( final String userDN, final ProfileID ldapProfile, final DomainID domainID, final boolean canonical )
    {
        this( userDN, ldapProfile, domainID );
        this.canonical = canonical;
    }

    public static UserIdentity create(
            final String userDN,
            final ProfileID ldapProfile,
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

    public ProfileID getLdapProfileID( )
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

    public String toDelimitedKey( )
    {
        return JsonFactory.get().serialize( this );
    }

    public String toDisplayString( )
    {
        return "[" + this.getDomainID() + "]"
                + " " + this.getUserDN()
                + " (" + this.getLdapProfileID().stringValue() + ")";
    }

    public static UserIdentity fromDelimitedKey( final SessionLabel sessionLabel, final String key )
            throws PwmUnrecoverableException
    {
        JavaHelper.requireNonEmpty( key );

        try
        {
            return JsonFactory.get().deserialize( key, UserIdentity.class );
        }
        catch ( final Exception e )
        {
            LOGGER.trace( sessionLabel, () -> "unable to deserialize UserIdentity: " + key + " using JSON method: " + e.getMessage() );
        }

        // old style
        final StringTokenizer st = new StringTokenizer( key, DELIM_SEPARATOR );

        DomainID domainID = null;
        if ( st.countTokens() < 2 )
        {
            final String msg = "not enough tokens while parsing delimited identity key";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, msg ) );
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
                final String msg = "error decoding DomainID '" + domainStr + "' from delimited UserIdentity: " + e.getMessage();
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, msg ) );
            }
        }

        if ( st.countTokens() > 3 )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "too many string tokens while parsing delimited identity key" ) );
        }
        final ProfileID profileID = ProfileID.create( st.nextToken() );
        final String userDN = st.nextToken();
        return create( userDN, profileID, domainID );
    }

    public boolean canonicalEquals( final SessionLabel sessionLabel, final UserIdentity otherIdentity, final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        if ( otherIdentity == null )
        {
            return false;
        }

        final UserIdentity thisCanonicalIdentity = this.canonicalized( sessionLabel, pwmApplication );
        final UserIdentity otherCanonicalIdentity = otherIdentity.canonicalized( sessionLabel, pwmApplication );
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

    public UserIdentity canonicalized( final SessionLabel sessionLabel, final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        if ( this.canonical )
        {
            return this;
        }

        final ChaiUser chaiUser = pwmApplication.domains().get( this.getDomainID() ).getProxiedChaiUser( sessionLabel, this );
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

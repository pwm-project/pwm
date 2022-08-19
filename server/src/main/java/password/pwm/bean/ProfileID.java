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

import org.jetbrains.annotations.NotNull;
import password.pwm.PwmConstants;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class ProfileID implements Serializable, Comparable<ProfileID>
{
    private static final Pattern REGEX_TEST = Pattern.compile( "^([a-zA-Z][a-zA-Z0-9-]{2,15})$" );

    private static final List<String> PROFILE_RESERVED_WORDS = List.of( "all", "nmas" );

    private static final Comparator<ProfileID> COMPARATOR = Comparator.nullsFirst( Comparator.comparing( k -> k.profileID ) );
    private static final Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst( Comparator.comparing( Function.identity() ) );

    public static final ProfileID PROFILE_ID_DEFAULT = new ProfileID( "default" );
    public static final ProfileID PROFILE_ID_ALL = new ProfileID( "all" );
    public static final ProfileID PROFILE_ID_NMAS = new ProfileID( "nmas" );

    private static final List<ProfileID> BUILT_IN_PROFILES = List.of( PROFILE_ID_DEFAULT, PROFILE_ID_ALL, PROFILE_ID_NMAS );
    private final String profileID;

    private ProfileID( final String profileID )
    {
        this.profileID = JavaHelper.requireNonEmpty( profileID );
    }

    public static ProfileID create( final String profileID )
    {
        return BUILT_IN_PROFILES.stream()
                .filter( p -> p.profileID.equals( profileID ) )
                .findFirst()
                .orElse( new ProfileID( profileID ) );
    }

    public static Optional<ProfileID> createNullable( final String profileID )
    {
        return StringUtil.isEmpty( profileID ) ? Optional.empty() : Optional.of( create( profileID ) );
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
        final ProfileID profileID1 = ( ProfileID ) o;
        return Objects.equals( profileID, profileID1.profileID );
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode( profileID );
    }

    @Override
    public String toString()
    {
        return profileID;
    }

    public String stringValue()
    {
        return profileID;
    }

    @Override
    public int compareTo( @NotNull final ProfileID o )
    {
        return COMPARATOR.compare( this, o );
    }

    public static Comparator<ProfileID> comparator()
    {
        return COMPARATOR;
    }

    public static Comparator<String> stringComparator()
    {
        return STRING_COMPARATOR;
    }

    public static List<String> validateUserValue( final String value )
    {
        Objects.requireNonNull( value );
        final String lCaseValue = value.toLowerCase( PwmConstants.DEFAULT_LOCALE );
        final Optional<String> reservedWordMatch = PROFILE_RESERVED_WORDS.stream()
                .map( String::toLowerCase )
                .filter( lCaseValue::contains )
                .findFirst();
        if ( reservedWordMatch.isPresent() )
        {
            return Collections.singletonList( "contains reserved word '" + reservedWordMatch.get() + "'" );
        }

        if ( REGEX_TEST.matcher( value ).matches() )
        {
            return Collections.singletonList( "pattern is invalid" );
        }

        return Collections.emptyList();
    }
}

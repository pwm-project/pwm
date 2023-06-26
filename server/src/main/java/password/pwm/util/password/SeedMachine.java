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

package password.pwm.util.password;

import password.pwm.util.java.CollectionUtil;
import password.pwm.util.secure.PwmRandom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.IntStream;

class SeedMachine
{
    private static final List<String> DEFAULT_SEED_PHRASES = makeDefaultSeedPhrases();
    private static final SeedMachine DEFAULT_SEED_MACHINE = create( PwmRandom.getInstance(), DEFAULT_SEED_PHRASES );

    private final Collection<String> seeds;
    private final PwmRandom pwmRandom;

    private final Map<PasswordCharType, String> cachedCharsOfType = new EnumMap<>( PasswordCharType.class );
    private final Map<PasswordCharType, String> cachedCharsOfTypeException = new EnumMap<>( PasswordCharType.class );
    private final Supplier<String> allChars = this::figureAllChars;

    private SeedMachine( final PwmRandom pwmRandom, final Collection<String> seeds )
    {
        this.pwmRandom = Objects.requireNonNull( pwmRandom );
        this.seeds = Objects.requireNonNull( seeds );
    }

    static SeedMachine defaultSeedMachine()
    {
        return DEFAULT_SEED_MACHINE;
    }

    static SeedMachine create( final PwmRandom pwmRandom, final Collection<String> seeds )
    {
        final List<String> normalizedSeeds = normalizeSeeds( seeds );
        return CollectionUtil.isEmpty( normalizedSeeds )
                ? DEFAULT_SEED_MACHINE
                : new SeedMachine( pwmRandom, normalizedSeeds );
    }

    public String getRandomSeed()
    {
        return new ArrayList<>( seeds ).get( pwmRandom.nextInt( seeds.size() ) );
    }

    public String getAllChars()
    {
        return allChars.get();
    }

    private String figureAllChars()
    {
        final String sb = uniqueChars( seeds );
        return sb.length() > 2 ? sb.toString() : uniqueChars( DEFAULT_SEED_PHRASES );
    }

    public String charsExceptOfType( final PasswordCharType passwordCharType )
    {
        return cachedCharsOfTypeException.computeIfAbsent( passwordCharType, passwordCharType1 ->
        {
            final String value = PasswordCharType.charsExceptOfType( getAllChars(), passwordCharType );
            return value.length() > 0
                    ? value
                    : PasswordCharType.charsExceptOfType( getAllChars(), passwordCharType1 );
        } );
    }

    public String charsOfType( final PasswordCharType passwordCharType )
    {
        return cachedCharsOfType.computeIfAbsent( passwordCharType, passwordCharType1 ->
        {
            final String value = PasswordCharType.charsOfType( getAllChars(), passwordCharType );
            return value.length() > 0
                    ? value
                    : PasswordCharType.charsOfType( getAllChars(), passwordCharType1 );
        } );
    }

    private static String uniqueChars( final Collection<String> input )
    {
        if ( input == null || input.isEmpty() )
        {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for ( final String s : input )
        {
            for ( final Character c : s.toCharArray() )
            {
                if ( sb.indexOf( c.toString() ) == -1 )
                {
                    sb.append( c );
                }
            }
        }
        return sb.toString();
    }

    private static List<String> makeDefaultSeedPhrases()
    {
        final List<String> asciiChars = IntStream.range( 33, 126 )
                .boxed()
                .map( Character::toString )
                .toList();
        return List.copyOf( asciiChars );
    }

    private static List<String> normalizeSeeds( final Collection<String> inputSeeds )
    {
        if ( inputSeeds == null )
        {
            return DEFAULT_SEED_PHRASES;
        }

        final List<String> newSeeds = new ArrayList<>( inputSeeds );
        newSeeds.removeIf( s -> s == null || s.length() < 1 );

        return newSeeds.isEmpty() ? DEFAULT_SEED_PHRASES : List.copyOf( newSeeds );
    }
}

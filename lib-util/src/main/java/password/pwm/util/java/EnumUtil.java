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

package password.pwm.util.java;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EnumUtil
{
    private EnumUtil()
    {
    }

    public static <E extends Enum<E>> Optional<E> readEnumFromCaseIgnoreString( final Class<E> enumClass, final String input )
    {
        return readEnumFromPredicate( enumClass, loopValue -> loopValue.name().equalsIgnoreCase( input ) );
    }

    public static <E extends Enum<E>> Optional<E> readEnumFromPredicate( final Class<E> enumClass, final Predicate<E> match )
    {
        if ( match == null )
        {
            return Optional.empty();
        }

        if ( enumClass == null || !enumClass.isEnum() )
        {
            return Optional.empty();
        }

        return enumStream( enumClass ).filter( match ).findFirst();
    }

    public static <E extends Enum<E>> Set<E> readEnumsFromPredicate( final Class<E> enumClass, final Predicate<E> match )
    {
        if ( match == null )
        {
            return Collections.emptySet();
        }

        if ( enumClass == null || !enumClass.isEnum() )
        {
            return Collections.emptySet();
        }

        return enumStream( enumClass ).filter( match ).collect( Collectors.toUnmodifiableSet() );
    }

    public static <E extends Enum<E>> Optional<E> readEnumFromString( final Class<E> enumClass, final String input )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return Optional.empty();
        }

        if ( enumClass == null || !enumClass.isEnum() )
        {
            return Optional.empty();
        }

        try
        {
            return Optional.of( Enum.valueOf( enumClass, input ) );
        }
        catch ( final IllegalArgumentException e )
        {
            /* noop */
        }

        return Optional.empty();
    }

    public static <E extends Enum<E>> boolean enumArrayContainsValue( final E[] enumArray, final E enumValue )
    {
        if ( enumArray == null || enumArray.length == 0 )
        {
            return false;
        }

        for ( final E loopValue : enumArray )
        {
            if ( loopValue == enumValue )
            {
                return true;
            }
        }

        return false;
    }

    public static <E extends Enum<E>> Stream<E> enumStream( final Class<E> enumClass )
    {
        return EnumSet.allOf( enumClass ).stream();
    }

    public static <E extends Enum<E>> void forEach( final Class<E> enumClass, final Consumer<E> consumer )
    {
        EnumSet.allOf( enumClass ).forEach( consumer );
    }
}

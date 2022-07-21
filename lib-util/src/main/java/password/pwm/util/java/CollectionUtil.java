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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CollectionUtil
{
    public static <T> Stream<T> iteratorToStream( final Iterator<T> iterator )
    {
        return StreamSupport.stream( Spliterators.spliteratorUnknownSize( iterator, Spliterator.ORDERED ), false );
    }

    public static <V> List<V> stripNulls( final List<V> input )
    {
        if ( input == null )
        {
            return Collections.emptyList();
        }

        return input.stream()
                .filter( Objects::nonNull )
                .collect( Collectors.toUnmodifiableList() );
    }

    public static <V> Set<V> stripNulls( final Set<V> input )
    {
        if ( input == null )
        {
            return Collections.emptySet();
        }

        return input.stream()
                .filter( Objects::nonNull )
                .collect( Collectors.toUnmodifiableSet() );
    }

    public static <K, V> Map<K, V> stripNulls( final Map<K, V> input )
    {
        if ( input == null )
        {
            return Collections.emptyMap();
        }

        return input.entrySet().stream()
                .filter( e -> e.getKey() != null && e.getValue() != null )
                .collect( collectorToLinkedMap( Map.Entry::getKey, Map.Entry::getValue ) );
    }

    public static <K extends Enum<K>, V> EnumMap<K, V> copiedEnumMap( final Map<K, V> source, final Class<K> classOfT )
    {
        if ( source == null )
        {
            return new EnumMap<>( classOfT );
        }

        final EnumMap<K, V> returnMap = new EnumMap<>( classOfT );
        for ( final Map.Entry<K, V> entry : source.entrySet() )
        {
            final K key = entry.getKey();
            if ( key != null )
            {
                returnMap.put( key, entry.getValue() );
            }
        }
        return returnMap;
    }

    public static <E extends Enum<E>> Set<E> readEnumSetFromStringCollection( final Class<E> enumClass, final Collection<String> inputs )
    {
        if ( inputs == null )
        {
            return Collections.emptySet();
        }

        final Set<E> set = inputs.stream()
                .map( input -> JavaHelper.readEnumFromString( enumClass, input ) )
                .flatMap( Optional::stream )
                .collect( Collectors.toSet() );

        return Collections.unmodifiableSet( copyToEnumSet( set, enumClass ) );
    }

    public static <E extends Enum<E>> Set<E> enumSetFromArray( final E[] arrayValues )
    {
        return arrayValues == null || arrayValues.length == 0
                ? Collections.emptySet()
                : Collections.unmodifiableSet( EnumSet.copyOf( Arrays.asList( arrayValues ) ) );
    }

    public static <E extends Enum<E>> Map<String, String> enumMapToStringMap(
            final Map<E, String> inputMap,
            final Function<E, String> keyToStringFunction
    )
    {
        return Collections.unmodifiableMap( inputMap.entrySet().stream()
                .collect( collectorToLinkedMap(
                        entry -> keyToStringFunction.apply( entry.getKey() ),
                        Map.Entry::getValue ) ) );
    }

    public static <E extends Enum<E>> Map<String, String> enumMapToStringMap( final Map<E, String> inputMap )
    {
        return enumMapToStringMap( inputMap, Enum::name );
    }

    public static <K> boolean isEmpty( final Collection<K> collection )
    {
        return collection == null || collection.isEmpty();
    }

    public static <K, V> boolean isEmpty( final Map<K, V> map )
    {
        return map == null || map.isEmpty();
    }

    public static <E extends Enum<E>> EnumSet<E> copyToEnumSet( final Collection<E> source, final Class<E> classOfT )
    {
        return isEmpty( source )
                ? EnumSet.noneOf( classOfT )
                : EnumSet.copyOf( source );
    }

    public static <E> List<E> iteratorToList( final Iterator<E> iterator )
    {
        if ( iterator == null )
        {
            return Collections.emptyList();
        }

        return iteratorToStream( iterator )
                .collect( Collectors.toUnmodifiableList() );
    }

    /**
     * Combines an ordered sequence of ordered maps.  Duplicate keys in later maps in the list will
     * silently overwrite the earlier values.  The returned map will be unmodifiable as per
     * {@link Collections#unmodifiableMap(Map)}.
     */
    @SuppressFBWarnings( "OCP_OVERLY_CONCRETE_PARAMETER" )
    public static <K, V> Map<K, V> combineOrderedMaps( final List<Map<K, V>> maps )
    {
        final Map<K, V> returnMap = new LinkedHashMap<>();
        for ( final Map<K, V> loopMap : maps )
        {
            returnMap.putAll( loopMap );
        }
        return Collections.unmodifiableMap( returnMap );
    }

    public static <T, K, U> Collector<T, ?, Map<K, U>> collectorToLinkedMap(
            final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper
    )
    {
        return Collectors.toMap(
                keyMapper,
                valueMapper,
                ( key1, key2 ) ->
                {
                    throw new IllegalStateException( "Duplicate key " + key1 );
                },
                LinkedHashMap::new
        );
    }

    public static <T, K extends Enum<K>, U> Collector<T, ?, Map<K, U>> collectorToEnumMap(
            final Class<K> keyClass,
            final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper
    )
    {
        return Collectors.toMap(
                keyMapper,
                valueMapper,
                ( key1, key2 ) ->
                {
                    throw new IllegalStateException( "Duplicate key " + key1 );
                },
                () -> new EnumMap<>( keyClass )
        );
    }

    public static <E extends Enum<E>> Stream<E> enumStream( final Class<E> enumClass )
    {
        return EnumSet.allOf( enumClass ).stream();
    }
}

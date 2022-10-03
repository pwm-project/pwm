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
import java.util.HashSet;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class CollectionUtil
{
    private CollectionUtil()
    {
    }

    public static <T> Stream<T> iteratorToStream( final Iterator<T> iterator )
    {
        return Optional.ofNullable( iterator )
                .map( it -> StreamSupport.stream( Spliterators.spliteratorUnknownSize( it, Spliterator.ORDERED ), false ) )
                .orElse( Stream.empty() );
    }

    public static <V> List<V> stripNulls( final List<V> input )
    {
        if ( input == null )
        {
            return List.of();
        }

        return input.stream()
                .filter( Objects::nonNull )
                .collect( Collectors.toUnmodifiableList() );
    }

    public static <V> Set<V> stripNulls( final Set<V> input )
    {
        if ( input == null )
        {
            return Set.of();
        }

        return input.stream()
                .filter( Objects::nonNull )
                .collect( Collectors.toUnmodifiableSet() );
    }

    public static <K, V> Map<K, V> stripNulls( final Map<K, V> input )
    {
        if ( input == null )
        {
            return Map.of();
        }

        final Stream<Map.Entry<K, V>> stream = input.entrySet().stream()
                .filter( CollectionUtil::testMapEntryForNotNull );

        final boolean ordered = input instanceof LinkedHashMap;
        return ordered
                ? stream.collect( CollectorUtil.toUnmodifiableLinkedMap( Map.Entry::getKey, Map.Entry::getValue ) )
                : stream.collect( Collectors.toUnmodifiableMap( Map.Entry::getKey, Map.Entry::getValue ) );
    }

    public static <K extends Enum<K>, V> EnumMap<K, V> copiedEnumMap( final Map<K, V> source, final Class<K> classOfT )
    {
        if ( CollectionUtil.isEmpty( source ) )
        {
            return new EnumMap<K, V>( classOfT );
        }

        return source.entrySet().stream()
                .filter( CollectionUtil::testMapEntryForNotNull )
                .collect( Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        CollectorUtil::errorOnDuplicateMergeOperator,
                        () -> new EnumMap<>( classOfT ) ) );

    }

    public static <E extends Enum<E>> Set<E> readEnumSetFromStringCollection( final Class<E> enumClass, final Collection<String> inputs )
    {
        if ( CollectionUtil.isEmpty( inputs ) )
        {
            return Collections.emptySet();
        }

        final Set<E> set = inputs.stream()
                .filter( Objects::nonNull )
                .map( input -> EnumUtil.readEnumFromString( enumClass, input ) )
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
        if ( CollectionUtil.isEmpty( inputMap ) )
        {
            return Collections.emptyMap();
        }

        return inputMap.entrySet().stream()
                .filter( CollectionUtil::testMapEntryForNotNull )
                .collect( CollectorUtil.toUnmodifiableLinkedMap(
                        entry -> keyToStringFunction.apply( entry.getKey() ),
                        Map.Entry::getValue ) );
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

    public static <E extends Enum<E>> EnumSet<E> copyToEnumSet( final Set<E> source, final Class<E> classOfT )
    {
        return isEmpty( source )
                ? EnumSet.noneOf( classOfT )
                : EnumSet.copyOf( source );
    }

    public static <E> List<E> iteratorToList( final Iterator<E> iterator )
    {
        return iteratorToStream( iterator )
                .collect( Collectors.toUnmodifiableList() );
    }

    /**
     * Combines an ordered sequence of ordered maps.  Duplicate keys in later maps in the list will
     * silently overwrite the earlier values.  The returned map will be unmodifiable as per
     * {@link Collections#unmodifiableMap(Map)}.
     */
    @SuppressFBWarnings( "OCP_OVERLY_CONCRETE_PARAMETER" )
    public static <K, V> Map<K, V> combineOrderedMaps( final List<Map<K, V>> listOfMaps )
    {
        if ( CollectionUtil.isEmpty( listOfMaps ) )
        {
            return Collections.emptyMap();
        }

        return listOfMaps.stream()
                .filter( Objects::nonNull )
                .flatMap( kvMap -> kvMap.entrySet().stream() )
                .filter( CollectionUtil::testMapEntryForNotNull )
                .collect( CollectorUtil.toUnmodifiableLinkedMap( Map.Entry::getKey, Map.Entry::getValue ) );
    }

    public static <T> Set<T> setUnion( final Set<T> set1, final Set<T> set2 )
    {
        final Set<T> unionSet = new HashSet<>( set1 == null ? Collections.emptySet() : set1 );
        unionSet.retainAll( set2 == null ? Collections.<T>emptySet() : set2 );
        return Set.copyOf( unionSet );
    }

    public static <T, R> List<R> convertListType( final List<T> input, final Function<T, R> convertFunction )

    {
        return stripNulls( input ).stream().map( convertFunction ).collect( Collectors.toUnmodifiableList() );
    }

    private static <K, V> boolean testMapEntryForNotNull( final Map.Entry<K, V> entry )
    {
        return entry != null && entry.getKey() != null && entry.getValue() != null;
    }

    public static <E extends Enum<E>, V> Map<E, V> unmodifiableEnumMap( final Map<E, V> inputSet, final Class<E> classOfT )
    {
        if ( CollectionUtil.isEmpty( inputSet ) )
        {
            return Map.of();
        }

        return Collections.unmodifiableMap( copiedEnumMap( inputSet, classOfT ) );
    }

    public static <E extends Enum<E>> Set<E> unmodifiableEnumSet( final Set<E> inputSet, final Class<E> classOfT )
    {
        if ( CollectionUtil.isEmpty( inputSet ) )
        {
            return Set.of();
        }

        return Collections.unmodifiableSet( copyToEnumSet( inputSet, classOfT ) );
    }

}

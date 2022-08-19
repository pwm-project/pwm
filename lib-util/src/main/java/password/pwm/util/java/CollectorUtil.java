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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class CollectorUtil
{
    public static <T, K, U> Collector<T, ?, Map<K, U>> toUnmodifiableLinkedMap(
            final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper
    )
    {
        final Collector<T, ?, Map<K, U>> wrappedCollector = toLinkedMap( keyMapper, valueMapper );
        return Collectors.collectingAndThen( wrappedCollector, Collections::unmodifiableMap );
    }

    public static <T, K, U> Collector<T, ?, Map<K, U>> toLinkedMap(
            final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper
    )
    {
        return Collectors.toMap(
                keyMapper,
                valueMapper,
                CollectorUtil::errorOnDuplicateMergeOperator,
                LinkedHashMap::new );
    }

    public static <T, K, U> Collector<T, ?, SortedMap<K, U>> toUnmodifiableSortedMap(
            final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper,
            final Comparator<K> comparator
    )
    {
        final Collector<T, ?, SortedMap<K, U>> wrappedCollector = toSortedMap( keyMapper, valueMapper, comparator );
        return Collectors.collectingAndThen( wrappedCollector, Collections::unmodifiableSortedMap );
    }

    public static <T, K, U> Collector<T, ?, SortedMap<K, U>> toSortedMap(
            final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper,
            final Comparator<K> comparator
    )
    {
        return Collectors.collectingAndThen( Collectors.toUnmodifiableMap(
                        keyMapper,
                        valueMapper ),
                s -> new TreeMap<>( comparator ) );
    }

    public static <T, K extends Enum<K>, U> Collector<T, ?, Map<K, U>> toUnmodifiableEnumMap(
            final Class<K> keyClass,
            final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper
    )
    {
        final Collector<T, ?, Map<K, U>> wrappedCollector = toEnumMap( keyClass, keyMapper, valueMapper );
        return Collectors.collectingAndThen( wrappedCollector, Collections::unmodifiableMap );
    }

    public static <T, K extends Enum<K>, U> Collector<T, ?, Map<K, U>> toEnumMap(
            final Class<K> keyClass,
            final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper
    )
    {
        return Collectors.toMap(
                keyMapper,
                valueMapper,
                CollectorUtil::errorOnDuplicateMergeOperator,
                () -> new EnumMap<>( keyClass ) );
    }

    static <V> V errorOnDuplicateMergeOperator( final V u, final V u2 )
    {
        throw new IllegalStateException( "Duplicate key " + u );
    }
}

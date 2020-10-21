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

package password.pwm.util.java;

import com.novell.ldapchai.util.StringHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.stream.Collectors;

public class StatisticCounterBundle<K extends Enum<K>>
{
    private final Class<K> keyType;
    private final Map<K, LongAccumulator> statMap;

    public StatisticCounterBundle( final Class<K> keyType )
    {
        this.keyType = keyType;
        statMap = new EnumMap<>( keyType );
    }

    public void increment( final K stat )
    {
        increment( stat, 1 );
    }

    public void increment( final K stat, final long amount )
    {
        statMap.computeIfAbsent( stat, k -> makeLongAccumulator() ).accumulate( amount );
    }

    public long get( final K stat )
    {
        final LongAccumulator longAdder = statMap.get( stat );
        return longAdder == null ? 0 : longAdder.longValue();
    }

    public Map<String, String> debugStats()
    {
        return Collections.unmodifiableMap( Arrays.stream( keyType.getEnumConstants() )
                .collect( Collectors.toMap(
                        Enum::name,
                        stat -> Long.toString( get( stat ) )
                ) ) );
    }

    public String debugString()
    {
        return StringHelper.stringMapToString( debugStats(), null );
    }

    private static LongAccumulator makeLongAccumulator()
    {
        return new LongAccumulator( Long::sum, 0L );
    }
}

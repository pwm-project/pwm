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

import password.pwm.util.EventRateMeter;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class StatisticRateBundle<K extends Enum<K>>
{
    private static final Duration DEFAULT_DURATION = Duration.ofMinutes( 1 );

    private final Class<K> keyType;
    private final Map<K, EventRateMeter> statMap;

    public StatisticRateBundle( final Class<K> keyType, final Duration avgPeriodLength )
    {
        this.keyType = keyType;
        statMap = new EnumMap<>( keyType );
        EnumUtil.forEach( keyType, k -> statMap.put( k, new EventRateMeter( avgPeriodLength ) ) );
    }

    public StatisticRateBundle( final Class<K> keyType )
    {
        this( keyType, DEFAULT_DURATION );
    }

    public void markEvent( final K stat )
    {
        statMap.get( stat ).markEvent();
    }

    public void markEvents( final K stat, final int count )
    {
        statMap.get( stat ).markEvents( count );
    }

    public BigDecimal rawEps( final K stat )
    {
        final EventRateMeter movingAverage = statMap.get( stat );
        return movingAverage.rawEps();
    }

    public String prettyEps( final K stat, final Locale locale )
    {
        return statMap.get( stat ).prettyEps( locale );
    }

    public Map<String, String> debugStats( final Locale locale )
    {
        return statMap.entrySet().stream().collect( Collectors.toUnmodifiableMap(
                entry -> entry.getKey().name(),
                entry -> entry.getValue().prettyEps( locale ) ) );
    }

    public String debugString( final Locale locale )
    {
        return StringUtil.mapToString( debugStats( locale ) );
    }
}

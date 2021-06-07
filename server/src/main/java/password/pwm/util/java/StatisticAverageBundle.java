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

import com.novell.ldapchai.util.StringHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

public class StatisticAverageBundle<K extends Enum<K>>
{
    private static final TimeDuration DEFAULT_DURATION = TimeDuration.MINUTE;

    private final Class<K> keyType;
    private final Map<K, MovingAverage> statMap;

    public StatisticAverageBundle( final Class<K> keyType, final TimeDuration avgPeriodLength )
    {
        this.keyType = keyType;
        statMap = new EnumMap<>( keyType );
        Arrays.stream( keyType.getEnumConstants() ).forEach( k -> statMap.put( k, new MovingAverage( avgPeriodLength ) ) );
    }

    public StatisticAverageBundle( final Class<K> keyType )
    {
        this( keyType, DEFAULT_DURATION );
    }

    public void update( final K stat, final long sample )
    {
        statMap.get( stat ).update( sample );
    }

    public void update( final K stat, final TimeDuration sample )
    {
        statMap.get( stat ).update( sample );
    }

    public double getAverage( final K stat )
    {
        final MovingAverage movingAverage = statMap.get( stat );
        return movingAverage == null ? 0 : movingAverage.getAverage();
    }

    public String getFormattedAverage( final K stat )
    {
        return statMap.get( stat ).getFormattedAverage( );
    }

    public Map<String, String> debugStats()
    {
        return Collections.unmodifiableMap( Arrays.stream( keyType.getEnumConstants() )
                .collect( Collectors.toMap(
                        Enum::name,
                        this::getFormattedAverage
                ) ) );
    }

    public String debugString()
    {
        return StringHelper.stringMapToString( debugStats(), null );
    }
}

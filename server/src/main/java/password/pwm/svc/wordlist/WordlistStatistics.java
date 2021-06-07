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

package password.pwm.svc.wordlist;

import lombok.Value;
import password.pwm.util.java.StatisticAverageBundle;
import password.pwm.util.java.StatisticCounterBundle;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

@Value
class WordlistStatistics
{
    private final Map<WordType, LongAdder> wordTypeHits = new HashMap<>(  );
    private final StatisticCounterBundle<CounterStat> counterStats = new StatisticCounterBundle<>( CounterStat.class );
    private final StatisticAverageBundle<AverageStat> averageStats = new StatisticAverageBundle<>( AverageStat.class );

    enum CounterStat
    {
        wordChecks,
        wordHits,
        wordMisses,
        chunkChecks,
        chunkHits,
        chunkMisses,
    }

    enum AverageStat
    {
        avgWordCheckLength,
        wordCheckTimeMS,
        chunkCheckTimeMS,
        chunksPerWordCheck,
    }

    WordlistStatistics()
    {
        for ( final WordType wordType : WordType.values() )
        {
            wordTypeHits.put( wordType, new LongAdder() );
        }
    }

    Map<String, String> asDebugMap()
    {
        final Map<String, String> outputMap = new TreeMap<>(  );
        for ( final Map.Entry<WordType, LongAdder> entry : wordTypeHits.entrySet() )
        {
            outputMap.put( "Hits-" + entry.getKey().name(), Long.toString( entry.getValue().sum() ) );
        }
        outputMap.putAll( counterStats.debugStats() );
        outputMap.putAll( averageStats.debugStats() );
        return Collections.unmodifiableMap( outputMap );
    }
}

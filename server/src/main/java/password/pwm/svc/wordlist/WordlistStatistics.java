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

package password.pwm.svc.wordlist;

import lombok.Value;
import password.pwm.util.java.MovingAverage;
import password.pwm.util.java.TimeDuration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

@Value
class WordlistStatistics
{
    private MovingAverage wordCheckTimeMS = new MovingAverage( TimeDuration.of( 5, TimeDuration.Unit.MINUTES ) );
    private MovingAverage chunksPerWordCheck = new MovingAverage( TimeDuration.of( 1, TimeDuration.Unit.DAYS ) );
    private LongAdder wordChecks = new LongAdder();
    private Map<WordType, LongAdder> wordTypeHits = new HashMap<>(  );
    private LongAdder misses = new LongAdder();

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
        outputMap.put( "AvgLocalDBWordCheckTimeMS", Double.toString( wordCheckTimeMS.getAverage() ) );
        outputMap.put( "ChunksPerCheck", Double.toString( chunksPerWordCheck.getAverage() ) );
        outputMap.put( "LocalDBWordChecks", Long.toString( wordChecks.sum() ) );
        outputMap.put( "Misses", Long.toString( misses.sum() ) );
        for ( final Map.Entry<WordType, LongAdder> entry : wordTypeHits.entrySet() )
        {
            outputMap.put( "Hits-" + entry.getKey().name(), Long.toString( entry.getValue().sum() ) );
        }
        return Collections.unmodifiableMap( outputMap );
    }
}

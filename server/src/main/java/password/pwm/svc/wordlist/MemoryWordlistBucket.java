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

import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryWordlistBucket extends AbstractWordlistBucket
{
    private final Map<String, String> map = new ConcurrentHashMap<>(  );
    private WordlistStatus wordlistStatus;

    public MemoryWordlistBucket( final PwmApplication pwmApplication, final WordlistConfiguration wordlistConfiguration, final WordlistType type )
    {
        super( pwmApplication, wordlistConfiguration, type );
    }

    @Override
    void putValues( final Map<String, String> values )
            throws PwmUnrecoverableException
    {
        map.putAll( values );
    }

    @Override
    boolean containsKey( final String key )
            throws PwmUnrecoverableException
    {
        return map.containsKey( key );
    }

    @Override
    String getValue( final String key )
            throws PwmUnrecoverableException
    {
        return map.get( key );
    }

    @Override
    public long size()
            throws PwmUnrecoverableException
    {
        return map.size();
    }

    @Override
    public void clear()
            throws PwmUnrecoverableException
    {
        map.clear();
    }

    @Override
    public WordlistStatus readWordlistStatus()
    {
        return wordlistStatus == null ? WordlistStatus.builder().build() : wordlistStatus;
    }

    @Override
    public void writeWordlistStatus( final WordlistStatus wordlistStatus )
    {
        this.wordlistStatus = wordlistStatus;
    }

    @Override
    public long spaceRemaining()
    {
        return Long.MAX_VALUE;
    }
}

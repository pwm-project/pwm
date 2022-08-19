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

import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class CollectorUtilTest
{
    @Test
    public void collectorToLinkedMap()
    {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put( "1", "1" );
        map.put( "2", "2" );
        map.put( "3", "3" );
        map.put( "4", "4" );
        map.put( "5", "5" );

        final Map<String, String> outputMap = map.entrySet().stream()
                .collect( CollectorUtil.toLinkedMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue ) );

        final Iterator<String> iter = outputMap.values().iterator();
        Assert.assertEquals( "1", iter.next() );
        Assert.assertEquals( "2", iter.next() );
        Assert.assertEquals( "3", iter.next() );
        Assert.assertEquals( "4", iter.next() );
        Assert.assertEquals( "5", iter.next() );

        Assert.assertEquals( "java.util.LinkedHashMap", outputMap.getClass().getName() );

        outputMap.put( "testKey", "testValue" );
    }

    @Test
    public void collectorToUnmodifiableLinkedMap()
    {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put( "1", "1" );
        map.put( "2", "2" );
        map.put( "3", "3" );
        map.put( "4", "4" );
        map.put( "5", "5" );

        final Map<String, String> outputMap = map.entrySet().stream()
                .collect( CollectorUtil.toUnmodifiableLinkedMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue ) );

        final Iterator<String> iter = outputMap.values().iterator();
        Assert.assertEquals( "1", iter.next() );
        Assert.assertEquals( "2", iter.next() );
        Assert.assertEquals( "3", iter.next() );
        Assert.assertEquals( "4", iter.next() );
        Assert.assertEquals( "5", iter.next() );

        Assert.assertThrows( UnsupportedOperationException.class, () -> outputMap.put( "testKey", "testValue" ) );
    }
}

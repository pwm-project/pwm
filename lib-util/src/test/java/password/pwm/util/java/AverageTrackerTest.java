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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AverageTrackerTest
{
    @Test
    public void testAverage()
    {
        final AverageTracker averageTracker = new AverageTracker( 5 );
        averageTracker.addSample( 5 );
        averageTracker.addSample( 6 );
        averageTracker.addSample( 7 );
        averageTracker.addSample( 8 );
        averageTracker.addSample( 9 );
        Assertions.assertEquals( 7, averageTracker.avgAsLong() );
    }

    @Test
    public void testRollingAverage()
    {
        final AverageTracker averageTracker = new AverageTracker( 5 );
        averageTracker.addSample( 5 );
        averageTracker.addSample( 6 );
        averageTracker.addSample( 7 );
        averageTracker.addSample( 8 );
        averageTracker.addSample( 9 );
        averageTracker.addSample( 10 );
        averageTracker.addSample( 15 );
        Assertions.assertEquals( 9, averageTracker.avgAsLong() );
    }

    @Test
    public void testLargeAverage()
    {
        final AverageTracker averageTracker = new AverageTracker( 5 );
        averageTracker.addSample( 9_223_372_036_854_775_807L  );
        averageTracker.addSample( 9_223_372_036_854_775_806L  );
        averageTracker.addSample( 9_223_372_036_854_775_805L  );
        averageTracker.addSample( 9_223_372_036_854_775_804L  );
        averageTracker.addSample( 9_223_372_036_854_775_803L  );
        Assertions.assertEquals( 9_223_372_036_854_775_805L, averageTracker.avgAsLong() );
    }
}

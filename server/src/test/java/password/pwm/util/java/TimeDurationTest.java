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

import org.junit.Assert;
import org.junit.Test;

public class TimeDurationTest
{
    @Test
    public void testConversions()
    {
        Assert.assertEquals( 1000, TimeDuration.SECOND.asMillis() );
        Assert.assertEquals( 10 * 1000, TimeDuration.SECONDS_10.asMillis() );
        Assert.assertEquals( 30 * 1000, TimeDuration.SECONDS_30.asMillis() );
        Assert.assertEquals( 60 * 1000, TimeDuration.MINUTE.asMillis() );
        Assert.assertEquals( 60 * 60 * 1000, TimeDuration.HOUR.asMillis() );
        Assert.assertEquals( 24 * 60 * 60 * 1000, TimeDuration.DAY.asMillis() );

        final TimeDuration timeDuration15m = TimeDuration.of( 15, TimeDuration.Unit.MINUTES );
        final TimeDuration timeDuration37h = TimeDuration.of( 37, TimeDuration.Unit.HOURS );

        Assert.assertEquals( 15 * 60 * 1000, timeDuration15m.asMillis() );
        Assert.assertEquals( 15 * 60 * 1000, timeDuration15m.as( TimeDuration.Unit.MILLISECONDS ) );
        Assert.assertEquals( 15 * 60, timeDuration15m.as( TimeDuration.Unit.SECONDS ) );
        Assert.assertEquals( 15, timeDuration15m.as( TimeDuration.Unit.MINUTES ) );
        Assert.assertEquals( 0, timeDuration15m.as( TimeDuration.Unit.HOURS ) );
        Assert.assertEquals( 0, timeDuration15m.as( TimeDuration.Unit.DAYS ) );

        Assert.assertTrue( timeDuration37h.isLongerThan( timeDuration15m ) );
        Assert.assertFalse( timeDuration37h.isShorterThan( timeDuration15m ) );
        Assert.assertFalse( timeDuration15m.isLongerThan( timeDuration37h ) );
        Assert.assertEquals( timeDuration15m, timeDuration15m );
        Assert.assertNotEquals( timeDuration15m, timeDuration37h );

        Assert.assertEquals( TimeDuration.MILLISECONDS_2, TimeDuration.MILLISECOND.add( TimeDuration.MILLISECOND ) );
        Assert.assertEquals( TimeDuration.MILLISECOND, TimeDuration.MILLISECONDS_2.subtract( TimeDuration.MILLISECOND ) );

        Assert.assertEquals( TimeDuration.MILLISECOND, TimeDuration.MILLISECOND );
        Assert.assertEquals( TimeDuration.SECOND, TimeDuration.SECOND );
        Assert.assertEquals( TimeDuration.SECONDS_10, TimeDuration.SECONDS_10 );
        Assert.assertEquals( TimeDuration.SECONDS_30, TimeDuration.SECONDS_30 );
        Assert.assertEquals( TimeDuration.MINUTE, TimeDuration.MINUTE );
        Assert.assertEquals( TimeDuration.HOUR, TimeDuration.HOUR );
        Assert.assertEquals( TimeDuration.DAY, TimeDuration.DAY );
    }
}

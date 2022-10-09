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

public class TimeDurationTest
{
    @Test
    public void testConversions()
    {
        Assertions.assertEquals( 1000, TimeDuration.SECOND.asMillis() );
        Assertions.assertEquals( 10 * 1000, TimeDuration.SECONDS_10.asMillis() );
        Assertions.assertEquals( 30 * 1000, TimeDuration.SECONDS_30.asMillis() );
        Assertions.assertEquals( 60 * 1000, TimeDuration.MINUTE.asMillis() );
        Assertions.assertEquals( 60 * 60 * 1000, TimeDuration.HOUR.asMillis() );
        Assertions.assertEquals( 24 * 60 * 60 * 1000, TimeDuration.DAY.asMillis() );

        final TimeDuration timeDuration15m = TimeDuration.of( 15, TimeDuration.Unit.MINUTES );
        final TimeDuration timeDuration37h = TimeDuration.of( 37, TimeDuration.Unit.HOURS );

        Assertions.assertEquals( 15 * 60 * 1000, timeDuration15m.asMillis() );
        Assertions.assertEquals( 15 * 60 * 1000, timeDuration15m.as( TimeDuration.Unit.MILLISECONDS ) );
        Assertions.assertEquals( 15 * 60, timeDuration15m.as( TimeDuration.Unit.SECONDS ) );
        Assertions.assertEquals( 15, timeDuration15m.as( TimeDuration.Unit.MINUTES ) );
        Assertions.assertEquals( 0, timeDuration15m.as( TimeDuration.Unit.HOURS ) );
        Assertions.assertEquals( 0, timeDuration15m.as( TimeDuration.Unit.DAYS ) );

        Assertions.assertTrue( timeDuration37h.isLongerThan( timeDuration15m ) );
        Assertions.assertFalse( timeDuration37h.isShorterThan( timeDuration15m ) );
        Assertions.assertFalse( timeDuration15m.isLongerThan( timeDuration37h ) );
        Assertions.assertEquals( timeDuration15m, timeDuration15m );
        Assertions.assertNotEquals( timeDuration15m, timeDuration37h );

        Assertions.assertEquals( TimeDuration.MILLISECONDS_2, TimeDuration.MILLISECOND.add( TimeDuration.MILLISECOND ) );
        Assertions.assertEquals( TimeDuration.MILLISECOND, TimeDuration.MILLISECONDS_2.subtract( TimeDuration.MILLISECOND ) );

        Assertions.assertEquals( TimeDuration.MILLISECOND, TimeDuration.MILLISECOND );
        Assertions.assertEquals( TimeDuration.SECOND, TimeDuration.SECOND );
        Assertions.assertEquals( TimeDuration.SECONDS_10, TimeDuration.SECONDS_10 );
        Assertions.assertEquals( TimeDuration.SECONDS_30, TimeDuration.SECONDS_30 );
        Assertions.assertEquals( TimeDuration.MINUTE, TimeDuration.MINUTE );
        Assertions.assertEquals( TimeDuration.HOUR, TimeDuration.HOUR );
        Assertions.assertEquals( TimeDuration.DAY, TimeDuration.DAY );
    }
}

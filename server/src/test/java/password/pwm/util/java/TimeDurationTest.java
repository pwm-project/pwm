/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


package password.pwm.util.java;

import org.junit.Assert;
import org.junit.Test;

public class TimeDurationTest
{
    @Test
    public void testConversions()
    {
        Assert.assertEquals( TimeDuration.SECOND.asMillis(), 1000 );
        Assert.assertEquals( TimeDuration.SECONDS_10.asMillis(), 10 * 1000 );
        Assert.assertEquals( TimeDuration.SECONDS_30.asMillis(), 30 * 1000 );
        Assert.assertEquals( TimeDuration.MINUTE.asMillis(), 60 * 1000 );
        Assert.assertEquals( TimeDuration.HOUR.asMillis(), 60 * 60 * 1000 );
        Assert.assertEquals( TimeDuration.DAY.asMillis(), 24 * 60 * 60 * 1000 );

        final TimeDuration timeDuration15m = TimeDuration.of( 15, TimeDuration.Unit.MINUTES );
        final TimeDuration timeDuration37h = TimeDuration.of( 37, TimeDuration.Unit.HOURS );

        Assert.assertEquals( timeDuration15m.asMillis(), 15 * 60 * 1000 );
        Assert.assertEquals( timeDuration15m.as( TimeDuration.Unit.MILLISECONDS ), 15 * 60 * 1000 );
        Assert.assertEquals( timeDuration15m.as( TimeDuration.Unit.SECONDS ), 15 * 60 );
        Assert.assertEquals( timeDuration15m.as( TimeDuration.Unit.MINUTES ), 15 );
        Assert.assertEquals( timeDuration15m.as( TimeDuration.Unit.HOURS ), 0 );
        Assert.assertEquals( timeDuration15m.as( TimeDuration.Unit.DAYS ), 0 );

        Assert.assertTrue( timeDuration37h.isLongerThan( timeDuration15m ) );
        Assert.assertFalse( timeDuration37h.isShorterThan( timeDuration15m ) );
        Assert.assertFalse( timeDuration15m.isLongerThan( timeDuration37h ) );
        Assert.assertEquals( timeDuration15m, timeDuration15m );
        Assert.assertNotEquals( timeDuration15m, timeDuration37h );

        Assert.assertEquals( TimeDuration.MILLISECOND.add( TimeDuration.MILLISECOND ), TimeDuration.MILLISECONDS_2 );
        Assert.assertEquals( TimeDuration.MILLISECONDS_2.subtract( TimeDuration.MILLISECOND ), TimeDuration.MILLISECOND );

        Assert.assertEquals( TimeDuration.MILLISECOND, TimeDuration.MILLISECOND );
        Assert.assertEquals( TimeDuration.SECOND, TimeDuration.SECOND );
        Assert.assertEquals( TimeDuration.SECONDS_10, TimeDuration.SECONDS_10 );
        Assert.assertEquals( TimeDuration.SECONDS_30, TimeDuration.SECONDS_30 );
        Assert.assertEquals( TimeDuration.MINUTE, TimeDuration.MINUTE );
        Assert.assertEquals( TimeDuration.HOUR, TimeDuration.HOUR );
        Assert.assertEquals( TimeDuration.DAY, TimeDuration.DAY );
    }
}

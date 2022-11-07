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

package password.pwm.svc.stats;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class StatisticsBundleKeyTest
{
    @Test
    public void testKeySorting()
    {
        final List<StatisticsBundleKey> list = new ArrayList<>();
        list.add( StatisticsBundleKey.fromString( "CURRENT" ) );
        list.add( StatisticsBundleKey.fromString( "DAILY_2000_1" ) );
        list.add( StatisticsBundleKey.fromString( "DAILY_2001_5" ) );
        list.add( StatisticsBundleKey.fromString( "DAILY_2000_5" ) );
        list.add( StatisticsBundleKey.fromString( "CUMULATIVE" ) );
        Collections.sort( list );

        Assertions.assertEquals( StatisticsBundleKey.CUMULATIVE, list.get( 0 ) );
        Assertions.assertEquals( StatisticsBundleKey.CURRENT, list.get( 1 ) );

        Assertions.assertEquals( 2000, list.get( 2 ).getYear() );
        Assertions.assertEquals( 1, list.get( 2 ).getDay() );

        Assertions.assertEquals( 2000, list.get( 3 ).getYear() );
        Assertions.assertEquals( 5, list.get( 3 ).getDay() );

        Assertions.assertEquals( 2001, list.get( 4 ).getYear() );
        Assertions.assertEquals( 5, list.get( 4 ).getDay() );
    }

    @Test
    public void testKeyRange()
    {
        final Set<StatisticsBundleKey> set = StatisticsBundleKey.range(
                StatisticsBundleKey.fromString( "DAILY_2000_15" ),
                StatisticsBundleKey.fromString( "DAILY_2000_10" ) );

        final List<StatisticsBundleKey> list = new ArrayList<>( set );

        Assertions.assertEquals( 6, list.size() );
        Assertions.assertEquals( StatisticsBundleKey.fromString( "DAILY_2000_10" ), list.get( 0 ) );
        Assertions.assertEquals( StatisticsBundleKey.fromString( "DAILY_2000_11" ), list.get( 1 ) );
        Assertions.assertEquals( StatisticsBundleKey.fromString( "DAILY_2000_12" ), list.get( 2 ) );
        Assertions.assertEquals( StatisticsBundleKey.fromString( "DAILY_2000_13" ), list.get( 3 ) );
        Assertions.assertEquals( StatisticsBundleKey.fromString( "DAILY_2000_14" ), list.get( 4 ) );
        Assertions.assertEquals( StatisticsBundleKey.fromString( "DAILY_2000_15" ), list.get( 5 ) );
    }
}


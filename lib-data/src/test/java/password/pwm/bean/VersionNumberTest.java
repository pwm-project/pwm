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

package password.pwm.bean;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VersionNumberTest
{
    @Test
    public void testParse()
    {
        {
            final VersionNumber versionNumber = VersionNumber.parse( "v1.2.3" );
            Assert.assertEquals( VersionNumber.of( 1, 2, 3 ), versionNumber );
        }

        {
            final VersionNumber versionNumber = VersionNumber.parse( "v1.2" );
            Assert.assertEquals( VersionNumber.of( 1, 2, 0 ), versionNumber );
        }
    }

    @Test
    public void testComparator()
    {
        final List<VersionNumber> list = new ArrayList<>();

        list.add( VersionNumber.of( 3, 2, 3  ) );
        list.add( VersionNumber.of( 1, 4, 5  ) );
        list.add( VersionNumber.of( 1, 3, 5  ) );
        list.add( VersionNumber.of( 1, 3, 3  ) );
        list.add( VersionNumber.of( 42, 2, 1  ) );
        list.add( VersionNumber.of( 7, 0, 11  ) );

        Collections.sort( list );

        Assert.assertEquals( VersionNumber.of( 1, 3, 3 ), list.get( 0 ) );
        Assert.assertEquals( VersionNumber.of( 1, 3, 5 ), list.get( 1 ) );
        Assert.assertEquals( VersionNumber.of( 1, 4, 5 ), list.get( 2 ) );
        Assert.assertEquals( VersionNumber.of( 3, 2, 3 ), list.get( 3 ) );
        Assert.assertEquals( VersionNumber.of( 7, 0, 11 ), list.get( 4 ) );
        Assert.assertEquals( VersionNumber.of( 42, 2, 1 ), list.get( 5 ) );
    }
}

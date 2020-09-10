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

package password.pwm.util.localdb;

import org.junit.Assert;
import org.junit.Test;

public class LocalDBStoredQueuePositionTest
{
    @Test
    public void positionTest()
    {
        final LocalDBStoredQueue.Position initialPosition = new LocalDBStoredQueue.Position( "0" );
        Assert.assertEquals( "000000", initialPosition.toString() );

        {
            LocalDBStoredQueue.Position position = initialPosition.next();
            Assert.assertEquals( "000001", position.toString() );
            position = position.next();
            Assert.assertEquals( "000002", position.toString() );
            position = position.next();
            Assert.assertEquals( "000003", position.toString() );

            position = position.previous();
            Assert.assertEquals( "000002", position.toString() );
            position = position.previous();
            Assert.assertEquals( "000001", position.toString() );
            position = position.previous();
            Assert.assertEquals( "000000", position.toString() );
            position = position.previous();
            Assert.assertEquals( "ZZZZZZ", position.toString() );
        }

        {
            LocalDBStoredQueue.Position position = initialPosition.previous();
            Assert.assertEquals( "ZZZZZZ", position.toString() );
            position = position.previous();
            Assert.assertEquals( "ZZZZZY", position.toString() );
            position = position.previous();
            Assert.assertEquals( "ZZZZZX", position.toString() );

            position = position.next();
            Assert.assertEquals( "ZZZZZY", position.toString() );
            position = position.next();
            Assert.assertEquals( "ZZZZZZ", position.toString() );
            position = position.next();
            Assert.assertEquals( "000000", position.toString() );
            position = position.next();
            Assert.assertEquals( "000001", position.toString() );
        }

        {
            final long distance = initialPosition.distanceToHead( new LocalDBStoredQueue.Position( "000003" ) );
            Assert.assertEquals( 3, distance );
        }

        {
            final long distance = initialPosition.distanceToHead( new LocalDBStoredQueue.Position( "ZZZZZX" ) );
            Assert.assertEquals( 2176782333L, distance );
        }
    }
}

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

public class DomainIDTest
{
    @Test
    public void testSorting()
    {
        final List<DomainID> list = new ArrayList<>( List.of(
                DomainID.create( "aaaa" ),
                DomainID.systemId(),
                DomainID.create( "bbbb" ) ) );

        Collections.sort( list );

        Assert.assertEquals( DomainID.systemId(), list.get( 0 ) );
        Assert.assertEquals( DomainID.create( "aaaa" ), list.get( 1 ) );
        Assert.assertEquals( DomainID.create( "bbbb" ), list.get( 2 ) );
    }
}

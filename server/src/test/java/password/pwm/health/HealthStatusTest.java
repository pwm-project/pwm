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

package password.pwm.health;

import org.junit.Assert;
import org.junit.Test;

import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Set;

public class HealthStatusTest
{

    @Test
    public void mostSevere()
    {
        final Set<HealthStatus> set = EnumSet.noneOf( HealthStatus.class );
        set.add( HealthStatus.GOOD );
        set.add( HealthStatus.CAUTION );
        set.add( HealthStatus.WARN );
        set.add( HealthStatus.CONFIG );
        Assert.assertEquals( HealthStatus.WARN, HealthStatus.mostSevere( set ).orElseThrow( NoSuchElementException::new ) );

        set.remove( HealthStatus.WARN );
        Assert.assertEquals( HealthStatus.CAUTION, HealthStatus.mostSevere( set ).orElseThrow( NoSuchElementException::new ) );
    }
}

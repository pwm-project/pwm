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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;

import java.util.ArrayList;
import java.util.List;

public class JavaChecker implements HealthChecker
{
    @Override
    public List<HealthRecord> doHealthCheck( final PwmApplication pwmApplication )
    {
        final List<HealthRecord> records = new ArrayList<>();

        final int maxActiveThreads = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.HEALTH_JAVA_MAX_THREADS ) );
        if ( Thread.activeCount() > maxActiveThreads )
        {
            records.add( HealthRecord.forMessage( HealthMessage.Java_HighThreads ) );
        }

        final long minMemory = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.HEALTH_JAVA_MIN_HEAP_BYTES ) );
        if ( Runtime.getRuntime().maxMemory() <= minMemory )
        {
            records.add( HealthRecord.forMessage( HealthMessage.Java_SmallHeap ) );
        }

        if ( records.isEmpty() )
        {
            records.add( HealthRecord.forMessage( HealthMessage.Java_OK ) );
        }

        return records;
    }
}

/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread safe rotating int incrementer with configurable floor and ceiling values.
 */
public class AtomicLoopIntIncrementer
{
    private final AtomicInteger incrementer = new AtomicInteger( 0 );
    private final int ceiling;
    private final int floor;

    public AtomicLoopIntIncrementer()
    {
        this.ceiling = Integer.MAX_VALUE;
        this.floor = 0;
    }

    public AtomicLoopIntIncrementer( final int ceiling )
    {
        this.ceiling = ceiling;
        this.floor = 0;
    }

    public int next( )
    {
        return incrementer.getAndUpdate( operand ->
        {
            operand++;
            if ( operand >= ceiling )
            {
                operand = floor;
            }
            return operand;
        } );
    }
}

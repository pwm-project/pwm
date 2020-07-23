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
    private final AtomicInteger incrementer;
    private final int ceiling;
    private final int floor;

    public AtomicLoopIntIncrementer()
    {
        this( 0, 0, Integer.MAX_VALUE );
    }

    private AtomicLoopIntIncrementer( final int initial, final int floor, final int ceiling )
    {
        if ( floor > ceiling )
        {
            throw new IllegalArgumentException( "floor must be less than ceiling" );
        }

        JavaHelper.rangeCheck( initial, ceiling, floor );

        this.ceiling = ceiling;
        this.floor = floor;
        this.incrementer = new AtomicInteger( initial );
    }

    public int get()
    {
        return incrementer.get();
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


    public static AtomicLoopIntIncrementerBuilder builder()
    {
        return new AtomicLoopIntIncrementerBuilder();
    }

    public static class AtomicLoopIntIncrementerBuilder
    {
        private int initial = 0;
        private int floor = 0;
        private int ceiling = Integer.MAX_VALUE;

        public AtomicLoopIntIncrementerBuilder initial( final int initial )
        {
            this.initial = initial;
            return this;
        }

        public AtomicLoopIntIncrementerBuilder ceiling( final int ceiling )
        {
            this.ceiling = ceiling;
            return this;
        }

        public AtomicLoopIntIncrementer build()
        {
            return new AtomicLoopIntIncrementer( initial, floor, ceiling );
        }
    }
}

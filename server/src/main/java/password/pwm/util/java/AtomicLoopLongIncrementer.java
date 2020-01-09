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

import lombok.Value;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread safe rotating int incrementer with configurable floor and ceiling values.
 */
@Value
public class AtomicLoopLongIncrementer
{
    private final AtomicLong incrementer;
    private final long ceiling;
    private final long floor;

    public AtomicLoopLongIncrementer()
    {
        this( 0, 0, Long.MAX_VALUE );
    }

    private AtomicLoopLongIncrementer( final long initial, final long floor, final long ceiling )
    {
        if ( floor > ceiling )
        {
            throw new IllegalArgumentException( "floor must be less than ceiling" );
        }

        JavaHelper.rangeCheck( initial, ceiling, floor );

        this.ceiling = ceiling;
        this.floor = floor;
        this.incrementer = new AtomicLong( initial );
    }

    public long get()
    {
        return incrementer.get();
    }

    public long next( )
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

    public static AtomicLoopLongIncrementerBuilder builder()
    {
        return new AtomicLoopLongIncrementerBuilder();
    }

    public static class AtomicLoopLongIncrementerBuilder
    {
        private long initial = 0;
        private long floor = 0;
        private long ceiling = Long.MAX_VALUE;

        public AtomicLoopLongIncrementerBuilder initial( final long initial )
        {
            this.initial = initial;
            return this;
        }

        public AtomicLoopLongIncrementer build()
        {
            return new AtomicLoopLongIncrementer( initial, floor, ceiling );
        }
    }
}

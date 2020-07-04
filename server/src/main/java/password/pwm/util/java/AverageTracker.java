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

package password.pwm.util.java;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.LinkedList;
import java.util.Queue;

public class AverageTracker
{
    private final int maxSamples;
    private final Queue<BigInteger> samples = new LinkedList<>();

    public AverageTracker( final int maxSamples )
    {
        this.maxSamples = maxSamples;
    }

    public void addSample( final long input )
    {
        samples.add( new BigInteger( Long.toString( input ) ) );
        while ( samples.size() > maxSamples )
        {
            samples.remove();
        }
    }

    public BigDecimal avg( )
    {
        if ( samples.isEmpty() )
        {
            throw new IllegalStateException( "unable to compute avg without samples" );
        }

        BigInteger total = BigInteger.ZERO;
        for ( final BigInteger sample : samples )
        {
            total = total.add( sample );
        }
        final BigDecimal maxAsBD = new BigDecimal( Integer.toString( maxSamples ) );
        return new BigDecimal( total ).divide( maxAsBD, MathContext.DECIMAL32 );
    }

    public long avgAsLong( )
    {
        return avg().longValue();
    }
}

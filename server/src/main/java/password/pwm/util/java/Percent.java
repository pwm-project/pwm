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
import java.math.MathContext;
import java.math.RoundingMode;

public class Percent
{
    private static final BigDecimal BIG_DECIMAL_ONE_HUNDRED = new BigDecimal( "100" );
    private static final int DEFAULT_SCALE = 2;
    private static final RoundingMode DEFAULT_ROUNDINGMODE = RoundingMode.UP;
    private final BigDecimal percentage;

    public static final Percent ZERO = new Percent( 0, 1 );
    public static final Percent ONE_HUNDRED = new Percent( 1, 1 );

    public Percent( final BigDecimal numerator, final BigDecimal denominator )
    {
        if ( numerator == null )
        {
            throw new NullPointerException( "numerator cannot be null" );
        }
        if ( denominator == null )
        {
            throw new NullPointerException( "denominator cannot be null" );
        }
        if ( denominator.compareTo( BigDecimal.ZERO ) <= 0 )
        {
            throw new NullPointerException( "denominator must be greater than zero" );
        }
        percentage = numerator.divide( denominator, MathContext.DECIMAL32 ).multiply( BIG_DECIMAL_ONE_HUNDRED );
    }

    public Percent( final BigDecimal numerator, final long denominator )
    {
        this( numerator, BigDecimal.valueOf( denominator ) );
    }

    public Percent( final float numerator, final float denominator )
    {
        this( BigDecimal.valueOf( numerator ), BigDecimal.valueOf( denominator ) );
    }

    public Percent( final long numerator, final long denominator )
    {
        this( BigDecimal.valueOf( numerator ), BigDecimal.valueOf( denominator ) );
    }

    public BigDecimal asBigDecimal( final int decimals )
    {
        final BigDecimal pct = asBigDecimal();
        return pct.scale() > decimals
                ? pct.setScale( decimals, DEFAULT_ROUNDINGMODE )
                : pct;
    }

    public BigDecimal asBigDecimal( )
    {
        return percentage;
    }

    public long asLong( )
    {
        return asBigDecimal().longValue();
    }

    public float asFloat( )
    {
        return asBigDecimal().floatValue();
    }

    public String pretty( )
    {
        return pretty( DEFAULT_SCALE );
    }

    public String pretty( final int decimals )
    {
        return asBigDecimal( decimals ).toString() + "%";
    }

    public boolean isComplete( )
    {
        return asBigDecimal( DEFAULT_SCALE ).compareTo( BIG_DECIMAL_ONE_HUNDRED ) >= 0;
    }
}

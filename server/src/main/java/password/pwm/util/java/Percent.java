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
    private static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.UP;

    public static final Percent ZERO = of( 0, 1 );
    public static final Percent ONE_HUNDRED = of( 1, 1 );

    private final BigDecimal percentage;

    private Percent( final BigDecimal numerator, final BigDecimal denominator )
    {
        if ( numerator == null || denominator == null )
        {
            percentage = BigDecimal.ZERO;
        }
        else if ( denominator.compareTo( BigDecimal.ZERO ) <= 0 )
        {
            percentage = BigDecimal.ZERO;
        }
        else
        {
            percentage = numerator.divide( denominator, MathContext.DECIMAL32 ).multiply( BIG_DECIMAL_ONE_HUNDRED );
        }
    }

    public static Percent of( final BigDecimal numerator, final long denominator )
    {
        return of( numerator, BigDecimal.valueOf( denominator ) );
    }

    public static Percent of( final float numerator, final float denominator )
    {
        return of( BigDecimal.valueOf( numerator ), BigDecimal.valueOf( denominator ) );
    }

    public static Percent of( final long numerator, final long denominator )
    {
        return of( BigDecimal.valueOf( numerator ), BigDecimal.valueOf( denominator ) );
    }

    public static Percent of( final BigDecimal numerator, final BigDecimal denominator )
    {
        return new Percent( numerator, denominator );
    }

    public BigDecimal asBigDecimal( final int decimals )
    {
        final BigDecimal pct = asBigDecimal();
        return pct.scale() > decimals
                ? pct.setScale( decimals, DEFAULT_ROUNDING_MODE )
                : pct;
    }

    public BigDecimal asBigDecimal( )
    {
        return percentage;
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

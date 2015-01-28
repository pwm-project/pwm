/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class Percent {
    private static final BigDecimal BIG_DECIMAL_ONE_HUNDRED = new BigDecimal("100");
    private static final int DEFAULT_SCALE = 2;
    private static final RoundingMode DEFAULT_ROUNDINGMODE = RoundingMode.UP;
    private final BigDecimal percentage;

    public static final Percent ZERO = new Percent(0,1);
    public static final Percent ONE_HUNDRED = new Percent(1,1);

    public Percent(final BigDecimal numerator,final BigDecimal denominator) {
        if (numerator == null) {
            throw new NullPointerException("numerator cannot be null");
        }
        if (denominator == null) {
            throw new NullPointerException("denominator cannot be null");
        }
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NullPointerException("denominator must be greater than zero");
        }
        percentage = numerator.divide(denominator, MathContext.DECIMAL32).multiply(BIG_DECIMAL_ONE_HUNDRED);
    }

    public Percent(final BigDecimal numerator, final long denominator) {
        this(numerator, BigDecimal.valueOf(denominator));
    }

    public Percent(final float numerator, final float denominator) {
        this(BigDecimal.valueOf(numerator), BigDecimal.valueOf(denominator));
    }

    public Percent(final long numerator, final long denominator) {
        this(BigDecimal.valueOf(numerator), BigDecimal.valueOf(denominator));
    }

    public BigDecimal asBigDecimal(int decimals) {
        final BigDecimal pct = asBigDecimal();
        return pct.scale() > decimals
                ? pct.setScale(decimals, DEFAULT_ROUNDINGMODE)
                : pct;
    }

    public BigDecimal asBigDecimal() {
        return percentage;
    }

    public long asLong() {
        return asBigDecimal().longValue();
    }

    public float asFloat() {
        return asBigDecimal().floatValue();
    }

    public String pretty() {
        return pretty(DEFAULT_SCALE);
    }

    public String pretty(int decimals) {
        return asBigDecimal(decimals).toString() + "%";
    }

    public boolean isComplete() {
        return asBigDecimal(DEFAULT_SCALE).compareTo(BIG_DECIMAL_ONE_HUNDRED) >= 0;
    }
}

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

import password.pwm.PwmConstants;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A thread-safe wrapper for {@link java.text.SimpleDateFormat}.  This class exists to prevent SimpleDateFormat from being used anywhere without proper synchronization.
 */
public class PwmDateFormat
{
    private final String formatString;
    private final Locale locale;
    private final TimeZone timeZone;

    private PwmDateFormat( final String formatString, final Locale locale, final TimeZone timeZone )
    {
        this.formatString = formatString;
        this.locale = locale;
        this.timeZone = (TimeZone) timeZone.clone();
    }

    public static PwmDateFormat newPwmDateFormat( final String formatString )
    {
        return new PwmDateFormat( formatString, PwmConstants.DEFAULT_LOCALE, PwmConstants.DEFAULT_TIMEZONE );
    }

    public static PwmDateFormat newPwmDateFormat( final String formatString, final Locale locale, final TimeZone timeZone )
    {
        return new PwmDateFormat( formatString, locale, timeZone );
    }

    public static String format( final String formatString, final Instant date )
            throws IllegalArgumentException, NullPointerException
    {
        return newPwmDateFormat( formatString ).format( date );
    }

    public static Instant parse( final String formatString, final String input )
            throws ParseException, IllegalArgumentException, NullPointerException
    {
        return newPwmDateFormat( formatString ).parse( input );
    }

    private SimpleDateFormat newSimpleDateFormat()
            throws IllegalArgumentException, NullPointerException
    {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat( formatString, locale );
        simpleDateFormat.setTimeZone( timeZone );
        return simpleDateFormat;
    }

    public String format( final Instant instant )
            throws IllegalArgumentException, NullPointerException
    {
        return newSimpleDateFormat().format( Date.from( instant ) );
    }

    public Instant parse( final String input )
            throws ParseException, IllegalArgumentException, NullPointerException
    {
        return newSimpleDateFormat().parse( input ).toInstant();
    }
}

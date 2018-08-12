/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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


package password.pwm.cr;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.TimeZone;

public class CrUtils
{
    static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss Z";

    static Instant parseDateString( final String input ) throws ParseException
    {
        final SimpleDateFormat dateFormatter = new SimpleDateFormat( DATE_FORMAT );
        dateFormatter.setTimeZone( TimeZone.getTimeZone( "Zulu" ) );
        return dateFormatter.parse( input ).toInstant();
    }

    static String formatDateString( final Instant input )
    {
        final SimpleDateFormat dateFormatter = new SimpleDateFormat( DATE_FORMAT );
        dateFormatter.setTimeZone( TimeZone.getTimeZone( "Zulu" ) );
        return dateFormatter.format( input );
    }
}

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


package password.pwm.cr;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.TimeZone;

@SuppressWarnings( "checkstyle:MultipleStringLiterals" )
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

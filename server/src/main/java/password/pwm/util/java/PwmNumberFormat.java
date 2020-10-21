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

import password.pwm.PwmConstants;

import java.text.NumberFormat;
import java.util.Locale;

public class PwmNumberFormat
{
    private final Locale locale;

    private PwmNumberFormat( final Locale locale )
    {
        this.locale = locale;
    }

    public static PwmNumberFormat forLocale( final Locale locale )
    {
        return new PwmNumberFormat( locale );
    }

    public static PwmNumberFormat forDefaultLocale( )
    {
        return new PwmNumberFormat( PwmConstants.DEFAULT_LOCALE );
    }

    public String format( final long number )
    {
        final NumberFormat numberFormat = NumberFormat.getInstance( locale );
        return numberFormat.format( number );
    }
}

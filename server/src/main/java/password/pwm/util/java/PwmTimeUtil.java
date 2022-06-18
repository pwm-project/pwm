/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import password.pwm.i18n.Display;
import password.pwm.svc.secure.DomainSecureService;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.secure.PwmRandom;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PwmTimeUtil
{
    public static String asLongString( final TimeDuration timeDuration )
    {
        return asLongString( timeDuration, PwmConstants.DEFAULT_LOCALE );
    }

    public static String asLongString( final TimeDuration timeDuration, final Locale locale )
    {
        final long ms = timeDuration.asMillis();
        final TimeDuration.FractionalTimeDetail fractionalTimeDetail = timeDuration.asFractionalTimeDetail();
        final List<String> segments = new ArrayList<>();

        //output number of days
        if ( fractionalTimeDetail.getDays() > 0 )
        {
            segments.add( fractionalTimeDetail.getDays()
                    + " "
                    + ( fractionalTimeDetail.getDays() == 1
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Day, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Days, null ) )
            );
        }

        //output number of hours
        if ( fractionalTimeDetail.getHours() > 0 )
        {
            segments.add( fractionalTimeDetail.getHours()
                    + " "
                    + ( fractionalTimeDetail.getHours() == 1
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Hour, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Hours, null ) )
            );
        }

        //output number of minutes
        if ( fractionalTimeDetail.getMinutes() > 0 )
        {
            segments.add( fractionalTimeDetail.getMinutes()
                    + " "
                    + ( fractionalTimeDetail.getMinutes() == 1
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Minute, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Minutes, null ) )
            );
        }

        //seconds & ms
        if ( fractionalTimeDetail.getSeconds() > 0 || segments.isEmpty() )
        {
            final StringBuilder sb = new StringBuilder();
            if ( sb.length() == 0 )
            {
                if ( ms < 10_000 )
                {
                    final BigDecimal msDecimal = new BigDecimal( ms ).movePointLeft( 3 );

                    final DecimalFormat formatter;

                    if ( ms > 5000 )
                    {
                        formatter = new DecimalFormat( "#.#" );
                    }
                    else if ( ms > 2000 )
                    {
                        formatter = new DecimalFormat( "#.##" );
                    }
                    else
                    {
                        formatter = new DecimalFormat( "#.###" );
                    }

                    sb.append( formatter.format( msDecimal ) );
                }
                else
                {
                    sb.append( fractionalTimeDetail.getSeconds() );
                }
            }
            else
            {
                sb.append( fractionalTimeDetail.getSeconds() );
            }

            sb.append( ' ' );
            sb.append( ms == 1000
                    ? LocaleHelper.getLocalizedMessage( locale, Display.Display_Second, null )
                    : LocaleHelper.getLocalizedMessage( locale, Display.Display_Seconds, null )
            );

            segments.add( sb.toString() );
        }

        return StringUtil.collectionToString( segments, ", " );
    }

    /**
     * Pause the calling thread the specified amount of time.
     */
    public static void jitterPause( final TimeDuration timeDuration, final DomainSecureService domainSecureService, final float factor )
    {
        final PwmRandom pwmRandom = domainSecureService.pwmRandom();
        final long ms = timeDuration.asMillis();
        final long jitterMs = (long) ( ms * factor );
        final long deviation = pwmRandom.nextBoolean() ? jitterMs + ms : jitterMs - ms;
        timeDuration.pause( TimeDuration.of( deviation, TimeDuration.Unit.MILLISECONDS ), () -> false );
    }

}

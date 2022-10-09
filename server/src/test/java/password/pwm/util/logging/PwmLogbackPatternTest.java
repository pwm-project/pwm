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

package password.pwm.util.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

class PwmLogbackPatternTest
{
    @Test
    public void doLayoutTest()
    {
        final Logger logger = new LoggerContext().getLogger( PwmLogbackPatternTest.class );
        final String classFqdn = PwmLogbackPatternTest.class.getName();
        final String message = "the message!";
        final LoggingEvent event = new LoggingEvent( classFqdn, logger, Level.DEBUG, message, null, new Object[0] );
        event.setInstant( Instant.EPOCH );

        final PwmLogbackPattern pattern = new PwmLogbackPattern();
        final String layout = pattern.doLayout( event );

        Assertions.assertEquals( "1970-01-01T00:00:00Z, DEBUG, logging.PwmLogbackPatternTest, the message!\n", layout );
    }

    @Test
    public void printLoggerNameTest()
            throws Exception
    {
        final Method method = PwmLogbackPattern.class.getDeclaredMethod( "printLoggerName", String.class );
        method.setAccessible( true );

        Assertions.assertEquals( "second.third", method.invoke( null, "zeroth.first.second.third" ) );
        Assertions.assertEquals( "second.third", method.invoke( null, "first.second.third" ) );
        Assertions.assertEquals( "second.third", method.invoke( null, ".second.third" ) );
        Assertions.assertEquals( "second.third", method.invoke( null, "second.third" ) );
        Assertions.assertEquals( "third", method.invoke( null, "third" ) );
    }
}

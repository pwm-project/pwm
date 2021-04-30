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

package password.pwm.util.debug;

import org.apache.commons.io.output.CountingOutputStream;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.LocalDBSearchQuery;
import password.pwm.util.logging.LocalDBSearchResults;
import password.pwm.util.logging.PwmLogEvent;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.function.Function;

class LogDebugItemGenerator implements AppItemGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LogDebugItemGenerator.class );

    static void outputLogs(
            final PwmApplication pwmApplication,
            final OutputStream outputStream,
            final Function<PwmLogEvent, String> logEventFormatter
    )
            throws Exception
    {
        final long maxByteCount = JavaHelper.silentParseLong( pwmApplication.getConfig().readAppProperty( AppProperty.CONFIG_MANAGER_ZIPDEBUG_MAXLOGBYTES ), 10_000_000 );
        final int maxSeconds = JavaHelper.silentParseInt( pwmApplication.getConfig().readAppProperty( AppProperty.CONFIG_MANAGER_ZIPDEBUG_MAXLOGSECONDS ), 60 );
        final LocalDBSearchQuery searchParameters = LocalDBSearchQuery.builder()
                .minimumLevel( PwmLogLevel.TRACE )
                .maxEvents( Integer.MAX_VALUE )
                .maxQueryTime( TimeDuration.of( maxSeconds, TimeDuration.Unit.SECONDS ) )
                .build();

        final LocalDBSearchResults searchResults = pwmApplication.getLocalDBLogger().readStoredEvents( searchParameters );
        final CountingOutputStream countingOutputStream = new CountingOutputStream( outputStream );

        final Writer writer = new OutputStreamWriter( countingOutputStream, PwmConstants.DEFAULT_CHARSET );
        {
            while ( searchResults.hasNext() && countingOutputStream.getByteCount() < maxByteCount )
            {
                final PwmLogEvent event = searchResults.next();
                final String output = logEventFormatter.apply( event );
                writer.write( output );
                writer.write( "\n" );
            }

        }

        // do not close writer because underlying stream should not be closed.
        writer.flush();
    }

    @Override
    public String getFilename()
    {
        return "debug.log";
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
    {
        final Instant startTime = Instant.now();
        final Function<PwmLogEvent, String> logEventFormatter = PwmLogEvent::toLogString;

        outputLogs( debugItemInput.getPwmApplication(), outputStream, logEventFormatter );
        LOGGER.trace( () -> "debug log output completed in ", () -> TimeDuration.fromCurrent( startTime ) );
    }
}

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

package password.pwm.util.debug;

import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogEvent;
import password.pwm.util.logging.PwmLogger;

import java.io.OutputStream;
import java.time.Instant;
import java.util.function.Function;

class LogJsonItemGenerator implements AppItemGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LogJsonItemGenerator.class );

    @Override
    public String getFilename()
    {
        return "debug.json";
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
    {
        final Instant startTime = Instant.now();
        final Function<PwmLogEvent, String> logEventFormatter = JsonFactory.get()::serialize;

        LogDebugItemGenerator.outputLogs( debugItemInput.getPwmApplication(), outputStream, logEventFormatter );
        LOGGER.trace( () -> "debug json output completed in ", () -> TimeDuration.fromCurrent( startTime ) );
    }
}

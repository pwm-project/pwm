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

import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.StringWriter;
import java.time.Instant;
import java.util.function.Supplier;

public class DebugGeneratorLogger
{
    private final StringWriter writer = new StringWriter();
    private final SessionLabel sessionLabel;

    public DebugGeneratorLogger( final SessionLabel sessionLabel )
    {
        this.sessionLabel = sessionLabel;
    }

    public void appendLine( final CharSequence charSequence )
    {
        writer.append( StringUtil.toIsoDate( Instant.now() ) );
        writer.append( ' ' );
        writer.append( charSequence );
        writer.append( '\n' );
    }

    public void error( final ItemGenerator source, final ErrorInformation errorInformation )
    {
        appendLine( errorInformation.toDebugStr() );
        PwmLogger.forClass( source.getClass() ).error( sessionLabel, errorInformation );
    }

    public void error( final ItemGenerator source, final Supplier<String> message )
    {
        appendLine( message.get() );
        PwmLogger.forClass( source.getClass() ).error( sessionLabel, message );
    }

    public String toString()
    {
        return writer.toString();
    }
}

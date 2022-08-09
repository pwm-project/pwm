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

package password.pwm.http.servlet.resource;

import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public enum TextFileResource
{
    eula( "eula.txt" ),
    welcome( "welcome.txt" ),
    privacy( "privacy.txt" ),;

    private static final PwmLogger LOGGER = PwmLogger.forClass( TextFileResource.class );

    private final String filename;

    TextFileResource( final String filename )
    {
        this.filename = filename;
    }

    public String getFilename()
    {
        return filename;
    }

    public static Optional<String> readTextFileResource( final PwmRequest pwmRequest, final TextFileResource resourceName )
            throws PwmUnrecoverableException
    {
        try
        {
            final String path = PwmConstants.URL_PREFIX_PUBLIC + "/resources/text/" + resourceName.getFilename();
            final InputStream inputStream = ContextManager.getContextManager( pwmRequest ).getResourceAsStream( path );
            final Optional<String> rawValue = JavaHelper.copyToString( inputStream, PwmConstants.DEFAULT_CHARSET, 10_000_000 );
            if ( rawValue.isPresent() )
            {
                final MacroRequest macroMachine = pwmRequest.getMacroMachine();
                return Optional.of( macroMachine.expandMacros( rawValue.get() ) );
            }
            return Optional.empty();
        }
        catch ( final IOException e )
        {
            LOGGER.error( pwmRequest, () -> "error reading resource text file " + resourceName.getFilename() + ", error: " + e.getMessage() );
        }

        return Optional.empty();
    }
}

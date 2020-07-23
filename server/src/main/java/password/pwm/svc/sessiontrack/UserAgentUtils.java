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

package password.pwm.svc.sessiontrack;

import com.blueconic.browscap.Capabilities;
import com.blueconic.browscap.ParseException;
import com.blueconic.browscap.UserAgentParser;
import com.blueconic.browscap.UserAgentService;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.PwmRequest;
import password.pwm.util.java.LazySoftReference;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.time.Instant;

public class UserAgentUtils
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserAgentUtils.class );

    private static final LazySoftReference<UserAgentParser> CACHED_PARSER = new LazySoftReference<>( UserAgentUtils::loadUserAgentParser );

    private static UserAgentParser loadUserAgentParser( )
    {
        try
        {
            return new UserAgentService().loadParser();
        }
        catch ( final IOException | ParseException e )
        {
            final String msg = "error loading user-agent parser: " + e.getMessage();
            LOGGER.error( () -> msg, e );
        }

        return null;
    }

    public static void initializeCache() throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        CACHED_PARSER.get();
        LOGGER.trace( () -> "loaded useragent parser in " + TimeDuration.compactFromCurrent( startTime ) );
    }

    public static void checkIfPreIE11( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final String userAgentString = pwmRequest.readHeaderValueAsString( HttpHeader.UserAgent );
        if ( StringUtil.isEmpty( userAgentString ) )
        {
            return;
        }

        boolean badBrowser = false;

        final UserAgentParser userAgentParser = CACHED_PARSER.get();
        final Capabilities capabilities = userAgentParser.parse( userAgentString );
        final String browser = capabilities.getBrowser();
        final String browserMajorVersion = capabilities.getBrowserMajorVersion();

        if ( "IE".equalsIgnoreCase( browser ) )
        {
            try
            {
                final int majorVersionInt = Integer.parseInt( browserMajorVersion );
                if ( majorVersionInt <= 10 )
                {
                    badBrowser = true;
                }
            }
            catch ( final NumberFormatException e )
            {
                LOGGER.error( () -> "error parsing user-agent major version" + e.getMessage(), e );
            }
        }

        if ( badBrowser )
        {
            final String errorMsg = "Internet Explorer version is not supported for this function.  Please use Internet Explorer 11 or higher or another web browser.";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg ) );
        }
    }
}

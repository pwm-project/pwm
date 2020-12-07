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

package password.pwm.svc.sessiontrack;

import com.blueconic.browscap.Capabilities;
import com.blueconic.browscap.ParseException;
import com.blueconic.browscap.UserAgentParser;
import com.blueconic.browscap.UserAgentService;
import lombok.Value;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySoftReference;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;

public class UserAgentUtils
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserAgentUtils.class );

    private static final LazySoftReference<UserAgentParser> CACHED_PARSER = new LazySoftReference<>( UserAgentUtils::loadUserAgentParser );

    public enum BrowserType
    {
        ie( "IE" ),
        ff( "Firefox" ),
        webkit( "Safari" ),
        chrome( "Chrome" ),;

        private final String browserCapName;

        BrowserType( final String browserCapName )
        {
            this.browserCapName = browserCapName;
        }

        static Optional<BrowserType> forBrowserCapName( final String browserCapName )
        {
            for ( final BrowserType browserType : BrowserType.values() )
            {
                if ( browserType.browserCapName.equalsIgnoreCase( browserCapName ) )
                {
                    return Optional.of( browserType );
                }
            }
            return Optional.empty();
        }
    }

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

    public static void initializeCache()
    {
        final Instant startTime = Instant.now();
        CACHED_PARSER.get();
        LOGGER.trace( () -> "loaded useragent parser", () -> TimeDuration.fromCurrent( startTime ) );
    }

    public static void checkIfPreIE11( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final Optional<BrowserInfo> optionalBrowserInfo = getBrowserInfo( pwmRequest );

        if ( optionalBrowserInfo.isPresent() )
        {
            final BrowserInfo browserInfo = optionalBrowserInfo.get();
            if ( BrowserType.ie == browserInfo.getBrowserType() )
            {
                if ( browserInfo.getMajorVersion() <= 10 && browserInfo.getMajorVersion() > -1 )
                {
                    final String errorMsg = "Internet Explorer version is not supported for this function.  Please use Internet Explorer 11 or higher or another web browser.";
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg ) );
                }
            }
        }
    }

    public static Optional<BrowserType> getBrowserType( final PwmRequest pwmRequest )
    {
        final Optional<BrowserInfo> optionalBrowserInfo = getBrowserInfo( pwmRequest );
        if ( optionalBrowserInfo.isPresent() )
        {
            final BrowserInfo browserInfo = optionalBrowserInfo.get();
            return Optional.ofNullable( browserInfo.getBrowserType() );
        }
        return Optional.empty();
    }

    public static Optional<BrowserInfo> getBrowserInfo( final PwmRequest pwmRequest )
    {
        final BrowserInfo cachedBrowserInfo = ( BrowserInfo ) pwmRequest.getAttribute( PwmRequestAttribute.BrowserInfo );
        if ( cachedBrowserInfo != null )
        {
            return Optional.of( cachedBrowserInfo );
        }
        final String userAgentString = pwmRequest.readHeaderValueAsString( HttpHeader.UserAgent );
        if ( StringUtil.isEmpty( userAgentString ) )
        {
            return Optional.empty();
        }

        final UserAgentParser userAgentParser = CACHED_PARSER.get();
        final Capabilities capabilities = userAgentParser.parse( userAgentString );
        final String browser = capabilities.getBrowser();
        final String browserMajorVersion = capabilities.getBrowserMajorVersion();
        final int intMajorVersion = JavaHelper.silentParseInt( browserMajorVersion, -1 );
        final Optional<BrowserType> optionalBrowserType = BrowserType.forBrowserCapName( browser );

        final BrowserInfo browserInfo = new BrowserInfo( optionalBrowserType.orElse( null ), intMajorVersion );
        pwmRequest.setAttribute( PwmRequestAttribute.BrowserInfo, browserInfo );
        return Optional.of( browserInfo );
    }

    @Value
    private static class BrowserInfo implements Serializable
    {
        private final BrowserType browserType;
        private final int majorVersion;
    }
}

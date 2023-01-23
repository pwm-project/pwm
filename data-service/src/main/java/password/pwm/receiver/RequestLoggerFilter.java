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

package password.pwm.receiver;

import password.pwm.util.java.AtomicLoopLongIncrementer;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;

@WebFilter( filterName = "RequestLoggerFilter", urlPatterns = "*" )
public class RequestLoggerFilter implements Filter
{
    private static final Logger LOGGER = Logger.createLogger( TelemetryViewerServlet.class );
    private static final AtomicLoopLongIncrementer REQ_COUNTER = new AtomicLoopLongIncrementer();

    @Override
    public void doFilter( final ServletRequest request, final ServletResponse response, final FilterChain chain )
            throws ServletException, IOException
    {
        final Instant startTime = Instant.now();

        chain.doFilter( request, response );

        final HttpServletRequest req = ( HttpServletRequest ) request;
        final long requestId = REQ_COUNTER.next();
        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );

        LOGGER.debug( () -> "http request #" + requestId + " for "
                + req.getRequestURI() + " from "
                + getSrcDisplayString( req )
                + " (" + timeDuration.asCompactString() + ")" );
    }

    @Override
    public void init( final FilterConfig filterConfig )
            throws ServletException
    {
        Filter.super.init( filterConfig );
    }

    @Override
    public void destroy()
    {
        Filter.super.destroy();
    }

    private static String getSrcDisplayString( final HttpServletRequest request )
    {
        final String address = srcAddress( request );
        final String hostname = srcHostname( request );

        if ( StringUtil.isEmpty( hostname ) || hostname.equals( address ) )
        {
            return address;
        }

        return address + "/" + hostname;
    }

    public static String srcAddress( final HttpServletRequest request )
    {
        final String xForwardedForValue = request.getHeader( "X-Forwarded-For" );
        if ( StringUtil.isEmpty( xForwardedForValue ) )
        {
            return request.getRemoteAddr();
        }

        final List<String> values = StringUtil.splitAndTrim( xForwardedForValue, "," );
        return values.get( 0 );
    }

    public static String srcHostname( final HttpServletRequest request )
    {
        final String addr = srcAddress( request );
        try
        {
            return InetAddress.getByName( addr ).getHostName();
        }
        catch ( final Exception e )
        {
            /* ignore */
        }
        return addr;
    }
}

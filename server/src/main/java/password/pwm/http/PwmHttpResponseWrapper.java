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

package password.pwm.http;

import password.pwm.config.AppConfig;
import password.pwm.util.Validator;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class PwmHttpResponseWrapper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmHttpResponseWrapper.class );

    private final HttpServletRequest httpServletRequest;
    private final HttpServletResponse httpServletResponse;
    private final AppConfig appConfig;

    public enum Flag
    {
        NonHttpOnly,
        BypassSanitation,
    }

    protected PwmHttpResponseWrapper(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AppConfig appConfig
    )
    {
        this.httpServletRequest = request;
        this.httpServletResponse = response;
        this.appConfig = appConfig;
    }

    public HttpServletResponse getHttpServletResponse( )
    {
        return this.httpServletResponse;
    }

    public boolean isCommitted( )
    {
        return this.httpServletResponse.isCommitted();
    }

    public void setHeader( final HttpHeader headerName, final String value )
    {
        this.httpServletResponse.setHeader(
                Validator.sanitizeHeaderValue( appConfig, headerName.getHttpName() ),
                Validator.sanitizeHeaderValue( appConfig, value )
        );
    }

    public void setStatus( final int status )
    {
        httpServletResponse.setStatus( status );
    }

    public void setContentType( final HttpContentType contentType )
    {
        this.getHttpServletResponse().setContentType( contentType.getHeaderValueWithEncoding() );
    }

    public PrintWriter getWriter( )
            throws IOException
    {
        return this.getHttpServletResponse().getWriter();
    }

    public OutputStream getOutputStream( )
            throws IOException
    {
        return this.getHttpServletResponse().getOutputStream();
    }
}

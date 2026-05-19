/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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

package password.pwm.http.filter;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * Verifies that {@link SameSiteCookieResponseWrapper} applies the {@code SameSite}
 * attribute at the moment each cookie is written, which is the fix for issue #716
 * (Jetty 12 makes the response read-only once committed).
 */
public class SameSiteCookieResponseWrapperTest
{
    private static final String SAME_SITE = "Strict";

    @Test
    public void setHeaderAppendsSameSiteToSetCookie()
    {
        final HttpServletResponse delegate = Mockito.mock( HttpServletResponse.class );
        final SameSiteCookieResponseWrapper wrapper = new SameSiteCookieResponseWrapper( delegate, SAME_SITE );

        wrapper.setHeader( "Set-Cookie", "name=value; Path=/; HttpOnly" );

        Mockito.verify( delegate ).setHeader( "Set-Cookie", "name=value; Path=/; HttpOnly; SameSite=Strict" );
    }

    @Test
    public void setHeaderCaseInsensitiveMatch()
    {
        final HttpServletResponse delegate = Mockito.mock( HttpServletResponse.class );
        final SameSiteCookieResponseWrapper wrapper = new SameSiteCookieResponseWrapper( delegate, SAME_SITE );

        wrapper.setHeader( "set-cookie", "x=1" );

        Mockito.verify( delegate ).setHeader( "set-cookie", "x=1; SameSite=Strict" );
    }

    @Test
    public void setHeaderUnrelatedPassthrough()
    {
        final HttpServletResponse delegate = Mockito.mock( HttpServletResponse.class );
        final SameSiteCookieResponseWrapper wrapper = new SameSiteCookieResponseWrapper( delegate, SAME_SITE );

        wrapper.setHeader( "X-Custom", "value" );

        Mockito.verify( delegate ).setHeader( "X-Custom", "value" );
    }

    @Test
    public void setHeaderPreservesExistingSameSite()
    {
        final HttpServletResponse delegate = Mockito.mock( HttpServletResponse.class );
        final SameSiteCookieResponseWrapper wrapper = new SameSiteCookieResponseWrapper( delegate, SAME_SITE );

        wrapper.setHeader( "Set-Cookie", "x=1; SameSite=Lax" );

        Mockito.verify( delegate ).setHeader( "Set-Cookie", "x=1; SameSite=Lax" );
    }

    @Test
    public void addHeaderAppendsSameSiteToSetCookie()
    {
        final HttpServletResponse delegate = Mockito.mock( HttpServletResponse.class );
        final SameSiteCookieResponseWrapper wrapper = new SameSiteCookieResponseWrapper( delegate, SAME_SITE );

        wrapper.addHeader( "Set-Cookie", "a=b" );

        Mockito.verify( delegate ).addHeader( "Set-Cookie", "a=b; SameSite=Strict" );
    }

    @Test
    public void addCookieSerializesWithSameSiteAndDoesNotDoubleEmit()
    {
        final HttpServletResponse delegate = Mockito.mock( HttpServletResponse.class );
        final SameSiteCookieResponseWrapper wrapper = new SameSiteCookieResponseWrapper( delegate, SAME_SITE );

        final Cookie cookie = new Cookie( "session", "abc123" );
        cookie.setPath( "/pwm" );
        cookie.setMaxAge( 3600 );
        cookie.setSecure( true );
        cookie.setHttpOnly( true );

        wrapper.addCookie( cookie );

        // Container's addCookie path must NOT be invoked, otherwise we'd emit a duplicate
        // Set-Cookie header without SameSite.
        Mockito.verify( delegate, Mockito.never() ).addCookie( Mockito.any( Cookie.class ) );

        final ArgumentCaptor<String> name = ArgumentCaptor.forClass( String.class );
        final ArgumentCaptor<String> value = ArgumentCaptor.forClass( String.class );
        Mockito.verify( delegate ).addHeader( name.capture(), value.capture() );

        Assert.assertEquals( "Set-Cookie", name.getValue() );
        final String emitted = value.getValue();
        Assert.assertTrue( "missing name=value: " + emitted, emitted.startsWith( "session=abc123" ) );
        Assert.assertTrue( "missing Max-Age: " + emitted, emitted.contains( "Max-Age=3600" ) );
        Assert.assertTrue( "missing Path: " + emitted, emitted.contains( "Path=/pwm" ) );
        Assert.assertTrue( "missing Secure: " + emitted, emitted.contains( "Secure" ) );
        Assert.assertTrue( "missing HttpOnly: " + emitted, emitted.contains( "HttpOnly" ) );
        Assert.assertTrue( "missing SameSite: " + emitted, emitted.endsWith( "SameSite=Strict" ) );
    }

    @Test
    public void addCookieWithEmptySameSiteFallsBackToDelegate()
    {
        final HttpServletResponse delegate = Mockito.mock( HttpServletResponse.class );
        final SameSiteCookieResponseWrapper wrapper = new SameSiteCookieResponseWrapper( delegate, "" );

        final Cookie cookie = new Cookie( "k", "v" );
        wrapper.addCookie( cookie );

        Mockito.verify( delegate ).addCookie( cookie );
        Mockito.verify( delegate, Mockito.never() ).addHeader( Mockito.anyString(), Mockito.anyString() );
    }

    @Test
    public void setHeaderWithEmptySameSiteIsTransparent()
    {
        final HttpServletResponse delegate = Mockito.mock( HttpServletResponse.class );
        final SameSiteCookieResponseWrapper wrapper = new SameSiteCookieResponseWrapper( delegate, "" );

        wrapper.setHeader( "Set-Cookie", "a=1" );

        Mockito.verify( delegate ).setHeader( "Set-Cookie", "a=1" );
    }

    @Test
    public void addCookieOmitsAbsentAttributes()
    {
        final HttpServletResponse delegate = Mockito.mock( HttpServletResponse.class );
        final SameSiteCookieResponseWrapper wrapper = new SameSiteCookieResponseWrapper( delegate, SAME_SITE );

        // No path, no domain, default max-age (-1), not secure, not http-only.
        final Cookie cookie = new Cookie( "k", "v" );
        wrapper.addCookie( cookie );

        final ArgumentCaptor<String> value = ArgumentCaptor.forClass( String.class );
        Mockito.verify( delegate ).addHeader( Mockito.eq( "Set-Cookie" ), value.capture() );
        final String emitted = value.getValue();

        Assert.assertEquals( "k=v; SameSite=Strict", emitted );
    }
}

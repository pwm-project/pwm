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

package password.pwm.http;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link PwmHttpRequestWrapper#isSensitiveUrlParameter(String)}, which prevents password-type
 * request parameters from being serialized into redirect/query-string URLs (and from there into logs,
 * browser history, or referer headers).
 */
public class PwmHttpRequestWrapperTest
{
    @Test
    public void passwordParametersAreSensitive()
    {
        Assert.assertTrue( PwmHttpRequestWrapper.isSensitiveUrlParameter( "password" ) );
        Assert.assertTrue( PwmHttpRequestWrapper.isSensitiveUrlParameter( "password1" ) );
        Assert.assertTrue( PwmHttpRequestWrapper.isSensitiveUrlParameter( "password2" ) );
        Assert.assertTrue( PwmHttpRequestWrapper.isSensitiveUrlParameter( "currentPassword" ) );
    }

    @Test
    public void passwordMatchIsCaseInsensitive()
    {
        Assert.assertTrue( PwmHttpRequestWrapper.isSensitiveUrlParameter( "PASSWORD" ) );
        Assert.assertTrue( PwmHttpRequestWrapper.isSensitiveUrlParameter( "Password" ) );
    }

    @Test
    public void nonSensitiveParametersAreNotStripped()
    {
        Assert.assertFalse( PwmHttpRequestWrapper.isSensitiveUrlParameter( "returnUrl" ) );
        Assert.assertFalse( PwmHttpRequestWrapper.isSensitiveUrlParameter( "forwardURL" ) );
        Assert.assertFalse( PwmHttpRequestWrapper.isSensitiveUrlParameter( "username" ) );
        Assert.assertFalse( PwmHttpRequestWrapper.isSensitiveUrlParameter( "" ) );
    }

    @Test
    public void nullParameterNameIsNotSensitive()
    {
        Assert.assertFalse( PwmHttpRequestWrapper.isSensitiveUrlParameter( null ) );
    }

    @Test
    public void tokenParameterIsDeliberatelyNotStrippedFromUrls()
    {
        // tokens are intentionally NOT stripped from URLs: email/magic-link flows legitimately carry a
        // token in the query string and replay that URL after authentication.  (Tokens are still masked
        // in debug logging via a separate strip set.)
        Assert.assertFalse( PwmHttpRequestWrapper.isSensitiveUrlParameter( "token" ) );
        Assert.assertFalse( PwmHttpRequestWrapper.isSensitiveUrlParameter( password.pwm.PwmConstants.PARAM_TOKEN ) );
    }
}

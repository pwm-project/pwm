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

package password.pwm.http.servlet.oauth;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.error.PwmUnrecoverableException;

public class OAuthConsumerServletTest
{
    private static final String DEFAULTS = "access_denied,usrcan";

    @Test
    public void cancelErrorValuesAppPropertyDefault()
            throws PwmUnrecoverableException
    {
        final Configuration conf = new Configuration( StoredConfigurationFactory.newConfig() );
        Assert.assertEquals( DEFAULTS, conf.readAppProperty( AppProperty.OAUTH_CANCEL_ERROR_VALUES ) );
        Assert.assertEquals( "sub_error", conf.readAppProperty( AppProperty.HTTP_PARAM_OAUTH_SUB_ERROR ) );
    }

    /**
     * PingFederate and other spec-conformant servers signal a user cancellation with the RFC 6749
     * section 4.1.2.1 <code>access_denied</code> error and no sub error.
     */
    @Test
    public void detectsRfc6749AccessDenied()
    {
        Assert.assertTrue( OAuthConsumerServlet.isUserCancelledError( "access_denied", null, DEFAULTS ) );
        Assert.assertTrue( OAuthConsumerServlet.isUserCancelledError( "access_denied", "", DEFAULTS ) );
    }

    /**
     * NetIQ OSP signals a user cancellation with a proprietary sub error.
     */
    @Test
    public void detectsNetIqUsrcanSubError()
    {
        Assert.assertTrue( OAuthConsumerServlet.isUserCancelledError( "some_error", "usrcan", DEFAULTS ) );
    }

    @Test
    public void doesNotDetectOtherErrors()
    {
        Assert.assertFalse( OAuthConsumerServlet.isUserCancelledError( "server_error", null, DEFAULTS ) );
        Assert.assertFalse( OAuthConsumerServlet.isUserCancelledError( "invalid_request", "svrerr", DEFAULTS ) );
        Assert.assertFalse( OAuthConsumerServlet.isUserCancelledError( null, null, DEFAULTS ) );
    }

    @Test
    public void matchingIsCaseInsensitive()
    {
        Assert.assertTrue( OAuthConsumerServlet.isUserCancelledError( "Access_Denied", null, DEFAULTS ) );
        Assert.assertTrue( OAuthConsumerServlet.isUserCancelledError( "x", "USRCAN", DEFAULTS ) );
    }

    /**
     * Clearing the app property disables cancel detection entirely, restoring the pre-existing
     * behavior of treating every oauth error response as an error.
     */
    @Test
    public void emptyConfigurationDisablesCancelDetection()
    {
        Assert.assertFalse( OAuthConsumerServlet.isUserCancelledError( "access_denied", "usrcan", "" ) );
        Assert.assertFalse( OAuthConsumerServlet.isUserCancelledError( "access_denied", "usrcan", null ) );
    }

    @Test
    public void honorsCustomConfiguredValues()
    {
        Assert.assertTrue( OAuthConsumerServlet.isUserCancelledError( "user_cancelled", null, "user_cancelled" ) );
        Assert.assertFalse( OAuthConsumerServlet.isUserCancelledError( "access_denied", null, "usrcan" ) );
    }
}

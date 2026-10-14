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

package password.pwm.ldap.auth;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.AppProperty;

/**
 * Tests for the forgotten password temporary random password decision: when ldap password writes are
 * disabled via {@link AppProperty#LDAP_PASSWORD_CHANGE_SELF_ENABLE}, an unknown-password session must not
 * overwrite the user's ldap password with a temporary value (it uses the proxy connection instead).
 */
public class LDAPAuthenticationRequestTest
{
    @Test
    public void tempPasswordWrittenByDefaultForForgottenPassword()
    {
        Assert.assertTrue( LDAPAuthenticationRequest.shouldSetTempUserPassword(
                false, true, AuthenticationType.AUTH_FROM_PUBLIC_MODULE ) );
    }

    @Test
    public void tempPasswordNotWrittenWhenLdapPasswordChangeDisabled()
    {
        Assert.assertFalse( LDAPAuthenticationRequest.shouldSetTempUserPassword(
                false, false, AuthenticationType.AUTH_FROM_PUBLIC_MODULE ) );
    }

    @Test
    public void tempPasswordNotWrittenWhenProxyConfiguredForForgotten()
    {
        Assert.assertFalse( LDAPAuthenticationRequest.shouldSetTempUserPassword(
                true, true, AuthenticationType.AUTH_FROM_PUBLIC_MODULE ) );
        Assert.assertFalse( LDAPAuthenticationRequest.shouldSetTempUserPassword(
                true, false, AuthenticationType.AUTH_FROM_PUBLIC_MODULE ) );
    }

    @Test
    public void tempPasswordOnlyWrittenForPublicModuleAuth()
    {
        for ( final AuthenticationType authenticationType : AuthenticationType.values() )
        {
            if ( authenticationType != AuthenticationType.AUTH_FROM_PUBLIC_MODULE )
            {
                Assert.assertFalse( "unexpected temp password for " + authenticationType,
                        LDAPAuthenticationRequest.shouldSetTempUserPassword( false, true, authenticationType ) );
            }
        }
    }

    @Test
    public void ldapPasswordChangeEnabledByDefault()
    {
        // existing deployments keep the temporary password behavior unless they opt out
        Assert.assertEquals( "true", AppProperty.LDAP_PASSWORD_CHANGE_SELF_ENABLE.getDefaultValue() );
    }
}

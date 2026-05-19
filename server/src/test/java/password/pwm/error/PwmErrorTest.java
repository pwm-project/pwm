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

package password.pwm.error;

import com.novell.ldapchai.exception.ChaiError;
import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;

import java.util.HashSet;
import java.util.Set;

public class PwmErrorTest
{

    @Test
    public void testPwmErrorNumbers() throws Exception
    {
        final Set<Integer> seenErrorNumbers = new HashSet<>();
        for ( final PwmError loopError : PwmError.values() )
        {
            if ( seenErrorNumbers.contains( loopError.getErrorCode() ) )
            {
                throw new Exception( "duplicate error code: " + loopError.getErrorCode() + " " + loopError.toString() );
            }
            seenErrorNumbers.add( loopError.getErrorCode() );
        }
    }

    @Test
    public void testLocalizedMessage()
    {
        for ( final PwmError pwmError : PwmError.values() )
        {
            pwmError.getLocalizedMessage( PwmConstants.DEFAULT_LOCALE, null );
        }
    }

    /**
     * Issue #728: verify that an LDAP NO_ACCESS error - which Chai produces when the
     * proxy account lacks ACL permission to modify the target user (common in Active
     * Directory with AdminSDHolder) - is mapped to a specific PWM error rather than
     * falling through to ERROR_INTERNAL or being misreported as PASSWORD_BADPASSWORD.
     */
    @Test
    public void testChaiNoAccessMapsToPermissionDenied()
    {
        Assert.assertEquals( PwmError.ERROR_LDAP_PERMISSION_DENIED, PwmError.forChaiError( ChaiError.NO_ACCESS ) );
    }

    /**
     * Issue #728: the new error must resolve to a non-empty localized message at the
     * default locale, otherwise the user will see only a bare error code.
     */
    @Test
    public void testPermissionDeniedHasLocalizedMessage()
    {
        final String message = PwmError.ERROR_LDAP_PERMISSION_DENIED.getLocalizedMessage( PwmConstants.DEFAULT_LOCALE, null );
        Assert.assertNotNull( message );
        Assert.assertFalse( "ERROR_LDAP_PERMISSION_DENIED resolves to an empty message", message.isEmpty() );
    }
}

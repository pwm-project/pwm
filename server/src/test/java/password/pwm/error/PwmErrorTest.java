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

package password.pwm.error;

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
}

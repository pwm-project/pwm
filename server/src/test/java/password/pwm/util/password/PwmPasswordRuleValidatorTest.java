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

package password.pwm.util.password;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PwmPasswordRuleValidatorTest
{
    @Test
    public void testContainsDisallowedValue() throws Exception
    {
        // containsDisallowedValue([new password], [disallowed value], [character match threshold])

        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "n", "n", 0 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "N", "n", 0 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "n", "N", 0 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "N", 0 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "o", 0 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "V", 0 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "e", 0 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "l", 0 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "n", "n", 10 ) );

        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "novell", 0 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "novell", 5 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "novell", 6 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "novell", 7 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "foo", 0 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "", 0 ) );

        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 1 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 2 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 3 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 4 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 5 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 6 ) );

        // Case shouldn't matter
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 1 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 2 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 3 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 4 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 5 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 6 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 1 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 2 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 3 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 4 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 5 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 6 ) );

        // Play around the threshold boundaries
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-nove-bar", "novell", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-ovel-bar", "novell", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-vell-bar", "novell", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-nove", "novell", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-ovel", "novell", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-vell", "novell", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "nove-bar", "novell", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "ovel-bar", "novell", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "vell-bar", "novell", 4 ) );
    }

    @Test
    public void testTooManyConsecutiveChars()
    {
        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( null, 4 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "", 4 ) );

        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "12345678", 0 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 0 ) );

        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 1 ) );
        // 'n' and 'o' are consecutive
        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 2 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 3 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 4 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 5 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 6 ) );

        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "xyznovell", 3 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novellabc", 3 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novfghell", 3 ) );

        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "Novell1235", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "Novell1234", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "1234Novell", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "Nov1234ell", 4 ) );

        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "123novabcellxyz", 4 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "123novabcellxyz", 3 ) );

        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", -1 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 0 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 1 ) );
        Assertions.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 27 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 26 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 25 ) );
        Assertions.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 2 ) );
    }
}

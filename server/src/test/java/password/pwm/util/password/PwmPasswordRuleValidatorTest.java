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

package password.pwm.util.password;

import org.junit.Assert;
import org.junit.Test;

public class PwmPasswordRuleValidatorTest
{
    @Test
    public void testContainsDisallowedValue() throws Exception
    {
        // containsDisallowedValue([new password], [disallowed value], [character match threshold])

        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "n", "n", 0 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "N", "n", 0 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "n", "N", 0 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "N", 0 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "o", 0 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "V", 0 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "e", 0 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "l", 0 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "n", "n", 10 ) );

        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "novell", 0 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "novell", 5 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "novell", 6 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "novell", 7 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "foo", 0 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "novell", "", 0 ) );

        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 1 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 2 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 3 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 4 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 5 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "novell", 6 ) );

        // Case shouldn't matter
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 1 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 2 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 3 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 4 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 5 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "LOVE", "novell", 6 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 1 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 2 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 3 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 4 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 5 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.containsDisallowedValue( "love", "NOVELL", 6 ) );

        // Play around the threshold boundaries
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-nove-bar", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-ovel-bar", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-vell-bar", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-nove", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-ovel", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "foo-vell", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "nove-bar", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "ovel-bar", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.containsDisallowedValue( "vell-bar", "novell", 4 ) );
    }

    @Test
    public void testTooManyConsecutiveChars()
    {
        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( null, 4 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "", 4 ) );

        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "12345678", 0 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 0 ) );

        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 1 ) );
        // 'n' and 'o' are consecutive
        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 2 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 3 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 4 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 5 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novell", 6 ) );

        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "xyznovell", 3 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novellabc", 3 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "novfghell", 3 ) );

        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "Novell1235", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "Novell1234", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "1234Novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "Nov1234ell", 4 ) );

        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "123novabcellxyz", 4 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "123novabcellxyz", 3 ) );

        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", -1 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 0 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 1 ) );
        Assert.assertFalse( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 27 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 26 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 25 ) );
        Assert.assertTrue( PwmPasswordRuleUtil.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 2 ) );
    }
}

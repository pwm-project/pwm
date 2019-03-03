/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.util;

import org.junit.Assert;
import org.junit.Test;

public class PwmPasswordRuleValidatorTest
{
    @Test
    public void testContainsDisallowedValue() throws Exception
    {
        // containsDisallowedValue([new password], [disallowed value], [character match threshold])

        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "n", "n", 0 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "N", "n", 0 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "n", "N", 0 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "N", 0 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "o", 0 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "V", 0 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "e", 0 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "l", 0 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "n", "n", 10 ) );

        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "novell", 0 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "novell", 5 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "novell", 6 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "novell", 7 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "foo", 0 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "novell", "", 0 ) );

        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "love", "novell", 1 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "love", "novell", 2 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "love", "novell", 3 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "love", "novell", 4 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "love", "novell", 5 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "love", "novell", 6 ) );

        // Case shouldn't matter
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "LOVE", "novell", 1 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "LOVE", "novell", 2 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "LOVE", "novell", 3 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "LOVE", "novell", 4 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "LOVE", "novell", 5 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "LOVE", "novell", 6 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "love", "NOVELL", 1 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "love", "NOVELL", 2 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "love", "NOVELL", 3 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "love", "NOVELL", 4 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "love", "NOVELL", 5 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.containsDisallowedValue( "love", "NOVELL", 6 ) );

        // Play around the threshold boundaries
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "foo-nove-bar", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "foo-ovel-bar", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "foo-vell-bar", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "foo-nove", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "foo-ovel", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "foo-vell", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "nove-bar", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "ovel-bar", "novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.containsDisallowedValue( "vell-bar", "novell", 4 ) );
    }

    @Test
    public void testTooManyConsecutiveChars()
    {
        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( null, 4 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "", 4 ) );

        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "12345678", 0 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "novell", 0 ) );

        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "novell", 1 ) );
        // 'n' and 'o' are consecutive
        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "novell", 2 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "novell", 3 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "novell", 4 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "novell", 5 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "novell", 6 ) );

        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "xyznovell", 3 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "novellabc", 3 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "novfghell", 3 ) );

        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "Novell1235", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "Novell1234", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "1234Novell", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "Nov1234ell", 4 ) );

        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "123novabcellxyz", 4 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "123novabcellxyz", 3 ) );

        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", -1 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 0 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 1 ) );
        Assert.assertFalse( PwmPasswordRuleValidator.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 27 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 26 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 25 ) );
        Assert.assertTrue( PwmPasswordRuleValidator.tooManyConsecutiveChars( "abcdefghijklmnopqrstuvwxyz", 2 ) );
    }
}

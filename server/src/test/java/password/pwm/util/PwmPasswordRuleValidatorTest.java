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
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.util.password.PwmPasswordRuleUtil;
import password.pwm.util.password.PwmPasswordRuleValidator;

import java.util.HashMap;
import java.util.Map;

public class PwmPasswordRuleValidatorTest
{
    @Test
    public void complexPolicyTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
        policyMap.put( PwmPasswordRule.MinimumLength.getKey(), "3" );
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );

        final PwmPasswordPolicy pwmPasswordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy( policyMap );

        final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator( null, pwmPasswordPolicy );
        Assert.assertTrue( pwmPasswordRuleValidator.testPassword( new PasswordData( "123" ), null, null, null ) );

    }

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

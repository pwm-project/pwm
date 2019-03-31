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

package password.pwm.config.profile;

import org.junit.Test;
import password.pwm.PwmConstants;

public class PwmPasswordRuleTest
{
    @Test
    public void testRuleLabels() throws Exception
    {
        for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
        {
            final String value = rule.getLabel( PwmConstants.DEFAULT_LOCALE, null );
            if ( value == null || value.contains( "MissingKey" ) )
            {
                throw new Exception( " missing label for PwmPasswordRule " + rule.name() );
            }
        }
    }
}

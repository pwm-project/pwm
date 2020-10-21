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

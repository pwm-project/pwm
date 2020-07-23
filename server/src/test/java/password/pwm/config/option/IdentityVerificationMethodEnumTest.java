/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.config.option;

import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.error.PwmUnrecoverableException;

public class IdentityVerificationMethodEnumTest
{
    @Test
    public void testLabels() throws PwmUnrecoverableException
    {
        final Configuration configuration = new Configuration( StoredConfigurationFactory.newConfig() );
        for ( final IdentityVerificationMethod method : IdentityVerificationMethod.values() )
        {
            method.getLabel( configuration, PwmConstants.DEFAULT_LOCALE );
        }
    }

    @Test
    public void testDescriptions() throws PwmUnrecoverableException
    {
        final Configuration configuration = new Configuration( StoredConfigurationFactory.newConfig() );
        for ( final IdentityVerificationMethod category : IdentityVerificationMethod.values() )
        {
            category.getDescription( configuration, PwmConstants.DEFAULT_LOCALE );
        }
    }

}

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

package password.pwm;

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

public class PwmApplication
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmApplication.class );
    private static final String DEFAULT_INSTANCE_ID = "-1";


    private PwmEnvironment pwmEnvironment;

    public PwmApplication( final PwmEnvironment pwmEnvironment )
            throws PwmUnrecoverableException
    {
        this.pwmEnvironment = pwmEnvironment;
        pwmEnvironment.verifyIfApplicationPathIsSetProperly();


    }

    public static PwmApplication createPwmApplication( final PwmEnvironment pwmEnvironment ) throws PwmUnrecoverableException
    {
        return new PwmApplication( pwmEnvironment );
    }


}

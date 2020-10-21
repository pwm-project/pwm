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

package password.pwm.svc.shorturl;

import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;

import java.util.Properties;

public class BasicUrlShortener implements AbstractUrlShortener
{
    private Properties configuration = null;

    public BasicUrlShortener( )
    {
    }

    public BasicUrlShortener( final Properties configuration )
    {
        this.configuration = configuration;
    }

    public void setConfiguration( final Properties configuration )
    {
        this.configuration = configuration;
    }

    public Properties getConfiguration( )
    {
        return configuration;
    }

    @Override
    public String shorten( final String input, final PwmApplication context ) throws PwmUnrecoverableException
    {
        /*
         * This function does nothing.
         * Real functionality has to be implemented by extending this class
         */
        return input;
    }
}

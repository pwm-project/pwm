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

package password.pwm.svc.wordlist;

import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;


/**
 * @author Jason D. Rivard
 */
public class WordlistService extends AbstractWordlist implements Wordlist
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( WordlistService.class );

    public WordlistService( )
    {
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        super.init( pwmApplication, WordlistType.WORDLIST );
    }

    @Override
    PwmLogger getLogger()
    {
        return LOGGER;
    }

    public boolean containsWord( final String word ) throws PwmUnrecoverableException
    {
        return super.containsWord( this.getWordTypesCache(), word );
    }
}

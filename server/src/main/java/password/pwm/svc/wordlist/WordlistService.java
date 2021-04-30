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

import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.time.Instant;


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
    protected WordlistType getWordlistType()
    {
        return WordlistType.WORDLIST;
    }

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    public boolean containsWord( final String word ) throws PwmUnrecoverableException
    {
        return super.containsWord( this.getWordTypesCache(), word );
    }

    protected void warmup()
    {
        getPwmApplication().getPwmScheduler().immediateExecuteRunnableInNewThread( new WarmupJob(), "wordlist-warmup" );
    }

    private class WarmupJob implements Runnable
    {
        @Override
        public void run()
        {

            final Instant startTime = Instant.now();
            final PwmRandom pwmRandom = getPwmApplication().getSecureService().pwmRandom();
            final int warmupCount = getConfiguration().getWarmupLookups();

            getLogger().trace( getSessionLabel(),
                    () -> "beginning warmup using " + warmupCount + " random words" );

            for ( int i = 0; i < warmupCount; i++ )
            {
                final String testWord = pwmRandom.alphaNumericString( pwmRandom.nextInt( 10 ) + 5 );
                try
                {
                    containsWord( getWordTypesCache(), testWord );

                    if ( status() != STATUS.OPEN )
                    {
                        LOGGER.trace( getSessionLabel(), () -> "exiting cancelled warmup..." );
                        return;
                    }
                }
                catch ( final PwmException e )
                {
                    getLogger().trace( getSessionLabel(), () -> "error during warmup word check: " + e.getMessage() );
                }
            }

            getLogger().trace( getSessionLabel(),
                    () -> "warmup using " + warmupCount + " random words complete",
                    () -> TimeDuration.fromCurrent( startTime ) );

            outputStats();
        }
    }
}

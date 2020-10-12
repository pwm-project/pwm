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

package password.pwm.util.logging;

import password.pwm.util.java.TimeDuration;

import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class LocalDBSearchResults implements Iterator<PwmLogEvent>
{
    private transient LocalDBLogger localDBLogger;
    private final Iterator<String> localDBIterator;
    private final LocalDBSearchQuery searchParameters;

    private final Instant startTime;

    private PwmLogEvent nextEvent;
    private int eventCount = 0;
    private Instant finishTime;

    LocalDBSearchResults( final LocalDBLogger localDBLogger,
                          final Iterator<String> localDBIterator,
                          final LocalDBSearchQuery searchParameters
    )
    {
        this.localDBLogger = localDBLogger;
        startTime = Instant.now();
        this.localDBIterator = localDBIterator;
        this.searchParameters = searchParameters;
        nextEvent = readNextEvent();
    }

    @Override
    public boolean hasNext( )
    {
        return nextEvent != null;
    }

    @Override
    public void remove( )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PwmLogEvent next( )
    {
        if ( nextEvent == null )
        {
            throw new NoSuchElementException();
        }

        final PwmLogEvent returnEvent = nextEvent;
        nextEvent = readNextEvent();
        return returnEvent;
    }

    private boolean isTimedOut( )
    {
        return searchParameters.getMaxQueryTime() != null
                && TimeDuration.fromCurrent( startTime ).isLongerThan( searchParameters.getMaxQueryTime() );
    }

    private boolean isSizeExceeded( )
    {
        return searchParameters.getMaxEvents() > 0
                && eventCount >= searchParameters.getMaxEvents();
    }

    private PwmLogEvent readNextEvent( )
    {
        if ( isSizeExceeded() || isTimedOut() )
        {
            finishTime = Instant.now();
            return null;
        }

        while ( !isTimedOut() && localDBIterator.hasNext() )
        {
            final String nextDbValue = localDBIterator.next();
            if ( nextDbValue == null )
            {
                finishTime = Instant.now();
                return null;
            }

            final PwmLogEvent logEvent = localDBLogger.readEvent( nextDbValue );
            if ( logEvent != null && localDBLogger.checkEventForParams( logEvent, searchParameters ) )
            {
                eventCount++;
                return logEvent;
            }
        }

        finishTime = Instant.now();
        return null;
    }

    public int getReturnedEvents( )
    {
        return eventCount;
    }


    public TimeDuration getSearchTime( )
    {
        return finishTime == null ? TimeDuration.fromCurrent( startTime ) : TimeDuration.between( startTime, finishTime );
    }
}

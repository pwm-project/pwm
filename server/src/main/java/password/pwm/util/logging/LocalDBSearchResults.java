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
        return finishTime == null ? TimeDuration.fromCurrent( startTime ) : new TimeDuration( startTime, finishTime );
    }
}

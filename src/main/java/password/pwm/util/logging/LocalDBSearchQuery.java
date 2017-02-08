/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

public class LocalDBSearchQuery {
    private final PwmLogLevel minimumLevel;
    private final int maxEvents;
    private final String username;
    private final String text;
    private final long maxQueryTime;
    private final LocalDBLogger.EventType eventType;

    public LocalDBSearchQuery(
            final PwmLogLevel minimumLevel,
            final int count,
            final String username,
            final String text,
            final long maxQueryTime,
            final LocalDBLogger.EventType eventType        )
    {
        this.eventType = eventType;
        this.maxQueryTime = maxQueryTime;
        this.text = text;
        this.username = username;
        this.maxEvents = count;
        this.minimumLevel = minimumLevel;
    }

    public PwmLogLevel getMinimumLevel()
    {
        return minimumLevel;
    }

    public int getMaxEvents()
    {
        return maxEvents;
    }

    public String getUsername()
    {
        return username;
    }

    public String getText()
    {
        return text;
    }

    public long getMaxQueryTime()
    {
        return maxQueryTime;
    }

    public LocalDBLogger.EventType getEventType()
    {
        return eventType;
    }
}

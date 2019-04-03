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

package password.pwm.resttest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SmsResponse
{
    public static final SmsResponse INSTANCE = new SmsResponse();

    Map<String, ArrayList<SmsPostResponseBody>> recentSmsMessages;

    public SmsResponse()
    {
        this.recentSmsMessages = new HashMap<String, ArrayList<SmsPostResponseBody>>();
    }

    /** Getters and Setters. */
    public static synchronized SmsResponse getInstance()
    {
        return INSTANCE;
    }

    Map<String, ArrayList<SmsPostResponseBody>> getRecentSmsMessages()
    {
        return recentSmsMessages;
    }

    public void setRecentSmsMessages( final Map<String, ArrayList<SmsPostResponseBody>> recentSmsMessages )
    {
        this.recentSmsMessages = recentSmsMessages;
    }

    /** Helper Functions. */
    public void addToMap( final String username, final SmsPostResponseBody responseBody )
    {
        if ( recentSmsMessages.containsKey( username ) )
        {
            recentSmsMessages.get( username ).add( responseBody );
        }
        else
        {
            final ArrayList<SmsPostResponseBody> arrayList = new ArrayList<>();
            arrayList.add( responseBody );
            recentSmsMessages.put( username, arrayList );
        }
    }

    public SmsPostResponseBody getRecentFromMap( final String username )
    {
        SmsPostResponseBody responseBody = new SmsPostResponseBody();
        if ( recentSmsMessages.containsKey( username ) )
        {
            final ArrayList<SmsPostResponseBody> userMessages = recentSmsMessages.get( username );
            int mostRecentIndex = 0;
            for ( int i = 0; i < userMessages.size(); i++ )
            {
                if ( userMessages.get( i ).getDate().toEpochMilli() > userMessages.get( mostRecentIndex ).getDate().toEpochMilli() )
                {
                    mostRecentIndex = i;
                }
            }
            responseBody = userMessages.get( mostRecentIndex );
        }
        return responseBody;
    }
}

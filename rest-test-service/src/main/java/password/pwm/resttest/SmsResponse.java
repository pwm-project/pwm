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

package password.pwm.resttest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SmsResponse
{
    private Map<String, ArrayList<SmsPostResponseBody>> recentSmsMessages;

    public SmsResponse()
    {
        this.recentSmsMessages = new HashMap<>();
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

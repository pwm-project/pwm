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

import java.time.Instant;

public class SmsPostResponseBody
{
    private String messageContent;
//    private Instant date;

    public SmsPostResponseBody( final String message )
    {
        final String[] strings = message.split( "&" );
        this.messageContent = strings[strings.length - 1];
    }

    public SmsPostResponseBody()
    {

    }


    public String getMessageContent()
    {
        return messageContent;
    }

//    public void setMessageContent( final String messageContent )
//    {
//        this.messageContent = messageContent;
//    }

    public Instant getDate()
    {
        return Instant.now();
    }

//    public void setDate( final Instant date )
//    {
//        this.date = Instant.now();
//    }
}

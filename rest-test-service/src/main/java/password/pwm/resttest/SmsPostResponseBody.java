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

import java.time.Instant;

public class SmsPostResponseBody
{
    private String messageContent;
    private Instant date;

    public SmsPostResponseBody( final String message, final Instant date )
    {
        final String[] strings = message.split( "&" );
        this.messageContent = strings[strings.length - 1];
        this.date = date;
    }

    public SmsPostResponseBody( final String message )
    {
        final String[] strings = message.split( "&" );
        this.messageContent = strings[strings.length - 1];
    }

    public SmsPostResponseBody( final Instant date )
    {
        this.date = date;
        this.messageContent = "";
    }

    public SmsPostResponseBody()
    {

    }

    public String getMessageContent()
    {
        return messageContent;
    }

    public void setMessageContent( final String messageContent )
    {
        this.messageContent = messageContent;
    }

    public Instant getDate()
    {
        return date;
    }

    public void setDate( final Instant date )
    {
        this.date = date;
    }
}

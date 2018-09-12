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

    public String shorten( final String input, final PwmApplication context ) throws PwmUnrecoverableException
    {
        /*
         * This function does nothing.
         * Real functionality has to be implemented by extending this class
         */
        return input;
    }
}

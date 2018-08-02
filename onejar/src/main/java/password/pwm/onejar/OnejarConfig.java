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

package password.pwm.onejar;

import java.io.File;
import java.io.InputStream;

class OnejarConfig
{
    private int port;
    private File applicationPath;
    private File workingPath;
    private InputStream war;
    private String context;
    private String localAddress;
    private String keystorePass;

    public int getPort( )
    {
        return port;
    }

    public void setPort( final int port )
    {
        this.port = port;
    }

    public File getApplicationPath( )
    {
        return applicationPath;
    }

    public void setApplicationPath( final File applicationPath )
    {
        this.applicationPath = applicationPath;
    }

    public File getWorkingPath( )
    {
        return workingPath;
    }

    public void setWorkingPath( final File workingPath )
    {
        this.workingPath = workingPath;
    }

    public InputStream getWar( )
    {
        return war;
    }

    public void setWar( final InputStream war )
    {
        this.war = war;
    }

    public String getContext( )
    {
        return context;
    }

    public void setContext( final String context )
    {
        this.context = context;
    }

    public String getLocalAddress( )
    {
        return localAddress;
    }

    public void setLocalAddress( final String localAddress )
    {
        this.localAddress = localAddress;
    }

    public String getKeystorePass( )
    {
        return keystorePass;
    }

    public void setKeystorePass( final String keystorePass )
    {
        this.keystorePass = keystorePass;
    }
}

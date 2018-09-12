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

package password.pwm.http.servlet.resource;

import password.pwm.http.bean.ImmutableByteArray;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

class MemoryFileResource implements FileResource
{
    private final String name;
    private final ImmutableByteArray contents;
    private final long lastModified;

    MemoryFileResource( final String name, final ImmutableByteArray contents, final long lastModified )
    {
        this.name = name;
        this.contents = contents;
        this.lastModified = lastModified;
    }

    public InputStream getInputStream( ) throws IOException
    {
        return new ByteArrayInputStream( contents.getBytes() );
    }

    public long length( )
    {
        return contents.getBytes().length;
    }

    public long lastModified( )
    {
        return lastModified;
    }

    public boolean exists( )
    {
        return true;
    }

    public String getName( )
    {
        return name;
    }
}

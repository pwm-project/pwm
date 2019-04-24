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

package password.pwm.util.secure;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;

public class ChecksumOutputStream extends OutputStream
{
    private final CRC32 crc32 = new CRC32();
    private final OutputStream wrappedStream;

    public ChecksumOutputStream( final OutputStream wrappedStream )
    {
        this.wrappedStream = wrappedStream;
    }

    @Override
    public void close( ) throws IOException
    {
        wrappedStream.close();
    }

    @Override
    public void write( final byte[] bytes ) throws IOException
    {
        write( bytes, 0, bytes.length );
    }

    @Override
    public void write( final byte[] bytes, final int off, final int len ) throws IOException
    {
        if ( len > 0 )
        {
            crc32.update( bytes, off, len );
        }

        wrappedStream.write( bytes, off, len );
    }

    @Override
    public void flush( ) throws IOException
    {
        wrappedStream.flush();
    }

    @Override
    public void write( final int b ) throws IOException
    {
        crc32.update( ( byte ) b );
        wrappedStream.write( b );
    }

    public String checksum( )
    {
        return stringifyChecksum( crc32.getValue() );
    }

    static String stringifyChecksum( final long value )
    {
        return Long.toString( value, 36 ).toLowerCase();
    }
}

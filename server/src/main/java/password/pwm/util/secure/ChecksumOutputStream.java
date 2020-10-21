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

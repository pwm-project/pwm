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
import java.io.InputStream;
import java.util.zip.CRC32;

public class ChecksumInputStream extends InputStream
{
    private final CRC32 crc32 = new CRC32();
    private final InputStream wrappedStream;

    public ChecksumInputStream( final InputStream wrappedStream )
    {
        this.wrappedStream = wrappedStream;
    }

    @Override
    public int read( ) throws IOException
    {
        final int value = wrappedStream.read();
        if ( value >= 0 )
        {
            crc32.update( ( byte ) value );
        }
        return value;
    }

    @Override
    public int read( final byte[] b ) throws IOException
    {
        final int length = wrappedStream.read( b );
        if ( length > 0 )
        {
            crc32.update( b, 0, length );
        }
        return length;
    }

    @Override
    public int read( final byte[] b, final int off, final int len ) throws IOException
    {
        final int length = wrappedStream.read( b, off, len );
        if ( length > 0 )
        {
            crc32.update( b, off, length );
        }
        return length;
    }

    @Override
    public long skip( final long n ) throws IOException
    {
        throw new IOException( "operation not supported" );
    }

    @Override
    public int available( ) throws IOException
    {
        return wrappedStream.available();
    }

    @Override
    public void close( ) throws IOException
    {
        wrappedStream.close();
    }

    @Override
    public synchronized void mark( final int readlimit )
    {
        wrappedStream.mark( readlimit );
    }

    @Override
    public synchronized void reset( ) throws IOException
    {
        throw new IOException( "operation not supported" );
    }

    @Override
    public boolean markSupported( )
    {
        return false;
    }

    public String checksum( )
    {
        return ChecksumOutputStream.stringifyChecksum( crc32.getValue() );
    }

    public String readUntilEndAndChecksum( ) throws IOException
    {
        final byte[] buffer = new byte[ 1024 ];

        while ( read( buffer ) > 0 )
        {
            // read out the remainder of the stream contents
        }

        return checksum();
    }
}

/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.util.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LengthLimitedInputStream extends InputStream
{
    private final InputStream realStream;
    private final long maxBytes;

    private long lengthCount = 0;

    public LengthLimitedInputStream( final InputStream inputStream, final long maxBytes )
    {
        this.realStream = inputStream;
        this.maxBytes = maxBytes;
    }

    private void checkLength( final long addLength )
            throws IOException
    {
        if ( addLength > 0 )
        {
            this.lengthCount += addLength;
            if ( lengthCount > maxBytes )
            {
                throw new IOException( "maximum input length exceeded" );
            }
        }
    }

    @Override
    public int read( final byte[] b ) throws IOException
    {
        final int bytesRead = realStream.read( b );
        checkLength( bytesRead );
        return bytesRead;
    }

    @Override
    public int read( final byte[] b, final int off, final int len ) throws IOException
    {
        final int bytesRead = realStream.read( b, off, len );
        checkLength( bytesRead );
        return bytesRead;
    }

    @Override
    public byte[] readAllBytes() throws IOException
    {
        final byte[] bytesRead = realStream.readAllBytes();
        checkLength( bytesRead == null ? 0 : bytesRead.length );
        return bytesRead;
    }

    @Override
    public byte[] readNBytes( final int len ) throws IOException
    {
        final byte[] readBytes = realStream.readNBytes( len );
        checkLength( readBytes == null ? 0 : readBytes.length );
        return readBytes;
    }

    @Override
    public int readNBytes( final byte[] b, final int off, final int len ) throws IOException
    {
        final int bytesRead = realStream.readNBytes( b, off, len );
        checkLength( bytesRead );
        return bytesRead;
    }

    @Override
    public long skip( final long n ) throws IOException
    {
        return realStream.skip( n );
    }

    @Override
    public int available() throws IOException
    {
        return realStream.available();
    }

    @Override
    public void close() throws IOException
    {
        realStream.close();
    }

    @Override
    public synchronized void mark( final int readlimit )
    {
        realStream.mark( readlimit );
    }

    @Override
    public synchronized void reset() throws IOException
    {
        realStream.reset();
    }

    @Override
    public boolean markSupported()
    {
        return realStream.markSupported();
    }

    @Override
    public long transferTo( final OutputStream out ) throws IOException
    {
        final long bytesRead = realStream.transferTo( out );
        checkLength( bytesRead );
        return bytesRead;
    }

    @Override
    public int read() throws IOException
    {
        final int byteValue = realStream.read();
        if ( byteValue > -1 )
        {
            checkLength( 1 );
        }
        return byteValue;
    }
}

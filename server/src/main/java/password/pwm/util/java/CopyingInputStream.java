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

package password.pwm.util.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Copies all data read by a wrapped input stream to a specified consumer as the data is read.
 */
public class CopyingInputStream extends InputStream
{
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private final InputStream realStream;
    private final Consumer<byte[]> consumer;

    public CopyingInputStream( final InputStream realStream, final Consumer<byte[]> consumer )
    {
        this.realStream = Objects.requireNonNull( realStream );
        this.consumer = Objects.requireNonNull( consumer );
    }

    @Override
    public int read( final byte[] b ) throws IOException
    {
        final int bytesRead = realStream.read( b );
        if ( bytesRead == b.length )
        {
            consumer.accept( b );
        }
        else if ( bytesRead >= 1 )
        {
            final byte[] tempBytes = new byte[bytesRead];
            System.arraycopy( b, 0, tempBytes, 0, bytesRead );
            consumer.accept( tempBytes );
        }
        return bytesRead;
    }

    @Override
    public int read( final byte[] b, final int off, final int len ) throws IOException
    {
        final int bytesRead = realStream.read( b, off, len );
        if ( bytesRead == b.length )
        {
            consumer.accept( b );
        }
        if ( bytesRead >= 1 )
        {
            final byte[] tempBytes = new byte[bytesRead];
            System.arraycopy( b, off, tempBytes, 0, bytesRead );
            consumer.accept( tempBytes );
        }
        return bytesRead;
    }

    @Override
    public byte[] readAllBytes() throws IOException
    {
        final byte[] bytesRead = realStream.readAllBytes();
        if ( bytesRead.length >= 1 )
        {
            consumer.accept( bytesRead );
        }
        return bytesRead;
    }

    @Override
    public byte[] readNBytes( final int len ) throws IOException
    {
        final byte[] readBytes = realStream.readNBytes( len );
        if ( readBytes.length >= 1 )
        {
            consumer.accept( readBytes );
        }
        return readBytes;
    }

    @Override
    public int readNBytes( final byte[] b, final int off, final int len ) throws IOException
    {
        final int bytesRead = realStream.readNBytes( b, off, len );
        if ( bytesRead == b.length )
        {
            consumer.accept( b );
        }
        if ( bytesRead >= 1 )
        {
            final byte[] tempBytes = new byte[len];
            System.arraycopy( b, off, tempBytes, 0, bytesRead );
            consumer.accept( tempBytes );
        }
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
        Objects.requireNonNull( out, "out" );

        long transferred = 0;
        final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;
        while ( ( read = this.read( buffer, 0, DEFAULT_BUFFER_SIZE ) ) >= 0 )
        {
            out.write( buffer, 0, read );
            transferred += read;
        }
        return transferred;
    }

    @Override
    public int read() throws IOException
    {
        final int byteValue = realStream.read();
        if ( byteValue > -1 )
        {
            final byte[] tempBytes = new byte[1];
            tempBytes[0] = ( byte ) byteValue;
            consumer.accept( tempBytes );
        }
        return byteValue;
    }
}

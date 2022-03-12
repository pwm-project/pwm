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
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Copies all data read by a wrapped input stream to a specified consumer as the data is read.
 */
public class CopyingOutputStream extends OutputStream
{
    private final OutputStream realStream;
    private final Consumer<byte[]> consumer;

    public CopyingOutputStream( final OutputStream realStream, final Consumer<byte[]> consumer )
    {
        this.realStream = Objects.requireNonNull( realStream );
        this.consumer = Objects.requireNonNull( consumer );
    }

    @Override
    public void write( final byte[] b ) throws IOException
    {
        realStream.write( b );
        consumer.accept( b );
    }

    @Override
    public void write( final byte[] b, final int off, final int len ) throws IOException
    {
        realStream.write( b, off, len );
        if ( ( off == 0 ) && ( len == b.length ) )
        {
            consumer.accept( b );
        }
        else
        {
            final byte[] tempBytes = new byte[len];
            System.arraycopy( b, 0, tempBytes, 0, len );
            consumer.accept( tempBytes );
        }
    }

    @Override
    public void flush() throws IOException
    {
        super.flush();
    }

    @Override
    public void write( final int b ) throws IOException
    {
        final byte[] tempBytes = new byte[]
                {
                        (byte) b,
                };
        consumer.accept( tempBytes );

    }

    @Override
    public void close() throws IOException
    {
        realStream.close();
    }
}

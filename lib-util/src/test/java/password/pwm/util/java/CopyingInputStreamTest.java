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


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;


public class CopyingInputStreamTest
{
    private static final String ASCII = "US-ASCII";

    private InputStream copyingStream;
    private OutputTester output;

    private static class OutputTester implements Consumer<byte[]>
    {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        @Override
        public void accept( final byte[] bytes )
        {
            try
            {
                byteArrayOutputStream.write( bytes );
            }
            catch ( final IOException e )
            {
                throw new RuntimeException( e );
            }
        }

        public byte[] bytes()
        {
            return byteArrayOutputStream.toByteArray();
        }
    }



    @BeforeEach
    public void setUp() throws Exception
    {
        final InputStream input = new ByteArrayInputStream( "abc".getBytes( ASCII ) );
        output = new OutputTester();
        copyingStream = new CopyingInputStream( input, output );
    }

    @Test
    public void testReadNothing() throws Exception
    {
        Assertions.assertEquals( "", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testReadOneByte() throws Exception
    {
        Assertions.assertEquals( 'a', copyingStream.read() );
        Assertions.assertEquals( "a", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testReadEverything() throws Exception
    {
        Assertions.assertEquals( 'a', copyingStream.read() );
        Assertions.assertEquals( 'b', copyingStream.read() );
        Assertions.assertEquals( 'c', copyingStream.read() );
        Assertions.assertEquals( -1, copyingStream.read() );
        Assertions.assertEquals( "abc", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testReadToArray() throws Exception
    {
        final byte[] buffer = new byte[8];
        Assertions.assertEquals( 3, copyingStream.read( buffer ) );
        Assertions.assertEquals( 'a', buffer[0] );
        Assertions.assertEquals( 'b', buffer[1] );
        Assertions.assertEquals( 'c', buffer[2] );
        Assertions.assertEquals( -1, copyingStream.read( buffer ) );
        Assertions.assertEquals( "abc", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testReadToArrayWithOffset() throws Exception
    {
        final byte[] buffer = new byte[8];
        Assertions.assertEquals( 3, copyingStream.read( buffer, 4, 4 ) );
        Assertions.assertEquals( 'a', buffer[4] );
        Assertions.assertEquals( 'b', buffer[5] );
        Assertions.assertEquals( 'c', buffer[6] );
        Assertions.assertEquals( -1, copyingStream.read( buffer, 4, 4 ) );
        Assertions.assertEquals( "abc", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testSkip() throws Exception
    {
        Assertions.assertEquals( 'a', copyingStream.read() );
        Assertions.assertEquals( 1, copyingStream.skip( 1 ) );
        Assertions.assertEquals( 'c', copyingStream.read() );
        Assertions.assertEquals( -1, copyingStream.read() );
        Assertions.assertEquals( "ac", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testMarkReset() throws Exception
    {
        Assertions.assertEquals( 'a', copyingStream.read() );
        copyingStream.mark( 1 );
        Assertions.assertEquals( 'b', copyingStream.read() );
        copyingStream.reset();
        Assertions.assertEquals( 'b', copyingStream.read() );
        Assertions.assertEquals( 'c', copyingStream.read() );
        Assertions.assertEquals( -1, copyingStream.read() );
        Assertions.assertEquals( "abbc", new String( output.bytes(), ASCII ) );
    }
}

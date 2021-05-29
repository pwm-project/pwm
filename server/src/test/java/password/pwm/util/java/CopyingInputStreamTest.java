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


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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



    @Before
    public void setUp() throws Exception
    {
        final InputStream input = new ByteArrayInputStream( "abc".getBytes( ASCII ) );
        output = new OutputTester();
        copyingStream = new CopyingInputStream( input, output );
    }

    @Test
    public void testReadNothing() throws Exception
    {
        Assert.assertEquals( "", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testReadOneByte() throws Exception
    {
        Assert.assertEquals( 'a', copyingStream.read() );
        Assert.assertEquals( "a", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testReadEverything() throws Exception
    {
        Assert.assertEquals( 'a', copyingStream.read() );
        Assert.assertEquals( 'b', copyingStream.read() );
        Assert.assertEquals( 'c', copyingStream.read() );
        Assert.assertEquals( -1, copyingStream.read() );
        Assert.assertEquals( "abc", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testReadToArray() throws Exception
    {
        final byte[] buffer = new byte[8];
        Assert.assertEquals( 3, copyingStream.read( buffer ) );
        Assert.assertEquals( 'a', buffer[0] );
        Assert.assertEquals( 'b', buffer[1] );
        Assert.assertEquals( 'c', buffer[2] );
        Assert.assertEquals( -1, copyingStream.read( buffer ) );
        Assert.assertEquals( "abc", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testReadToArrayWithOffset() throws Exception
    {
        final byte[] buffer = new byte[8];
        Assert.assertEquals( 3, copyingStream.read( buffer, 4, 4 ) );
        Assert.assertEquals( 'a', buffer[4] );
        Assert.assertEquals( 'b', buffer[5] );
        Assert.assertEquals( 'c', buffer[6] );
        Assert.assertEquals( -1, copyingStream.read( buffer, 4, 4 ) );
        Assert.assertEquals( "abc", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testSkip() throws Exception
    {
        Assert.assertEquals( 'a', copyingStream.read() );
        Assert.assertEquals( 1, copyingStream.skip( 1 ) );
        Assert.assertEquals( 'c', copyingStream.read() );
        Assert.assertEquals( -1, copyingStream.read() );
        Assert.assertEquals( "ac", new String( output.bytes(), ASCII ) );
    }

    @Test
    public void testMarkReset() throws Exception
    {
        Assert.assertEquals( 'a', copyingStream.read() );
        copyingStream.mark( 1 );
        Assert.assertEquals( 'b', copyingStream.read() );
        copyingStream.reset();
        Assert.assertEquals( 'b', copyingStream.read() );
        Assert.assertEquals( 'c', copyingStream.read() );
        Assert.assertEquals( -1, copyingStream.read() );
        Assert.assertEquals( "abbc", new String( output.bytes(), ASCII ) );
    }
}

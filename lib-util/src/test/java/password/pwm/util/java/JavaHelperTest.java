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
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

public class JavaHelperTest
{
    @Test
    public void binaryArrayToHexTest()
    {
        {
            final byte[] bytes = {
                    3,
                    127,
                    41,
                    16,
            };
            Assertions.assertEquals( "037F2910", JavaHelper.binaryArrayToHex( bytes ) );
        }

        {

            final byte[] bytes = new byte[128];
            IntStream.range( 0, 127 ).forEach( value -> bytes[value] = (byte) value );
            Assertions.assertEquals(
                        """
                        000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F202122232425262728292A2B2\
                        C2D2E2F303132333435363738393A3B3C3D3E3F404142434445464748494A4B4C4D4E4F505152535455565758\
                        595A5B5C5D5E5F606162636465666768696A6B6C6D6E6F707172737475767778797A7B7C7D7E00\
                        """,
                    JavaHelper.binaryArrayToHex( bytes ) );
        }
    }

    @Test
    public void concatByteArraysTwo()
    {
        final byte[] byteArray1 = new byte[]
                {
                        0, 122, 5,
                };
        final byte[] byteArray2 = new byte[]
                {
                        6, 121, 19,
                };

        final byte[] output = JavaHelper.concatByteArrays( byteArray1, byteArray2 );
        Assertions.assertArrayEquals( new byte[]
                {
                        0, 122, 5, 6, 121, 19,
                },
                output );
    }

    @Test
    public void concatByteArraysMultiple()
    {
        final byte[] byteArray1 = new byte[]
                {
                        0, 122, 5,
                };
        final byte[] byteArray2 = new byte[]
                {
                };
        final byte[] byteArray3 = new byte[]
                {
                        37,
                };
        final byte[] byteArray4 = new byte[]
                {
                        21, 14,
                };

        final byte[] output = JavaHelper.concatByteArrays( byteArray1, byteArray2, byteArray3, byteArray4 );
        Assertions.assertArrayEquals( new byte[]
                {
                        0, 122, 5, 37, 21, 14,
                },
                output );
    }
}

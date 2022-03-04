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

import org.junit.Assert;
import org.junit.Test;

public class JavaHelperTest
{

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
        Assert.assertArrayEquals( new byte[]
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
        Assert.assertArrayEquals( new byte[]
                {
                        0, 122, 5, 37, 21, 14,
                },
                output );
    }
}

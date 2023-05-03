/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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
import password.pwm.util.secure.PwmRandom;

public class StringUtilTest
{

    @Test
    public void stripAllWhitespace()
    {
        final String input = " this is a \n test \t string\r\nsecond line";
        final String expected = "thisisateststringsecondline";
        Assert.assertEquals( expected, StringUtil.stripAllWhitespace( input ) );
    }

    @Test
    public void whitespaceInsertAndStrip()
    {
        final String original = PwmRandom.getInstance().alphaNumericString( 1024 * 1024 );
        final String linebreaks = StringUtil.insertRepeatedLineBreaks( original, 80 );
        final String stripped = StringUtil.stripAllWhitespace( linebreaks );
        Assert.assertEquals( original, stripped );

    }

    private static byte[] makeB64inputByteArray()
    {
        final int byteLength = 256;
        final byte[] inputArray = new byte[byteLength];

        byte nextByte = 0;
        for ( int i = 0; i < byteLength; i++ )
        {
            inputArray[i] = nextByte;
            nextByte += i;
        }

        return inputArray;
    }

    private static final String B64_TEST = "AAABAwYKDxUcJC03Qk5baXiImau+0uf9FCxFX3qWs9HwEDFTdpq/5Qw0XYey3gs5aJjJ+y5il80EPHWv6iZjoeAgYaPm"
            + "Km+1/ESN1yJuuwlYqPlLnvJHnfRMpf9athNx0DCR81a6H4XsVL0nkv5r2Ui4KZsOgvdt5FzVT8pGw0HAQMFDxkrPVdxk7XcCjhupOMhZ634Sp"
            + "z3UbAWfOtZzEbBQ8ZM22n8lzHQdx3Iey3ko2Ik77qJXDcR8Ne+qZiPhoGAh46ZqL/W8hE0X4q57SRjouYteMgfdtIxlPxr207GQcFEzFvrfxa"
            + "yUfWdSPisZCPjp287Ct62knJWPioaDgQ==";

    private static final String B64_TEST_URL_SAFE = "AAABAwYKDxUcJC03Qk5baXiImau-0uf9FCxFX3qWs9HwEDFTdpq_5Qw0XYey3gs5aJjJ-y5il80EPHWv6iZjoeAgYaPm"
            + "Km-1_ESN1yJuuwlYqPlLnvJHnfRMpf9athNx0DCR81a6H4XsVL0nkv5r2Ui4KZsOgvdt5FzVT8pGw0HAQMFDxkrPVdxk7XcCjhupOMhZ634Sp"
            + "z3UbAWfOtZzEbBQ8ZM22n8lzHQdx3Iey3ko2Ik77qJXDcR8Ne-qZiPhoGAh46ZqL_W8hE0X4q57SRjouYteMgfdtIxlPxr207GQcFEzFvrfxa"
            + "yUfWdSPisZCPjp287Ct62knJWPioaDgQ==";

    private static final String B64_TEST_GZIP = "H4sIAAAAAAAAAAEAAf/+AAABAwYKDxUcJC03Qk5baXiImau+0uf9FCxFX3qWs9HwEDFTdpq/5Qw0XYey3gs5aJjJ+y5il"
            + "80EPHWv6iZjoeAgYaPmKm+1/ESN1yJuuwlYqPlLnvJHnfRMpf9athNx0DCR81a6H4XsVL0nkv5r2Ui4KZsOgvdt5FzVT8pGw0HAQMFDxkrPVdx"
            + "k7XcCjhupOMhZ634Spz3UbAWfOtZzEbBQ8ZM22n8lzHQdx3Iey3ko2Ik77qJXDcR8Ne+qZiPhoGAh46ZqL/W8hE0X4q57SRjouYteMgfdtIxlP"
            + "xr207GQcFEzFvrfxayUfWdSPisZCPjp287Ct62knJWPioaDgR3bmXcAAQAA";

    private static final String B64_TEST_GZIP_URL_SAFE = "H4sIAAAAAAAAAAEAAf_-AAABAwYKDxUcJC03Qk5baXiImau-0uf9FCxFX3qWs9HwEDFTdpq_5Qw0XYey3gs5aJjJ-y5il"
            + "80EPHWv6iZjoeAgYaPmKm-1_ESN1yJuuwlYqPlLnvJHnfRMpf9athNx0DCR81a6H4XsVL0nkv5r2Ui4KZsOgvdt5FzVT8pGw0HAQMFDxkrPVdx"
            + "k7XcCjhupOMhZ634Spz3UbAWfOtZzEbBQ8ZM22n8lzHQdx3Iey3ko2Ik77qJXDcR8Ne-qZiPhoGAh46ZqL_W8hE0X4q57SRjouYteMgfdtIxlP"
            + "xr207GQcFEzFvrfxayUfWdSPisZCPjp287Ct62knJWPioaDgR3bmXcAAQAA";

    @Test
    public void base64TestEncode() throws Exception
    {
        final String b64string = StringUtil.base64Encode( makeB64inputByteArray() );
        Assert.assertEquals( B64_TEST, b64string );
    }

    @Test
    public void base64TestDecode() throws Exception
    {
        final byte[] b64array = StringUtil.base64Decode( B64_TEST );
        Assert.assertArrayEquals( makeB64inputByteArray(), b64array );
    }

    @Test
    public void base64TestEncodeUrlSafe() throws Exception
    {
        final String b64string = StringUtil.base64Encode( makeB64inputByteArray(), StringUtil.Base64Options.URL_SAFE );
        Assert.assertEquals( B64_TEST_URL_SAFE, b64string );
    }

    @Test
    public void base64TestDecodeUrlSafe() throws Exception
    {
        final byte[] b64array = StringUtil.base64Decode( B64_TEST_URL_SAFE, StringUtil.Base64Options.URL_SAFE );
        Assert.assertArrayEquals( makeB64inputByteArray(), b64array );
    }

    // removed test for pwm v2.0.x, gzip output changed in JDK 15 which breaks this test.
    // @Test
    public void base64TestEncodeGzipAndUrlSafe() throws Exception
    {
        final String b64string = StringUtil.base64Encode( makeB64inputByteArray(), StringUtil.Base64Options.URL_SAFE, StringUtil.Base64Options.GZIP );
        Assert.assertEquals( B64_TEST_GZIP_URL_SAFE, b64string );
    }

    // removed test for pwm v2.0.x, gzip output changed in JDK 15 which breaks this test.
    // @Test
    public void base64TestDecodeGzipAndUrlSafe() throws Exception
    {
        final byte[] b64array = StringUtil.base64Decode( B64_TEST_GZIP_URL_SAFE, StringUtil.Base64Options.URL_SAFE, StringUtil.Base64Options.GZIP );
        Assert.assertArrayEquals( makeB64inputByteArray(), b64array );
    }

    // removed test for pwm v2.0.x, gzip output changed in JDK 15 which breaks this test.
    // @Test
    public void base64TestEncodeGzip() throws Exception
    {
        final String b64string = StringUtil.base64Encode( makeB64inputByteArray(), StringUtil.Base64Options.GZIP );
        Assert.assertEquals( B64_TEST_GZIP, b64string );
    }

    @Test
    public void base64TestDecodeGzip() throws Exception
    {
        final byte[] b64array = StringUtil.base64Decode( B64_TEST_GZIP, StringUtil.Base64Options.GZIP );
        Assert.assertArrayEquals( makeB64inputByteArray(), b64array );
    }
}

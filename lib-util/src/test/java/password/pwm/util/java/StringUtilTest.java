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

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class StringUtilTest
{
    @Test
    public void repeatedInsertTest()
    {
        final String input = "this is a test this is a test this is a test.";
        final String expected = "this is a !test this !is a test !this is a !test.";
        Assert.assertEquals( expected, StringUtil.repeatedInsert( input, 10, "!" ) );
    }


    @Test
    public void stripAllWhitespaceTest1()
    {
        final String input = " this is a \n test \t string\r\nsecond line ";
        final String expected = "thisisateststringsecondline";
        Assert.assertEquals( expected, StringUtil.stripAllWhitespace( input ) );
    }

    @Test
    public void stripAllWhitespaceTest2()
    {
        final String input = " this is a \n test \t string\r\nsecond line ";
        final String expected = "thisisateststringsecondline";
        Assert.assertEquals( expected, StringUtil.stripAllWhitespace( input ) );
    }

    @Test
    public void stripAllWhitespaceTest3()
    {
        final String input = "nochangetest";
        final String expected = "nochangetest";
        Assert.assertEquals( expected, StringUtil.stripAllWhitespace( input ) );
    }

    @Test
    public void stripAllWhitespaceTest4()
    {
        final String input = "MIIEsTCCA5mgAwIBAgIQBOHnpNxc8vNtwCtCuF0VnzANBgkqhkiG9w0BAQsFADBsMQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYD\n"
                + "       VQQLExB3d3cuZGlnaWNlcnQuY29tMSswKQYDVQQDEyJEaWdpQ2VydCBIaWdoIEFzc3VyYW5jZSBFViBSb290IENBMB4XDTEzMTAyMjEyMDAwMFoXDTI4MTAy\n"
                + "       MjEyMDAwMFowcDELMAkGA1UEBhMCVVMxFTATBgNVBAoTDERpZ2lDZXJ0IEluYzEZMBcGA1UECxMQd3d3LmRpZ2ljZXJ0LmNvbTEvMC0GA1UEAxMmRGlnaUNl\n"
                + "       cnQgU0hBMiBIaWdoIEFzc3VyYW5jZSBTZXJ2ZXIgQ0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC24C/CJAbIbQRf1+8KZAayfSImZRauQkCb\n"
                + "       ztyfn3YHPsMwVYcZuU+UDlqUH1VWtMICKq/QmO4LQNfE0DtyyB Se75CxEamu0si4QzrZCwvV1ZX1QK/IHe1NnF9Xt4ZQaJn1itrSxwUfqJfJ3KSxgoQtxq2l\n"
                + "       nMcZgqaFD15EWCo3j/018QsIJzJa9buLnqS9UdAn4t07QjOjBSjEuyjMmqwrIw14xnvmXnG3Sj4I+4G3FhahnSMSTeXXkgisdaScus0Xsh5ENWV/UyU50RwK\n"
                + "       mmMbGZJ0aAo3wsJSSMs5WqK24V3B3aAguCGikyZvFEohQcftbZvySC/zA/WiaJJTL17jAgMBAAGjggFJMIIBRTASBgNVHRMBAf8ECDAGAQH/AgEAMA4GA1Ud\n"
                + "       DwEB/wQEAwIBhjAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwNAYIKwYBBQUHAQEEKDAmMCQGCCsGAQUFBzABhhhodHRwOi8vb2NzcC5kaWdpY2Vy\n"
                + "       dC5jb20wSwYDVR0fBEQwQjBAoD6gPIY6aHR0cDovL2NybDQuZGln aWNlcnQuY29tL0RpZ2lDZXJ0SGlnaEFzc3VyYW5jZUVWUm9vdENBLmNybDA9BgNVHSAE\n"
                + "       NjA0MDIGBFUdIAAwKjAoBggrBgEFBQcCARYcaHR0cHM6Ly93d3cuZGlnaWNlcnQuY29tL0NQUzAdBgNVHQ4EFgQUUWj/kK8CB3U8zNllZGKiErhZcjswHwYD\n"
                + "       VR0jBBgwFoAUsT7DaQP4v0cB1JgmGggC72NkK8MwDQYJKoZIhvcNAQELBQADggEBABiKlYkD5m3fXPwdaOpKj4PWUS+Na0QWnqxj9dJubISZi6qBcYRb7TRO\n"
                + "       sLd5kinMLYBq8I4g4Xmk/gNHE+r1hspZcX30BJZr01lYPf7TMSVcGDiEo+afgv2MW5gxTs14 nhr9hctJqvIni5ly/D6q1UEL2tU2ob8cbkdJf17ZSHwD2f2L\n"
                + "       SaCYJ    kJA69aSEaRkCldUxPUd1gJea6zuxICaEnL6VpPX/78whQYwvwt/Tv9XBZ0k7YXDK/umdaisLRbvfXknsuvCnQsH6qqF0wGjIChBWUMo0oHjqvbsezt3\n"
                + "       tkBigAVBRQHvFwY+3sAzm2fTYS5yh+Rp/BIAV0AecPUeybQ=\n";
        final String expected = "MIIEsTCCA5mgAwIBAgIQBOHnpNxc8vNtwCtCuF0VnzANBgkqhkiG9w0BAQsFADBsMQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYD"
                + "VQQLExB3d3cuZGlnaWNlcnQuY29tMSswKQYDVQQDEyJEaWdpQ2VydCBIaWdoIEFzc3VyYW5jZSBFViBSb290IENBMB4XDTEzMTAyMjEyMDAwMFoXDTI4MTAy"
                + "MjEyMDAwMFowcDELMAkGA1UEBhMCVVMxFTATBgNVBAoTDERpZ2lDZXJ0IEluYzEZMBcGA1UECxMQd3d3LmRpZ2ljZXJ0LmNvbTEvMC0GA1UEAxMmRGlnaUNl"
                + "cnQgU0hBMiBIaWdoIEFzc3VyYW5jZSBTZXJ2ZXIgQ0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC24C/CJAbIbQRf1+8KZAayfSImZRauQkCb"
                + "ztyfn3YHPsMwVYcZuU+UDlqUH1VWtMICKq/QmO4LQNfE0DtyyBSe75CxEamu0si4QzrZCwvV1ZX1QK/IHe1NnF9Xt4ZQaJn1itrSxwUfqJfJ3KSxgoQtxq2l"
                + "nMcZgqaFD15EWCo3j/018QsIJzJa9buLnqS9UdAn4t07QjOjBSjEuyjMmqwrIw14xnvmXnG3Sj4I+4G3FhahnSMSTeXXkgisdaScus0Xsh5ENWV/UyU50RwK"
                + "mmMbGZJ0aAo3wsJSSMs5WqK24V3B3aAguCGikyZvFEohQcftbZvySC/zA/WiaJJTL17jAgMBAAGjggFJMIIBRTASBgNVHRMBAf8ECDAGAQH/AgEAMA4GA1Ud"
                + "DwEB/wQEAwIBhjAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwNAYIKwYBBQUHAQEEKDAmMCQGCCsGAQUFBzABhhhodHRwOi8vb2NzcC5kaWdpY2Vy"
                + "dC5jb20wSwYDVR0fBEQwQjBAoD6gPIY6aHR0cDovL2NybDQuZGlnaWNlcnQuY29tL0RpZ2lDZXJ0SGlnaEFzc3VyYW5jZUVWUm9vdENBLmNybDA9BgNVHSAE"
                + "NjA0MDIGBFUdIAAwKjAoBggrBgEFBQcCARYcaHR0cHM6Ly93d3cuZGlnaWNlcnQuY29tL0NQUzAdBgNVHQ4EFgQUUWj/kK8CB3U8zNllZGKiErhZcjswHwYD"
                + "VR0jBBgwFoAUsT7DaQP4v0cB1JgmGggC72NkK8MwDQYJKoZIhvcNAQELBQADggEBABiKlYkD5m3fXPwdaOpKj4PWUS+Na0QWnqxj9dJubISZi6qBcYRb7TRO"
                + "sLd5kinMLYBq8I4g4Xmk/gNHE+r1hspZcX30BJZr01lYPf7TMSVcGDiEo+afgv2MW5gxTs14nhr9hctJqvIni5ly/D6q1UEL2tU2ob8cbkdJf17ZSHwD2f2L"
                + "SaCYJkJA69aSEaRkCldUxPUd1gJea6zuxICaEnL6VpPX/78whQYwvwt/Tv9XBZ0k7YXDK/umdaisLRbvfXknsuvCnQsH6qqF0wGjIChBWUMo0oHjqvbsezt3"
                + "tkBigAVBRQHvFwY+3sAzm2fTYS5yh+Rp/BIAV0AecPUeybQ=";
        Assert.assertEquals( expected, StringUtil.stripAllWhitespace( input ) );
    }

    @Test
    public void stripAllWhitespaceTest5()
    {
        final String input = "H4sIAAAAAAAAAKR7A5Rly5ZtVlbatm3btm3bNipt27Ztm5W2baPS/97X/bvve79H9e3+Z4w9ttaaJ2LuWDNW7IgtJ/kdCAIADAwMQNViXcL1eWngGAAAIOcb\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "\n"
                + "AADSH3tpYSV+anEZEVppfhlxEWFFJRppEe/YTYlBOrig6+/uIVw/sDUi8JBprZYhUSn8d6qkEupMWKUt4rXbba+\n"
                + "H\n"
                + "a\n"
                + "b\n"
                + "T\n"
                + "f\n"
                + "+\n"
                + "y\n"
                + "r\n"
                + "8\n"
                + "H\n"
                + "H\n"
                + "m\n"
                + "m\n"
                + "J\n"
                + "z\n"
                + "N\n"
                + "R\n"
                + "q\n"
                + "n\n"
                + "w\n"
                + "K\n"
                + "4\n"
                + "B\n"
                + "p\n"
                + "Q\n"
                + "7\n"
                + "/\n"
                + "g";
        final String expected = "H4sIAAAAAAAAAKR7A5Rly5ZtVlbatm3btm3bNipt27Ztm5W2baPS/97X/bvve79H9e3+Z4w9ttaaJ2LuWDNW7IgtJ/kdCAIADAwMQNViXcL1eWngGAAAIOcb"
                + "AADSH3tpYSV+anEZEVppfhlxEWFFJRppEe/YTYlBOrig6+/uIVw/sDUi8JBprZYhUSn8d6qkEupMWKUt4rXbba+HabTf+yr8HHmmJzNRqnwK4BpQ7/g";
        Assert.assertEquals( expected, StringUtil.stripAllWhitespace( input ) );
    }

    @Test
    public void whitespaceInsertAndStripTest()
    {
        final String lineSeparator = System.lineSeparator();

        final String original = RandomStringUtils.random( 1024 * 1024, true, true );
        final String linebreaks = StringUtil.insertRepeatedLineBreaks( original, 80 );
        Assert.assertEquals( lineSeparator, linebreaks.substring( 80, 80 + lineSeparator.length() ) );
        final String stripped = StringUtil.stripAllWhitespace( linebreaks );
        Assert.assertEquals( original, stripped );
    }

    @Test
    @SuppressWarnings( "AvoidEscapedUnicodeCharacters" )
    public void stripNonPrintableCharactersTet()
    {
        final String input = "0�\u0000\u0000\u0000\u0007\u0002\u0001\u0001\u0002\u0002�\u007F";
        final String expected = "0�?????????�?";
        Assert.assertEquals( expected, StringUtil.cleanNonPrintableCharacters( input ) );
    }

    @Test
    public void urlPathEncodeTest()
    {
        final String input = "dsad(dsadaasds)dsdasdad";
        Assert.assertEquals( "dsad%28dsadaasds%29dsdasdad", StringUtil.urlPathEncode( input ) );
    }

    @Test
    public void base32EncodeTest() throws IOException
    {
        final byte[] input = new byte[500];
        for ( int i = 0; i < 500; i++ )
        {
            input[i] = 65;
        }

        final String b32value = StringUtil.base32Encode( input );
        final String expectedValue = "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKBIFAUCQKB"
                + "IFAUCQKB";

        Assert.assertEquals( expectedValue, b32value );
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

    @Test
    public void base64TestEncodeGzipAndUrlSafe() throws Exception
    {
        final String b64string = StringUtil.base64Encode( makeB64inputByteArray(), StringUtil.Base64Options.URL_SAFE, StringUtil.Base64Options.GZIP );
        Assert.assertEquals( B64_TEST_GZIP_URL_SAFE, b64string );
    }

    @Test
    public void base64TestDecodeGzipAndUrlSafe() throws Exception
    {
        final byte[] b64array = StringUtil.base64Decode( B64_TEST_GZIP_URL_SAFE, StringUtil.Base64Options.URL_SAFE, StringUtil.Base64Options.GZIP );
        Assert.assertArrayEquals( makeB64inputByteArray(), b64array );
    }

    @Test
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

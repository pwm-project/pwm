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

package password.pwm.svc.wordlist;

import org.junit.Assert;
import org.junit.Test;

public class WordTypeTest
{
    @Test
    public void testDetermineWordTypes()
    {
        Assert.assertEquals( WordType.RAW, WordType.determineWordType( "password" ) );

        Assert.assertEquals( WordType.RAW, WordType.determineWordType( "sha1:password" ) );
        Assert.assertEquals( WordType.RAW, WordType.determineWordType( "sha1:5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD" ) );
        Assert.assertEquals( WordType.RAW, WordType.determineWordType( "sha1:5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD80" ) );

        Assert.assertEquals( WordType.SHA1, WordType.determineWordType( "sha1:5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8" ) );
        Assert.assertEquals( WordType.SHA1, WordType.determineWordType( "SHA1:5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8" ) );
        Assert.assertEquals( WordType.SHA1, WordType.determineWordType( "sha1:5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8" ) );
        Assert.assertEquals( WordType.SHA1, WordType.determineWordType( "SHA1:5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8" ) );

        Assert.assertEquals( WordType.MD5, WordType.determineWordType( "md5:5F4DCC3B5AA765D61D8327DEB882CF99" ) );
        Assert.assertEquals( WordType.SHA256, WordType.determineWordType( "sha256:5E884898DA28047151D0E56F8DC6292773603D0D6AABBDD62A11EF721D1542D8" ) );
        Assert.assertEquals( WordType.SHA512, WordType.determineWordType(
                "sha512:B109F3BBBC244EB82441917ED06D618B9008DD09B3BEFD1B5E07394C706A8BB980B1D7785E5976EC049B46DF5F1326AF5A2EA6D103FD07C95385FFAB0CACBC86" ) );
    }
}

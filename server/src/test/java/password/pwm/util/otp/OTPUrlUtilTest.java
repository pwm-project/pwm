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

package password.pwm.util.otp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import password.pwm.svc.otp.OTPUrlUtil;
import password.pwm.svc.otp.OTPUserRecord;

/**
 * @author mpieters
 */
public class OTPUrlUtilTest
{

    /**
     * Test of composeOtpUrl method and decomposeOtpUrl, of class OTPUrlUtil.
     */
    @Test
    public void testComposeAndDecomposeOtpUrl()
    {

        final OTPUserRecord otp = new OTPUserRecord();
        otp.setIdentifier( "TEST" );
        otp.setSecret( "2222222222222222" );

        final String result = OTPUrlUtil.composeOtpUrl( otp );
        final OTPUserRecord xotp = OTPUrlUtil.decomposeOtpUrl( result );
        Assertions.assertNotNull( xotp );
        Assertions.assertEquals( otp.getIdentifier(), xotp.getIdentifier() );
        Assertions.assertEquals( otp.getSecret(), xotp.getSecret() );
    }

}

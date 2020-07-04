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

package password.pwm.http.servlet.oauth;

import org.junit.Assert;
import org.junit.Test;

public class OAuthMachineTest
{
    @Test
    public void parserTest1()
    {
        final String input = "{\n"
                + "\t\"access_token\":\"Q6hgBgSZMMvVnOP2tOTufILVfao82kcHtVqE9pspzC55oqKdMjuaz9Jpj3KpTlv\",\n"
                + "\t\"token_type\":\"bearer\",\n"
                + "\t\"expires_in\":3599,\n"
                + "\t\"scope\":\"profile\"\n"
                + "}";
        final OAuthSettings oAuthSettings = OAuthSettings.builder().build();
        final OAuthMachine oAuthMachine = new OAuthMachine( null, oAuthSettings );
        Assert.assertEquals( "3599", oAuthMachine.readAttributeFromBodyMap( input, "expires_in" ) );
    }

    @Test
    public void parserTest2()
    {
        final String input = "{\"sub\":\"0c8463c904e6444fa5c2b4597f816bc2\",\"claims\":[],\"email\":\"testadmin@example.com\"}";
        final OAuthSettings oAuthSettings = OAuthSettings.builder().build();
        final OAuthMachine oAuthMachine = new OAuthMachine( null, oAuthSettings );
        Assert.assertEquals( "testadmin@example.com", oAuthMachine.readAttributeFromBodyMap( input, "email" ) );
        Assert.assertNull( oAuthMachine.readAttributeFromBodyMap( input, "claims" ) );
    }

    @Test
    public void parserTest3()
    {
        final String input = "{\"sub\":\"0c8463c904e6444fa5c2b4597f816bc2\",\"claims\":[\"value1\",\"value2\"],\"email\":\"testadmin@example.com\"}";
        final OAuthSettings oAuthSettings = OAuthSettings.builder().build();
        final OAuthMachine oAuthMachine = new OAuthMachine( null, oAuthSettings );
        Assert.assertEquals( "value1", oAuthMachine.readAttributeFromBodyMap( input, "claims" ) );
    }
}

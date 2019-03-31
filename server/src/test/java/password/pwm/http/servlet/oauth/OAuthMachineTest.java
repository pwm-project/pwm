/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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

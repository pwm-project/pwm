/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.util.macro;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.ldap.UserInfo;

public class MacroTest
{

    @Test
    public void testStaticMacros() throws Exception
    {
        final MacroMachine macroMachine = MacroMachine.forStatic();

        // app name
        {
            final String goal = "test " + PwmConstants.PWM_APP_NAME + " test";
            final String expanded = macroMachine.expandMacros( "test @PwmAppName@ test" );
            Assert.assertEquals( goal, expanded );
        }

        // urlEncoding macro
        {
            final String goal = "https%3A%2F%2Fwww.example.com";
            final String expanded = macroMachine.expandMacros( "@Encode:urlPath:[[https://www.example.com]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // base64 macro
        {
            final String goal = "aHR0cHM6Ly93d3cuZXhhbXBsZS5jb20=";
            final String expanded = macroMachine.expandMacros( "@Encode:base64:[[https://www.example.com]]@" );
            Assert.assertEquals( goal, expanded );
        }
    }

    @Test
    public void testStaticHashMacros() throws Exception
    {
        final MacroMachine macroMachine = MacroMachine.forStatic();

        // md5 macro
        {
            final String goal = "f96b697d7cb7938d525a2f31aaf161d0";
            final String expanded = macroMachine.expandMacros( "@Hash:md5:[[message digest]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // sha1 macro
        {
            final String goal = "5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8";
            final String expanded = macroMachine.expandMacros( "@Hash:sha1:[[password]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // sha256 macro
        {
            final String goal = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";
            final String expanded = macroMachine.expandMacros( "@Hash:sha256:[[password]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // sha512 macro
        {
            final String goal = "b109f3bbbc244eb82441917ed06d618b9008dd09b3befd1b5e07394c706a8bb980b1d7785e5976ec049b46df5f1326af5a2ea6d103fd07c95385ffab0cacbc86";
            final String expanded = macroMachine.expandMacros( "@Hash:sha512:[[password]]@" );
            Assert.assertEquals( goal, expanded );
        }
    }

    @Test
    public void testUserMacros() throws Exception
    {
        final String userDN = "cn=test1,ou=test,o=org";

        final MacroMachine macroMachine;
        {
            final PwmApplication pwmApplication = Mockito.mock( PwmApplication.class );
            Mockito.when( pwmApplication.getApplicationMode() ).thenReturn( PwmApplicationMode.RUNNING );
            Mockito.when( pwmApplication.getConfig() ).thenReturn( new Configuration( StoredConfigurationFactory.newConfig() ) );

            final UserInfo userInfo = Mockito.mock( UserInfo.class );
            final UserIdentity userIdentity = new UserIdentity( userDN, "profile" );
            Mockito.when( userInfo.getUserIdentity() ).thenReturn( userIdentity );
            Mockito.when( userInfo.readStringAttribute( "givenName" ) ).thenReturn( "Jason" );

            final LoginInfoBean loginInfoBean = Mockito.mock( LoginInfoBean.class );
            Mockito.when( loginInfoBean.isAuthenticated() ).thenReturn( true );
            Mockito.when( loginInfoBean.getUserIdentity() ).thenReturn( userIdentity );

            macroMachine = MacroMachine.forUser( pwmApplication, null, userInfo, loginInfoBean );
        }

        // userDN macro
        {
            final String goal = userDN;
            final String expanded = macroMachine.expandMacros( "@LDAP:dn@" );
            Assert.assertEquals( goal, expanded );
        }

        // userDN + urlEncoding macro
        {
            final String goal = "test cn%3Dtest1%2Cou%3Dtest%2Co%3Dorg";
            final String expanded = macroMachine.expandMacros( "test @Encode:urlPath:[[@LDAP:dn@]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // user attribute macro
        {
            final String goal = "test Jason test";
            final String expanded = macroMachine.expandMacros( "test @LDAP:givenName@ test" );
            Assert.assertEquals( goal, expanded );
        }
    }
}

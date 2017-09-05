/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.util.macro;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.ldap.UserInfo;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MacroTest {

    @Test
    public void testStaticMacros() throws Exception
    {
        final MacroMachine macroMachine = MacroMachine.forStatic();

        { // app name
            final String goal = "test " + PwmConstants.PWM_APP_NAME + " test";
            final String expanded = macroMachine.expandMacros("test @PwmAppName@ test");
            Assert.assertEquals(goal,expanded);
        }

        { // urlEncoding macro
            final String goal = "https%3A%2F%2Fwww.example.com";
            final String expanded = macroMachine.expandMacros("@Encode:urlPath:[[https://www.example.com]]@");
            Assert.assertEquals(goal,expanded);
        }

        { // base64 macro
            final String goal = "aHR0cHM6Ly93d3cuZXhhbXBsZS5jb20=";
            final String expanded = macroMachine.expandMacros("@Encode:base64:[[https://www.example.com]]@");
            Assert.assertEquals(goal,expanded);
        }
    }

    @Test
    public void testStaticHashMacros() throws Exception
    {
        final MacroMachine macroMachine = MacroMachine.forStatic();

        { // md5 macro
            final String goal = "f96b697d7cb7938d525a2f31aaf161d0";
            final String expanded = macroMachine.expandMacros("@Hash:md5:[[message digest]]@");
            Assert.assertEquals(goal,expanded);
        }

        { // sha1 macro
            final String goal = "5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8";
            final String expanded = macroMachine.expandMacros("@Hash:sha1:[[password]]@");
            Assert.assertEquals(goal,expanded);
        }

        { // sha256 macro
            final String goal = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";
            final String expanded = macroMachine.expandMacros("@Hash:sha256:[[password]]@");
            Assert.assertEquals(goal,expanded);
        }

        { // sha512 macro
            final String goal = "b109f3bbbc244eb82441917ed06d618b9008dd09b3befd1b5e07394c706a8bb980b1d7785e5976ec049b46df5f1326af5a2ea6d103fd07c95385ffab0cacbc86";
            final String expanded = macroMachine.expandMacros("@Hash:sha512:[[password]]@");
            Assert.assertEquals(goal,expanded);
        }
    }

    @Test
    public void testUserMacros() throws Exception
    {

        final String userDN = "cn=test1,ou=test,o=org";

        final MacroMachine macroMachine;
        {
            final PwmApplication pwmApplication = mock(PwmApplication.class);
            when(pwmApplication.getApplicationMode()).thenReturn(PwmApplicationMode.RUNNING);
            when(pwmApplication.getConfig()).thenReturn(new Configuration(StoredConfigurationImpl.newStoredConfiguration()));

            final UserInfo userInfo = mock(UserInfo.class);
            final UserIdentity userIdentity = new UserIdentity(userDN, "profile");
            when(userInfo.getUserIdentity()).thenReturn(userIdentity);
            when(userInfo.readStringAttribute("givenName")).thenReturn("Jason");

            final LoginInfoBean loginInfoBean = mock(LoginInfoBean.class);
            when(loginInfoBean.isAuthenticated()).thenReturn(true);
            when(loginInfoBean.getUserIdentity()).thenReturn(userIdentity);

            macroMachine = MacroMachine.forUser(pwmApplication, null, userInfo, loginInfoBean);
        }

        { // userDN macro
            final String goal = userDN;
            final String expanded = macroMachine.expandMacros("@LDAP:dn@");
            Assert.assertEquals(goal,expanded);
        }

        { // userDN + urlEncoding macro
            final String goal = "test cn%3Dtest1%2Cou%3Dtest%2Co%3Dorg";
            final String expanded = macroMachine.expandMacros("test @Encode:urlPath:[[@LDAP:dn@]]@");
            Assert.assertEquals(goal,expanded);
        }

        { // user attribute macro
            final String goal = "test Jason test";
            final String expanded = macroMachine.expandMacros("test @LDAP:givenName@ test");
            Assert.assertEquals(goal,expanded);
        }
    }
}

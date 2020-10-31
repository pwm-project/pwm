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

package password.pwm.util.macro;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.SampleDataGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class MacroTest
{
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    private MacroRequest macroRequest;

    @Before
    public void setUp() throws PwmUnrecoverableException
    {
        macroRequest = SampleDataGenerator.sampleMacroRequest( null );
    }


    @Test
    public void testStaticMacros() throws Exception
    {
        // app name
        {
            final String goal = "test " + PwmConstants.PWM_APP_NAME + " test";
            final String expanded = macroRequest.expandMacros( "test @PwmAppName@ test" );
            Assert.assertEquals( goal, expanded );
        }


        {
            final String goal = "test " + PwmSetting.PWM_SITE_URL.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ) + " test";
            final String expanded = macroRequest.expandMacros( "test @PwmSettingReference:pwm.selfURL@ test" );
            Assert.assertEquals( goal, expanded );
        }

        // urlEncoding macro
        {
            final String goal = "https%3A%2F%2Fwww.example.com";
            final String expanded = macroRequest.expandMacros( "@Encode:urlPath:[[https://www.example.com]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // base64 macro
        {
            final String goal = "aHR0cHM6Ly93d3cuZXhhbXBsZS5jb20=";
            final String expanded = macroRequest.expandMacros( "@Encode:base64:[[https://www.example.com]]@" );
            Assert.assertEquals( goal, expanded );
        }
    }

    @Test
    public void testStaticHashMacros() throws Exception
    {
        // md5 macro
        {
            final String goal = "f96b697d7cb7938d525a2f31aaf161d0";
            final String expanded = macroRequest.expandMacros( "@Hash:md5:[[message digest]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // sha1 macro
        {
            final String goal = "5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8";
            final String expanded = macroRequest.expandMacros( "@Hash:sha1:[[password]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // sha256 macro
        {
            final String goal = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";
            final String expanded = macroRequest.expandMacros( "@Hash:sha256:[[password]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // sha512 macro
        {
            final String goal = "b109f3bbbc244eb82441917ed06d618b9008dd09b3befd1b5e07394c706a8bb980b1d7785e5976ec049b46df5f1326af5a2ea6d103fd07c95385ffab0cacbc86";
            final String expanded = macroRequest.expandMacros( "@Hash:sha512:[[password]]@" );
            Assert.assertEquals( goal, expanded );
        }
    }

    @Test
    public void testUserIDMacro() throws Exception
    {
        final String goal = "test FLast test";
        final String expanded = macroRequest.expandMacros( "test @User:ID@ test" );
        Assert.assertEquals( goal, expanded );
    }

    @Test
    public void testTargetUserIDMacro() throws Exception
    {
        final String goal = "test TUser test";
        final String expanded = macroRequest.expandMacros( "test @TargetUser:ID@ test" );
        Assert.assertEquals( goal, expanded );
    }

    @Test
    public void testUserPwExpireTimeMacro()
    {
        {
            final String goal = "UserPwExpireTime 2000-02-03T01:01:01Z test";
            final String expanded = macroRequest.expandMacros( "UserPwExpireTime @User:PwExpireTime@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "UserPwExpireTime 1:01 AM, UTC test";
            final String expanded = macroRequest.expandMacros( "UserPwExpireTime @User:PwExpireTime:K/:mm a, z@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "UserPwExpireTime 6:31 AM, IST test";
            final String expanded = macroRequest.expandMacros( "UserPwExpireTime @User:PwExpireTime:K/:mm a, z:IST@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "UserPwExpireTime 2000.02.03 test";
            final String expanded = macroRequest.expandMacros( "UserPwExpireTime @User:PwExpireTime:yyyy.MM.dd@ test" );
            Assert.assertEquals( goal, expanded );
        }
    }

    @Test
    public void testTargetUserPwExpireTimeMacro()
    {
        {
            final String goal = "TargetUserPwExpireTime 1973-01-03T22:45:21Z test";
            final String expanded = macroRequest.expandMacros( "TargetUserPwExpireTime @TargetUser:PwExpireTime@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "TargetUserPwExpireTime 10:45 PM, UTC test";
            final String expanded = macroRequest.expandMacros( "TargetUserPwExpireTime @TargetUser:PwExpireTime:K/:mm a, z@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "TargetUserPwExpireTime 4:15 AM, IST test";
            final String expanded = macroRequest.expandMacros( "TargetUserPwExpireTime @TargetUser:PwExpireTime:K/:mm a, z:IST@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "TargetUserPwExpireTime 1973.01.03 test";
            final String expanded = macroRequest.expandMacros( "TargetUserPwExpireTime @TargetUser:PwExpireTime:yyyy.MM.dd@ test" );
            Assert.assertEquals( goal, expanded );
        }
    }

    @Test
    public void testUserOtpSetupTimeMacro()
    {
        {
            final String goal = "OtpSetupTime 1999-10-30T04:56:04Z test";
            final String expanded = macroRequest.expandMacros( "OtpSetupTime @OtpSetupTime@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "OtpSetupTime 4:56 AM, UTC test";
            final String expanded = macroRequest.expandMacros( "OtpSetupTime @OtpSetupTime:K/:mm a, z@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "OtpSetupTime 10:26 AM, IST test";
            final String expanded = macroRequest.expandMacros( "OtpSetupTime @OtpSetupTime:K/:mm a, z:IST@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "OtpSetupTime 1999.10.30 test";
            final String expanded = macroRequest.expandMacros( "OtpSetupTime @OtpSetupTime:yyyy.MM.dd@ test" );
            Assert.assertEquals( goal, expanded );
        }
    }

    @Test
    public void testResponseSetupTimeMacro()
    {
        {
            final String goal = "ResponseSetupTime 1999-10-30T01:17:55Z test";
            final String expanded = macroRequest.expandMacros( "ResponseSetupTime @ResponseSetupTime@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "ResponseSetupTime 1:17 AM, UTC test";
            final String expanded = macroRequest.expandMacros( "ResponseSetupTime @ResponseSetupTime:K/:mm a, z@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "ResponseSetupTime 6:47 AM, IST test";
            final String expanded = macroRequest.expandMacros( "ResponseSetupTime @ResponseSetupTime:K/:mm a, z:IST@ test" );
            Assert.assertEquals( goal, expanded );
        }

        {
            final String goal = "ResponseSetupTime 1999.10.30 test";
            final String expanded = macroRequest.expandMacros( "ResponseSetupTime @ResponseSetupTime:yyyy.MM.dd@ test" );
            Assert.assertEquals( goal, expanded );
        }
    }

    @Test
    public void testUserDaysUntilPwExpireMacro()
            throws PwmUnrecoverableException
    {
        final Duration duration = Duration.between( macroRequest.getUserInfo().getPasswordExpirationTime(), Instant.now() );
        final long days = TimeUnit.DAYS.convert( duration.toMillis(), TimeUnit.MILLISECONDS );
        final String goal = "UserDaysUntilPwExpire " + days + " test";
        final String expanded = macroRequest.expandMacros( "UserDaysUntilPwExpire @User:DaysUntilPwExpire@ test" );
        Assert.assertEquals( goal, expanded );
    }


    @Test
    public void testTargetUserDaysUntilPwExpireMacro()
            throws PwmUnrecoverableException
    {
        final Duration duration = Duration.between( macroRequest.getTargetUserInfo().getPasswordExpirationTime(), Instant.now() );
        final long days = TimeUnit.DAYS.convert( duration.toMillis(), TimeUnit.MILLISECONDS );

        final String goal = "TargetUserDaysUntilPwExpire " + days + " test";
        final String expanded = macroRequest.expandMacros( "TargetUserDaysUntilPwExpire @TargetUser:DaysUntilPwExpire@ test" );
        Assert.assertEquals( goal, expanded );
    }

    @Test
    public void testPasswordMacro()
    {

        final String goal = "UserPassword PaSSw0rd test";
        final String expanded = macroRequest.expandMacros( "UserPassword @User:Password@ test" );
        Assert.assertEquals( goal, expanded );
    }

    @Test
    public void testUserEmailMacro()
    {

        final String goal = "UserEmail FLast@example.com test";
        final String expanded = macroRequest.expandMacros( "UserEmail @User:Email@ test" );
        Assert.assertEquals( goal, expanded );
    }

    @Test
    public void testTargetUserEmailMacro()
    {

        final String goal = "TargetUserEmail TUser@example.com test";
        final String expanded = macroRequest.expandMacros( "TargetUserEmail @TargetUser:Email@ test" );
        Assert.assertEquals( goal, expanded );
    }


    @Test
    public void testUserDNMacro()
    {

        final String goal = "UserDN cn=FLast,ou=test,o=org test";
        final String expanded = macroRequest.expandMacros( "UserDN @LDAP:DN@ test" );
        Assert.assertEquals( goal, expanded );
    }

    @Test
    public void testUserLdapProfileMacro()
    {

        final String goal = "UserLdapProfile profile1 test";
        final String expanded = macroRequest.expandMacros( "UserLdapProfile @User:LdapProfile@ test" );
        Assert.assertEquals( goal, expanded );
    }

    @Test
    public void testLdapMacro()
    {
        // userID macro
        {
            final String goal = "cn=FLast,ou=test,o=org";
            final String expanded = macroRequest.expandMacros( "@LDAP:dn@" );
            Assert.assertEquals( goal, expanded );
        }


        {
            final String goal = "test cn%3DFLast%2Cou%3Dtest%2Co%3Dorg";
            final String expanded = macroRequest.expandMacros( "test @Encode:urlPath:[[@LDAP:dn@]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // user attribute macro
        {
            final String goal = "test First test";
            final String expanded = macroRequest.expandMacros( "test @LDAP:givenName@ test" );
            Assert.assertEquals( goal, expanded );
        }

        // user attribute with max chars macro
        {
            final String goal = "test Firs test";
            final String expanded = macroRequest.expandMacros( "test @LDAP:givenName:4@ test" );
            Assert.assertEquals( goal, expanded );
        }

        // user attribute with max chars and pad macro
        {
            final String goal = "test Firstooooo test";
            final String expanded = macroRequest.expandMacros( "test @LDAP:givenName:10:o@ test" );
            Assert.assertEquals( goal, expanded );
        }
    }

    @Test
    public void testTargetUserLdapMacro()
    {
        // userID macro
        {
            final String goal = "cn=TUser,ou=test,o=org";
            final String expanded = macroRequest.expandMacros( "@TargetUser:LDAP:dn@" );
            Assert.assertEquals( goal, expanded );
        }


        {
            final String goal = "test cn%3DTUser%2Cou%3Dtest%2Co%3Dorg";
            final String expanded = macroRequest.expandMacros( "test @Encode:urlPath:[[@TargetUser:LDAP:dn@]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // user attribute macro
        {
            final String goal = "test Target test";
            final String expanded = macroRequest.expandMacros( "test @TargetUser:LDAP:givenName@ test" );
            Assert.assertEquals( goal, expanded );
        }

        // user attribute with max chars macro
        {
            final String goal = "test Targ test";
            final String expanded = macroRequest.expandMacros( "test @TargetUser:LDAP:givenName:4@ test" );
            Assert.assertEquals( goal, expanded );
        }

        // user attribute with max chars and pad macro
        {
            final String goal = "test Targetoooo test";
            final String expanded = macroRequest.expandMacros( "test @TargetUser:LDAP:givenName:10:o@ test" );
            Assert.assertEquals( goal, expanded );
        }
    }

    @Test
    public void testUserLdapMacro()
    {
        // userID macro
        {
            final String goal = "cn=FLast,ou=test,o=org";
            final String expanded = macroRequest.expandMacros( "@User:LDAP:dn@" );
            Assert.assertEquals( goal, expanded );
        }


        {
            final String goal = "test cn%3DFLast%2Cou%3Dtest%2Co%3Dorg";
            final String expanded = macroRequest.expandMacros( "test @Encode:urlPath:[[@User:LDAP:dn@]]@" );
            Assert.assertEquals( goal, expanded );
        }

        // user attribute macro
        {
            final String goal = "test First test";
            final String expanded = macroRequest.expandMacros( "test @User:LDAP:givenName@ test" );
            Assert.assertEquals( goal, expanded );
        }

        // user attribute with max chars macro
        {
            final String goal = "test Firs test";
            final String expanded = macroRequest.expandMacros( "test @User:LDAP:givenName:4@ test" );
            Assert.assertEquals( goal, expanded );
        }

        // user attribute with max chars and pad macro
        {
            final String goal = "test Firstooooo test";
            final String expanded = macroRequest.expandMacros( "test @User:LDAP:givenName:10:o@ test" );
            Assert.assertEquals( goal, expanded );
        }
    }
}

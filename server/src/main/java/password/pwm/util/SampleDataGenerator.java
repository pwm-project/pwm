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

package password.pwm.util;

import com.novell.ldapchai.cr.Answer;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoBean;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.operations.otp.OTPUserRecord;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SampleDataGenerator
{
    public static UserInfo sampleUserData()
    {
        final Map<String, String> userAttributes = new LinkedHashMap<>();
        userAttributes.put( "givenName", "First" );
        userAttributes.put( "sn", "Last" );
        userAttributes.put( "cn", "FLast" );
        userAttributes.put( "fullname", "First Last" );
        userAttributes.put( "uid", "FLast" );
        userAttributes.put( "mail", "FLast@example.com" );
        userAttributes.put( "carLicense", "6YJ S32" );
        userAttributes.put( "mobile", "800-555-1212" );
        userAttributes.put( "objectClass", "inetOrgPerson" );
        userAttributes.put( "personalMobile", "800-555-1313" );
        userAttributes.put( "title", "Title" );
        userAttributes.put( "c", "USA" );
        userAttributes.put( "co", "County" );
        userAttributes.put( "description", "User Description" );
        userAttributes.put( "department", "Department" );
        userAttributes.put( "initials", "M" );
        userAttributes.put( "postalcode", "12345-6789" );
        userAttributes.put( "samaccountname", "FLast" );
        userAttributes.put( "userprincipalname", "FLast" );


        final OTPUserRecord otpUserRecord = new OTPUserRecord();
        otpUserRecord.setTimestamp( Instant.ofEpochSecond( 941259364 ) );
        final ResponseInfoBean responseInfoBean = new ResponseInfoBean(
                Collections.emptyMap(),
                Collections.emptyMap(),
                PwmConstants.DEFAULT_LOCALE,
                8 + 3,
                null,
                DataStorageMethod.LOCALDB,
                Answer.FormatType.PBKDF2
        );
        responseInfoBean.setTimestamp( Instant.ofEpochSecond( 941246275 ) );

        final UserIdentity userIdentity = UserIdentity.createUserIdentity( "cn=FLast,ou=test,o=org", "profile1" );

        return UserInfoBean.builder()
                .userIdentity( userIdentity )
                .username( "FLast" )
                .userEmailAddress( "FLast@example.com" )
                .attributes( userAttributes )
                .passwordExpirationTime( Instant.ofEpochSecond( 949539661 ) )
                .responseInfoBean( responseInfoBean )
                .otpUserRecord( otpUserRecord )
                .build();
    }

    public static UserInfo sampleTargetUserInfo()
    {

            final Map<String, String> userAttributes = new LinkedHashMap<>();
            userAttributes.put( "givenName", "Target" );
            userAttributes.put( "sn", "User" );
            userAttributes.put( "cn", "TUser" );
            userAttributes.put( "fullname", "Target User" );
            userAttributes.put( "uid", "TUser" );
            userAttributes.put( "mail", "TUser@example.com" );
            userAttributes.put( "carLicense", "6YJ S32" );
            userAttributes.put( "mobile", "800-555-1212" );
            userAttributes.put( "objectClass", "inetOrgPerson" );
            userAttributes.put( "personalMobile", "800-555-1313" );
            userAttributes.put( "title", "Title" );
            userAttributes.put( "c", "USA" );
            userAttributes.put( "co", "County" );
            userAttributes.put( "description", "Target User Description" );
            userAttributes.put( "department", "Department" );
            userAttributes.put( "initials", "M" );
            userAttributes.put( "postalcode", "12345-6789" );
            userAttributes.put( "samaccountname", "TUser" );
            userAttributes.put( "userprincipalname", "TUser" );


            final OTPUserRecord otpUserRecord = new OTPUserRecord();
            otpUserRecord.setTimestamp( Instant.ofEpochSecond( 941252344 ) );
            final ResponseInfoBean responseInfoBean = new ResponseInfoBean(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    PwmConstants.DEFAULT_LOCALE,
                    8 + 3,
                    null,
                    DataStorageMethod.LOCALDB,
                    Answer.FormatType.PBKDF2
            );
            responseInfoBean.setTimestamp( Instant.ofEpochSecond( 941244474 ) );

            final UserIdentity userIdentity = UserIdentity.createUserIdentity( "cn=TUser,ou=test,o=org", "profile1" );

            return UserInfoBean.builder()
                    .userIdentity( userIdentity )
                    .username( "TUser" )
                    .userEmailAddress( "TUser@example.com" )
                    .attributes( userAttributes )
                    .passwordExpirationTime( Instant.ofEpochSecond( 94949121 ) )
                    .responseInfoBean( responseInfoBean )
                    .otpUserRecord( otpUserRecord )
                    .build();

    }

    public static MacroRequest sampleMacroRequest( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        final UserInfo targetUserInfoBean = sampleTargetUserInfo();


        final UserInfo userInfoBean = sampleUserData();

        final LoginInfoBean loginInfoBean = new LoginInfoBean();
        loginInfoBean.setAuthenticated( true );
        loginInfoBean.setUserIdentity( userInfoBean.getUserIdentity() );
        loginInfoBean.setUserCurrentPassword( PasswordData.forStringValue( "PaSSw0rd" ) );

        return MacroRequest.builder()
                .pwmApplication( pwmApplication )
                .userInfo( userInfoBean )
                .targetUserInfo( targetUserInfoBean )
                .loginInfoBean( loginInfoBean )
                .build();

    }
}

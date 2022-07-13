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

package password.pwm.util;

import com.novell.ldapchai.cr.Answer;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.PwmEnvironment;
import password.pwm.bean.DomainID;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.otp.OTPUserRecord;
import password.pwm.user.UserInfo;
import password.pwm.user.UserInfoBean;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.macro.MacroRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public class SampleDataGenerator
{
    private static final DomainID SAMPLE_USER_DOMAIN = DomainID.create( "default" );
    private static final String SAMPLE_USER_LDAP_PROFILE = "default";

    private static final UserIdentity SAMPLE_CONFIG_MODIFIER_IDENTITY = UserIdentity
            .create( "cn=configModifier,ou=users,o=org", SAMPLE_USER_LDAP_PROFILE, SAMPLE_USER_DOMAIN );

    private static final Map<String, String> USER1_ATTRIBUTES = Map.ofEntries(
            Map.entry( "givenName", "First" ),
            Map.entry( "sn", "Last" ),
            Map.entry( "cn", "FLast" ),
            Map.entry( "fullname", "First Last" ),
            Map.entry( "uid", "FLast" ),
            Map.entry( "mail", "FLast@example.com" ),
            Map.entry( "carLicense", "6YJ S32" ),
            Map.entry( "mobile", "800-555-1212" ),
            Map.entry( "objectClass", "inetOrgPerson" ),
            Map.entry( "personalMobile", "800-555-1313" ),
            Map.entry( "title", "Title" ),
            Map.entry( "c", "USA" ),
            Map.entry( "co", "County" ),
            Map.entry( "description", "User Description" ),
            Map.entry( "department", "Department" ),
            Map.entry( "initials", "M" ),
            Map.entry( "postalcode", "12345-6789" ),
            Map.entry( "samaccountname", "FLast" ),
            Map.entry( "userprincipalname", "FLast" ) );

    private static final Map<String, String> USER2_ATTRIBUTES = Map.ofEntries(
            Map.entry( "givenName", "Target" ),
            Map.entry( "sn", "User" ),
            Map.entry( "cn", "TUser" ),
            Map.entry( "fullname", "Target User" ),
            Map.entry( "uid", "TUser" ),
            Map.entry( "mail", "TUser@example.com" ),
            Map.entry( "carLicense", "6YJ S32" ),
            Map.entry( "mobile", "800-555-1212" ),
            Map.entry( "objectClass", "inetOrgPerson" ),
            Map.entry( "personalMobile", "800-555-1313" ),
            Map.entry( "title", "Title" ),
            Map.entry( "c", "USA" ),
            Map.entry( "co", "County" ),
            Map.entry( "description", "Target User Description" ),
            Map.entry( "department", "Department" ),
            Map.entry( "initials", "M" ),
            Map.entry( "postalcode", "12345-6789" ),
            Map.entry( "samaccountname", "TUser" ),
            Map.entry( "userprincipalname", "TUser" ) );


    public static UserInfo sampleUserData()
    {
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

        final UserIdentity userIdentity = UserIdentity.create( "cn=FLast,ou=test,o=org", SAMPLE_USER_LDAP_PROFILE, SAMPLE_USER_DOMAIN );

        return UserInfoBean.builder()
                .userIdentity( userIdentity )
                .username( "FLast" )
                .userEmailAddress( "FLast@example.com" )
                .attributes( USER1_ATTRIBUTES )
                .passwordExpirationTime( Instant.ofEpochSecond( 949539661 ) )
                .responseInfoBean( responseInfoBean )
                .otpUserRecord( otpUserRecord )
                .build();
    }

    public static UserInfo sampleTargetUserInfo()
    {
        final OTPUserRecord otpUserRecord = new OTPUserRecord();
        otpUserRecord.setTimestamp( Instant.ofEpochSecond( 941252344 ) );
        final ResponseInfoBean responseInfoBean = new ResponseInfoBean(
                Collections.emptyMap(),
                Collections.emptyMap(),
                PwmConstants.DEFAULT_LOCALE,
                8 + 3,
                null,
                DataStorageMethod.LOCALDB,
                Answer.FormatType.PBKDF2 );

        responseInfoBean.setTimestamp( Instant.ofEpochSecond( 941244474 ) );

        final UserIdentity userIdentity = UserIdentity.create( "cn=TUser,ou=test,o=org", SAMPLE_USER_LDAP_PROFILE, SAMPLE_USER_DOMAIN );

        return UserInfoBean.builder()
                .userIdentity( userIdentity )
                .username( "TUser" )
                .userEmailAddress( "TUser@example.com" )
                .attributes( USER2_ATTRIBUTES )
                .passwordExpirationTime( Instant.ofEpochSecond( 94949121 ) )
                .responseInfoBean( responseInfoBean )
                .otpUserRecord( otpUserRecord )
                .build();
    }

    public static MacroRequest sampleMacroRequest( final PwmDomain pwmDomain )
            throws PwmUnrecoverableException
    {
        final UserInfo targetUserInfoBean = sampleTargetUserInfo();


        final UserInfo userInfoBean = sampleUserData();

        final LoginInfoBean loginInfoBean = new LoginInfoBean();
        loginInfoBean.setAuthenticated( true );
        loginInfoBean.setUserIdentity( userInfoBean.getUserIdentity() );
        loginInfoBean.setUserCurrentPassword( PasswordData.forStringValue( "PaSSw0rd" ) );

        return MacroRequest.builder()
                .pwmApplication( makeSamplePwmApp( ) )
                .userInfo( userInfoBean )
                .targetUserInfo( targetUserInfoBean )
                .loginInfoBean( loginInfoBean )
                .build();

    }

    private static AppConfig makeConfig()
            throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = StoredConfigurationFactory.newConfig();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        modifier.writeSetting( StoredConfigKey.forSetting(
                        PwmSetting.EVENTS_JAVA_STDOUT_LEVEL, SAMPLE_USER_LDAP_PROFILE, SAMPLE_USER_DOMAIN ),
                new StringValue( PwmLogLevel.FATAL.toString() ), SAMPLE_CONFIG_MODIFIER_IDENTITY );
        modifier.writeSetting( StoredConfigKey.forSetting(
                        PwmSetting.LDAP_PROFILE_DISPLAY_NAME, SAMPLE_USER_LDAP_PROFILE, SAMPLE_USER_DOMAIN ),
                new LocalizedStringValue( Map.of( "", "ProfileName" ) ), SAMPLE_CONFIG_MODIFIER_IDENTITY );

        return new AppConfig( modifier.newStoredConfiguration() );
    }

    private static PwmApplication makeSamplePwmApp()
            throws PwmUnrecoverableException
    {
        return makeSamplePwmApp( makeConfig() );
    }

    private static PwmApplication makeSamplePwmApp( final AppConfig appConfig )
            throws PwmUnrecoverableException
    {
        Logger.getRootLogger().setLevel( Level.OFF );
        final PwmEnvironment pwmEnvironment = PwmEnvironment.builder()
                .config( appConfig )
                .applicationPath( null )
                .applicationMode( PwmApplicationMode.READ_ONLY )
                .internalRuntimeInstance( true )
                .build();

        return PwmApplication.createPwmApplication( pwmEnvironment );
    }
}

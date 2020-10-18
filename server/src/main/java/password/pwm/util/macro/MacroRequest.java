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

import com.novell.ldapchai.cr.Answer;
import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.CommonValues;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoBean;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.util.PasswordData;
import password.pwm.util.operations.otp.OTPUserRecord;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Value
@Builder( toBuilder = true )
public class MacroRequest
{
    private final PwmApplication pwmApplication;
    private final SessionLabel sessionLabel;
    private final UserInfo userInfo;
    private final LoginInfoBean loginInfoBean;
    private final MacroReplacer macroReplacer;

    public MacroRequest(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean,
            final MacroReplacer macroReplacer
    )
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.userInfo = userInfo;
        this.loginInfoBean = loginInfoBean;
        this.macroReplacer = macroReplacer;
    }

    public static MacroRequest forStatic( )
    {
        return new MacroRequest( null, null, null, null, null );
    }

    public static MacroRequest forUser(
            final CommonValues commonValues,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        return forUser( commonValues.getPwmApplication(), commonValues.getLocale(), commonValues.getSessionLabel(), userIdentity );
    }

    public static MacroRequest forUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        return forUser( pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getLabel(), userIdentity );
    }

    public static MacroRequest forUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final MacroReplacer macroReplacer
    )
            throws PwmUnrecoverableException
    {
        return forUser( pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getLabel(), userIdentity, macroReplacer );
    }

    public static MacroRequest forUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean
    )
    {
        return new MacroRequest( pwmApplication, sessionLabel, userInfo, loginInfoBean, null );
    }

    public static MacroRequest forUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean,
            final MacroReplacer macroReplacer
    )
    {
        return new MacroRequest( pwmApplication, sessionLabel, userInfo, loginInfoBean, macroReplacer );
    }

    public static MacroRequest forUser(
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfoBean = UserInfoFactory.newUserInfoUsingProxy( pwmApplication, sessionLabel, userIdentity, userLocale );
        return new MacroRequest( pwmApplication, sessionLabel, userInfoBean, null, null );
    }

    public static MacroRequest forUser(
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final MacroReplacer macroReplacer
    )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfoBean = UserInfoFactory.newUserInfoUsingProxy( pwmApplication, sessionLabel, userIdentity, userLocale );
        return new MacroRequest( pwmApplication, sessionLabel, userInfoBean, null, macroReplacer );
    }

    public static MacroRequest forNonUserSpecific(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel
    )
    {
        return new MacroRequest( pwmApplication, sessionLabel, null, null, null );
    }

    public String expandMacros( final String input )
    {
        return MacroMachine.expandMacros( this, input );
    }

    public static MacroRequest sampleMacroRequest( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put( "givenName", "First" );
        attributes.put( "sn", "Last" );
        attributes.put( "cn", "FLast" );
        attributes.put( "fullname", "First Last" );
        attributes.put( "uid", "FLast" );
        attributes.put( "mail", "FLast@example.com" );
        attributes.put( "carLicense", "6YJ S32" );
        attributes.put( "mobile", "800-555-1212" );
        attributes.put( "objectClass", "inetOrgPerson" );
        attributes.put( "personalMobile", "800-555-1313" );
        attributes.put( "title", "Title" );
        attributes.put( "c", "USA" );
        attributes.put( "co", "County" );
        attributes.put( "description", "User Description" );
        attributes.put( "department", "Department" );
        attributes.put( "initials", "M" );
        attributes.put( "postalcode", "12345-6789" );
        attributes.put( "samaccountname", "FLast" );
        attributes.put( "userprincipalname", "FLast" );


        final UserIdentity userIdentity = new UserIdentity( "cn=test1,ou=test,o=org", "profile1" );
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
        final UserInfoBean userInfoBean = UserInfoBean.builder()
                .userIdentity( userIdentity )
                .username( "jrivard" )
                .userEmailAddress( "zippy@example.com" )
                .attributes( attributes )
                .passwordExpirationTime( Instant.ofEpochSecond( 949539661 ) )
                .responseInfoBean( responseInfoBean )
                .otpUserRecord( otpUserRecord )
                .cachedAttributeValues( Collections.singletonMap( "givenName", "Jason" ) )
                .build();

        final LoginInfoBean loginInfoBean = new LoginInfoBean();
        loginInfoBean.setAuthenticated( true );
        loginInfoBean.setUserIdentity( userIdentity );
        loginInfoBean.setUserCurrentPassword( PasswordData.forStringValue( "PaSSw0rd" ) );

        return MacroRequest.builder()
                .pwmApplication( pwmApplication )
                .userInfo( userInfoBean )
                .loginInfoBean( loginInfoBean )
                .build();

    }

}

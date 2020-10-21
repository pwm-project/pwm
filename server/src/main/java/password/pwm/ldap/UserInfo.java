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

package password.pwm.ldap;

import password.pwm.bean.PasswordStatus;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.operations.otp.OTPUserRecord;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserInfo
{
    Map<String, String> getCachedPasswordRuleAttributes( ) throws PwmUnrecoverableException;

    Map<String, String> getCachedAttributeValues( ) throws PwmUnrecoverableException;

    Instant getLastLdapLoginTime( ) throws PwmUnrecoverableException;

    String getLanguage( ) throws PwmUnrecoverableException;

    ChallengeProfile getChallengeProfile( ) throws PwmUnrecoverableException;

    PwmPasswordPolicy getPasswordPolicy( ) throws PwmUnrecoverableException;

    UserIdentity getUserIdentity( );

    Instant getPasswordExpirationTime( ) throws PwmUnrecoverableException;

    String getUsername( ) throws PwmUnrecoverableException;

    PasswordStatus getPasswordStatus( ) throws PwmUnrecoverableException;

    boolean isRequiresNewPassword( ) throws PwmUnrecoverableException;

    boolean isRequiresResponseConfig( ) throws PwmUnrecoverableException;

    boolean isRequiresOtpConfig( ) throws PwmUnrecoverableException;

    boolean isRequiresUpdateProfile( ) throws PwmUnrecoverableException;

    boolean isRequiresInteraction( ) throws PwmUnrecoverableException;

    boolean isAccountEnabled( ) throws PwmUnrecoverableException;

    boolean isAccountExpired( ) throws PwmUnrecoverableException;

    boolean isPasswordLocked( ) throws PwmUnrecoverableException;

    boolean isWithinPasswordMinimumLifetime( ) throws PwmUnrecoverableException;

    Instant getPasswordLastModifiedTime( ) throws PwmUnrecoverableException;

    String getUserEmailAddress( ) throws PwmUnrecoverableException;

    String getUserEmailAddress2( ) throws PwmUnrecoverableException;

    String getUserEmailAddress3( ) throws PwmUnrecoverableException;

    String getUserSmsNumber( ) throws PwmUnrecoverableException;

    String getUserSmsNumber2( ) throws PwmUnrecoverableException;

    String getUserSmsNumber3( ) throws PwmUnrecoverableException;

    String getUserGuid( ) throws PwmUnrecoverableException;

    ResponseInfoBean getResponseInfoBean( ) throws PwmUnrecoverableException;

    OTPUserRecord getOtpUserRecord( ) throws PwmUnrecoverableException;

    Instant getAccountExpirationTime( ) throws PwmUnrecoverableException;

    Map<ProfileDefinition, String> getProfileIDs( ) throws PwmUnrecoverableException;

    String readStringAttribute( String attribute ) throws PwmUnrecoverableException;

    byte[] readBinaryAttribute( String attribute ) throws PwmUnrecoverableException;

    Instant readDateAttribute( String attribute ) throws PwmUnrecoverableException;

    List<String> readMultiStringAttribute( String attribute ) throws PwmUnrecoverableException;

    Map<String, String> readStringAttributes( Collection<String> attributes ) throws PwmUnrecoverableException;

    Instant getPasswordExpirationNoticeSendTime( ) throws PwmUnrecoverableException;
}

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

package password.pwm.ldap;

import password.pwm.bean.PasswordStatus;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.ProfileType;
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

    Instant getPasswordLastModifiedTime( ) throws PwmUnrecoverableException;

    String getUserEmailAddress( ) throws PwmUnrecoverableException;

    String getUserSmsNumber( ) throws PwmUnrecoverableException;

    String getUserGuid( ) throws PwmUnrecoverableException;

    ResponseInfoBean getResponseInfoBean( ) throws PwmUnrecoverableException;

    OTPUserRecord getOtpUserRecord( ) throws PwmUnrecoverableException;

    Instant getAccountExpirationTime( ) throws PwmUnrecoverableException;

    Map<ProfileType, String> getProfileIDs( ) throws PwmUnrecoverableException;

    String readStringAttribute( String attribute ) throws PwmUnrecoverableException;

    Instant readDateAttribute( String attribute ) throws PwmUnrecoverableException;

    List<String> readMultiStringAttribute( String attribute ) throws PwmUnrecoverableException;

    Map<String, String> readStringAttributes( Collection<String> attributes ) throws PwmUnrecoverableException;
}

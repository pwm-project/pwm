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

package password.pwm.svc.report;

import com.novell.ldapchai.cr.Answer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import password.pwm.bean.PasswordStatus;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter( AccessLevel.PRIVATE )
public class UserCacheRecord implements Serializable
{
    public String userDN;
    public String ldapProfile;
    public String userGUID;

    public String username;
    public String email;

    public Instant cacheTimestamp = Instant.now();

    public PasswordStatus passwordStatus;
    public Instant passwordExpirationTime;
    public Instant passwordChangeTime;
    public Instant lastLoginTime;
    public Instant accountExpirationTime;

    public boolean hasResponses;
    public boolean hasHelpdeskResponses;
    public Instant responseSetTime;
    public DataStorageMethod responseStorageMethod;
    public Answer.FormatType responseFormatType;

    public boolean hasOtpSecret;
    public Instant otpSecretSetTime;

    public boolean requiresPasswordUpdate;
    public boolean requiresResponseUpdate;
    public boolean requiresProfileUpdate;

    void addUiBeanData(
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        this.setUserDN( userInfo.getUserIdentity().getUserDN() );
        this.setLdapProfile( userInfo.getUserIdentity().getLdapProfileID() );
        this.setUsername( userInfo.getUsername() );
        this.setEmail( userInfo.getUserEmailAddress() );
        this.setUserGUID( userInfo.getUserGuid() );

        this.setPasswordStatus( userInfo.getPasswordStatus() );

        this.setPasswordChangeTime( userInfo.getPasswordLastModifiedTime() );
        this.setPasswordExpirationTime( userInfo.getPasswordExpirationTime() );
        this.setLastLoginTime( userInfo.getLastLdapLoginTime() );
        this.setAccountExpirationTime( userInfo.getAccountExpirationTime() );

        this.setHasResponses( userInfo.getResponseInfoBean() != null );
        this.setResponseSetTime( userInfo.getResponseInfoBean() != null
                ? userInfo.getResponseInfoBean().getTimestamp()
                : null
        );
        this.setResponseStorageMethod( userInfo.getResponseInfoBean() != null
                ? userInfo.getResponseInfoBean().getDataStorageMethod()
                : null
        );
        this.setResponseFormatType( userInfo.getResponseInfoBean() != null
                ? userInfo.getResponseInfoBean().getFormatType()
                : null
        );

        this.setRequiresPasswordUpdate( userInfo.isRequiresNewPassword() );
        this.setRequiresResponseUpdate( userInfo.isRequiresResponseConfig() );
        this.setRequiresProfileUpdate( userInfo.isRequiresUpdateProfile() );
        this.setCacheTimestamp( Instant.now() );

        this.setHasOtpSecret( userInfo.getOtpUserRecord() != null );
        this.setOtpSecretSetTime( userInfo.getOtpUserRecord() != null && userInfo.getOtpUserRecord().getTimestamp() != null
                ? userInfo.getOtpUserRecord().getTimestamp()
                : null
        );

        this.setHasHelpdeskResponses( userInfo.getResponseInfoBean() != null
                && userInfo.getResponseInfoBean().getHelpdeskCrMap() != null
                && !userInfo.getResponseInfoBean().getHelpdeskCrMap().isEmpty()
        );
    }

}

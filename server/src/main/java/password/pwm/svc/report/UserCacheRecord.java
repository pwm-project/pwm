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
import lombok.Builder;
import lombok.Value;
import password.pwm.bean.PasswordStatus;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;

import java.io.Serializable;
import java.time.Instant;

@Value
@Builder
public class UserCacheRecord implements Serializable
{
    private String userDN;
    private String ldapProfile;
    private String userGUID;

    private String username;
    private String email;

    private PasswordStatus passwordStatus;
    private Instant passwordExpirationTime;
    private Instant passwordChangeTime;
    private Instant lastLoginTime;
    private Instant accountExpirationTime;
    private Instant passwordExpirationNoticeSendTime;

    private boolean hasResponses;
    private boolean hasHelpdeskResponses;
    private Instant responseSetTime;
    private DataStorageMethod responseStorageMethod;
    private Answer.FormatType responseFormatType;

    private boolean hasOtpSecret;
    private Instant otpSecretSetTime;

    private boolean requiresPasswordUpdate;
    private boolean requiresResponseUpdate;
    private boolean requiresProfileUpdate;

    private Instant cacheTimestamp;

    static UserCacheRecord fromUserInfo(
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final UserCacheRecordBuilder builder = new UserCacheRecordBuilder();
        builder.userDN( userInfo.getUserIdentity().getUserDN() );
        builder.ldapProfile( userInfo.getUserIdentity().getLdapProfileID() );
        builder.username( userInfo.getUsername() );
        builder.email( userInfo.getUserEmailAddress() );
        builder.userGUID( userInfo.getUserGuid() );

        builder.passwordStatus( userInfo.getPasswordStatus() );

        builder.passwordChangeTime( userInfo.getPasswordLastModifiedTime() );
        builder.passwordExpirationTime( userInfo.getPasswordExpirationTime() );
        builder.lastLoginTime( userInfo.getLastLdapLoginTime() );
        builder.accountExpirationTime( userInfo.getAccountExpirationTime() );
        builder.passwordExpirationNoticeSendTime( userInfo.getPasswordExpirationNoticeSendTime() );

        builder.hasResponses( userInfo.getResponseInfoBean() != null );
        builder.responseSetTime( userInfo.getResponseInfoBean() != null
                ? userInfo.getResponseInfoBean().getTimestamp()
                : null
        );
        builder.responseStorageMethod( userInfo.getResponseInfoBean() != null
                ? userInfo.getResponseInfoBean().getDataStorageMethod()
                : null
        );
        builder.responseFormatType( userInfo.getResponseInfoBean() != null
                ? userInfo.getResponseInfoBean().getFormatType()
                : null
        );

        builder.requiresPasswordUpdate( userInfo.isRequiresNewPassword() );
        builder.requiresResponseUpdate( userInfo.isRequiresResponseConfig() );
        builder.requiresProfileUpdate( userInfo.isRequiresUpdateProfile() );

        builder.hasOtpSecret( userInfo.getOtpUserRecord() != null );
        builder.otpSecretSetTime( userInfo.getOtpUserRecord() != null && userInfo.getOtpUserRecord().getTimestamp() != null
                ? userInfo.getOtpUserRecord().getTimestamp()
                : null
        );

        builder.hasHelpdeskResponses( userInfo.getResponseInfoBean() != null
                && userInfo.getResponseInfoBean().getHelpdeskCrMap() != null
                && !userInfo.getResponseInfoBean().getHelpdeskCrMap().isEmpty()
        );

        builder.cacheTimestamp( Instant.now() );

        return builder.build();
    }

}

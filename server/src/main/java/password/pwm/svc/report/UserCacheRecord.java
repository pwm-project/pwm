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

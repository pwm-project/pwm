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

package password.pwm.bean.pub;

import lombok.Builder;
import lombok.Value;
import password.pwm.bean.PasswordStatus;
import password.pwm.config.Configuration;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.tag.PasswordRequirementsTag;
import password.pwm.ldap.UserInfo;
import password.pwm.util.macro.MacroRequest;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Value
@Builder
public class PublicUserInfoBean implements Serializable
{
    private String userDN;
    private String ldapProfile;
    private String userID;
    private String userGUID;
    private String userEmailAddress;
    private String userEmailAddress2;
    private String userEmailAddress3;
    private String userSmsNumber;
    private String userSmsNumber2;
    private String userSmsNumber3;
    private String language;
    private Instant passwordExpirationTime;
    private Instant passwordLastModifiedTime;
    private Instant lastLoginTime;
    private Instant accountExpirationTime;
    private boolean requiresNewPassword;
    private boolean requiresResponseConfig;
    private boolean requiresUpdateProfile;
    private boolean requiresOtpConfig;
    private boolean requiresInteraction;

    private PasswordStatus passwordStatus;
    private Map<String, String> passwordPolicy;
    private List<String> passwordRules;
    private Map<String, String> attributes;

    public static PublicUserInfoBean fromUserInfoBean(
            final UserInfo userInfoBean,
            final Configuration config,
            final Locale locale,
            final MacroRequest macroRequest
    )
            throws PwmUnrecoverableException
    {
        final PublicUserInfoBean.PublicUserInfoBeanBuilder publicUserInfoBean = PublicUserInfoBean.builder();
        publicUserInfoBean.userDN = ( userInfoBean.getUserIdentity() == null ) ? "" : userInfoBean.getUserIdentity().getUserDN();
        publicUserInfoBean.ldapProfile = ( userInfoBean.getUserIdentity() == null ) ? "" : userInfoBean.getUserIdentity().getLdapProfileID();
        publicUserInfoBean.userID = userInfoBean.getUsername();
        publicUserInfoBean.userGUID = userInfoBean.getUserGuid();
        publicUserInfoBean.userEmailAddress = userInfoBean.getUserEmailAddress();
        publicUserInfoBean.userEmailAddress2 = userInfoBean.getUserEmailAddress2();
        publicUserInfoBean.userEmailAddress3 = userInfoBean.getUserEmailAddress3();
        publicUserInfoBean.userSmsNumber = userInfoBean.getUserSmsNumber();
        publicUserInfoBean.userSmsNumber2 = userInfoBean.getUserSmsNumber2();
        publicUserInfoBean.userSmsNumber3 = userInfoBean.getUserSmsNumber3();
        publicUserInfoBean.passwordExpirationTime = userInfoBean.getPasswordExpirationTime();
        publicUserInfoBean.passwordLastModifiedTime = userInfoBean.getPasswordLastModifiedTime();
        publicUserInfoBean.passwordStatus = userInfoBean.getPasswordStatus();
        publicUserInfoBean.accountExpirationTime = userInfoBean.getAccountExpirationTime();
        publicUserInfoBean.lastLoginTime = userInfoBean.getLastLdapLoginTime();

        publicUserInfoBean.requiresNewPassword = userInfoBean.isRequiresNewPassword();
        publicUserInfoBean.requiresResponseConfig = userInfoBean.isRequiresResponseConfig();
        publicUserInfoBean.requiresUpdateProfile = userInfoBean.isRequiresUpdateProfile();
        publicUserInfoBean.requiresOtpConfig = userInfoBean.isRequiresOtpConfig();
        publicUserInfoBean.requiresInteraction = userInfoBean.isRequiresInteraction();
        publicUserInfoBean.language = userInfoBean.getLanguage();

        publicUserInfoBean.passwordPolicy = new HashMap<>();
        for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
        {
            publicUserInfoBean.passwordPolicy.put( rule.name(), userInfoBean.getPasswordPolicy().getValue( rule ) );
        }

        publicUserInfoBean.passwordRules = PasswordRequirementsTag.getPasswordRequirementsStrings(
                userInfoBean.getPasswordPolicy(),
                config,
                locale,
                macroRequest
        );

        if ( userInfoBean.getCachedAttributeValues() != null && !userInfoBean.getCachedAttributeValues().isEmpty() )
        {
            publicUserInfoBean.attributes = Collections.unmodifiableMap( userInfoBean.getCachedAttributeValues() );
        }

        return publicUserInfoBean.build();
    }
}

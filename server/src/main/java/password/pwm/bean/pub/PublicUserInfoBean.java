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

package password.pwm.bean.pub;

import lombok.Getter;
import password.pwm.bean.PasswordStatus;
import password.pwm.config.Configuration;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.tag.PasswordRequirementsTag;
import password.pwm.ldap.UserInfo;
import password.pwm.util.macro.MacroMachine;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
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
            final MacroMachine macroMachine
    )
            throws PwmUnrecoverableException
    {
        final PublicUserInfoBean publicUserInfoBean = new PublicUserInfoBean();
        publicUserInfoBean.userDN = ( userInfoBean.getUserIdentity() == null ) ? "" : userInfoBean.getUserIdentity().getUserDN();
        publicUserInfoBean.ldapProfile = ( userInfoBean.getUserIdentity() == null ) ? "" : userInfoBean.getUserIdentity().getLdapProfileID();
        publicUserInfoBean.userID = userInfoBean.getUsername();
        publicUserInfoBean.userGUID = publicUserInfoBean.getUserGUID();
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

        publicUserInfoBean.passwordPolicy = new HashMap<>();
        for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
        {
            publicUserInfoBean.passwordPolicy.put( rule.name(), userInfoBean.getPasswordPolicy().getValue( rule ) );
        }

        publicUserInfoBean.passwordRules = PasswordRequirementsTag.getPasswordRequirementsStrings(
                userInfoBean.getPasswordPolicy(),
                config,
                locale,
                macroMachine
        );

        if ( userInfoBean.getCachedAttributeValues() != null && !userInfoBean.getCachedAttributeValues().isEmpty() )
        {
            publicUserInfoBean.attributes = Collections.unmodifiableMap( userInfoBean.getCachedAttributeValues() );
        }

        return publicUserInfoBean;
    }
}

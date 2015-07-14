/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.bean;

import password.pwm.config.Configuration;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.http.tag.PasswordRequirementsTag;

import java.io.Serializable;
import java.util.*;

public class PublicUserInfoBean implements Serializable {
    public String userDN;
    public String ldapProfile;
    public String userID;
    public String userEmailAddress;
    public Date passwordExpirationTime;
    public Date passwordLastModifiedTime;
    public boolean requiresNewPassword;
    public boolean requiresResponseConfig;
    public boolean requiresUpdateProfile;
    public boolean requiresInteraction;

    public PasswordStatus passwordStatus;
    public Map<String, String> passwordPolicy;
    public List<String> passwordRules;
    public Map<String, String> attributes;

    public static PublicUserInfoBean fromUserInfoBean(final UserInfoBean userInfoBean, final Configuration config, final Locale locale) {
        final PublicUserInfoBean publicUserInfoBean = new PublicUserInfoBean();
        publicUserInfoBean.userDN = (userInfoBean.getUserIdentity() == null) ? "" : userInfoBean.getUserIdentity().getUserDN();
        publicUserInfoBean.ldapProfile = (userInfoBean.getUserIdentity() == null) ? "" : userInfoBean.getUserIdentity().getLdapProfileID();
        publicUserInfoBean.userID = userInfoBean.getUsername();
        publicUserInfoBean.userEmailAddress = userInfoBean.getUserEmailAddress();
        publicUserInfoBean.passwordExpirationTime = userInfoBean.getPasswordExpirationTime();
        publicUserInfoBean.passwordLastModifiedTime = userInfoBean.getPasswordLastModifiedTime();
        publicUserInfoBean.passwordStatus = userInfoBean.getPasswordState();

        publicUserInfoBean.requiresNewPassword = userInfoBean.isRequiresNewPassword();
        publicUserInfoBean.requiresResponseConfig = userInfoBean.isRequiresResponseConfig();
        publicUserInfoBean.requiresUpdateProfile = userInfoBean.isRequiresResponseConfig();
        publicUserInfoBean.requiresInteraction = userInfoBean.isRequiresNewPassword()
                || userInfoBean.isRequiresResponseConfig()
                || userInfoBean.isRequiresUpdateProfile()
                || userInfoBean.getPasswordState().isWarnPeriod();


        publicUserInfoBean.passwordPolicy = new HashMap<>();
        for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
            publicUserInfoBean.passwordPolicy.put(rule.name(), userInfoBean.getPasswordPolicy().getValue(rule));
        }

        publicUserInfoBean.passwordRules = PasswordRequirementsTag.getPasswordRequirementsStrings(
                userInfoBean.getPasswordPolicy(),
                config,
                locale
        );

        if (userInfoBean.getCachedAttributeValues() != null && !userInfoBean.getCachedAttributeValues().isEmpty()) {
            publicUserInfoBean.attributes = Collections.unmodifiableMap(userInfoBean.getCachedAttributeValues());
        }

        return publicUserInfoBean;
    }
}

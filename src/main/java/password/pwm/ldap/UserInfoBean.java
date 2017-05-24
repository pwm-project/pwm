/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Builder;
import lombok.Getter;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.util.operations.otp.OTPUserRecord;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class UserInfoBean implements UserInfo {
// ------------------------------ FIELDS ------------------------------

    private UserIdentity userIdentity;
    private String username;
    private String userEmailAddress;
    private String userSmsNumber;
    private String userGuid;

    /**
     * A listing of all readable attributes on the ldap user object
     */
    private Map<String,String> cachedPasswordRuleAttributes = Collections.emptyMap();

    private Map<String,String> cachedAttributeValues = Collections.emptyMap();
    
    private Map<ProfileType,String> profileIDs = new HashMap<>();

    private PasswordStatus passwordStatus = PasswordStatus.builder().build();

    private PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.defaultPolicy();
    private ChallengeProfile challengeProfile = null;
    private ResponseInfoBean responseInfoBean = null;
    private OTPUserRecord otpUserRecord = null;

    private Instant passwordExpirationTime;
    private Instant passwordLastModifiedTime;
    private Instant lastLdapLoginTime;
    private Instant accountExpirationTime;

    private boolean requiresNewPassword;
    private boolean requiresResponseConfig;
    private boolean requiresOtpConfig;
    private boolean requiresUpdateProfile;

    private Map<String,String> attributes;

    @Override
    public String readStringAttribute(final String attribute, final Flag... flags) throws ChaiUnavailableException, ChaiOperationException
    {
        return null;
    }

    @Override
    public Date readDateAttribute(final String attribute) throws ChaiUnavailableException, ChaiOperationException
    {
        return null;
    }

    @Override
    public List<String> readMultiStringAttribute(final String attribute, final Flag... flags) throws ChaiUnavailableException, ChaiOperationException
    {
        return null;
    }

    @Override
    public Map<String, String> readStringAttributes(final Collection<String> attributes, final Flag... flags) throws ChaiUnavailableException, ChaiOperationException
    {
        return null;
    }

    @Override
    public Map<String, List<String>> readMultiStringAttributes(final Collection<String> attributes, final Flag... flags) throws ChaiUnavailableException, ChaiOperationException
    {
        return null;
    }
}


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

import com.novell.ldapchai.impl.edir.entry.EdirEntries;
import lombok.Builder;
import lombok.Getter;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class UserInfoBean implements UserInfo {

    private final UserIdentity userIdentity;
    private final String username;
    private final String userEmailAddress;
    private final String userSmsNumber;
    private final String userGuid;

    /**
     * A listing of all readable attributes on the ldap user object
     */
    @Builder.Default
    private final Map<String,String> cachedPasswordRuleAttributes = Collections.emptyMap();

    @Builder.Default
    private final Map<String,String> cachedAttributeValues = Collections.emptyMap();

    @Builder.Default
    private final Map<ProfileType,String> profileIDs = new HashMap<>();

    @Builder.Default
    private final PasswordStatus passwordStatus = PasswordStatus.builder().build();

    @Builder.Default
    private final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.defaultPolicy();

    private final ChallengeProfile challengeProfile;
    private final ResponseInfoBean responseInfoBean;
    private final OTPUserRecord otpUserRecord;

    private final Instant passwordExpirationTime;
    private final Instant passwordLastModifiedTime;
    private final Instant lastLdapLoginTime;
    private final Instant accountExpirationTime;

    private final boolean requiresNewPassword;
    private final boolean requiresResponseConfig;
    private final boolean requiresOtpConfig;
    private final boolean requiresUpdateProfile;
    private final boolean requiresInteraction;

    @Builder.Default
    private Map<String,String> attributes = Collections.emptyMap();

    @Override
    public String readStringAttribute(final String attribute) throws PwmUnrecoverableException
    {
        return attributes.get(attribute);
    }

    @Override
    public Date readDateAttribute(final String attribute) throws PwmUnrecoverableException
    {
        if (attributes.containsKey(attribute)) {
            return EdirEntries.convertZuluToDate(attributes.get(attribute));
        }
        return null;
    }

    @Override
    public List<String> readMultiStringAttribute(final String attribute) throws PwmUnrecoverableException
    {
        if (attributes.containsKey(attribute)) {
            return Collections.unmodifiableList(Collections.singletonList(attributes.get(attribute)));
        }

        return Collections.emptyList();
    }

    @Override
    public Map<String, String> readStringAttributes(final Collection<String> attributes) throws PwmUnrecoverableException
    {
        final Map<String,String> returnObj = new LinkedHashMap<>();
        for (final String attribute : attributes) {
            if (this.attributes.containsKey(attribute)) {
                returnObj.put(attribute, this.attributes.get(attribute));
            }
        }
        return Collections.unmodifiableMap(returnObj);
    }
}


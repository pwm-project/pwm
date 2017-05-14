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

import lombok.Getter;
import lombok.Setter;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.util.operations.otp.OTPUserRecord;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A bean that is stored in the user's session.   Only information that is particular to logged in user is stored in the
 * user info bean.  Information more topical to the session is stored in {@link LocalSessionStateBean}.
 * <p/>
 * For any given HTTP session using PWM, several {@link UserInfoBean}s may be created during
 * the life of the session, however at any given time, no more than one will be stored in
 * the HTTP session.  If the user is not authenticated (determined by {@link LocalSessionStateBean})
 * then there should not be a {@link UserInfoBean} in the HTTP session.
 *
 * @author Jason D. Rivard
 * @see UserInfoReader#populateUserInfoBean
 */
@Getter
@Setter
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

    private PasswordStatus passwordState = new PasswordStatus();

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
}


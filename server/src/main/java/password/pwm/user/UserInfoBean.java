/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.user;

import com.novell.ldapchai.impl.edir.entry.EdirEntries;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.pub.PublicUserInfoBean;
import password.pwm.config.DomainConfig;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.tag.PasswordRequirementsTag;
import password.pwm.svc.otp.OTPUserRecord;
import password.pwm.util.macro.MacroRequest;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Value
@Builder
public class UserInfoBean implements UserInfo
{
    private final UserIdentity userIdentity;
    private final String username;
    private final String userEmailAddress;
    private final String userEmailAddress2;
    private final String userEmailAddress3;

    private final String userSmsNumber;
    private final String userSmsNumber2;
    private final String userSmsNumber3;

    private final String userGuid;

    /**
     * A listing of all readable attributes on the ldap user object.
     */
    @Singular
    private final Map<String, String> cachedPasswordRuleAttributes;

    @Singular
    private final Map<String, String> cachedAttributeValues;

    @Singular
    private final Map<ProfileDefinition, String> profileIDs;

    @Builder.Default
    private final PasswordStatus passwordStatus = PasswordStatus.builder().build();

    @Builder.Default
    private final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.defaultPolicy();

    private final String language;

    private final ChallengeProfile challengeProfile;
    private final ResponseInfoBean responseInfoBean;
    private final OTPUserRecord otpUserRecord;

    private final Instant passwordExpirationTime;
    private final Instant passwordLastModifiedTime;
    private final Instant lastLdapLoginTime;
    private final Instant accountExpirationTime;
    private final Instant passwordExpirationNoticeSendTime;

    private final boolean accountEnabled;
    private final boolean accountExpired;
    private final boolean passwordLocked;

    private final boolean requiresNewPassword;
    private final boolean requiresResponseConfig;
    private final boolean requiresOtpConfig;
    private final boolean requiresUpdateProfile;
    private final boolean requiresInteraction;
    private final boolean withinPasswordMinimumLifetime;

    @Singular
    private final Map<String, String> attributes;

    public static PublicUserInfoBean toPublicUserInfoBean(
            final UserInfo userInfoBean,
            final DomainConfig config,
            final Locale locale,
            final MacroRequest macroRequest
    )
            throws PwmUnrecoverableException
    {
        final PublicUserInfoBean.PublicUserInfoBeanBuilder publicUserInfoBean = PublicUserInfoBean.builder();
        publicUserInfoBean.userDN( userInfoBean.getUserIdentity() == null ? "" : userInfoBean.getUserIdentity().getUserDN() );
        publicUserInfoBean.ldapProfile( userInfoBean.getUserIdentity() == null ? "" : userInfoBean.getUserIdentity().getLdapProfileID() );
        publicUserInfoBean.userID( userInfoBean.getUsername() );
        publicUserInfoBean.userGUID( userInfoBean.getUserGuid() );
        publicUserInfoBean.userEmailAddress( userInfoBean.getUserEmailAddress() );
        publicUserInfoBean.userEmailAddress2( userInfoBean.getUserEmailAddress2() );
        publicUserInfoBean.userEmailAddress3( userInfoBean.getUserEmailAddress3() );
        publicUserInfoBean.userSmsNumber( userInfoBean.getUserSmsNumber() );
        publicUserInfoBean.userSmsNumber2( userInfoBean.getUserSmsNumber2() );
        publicUserInfoBean.userSmsNumber3( userInfoBean.getUserSmsNumber3() );
        publicUserInfoBean.passwordExpirationTime( userInfoBean.getPasswordExpirationTime() );
        publicUserInfoBean.passwordLastModifiedTime( userInfoBean.getPasswordLastModifiedTime() );
        publicUserInfoBean.passwordStatus( userInfoBean.getPasswordStatus() );
        publicUserInfoBean.accountExpirationTime( userInfoBean.getAccountExpirationTime() );
        publicUserInfoBean.lastLoginTime( userInfoBean.getLastLdapLoginTime() );

        publicUserInfoBean.requiresNewPassword( userInfoBean.isRequiresNewPassword() );
        publicUserInfoBean.requiresResponseConfig( userInfoBean.isRequiresResponseConfig() );
        publicUserInfoBean.requiresUpdateProfile( userInfoBean.isRequiresUpdateProfile() );
        publicUserInfoBean.requiresOtpConfig( userInfoBean.isRequiresOtpConfig() );
        publicUserInfoBean.requiresInteraction( userInfoBean.isRequiresInteraction() );
        publicUserInfoBean.language( userInfoBean.getLanguage() );

        publicUserInfoBean.passwordPolicy( Collections.unmodifiableMap( userInfoBean.getPasswordPolicy().getPolicyMap() ) );

        publicUserInfoBean.passwordRules( PasswordRequirementsTag.getPasswordRequirementsStrings(
                userInfoBean.getPasswordPolicy(),
                config,
                locale,
                macroRequest ) );

        if ( userInfoBean.getCachedAttributeValues() != null && !userInfoBean.getCachedAttributeValues().isEmpty() )
        {
            publicUserInfoBean.attributes( Collections.unmodifiableMap( userInfoBean.getCachedAttributeValues() ) );
        }

        return publicUserInfoBean.build();
    }

    @Override
    public String readStringAttribute( final String attribute ) throws PwmUnrecoverableException
    {
        return attributes.get( attribute );
    }

    @Override
    public Instant readDateAttribute( final String attribute ) throws PwmUnrecoverableException
    {
        if ( attributes.containsKey( attribute ) )
        {
            return EdirEntries.convertZuluToInstant( attributes.get( attribute ) );
        }
        return null;
    }

    @Override
    public List<String> readMultiStringAttribute( final String attribute ) throws PwmUnrecoverableException
    {
        if ( attributes.containsKey( attribute ) )
        {
            return Collections.singletonList( attributes.get( attribute ) );
        }

        return Collections.emptyList();
    }

    @Override
    public byte[] readBinaryAttribute( final String attribute ) throws PwmUnrecoverableException
    {
        throw new UnsupportedOperationException( "method not implemented" );
    }

    @Override
    public Map<String, String> readStringAttributes( final Collection<String> attributes ) throws PwmUnrecoverableException
    {
        final Map<String, String> returnObj = new LinkedHashMap<>();
        for ( final String attribute : attributes )
        {
            if ( this.attributes.containsKey( attribute ) )
            {
                returnObj.put( attribute, this.attributes.get( attribute ) );
            }
        }
        return Collections.unmodifiableMap( returnObj );
    }
}


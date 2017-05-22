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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.PwmPasswordRuleValidator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class UserInfoFactory {

    private static final PwmLogger LOGGER = PwmLogger.forClass(UserInfoFactory.class);

    private final PwmApplication pwmApplication;
    private final SessionLabel sessionLabel;
    private final Settings settings;

    public UserInfoFactory(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel
    )
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.settings = new Settings();
    }

    public UserInfoFactory(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Settings settings
    )
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.settings = settings.copy();
    }

    public PasswordStatus readPasswordStatus(
            final ChaiUser theUser,
            final PwmPasswordPolicy passwordPolicy,
            final UserInfoBean userInfoBean,
            final PasswordData currentPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final PasswordStatus.PasswordStatusBuilder passwordStatusBuilder = PasswordStatus.builder();
        final String userDN = theUser.getEntryDN();

        final long startTime = System.currentTimeMillis();
        LOGGER.trace(sessionLabel, "beginning password status check process for " + userDN);

        // check if password meets existing policy.
        if (userInfoBean != null && passwordPolicy.getRuleHelper().readBooleanValue(PwmPasswordRule.EnforceAtLogin)) {
            if (currentPassword != null) {
                try {
                    final PwmPasswordRuleValidator passwordRuleValidator = new PwmPasswordRuleValidator(pwmApplication, passwordPolicy);
                    passwordRuleValidator.testPassword(currentPassword, null, userInfoBean, theUser);
                } catch (PwmDataValidationException | PwmUnrecoverableException e) {
                    LOGGER.debug(sessionLabel, "user " + userDN + " password does not conform to current password policy (" + e.getMessage() + "), marking as requiring change.");
                    passwordStatusBuilder.violatesPolicy(true);
                }
            }
        }

        boolean ldapPasswordExpired = false;
        try {
            ldapPasswordExpired = theUser.isPasswordExpired();

            if (ldapPasswordExpired) {
                LOGGER.trace(sessionLabel, "password for " + userDN + " appears to be expired");
            } else {
                LOGGER.trace(sessionLabel, "password for " + userDN + " does not appear to be expired");
            }
        } catch (ChaiOperationException e) {
            LOGGER.info(sessionLabel, "error reading LDAP attributes for " + userDN + " while reading isPasswordExpired(): " + e.getMessage());
        }

        try {
            Date ldapPasswordExpirationTime = theUser.readPasswordExpirationDate();
            if (ldapPasswordExpirationTime != null && ldapPasswordExpirationTime.getTime() < 0) {
                // If ldapPasswordExpirationTime is less than 0, this may indicate an extremely late date, past the epoch.
                LOGGER.debug(sessionLabel, "ignoring past-dated password expiration time: " + JavaHelper.toIsoDate(ldapPasswordExpirationTime));
                ldapPasswordExpirationTime = null;
            }

            boolean preExpired = false;
            if (ldapPasswordExpirationTime != null) {
                final TimeDuration expirationInterval = TimeDuration.fromCurrent(ldapPasswordExpirationTime);
                LOGGER.trace(sessionLabel, "read password expiration time: "
                        + JavaHelper.toIsoDate(ldapPasswordExpirationTime)
                        + ", " + expirationInterval.asCompactString() + " from now"
                );
                final long diff = ldapPasswordExpirationTime.getTime() - System.currentTimeMillis();

                // now check to see if the user's expire time is within the 'preExpireTime' setting.
                final long preExpireMs = config.readSettingAsLong(PwmSetting.PASSWORD_EXPIRE_PRE_TIME) * 1000;
                if (diff > 0 && diff < preExpireMs) {
                    LOGGER.debug(sessionLabel, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as pre-expired");
                    preExpired = true;
                } else if (ldapPasswordExpired) {
                    preExpired = true;
                    LOGGER.debug(sessionLabel, "user " + userDN + " password is expired, marking as pre-expired.");
                }

                // now check to see if the user's expire time is within the 'preWarnTime' setting.
                final long preWarnMs = config.readSettingAsLong(PwmSetting.PASSWORD_EXPIRE_WARN_TIME) * 1000;
                // don't check if the 'preWarnTime' setting is zero or less than the expirePreTime
                if (!ldapPasswordExpired && !preExpired) {
                    if (!(preWarnMs == 0 || preWarnMs < preExpireMs)) {
                        if (diff > 0 && diff < preWarnMs) {
                            LOGGER.debug(sessionLabel,
                                    "user " + userDN + " password will expire within " + TimeDuration.asCompactString(
                                            diff) + ", marking as within warn period");
                            passwordStatusBuilder.warnPeriod(true);
                        } else if (ldapPasswordExpired) {
                            LOGGER.debug(sessionLabel,
                                    "user " + userDN + " password is expired, marking as within warn period");
                            passwordStatusBuilder.warnPeriod(true);
                        }
                    }
                }

                passwordStatusBuilder.preExpired(preExpired);
            }

        } catch (ChaiOperationException e) {
            LOGGER.info(sessionLabel, "error reading user attrs for " + userDN + " while reading passwordExpirationDate(): " + e.getMessage());
        }

        LOGGER.debug(sessionLabel, "completed user password status check for " + userDN + " " + passwordStatusBuilder + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
        passwordStatusBuilder.expired(ldapPasswordExpired);
        return passwordStatusBuilder.build();
    }

    public UserInfo populateActorUserInfoBeanUsingProxy(
            final UserIdentity userIdentity,
            final Locale locale,
            final PasswordData currentPassword
    )
            throws PwmUnrecoverableException
    {
        final String userLdapProfile = userIdentity.getLdapProfileID();
        final ChaiProvider provider = pwmApplication.getProxyChaiProvider(userLdapProfile);
        return populateUserInfoBean(
                locale,
                userIdentity,
                provider,
                currentPassword
        );
    }

    public UserInfo populateUserInfoBean(
            final Locale userLocale,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final ChaiProvider provider = pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID());
        return populateUserInfoBean(userLocale, userIdentity, provider, null);
    }

    public UserInfo populateUserInfoBean(
            final Locale userLocale,
            final UserIdentity userIdentity,
            final ChaiProvider provider
    )
            throws PwmUnrecoverableException
    {
        try {
            return makeUserInfoImpl(userLocale, userIdentity, provider, null);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage()));
        }
    }

    public UserInfo populateUserInfoBean(
            final Locale userLocale,
            final UserIdentity userIdentity,
            final ChaiProvider provider,
            final PasswordData currentPassword
    )
            throws PwmUnrecoverableException
    {
        try {
            return makeUserInfoImpl(userLocale, userIdentity, provider, currentPassword);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage()));
        }
    }

    private UserInfo makeUserInfoImpl(
            final Locale userLocale,
            final UserIdentity userIdentity,
            final ChaiProvider provider,
            final PasswordData currentPassword
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        return UserInfoReader.createLazyUserInfo(userIdentity, currentPassword, sessionLabel, userLocale, pwmApplication, provider);
    }


    static Set<String> figurePasswordRuleAttributes(
            final UserInfo uiBean
    ) throws PwmUnrecoverableException
    {
        final Set<String> interestingUserAttributes = new HashSet<>();
        interestingUserAttributes.addAll(uiBean.getPasswordPolicy().getRuleHelper().getDisallowedAttributes());
        if (uiBean.getPasswordPolicy().getRuleHelper().getADComplexityLevel() == ADPolicyComplexity.AD2003
                || uiBean.getPasswordPolicy().getRuleHelper().getADComplexityLevel() == ADPolicyComplexity.AD2008) {
            interestingUserAttributes.add("sAMAccountName");
            interestingUserAttributes.add("displayName");
            interestingUserAttributes.add("fullname");
            interestingUserAttributes.add("cn");
        }
        return interestingUserAttributes;
    }

    public static class Settings implements Serializable {
        private Settings copy()
        {
            return JsonUtil.deserialize(JsonUtil.serialize(this), this.getClass());
        }
    }

}

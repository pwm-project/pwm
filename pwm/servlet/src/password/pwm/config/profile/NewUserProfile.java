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

package password.pwm.config.profile;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.TimeDuration;
import password.pwm.util.operations.PasswordUtility;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NewUserProfile extends AbstractProfile {

    private static final ProfileType PROFILE_TYPE = ProfileType.NewUser;

    private Date newUserPasswordPolicyCacheTime;
    private final Map<Locale,PwmPasswordPolicy> newUserPasswordPolicyCache = new HashMap<>();

    protected NewUserProfile(String identifier, Map<PwmSetting, StoredValue> storedValueMap) {
        super(identifier, storedValueMap);
    }

    public static NewUserProfile makeFromStoredConfiguration(final StoredConfiguration storedConfiguration, final String identifier) {
        final Map<PwmSetting,StoredValue> valueMap = makeValueMap(storedConfiguration, identifier, PROFILE_TYPE.getCategory());
        return new NewUserProfile(identifier, valueMap);

    }

    @Override
    public ProfileType profileType() {
        return PROFILE_TYPE;
    }

    @Override
    public String getDisplayName(Locale locale) {
        final String value = this.readSettingAsLocalizedString(PwmSetting.NEWUSER_PROFILE_DISPLAY_NAME, locale);
        return value != null && !value.isEmpty() ? value : this.getIdentifier();
    }

    public PwmPasswordPolicy getNewUserPasswordPolicy(final PwmApplication pwmApplication, final Locale userLocale)
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final long maxNewUserCacheMS = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.CONFIG_NEWUSER_PASSWORD_POLICY_CACHE_MS));
        if (newUserPasswordPolicyCacheTime != null && TimeDuration.fromCurrent(newUserPasswordPolicyCacheTime).isLongerThan(maxNewUserCacheMS)) {
            newUserPasswordPolicyCacheTime = new Date();
            newUserPasswordPolicyCache.clear();
        }

        final PwmPasswordPolicy cachedPolicy = newUserPasswordPolicyCache.get(userLocale);
        if (cachedPolicy != null) {
            return cachedPolicy;
        }
        
        final PwmPasswordPolicy thePolicy;
        final LdapProfile defaultLdapProfile = config.getDefaultLdapProfile();
        final String configuredNewUserPasswordDN = readSettingAsString(PwmSetting.NEWUSER_PASSWORD_POLICY_USER);
        if (configuredNewUserPasswordDN == null || configuredNewUserPasswordDN.length() < 1) {
            final String errorMsg = "the setting " + PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug(this.getIdentifier(),PwmConstants.DEFAULT_LOCALE) + " must have a value";
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg));
        } else {

            final String lookupDN;
            if (configuredNewUserPasswordDN.equalsIgnoreCase("TESTUSER") ) {
                lookupDN = defaultLdapProfile.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
                if (lookupDN == null || lookupDN.isEmpty()) {
                    final String errorMsg ="setting " 
                            + PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug(defaultLdapProfile.getIdentifier(),PwmConstants.DEFAULT_LOCALE) 
                            + " must be configured since setting "
                            + PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug(this.getIdentifier(),PwmConstants.DEFAULT_LOCALE)
                            + " is set to TESTUSER";
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg));
                }
            } else {
                lookupDN = configuredNewUserPasswordDN;
            }

            if (lookupDN.isEmpty()) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,"user ldap dn in setting " + PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug(null,PwmConstants.DEFAULT_LOCALE) + " can not be resolved"));
            } else {
                try {
                    final ChaiUser chaiUser = ChaiFactory.createChaiUser(lookupDN, pwmApplication.getProxyChaiProvider(defaultLdapProfile.getIdentifier()));
                    final UserIdentity userIdentity = new UserIdentity(lookupDN, defaultLdapProfile.getIdentifier());
                    thePolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication, null, userIdentity, chaiUser, userLocale);
                } catch (ChaiUnavailableException e) {
                    throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
                }
            }
        }
        newUserPasswordPolicyCache.put(userLocale,thePolicy);
        return thePolicy;
    }

}

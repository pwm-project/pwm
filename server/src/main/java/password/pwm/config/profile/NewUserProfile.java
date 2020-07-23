/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.config.profile;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
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
import password.pwm.util.java.TimeDuration;
import password.pwm.util.password.PasswordUtility;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NewUserProfile extends AbstractProfile implements Profile
{

    private static final ProfileDefinition PROFILE_TYPE = ProfileDefinition.NewUser;
    public static final String TEST_USER_CONFIG_VALUE = "TESTUSER";

    private Instant newUserPasswordPolicyCacheTime;
    private final Map<Locale, PwmPasswordPolicy> newUserPasswordPolicyCache = new HashMap<>();

    protected NewUserProfile( final String identifier, final Map<PwmSetting, StoredValue> storedValueMap )
    {
        super( identifier, storedValueMap );
    }

    @Override
    public ProfileDefinition profileType( )
    {
        return PROFILE_TYPE;
    }

    @Override
    public String getDisplayName( final Locale locale )
    {
        final String value = this.readSettingAsLocalizedString( PwmSetting.NEWUSER_PROFILE_DISPLAY_NAME, locale );
        return value != null && !value.isEmpty() ? value : this.getIdentifier();
    }

    public PwmPasswordPolicy getNewUserPasswordPolicy( final PwmApplication pwmApplication, final Locale userLocale )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final long maxNewUserCacheMS = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.CONFIG_NEWUSER_PASSWORD_POLICY_CACHE_MS ) );
        if ( newUserPasswordPolicyCacheTime != null && TimeDuration.fromCurrent( newUserPasswordPolicyCacheTime ).isLongerThan( maxNewUserCacheMS ) )
        {
            newUserPasswordPolicyCacheTime = Instant.now();
            newUserPasswordPolicyCache.clear();
        }

        final PwmPasswordPolicy cachedPolicy = newUserPasswordPolicyCache.get( userLocale );
        if ( cachedPolicy != null )
        {
            return cachedPolicy;
        }

        final PwmPasswordPolicy thePolicy;
        final LdapProfile defaultLdapProfile = config.getDefaultLdapProfile();
        final String configuredNewUserPasswordDN = readSettingAsString( PwmSetting.NEWUSER_PASSWORD_POLICY_USER );
        if ( configuredNewUserPasswordDN == null || configuredNewUserPasswordDN.length() < 1 )
        {
            final String errorMsg = "the setting " + PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug( this.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
                    + " must have a value";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg ) );
        }
        else
        {

            final String lookupDN;
            if ( TEST_USER_CONFIG_VALUE.equalsIgnoreCase( configuredNewUserPasswordDN ) )
            {
                lookupDN = defaultLdapProfile.readSettingAsString( PwmSetting.LDAP_TEST_USER_DN );
                if ( lookupDN == null || lookupDN.isEmpty() )
                {
                    final String errorMsg = "setting "
                            + PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( defaultLdapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
                            + " must be configured since setting "
                            + PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug( this.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
                            + " is set to " + TEST_USER_CONFIG_VALUE;
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, errorMsg ) );
                }
            }
            else
            {
                lookupDN = configuredNewUserPasswordDN;
            }

            if ( lookupDN.isEmpty() )
            {
                throw new PwmUnrecoverableException( new ErrorInformation(
                        PwmError.ERROR_INVALID_CONFIG,
                        "user ldap dn in setting " + PwmSetting.NEWUSER_PASSWORD_POLICY_USER.toMenuLocationDebug(
                                null,
                                PwmConstants.DEFAULT_LOCALE ) + " can not be resolved" )
                );
            }
            else
            {
                try
                {
                    final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider( defaultLdapProfile.getIdentifier() );
                    final ChaiUser chaiUser = chaiProvider.getEntryFactory().newChaiUser( lookupDN );
                    final UserIdentity userIdentity = new UserIdentity( lookupDN, defaultLdapProfile.getIdentifier() );
                    thePolicy = PasswordUtility.readPasswordPolicyForUser( pwmApplication, null, userIdentity, chaiUser, userLocale );
                }
                catch ( final ChaiUnavailableException e )
                {
                    throw new PwmUnrecoverableException( PwmError.forChaiError( e.getErrorCode() ) );
                }
            }
        }
        newUserPasswordPolicyCache.put( userLocale, thePolicy );
        return thePolicy;
    }

    public TimeDuration getTokenDurationEmail( final Configuration configuration )
    {
        final long newUserDuration = readSettingAsLong( PwmSetting.NEWUSER_TOKEN_LIFETIME_EMAIL );
        if ( newUserDuration < 1 )
        {
            final long defaultDuration = configuration.readSettingAsLong( PwmSetting.TOKEN_LIFETIME );
            return TimeDuration.of( defaultDuration, TimeDuration.Unit.SECONDS );
        }
        return TimeDuration.of( newUserDuration, TimeDuration.Unit.SECONDS );
    }

    public TimeDuration getTokenDurationSMS( final Configuration configuration )
    {
        final long newUserDuration = readSettingAsLong( PwmSetting.NEWUSER_TOKEN_LIFETIME_SMS );
        if ( newUserDuration < 1 )
        {
            final long defaultDuration = configuration.readSettingAsLong( PwmSetting.TOKEN_LIFETIME );
            return TimeDuration.of( defaultDuration, TimeDuration.Unit.SECONDS );
        }
        return TimeDuration.of( newUserDuration, TimeDuration.Unit.SECONDS );
    }

    public static class NewUserProfileFactory implements ProfileFactory
    {
        @Override
        public Profile makeFromStoredConfiguration( final StoredConfiguration storedConfiguration, final String identifier )
        {
            return new NewUserProfile( identifier, makeValueMap( storedConfiguration, identifier, PROFILE_TYPE.getCategory() ) );
        }
    }
}

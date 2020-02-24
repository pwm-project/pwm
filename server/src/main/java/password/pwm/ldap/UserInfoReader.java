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

package password.pwm.ldap;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.SearchScope;
import password.pwm.PwmApplication;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.option.ForceSetupPolicy;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.profile.SetupOtpProfile;
import password.pwm.config.profile.UpdateProfileProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.PwmService;
import password.pwm.svc.pwnotify.PwNotifyUserStatus;
import password.pwm.util.PasswordData;
import password.pwm.util.form.FormUtility;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CachingProxyWrapper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.CrService;
import password.pwm.util.operations.OtpService;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.operations.otp.OTPUserRecord;
import password.pwm.util.password.PwmPasswordRuleValidator;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class UserInfoReader implements UserInfo
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserInfoReader.class );

    private final UserIdentity userIdentity;
    private final PasswordData currentPassword;
    private final Locale locale;

    private final ChaiUser chaiUser;
    private final SessionLabel sessionLabel;
    private final PwmApplication pwmApplication;

    /**
     * A reference to this object, but with memorized (cached) method implementations.  In most cases references to 'this'
     * inside this class should use this {@code selfCachedReference} instead.
     */
    private UserInfo selfCachedReference;

    private UserInfoReader(
            final UserIdentity userIdentity,
            final PasswordData currentPassword,
            final SessionLabel sessionLabel,
            final Locale locale,
            final PwmApplication pwmApplication,
            final ChaiProvider chaiProvider
    )
            throws ChaiUnavailableException
    {
        this.userIdentity = userIdentity;
        this.currentPassword = currentPassword;
        this.pwmApplication = pwmApplication;
        this.locale = locale;
        this.sessionLabel = sessionLabel;

        final ChaiProvider cachingProvider = CachingProxyWrapper.create( ChaiProvider.class, chaiProvider );
        this.chaiUser = cachingProvider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );
    }

    static UserInfo create(
            final UserIdentity userIdentity,
            final PasswordData currentPassword,
            final SessionLabel sessionLabel,
            final Locale locale,
            final PwmApplication pwmApplication,
            final ChaiProvider chaiProvider
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        LdapOperationsHelper.addConfiguredUserObjectClass( sessionLabel, userIdentity, pwmApplication );

        final UserInfoReader userInfo = new UserInfoReader( userIdentity, currentPassword, sessionLabel, locale, pwmApplication, chaiProvider );
        final UserInfo selfCachedReference = CachingProxyWrapper.create( UserInfo.class, userInfo );
        userInfo.selfCachedReference = selfCachedReference;
        return selfCachedReference;
    }

    @Override
    public Map<String, String> getCachedPasswordRuleAttributes( ) throws PwmUnrecoverableException
    {
        final Set<String> interestingUserAttributes = figurePasswordRuleAttributes( selfCachedReference );
        final Map<String, String> allUserAttrs = readStringAttributes( interestingUserAttributes );
        return Collections.unmodifiableMap( allUserAttrs );
    }

    @Override
    public Map<String, String> getCachedAttributeValues( ) throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = getUserIdentity().getLdapProfile( pwmApplication.getConfig() );
        final List<String> cachedAttributeNames = ldapProfile.readSettingAsStringArray( PwmSetting.CACHED_USER_ATTRIBUTES );
        if ( cachedAttributeNames != null && !cachedAttributeNames.isEmpty() )
        {
            final Map<String, String> attributeValues = readStringAttributes( new HashSet<>( cachedAttributeNames ) );
            return Collections.unmodifiableMap( attributeValues );
        }
        return Collections.emptyMap();
    }

    @Override
    public Instant getLastLdapLoginTime( ) throws PwmUnrecoverableException
    {
        try
        {
            return chaiUser.readLastLoginTime();
        }
        catch ( final ChaiOperationException e )
        {
            LOGGER.warn( sessionLabel, () -> "error reading user's last ldap login time: " + e.getMessage() );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        return null;
    }

    @Override
    public ChallengeProfile getChallengeProfile( ) throws PwmUnrecoverableException
    {
        final PwmPasswordPolicy pwmPasswordPolicy = selfCachedReference.getPasswordPolicy();
        final CrService crService = pwmApplication.getCrService();
        return crService.readUserChallengeProfile(
                sessionLabel,
                getUserIdentity(),
                chaiUser,
                pwmPasswordPolicy,
                locale
        );
    }

    @Override
    public PwmPasswordPolicy getPasswordPolicy( ) throws PwmUnrecoverableException
    {
        return PasswordUtility.readPasswordPolicyForUser( pwmApplication, sessionLabel, getUserIdentity(), chaiUser, locale );
    }

    @Override
    public UserIdentity getUserIdentity( )
    {
        return userIdentity;
    }

    @Override
    public Instant getPasswordExpirationTime( ) throws PwmUnrecoverableException
    {
        return LdapOperationsHelper.readPasswordExpirationTime( chaiUser );
    }

    @Override
    public String getUsername( ) throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = getUserIdentity().getLdapProfile( pwmApplication.getConfig() );
        final String uIDattr = ldapProfile.getUsernameAttribute();
        return readStringAttribute( uIDattr );
    }

    @Override
    public PasswordStatus getPasswordStatus( ) throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final PasswordStatus.PasswordStatusBuilder passwordStatusBuilder = PasswordStatus.builder();
        final String userDN = chaiUser.getEntryDN();
        final PwmPasswordPolicy passwordPolicy = selfCachedReference.getPasswordPolicy();

        final long startTime = System.currentTimeMillis();
        LOGGER.trace( sessionLabel, () -> "beginning password status check process for " + userDN );

        // check if password meets existing policy.
        if ( passwordPolicy.getRuleHelper().readBooleanValue( PwmPasswordRule.EnforceAtLogin ) )
        {
            if ( currentPassword != null )
            {
                try
                {
                    final PwmPasswordRuleValidator passwordRuleValidator = new PwmPasswordRuleValidator( pwmApplication, passwordPolicy );
                    passwordRuleValidator.testPassword( currentPassword, null, selfCachedReference, chaiUser );
                }
                catch ( final PwmDataValidationException | PwmUnrecoverableException e )
                {
                    LOGGER.debug( sessionLabel, () -> "user " + userDN + " password does not conform to current password policy ("
                            + e.getMessage() + "), marking as requiring change." );
                    passwordStatusBuilder.violatesPolicy( true );
                }
                catch ( final ChaiUnavailableException e )
                {
                    throw PwmUnrecoverableException.fromChaiException( e );
                }
            }
        }

        boolean ldapPasswordExpired = false;
        try
        {
            ldapPasswordExpired = chaiUser.isPasswordExpired();

            if ( ldapPasswordExpired )
            {
                LOGGER.trace( sessionLabel, () -> "password for " + userDN + " appears to be expired" );
            }
            else
            {
                LOGGER.trace( sessionLabel, () -> "password for " + userDN + " does not appear to be expired" );
            }
        }
        catch ( final ChaiOperationException e )
        {
            LOGGER.info( sessionLabel, () -> "error reading LDAP attributes for " + userDN + " while reading isPasswordExpired(): " + e.getMessage() );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }

        final Instant ldapPasswordExpirationTime = selfCachedReference.getPasswordExpirationTime();

        boolean preExpired = false;
        if ( ldapPasswordExpirationTime != null )
        {
            final TimeDuration expirationInterval = TimeDuration.fromCurrent( ldapPasswordExpirationTime );
            LOGGER.trace( sessionLabel, () -> "read password expiration time: "
                    + JavaHelper.toIsoDate( ldapPasswordExpirationTime )
                    + ", " + expirationInterval.asCompactString() + " from now"
            );
            final TimeDuration diff = TimeDuration.fromCurrent( ldapPasswordExpirationTime );

            // now check to see if the user's expire time is within the 'preExpireTime' setting.
            final long preExpireMs = config.readSettingAsLong( PwmSetting.PASSWORD_EXPIRE_PRE_TIME ) * 1000;
            if ( diff.asMillis() > 0 && diff.asMillis() < preExpireMs )
            {
                LOGGER.debug( sessionLabel, () -> "user " + userDN + " password will expire within "
                        + diff.asCompactString()
                        + ", marking as pre-expired" );
                preExpired = true;
            }
            else if ( ldapPasswordExpired )
            {
                preExpired = true;
                LOGGER.debug( sessionLabel, () -> "user " + userDN + " password is expired, marking as pre-expired." );
            }

            // now check to see if the user's expire time is within the 'preWarnTime' setting.
            final long preWarnMs = config.readSettingAsLong( PwmSetting.PASSWORD_EXPIRE_WARN_TIME ) * 1000;
            // don't check if the 'preWarnTime' setting is zero or less than the expirePreTime
            if ( !ldapPasswordExpired && !preExpired )
            {
                if ( !( preWarnMs == 0 || preWarnMs < preExpireMs ) )
                {
                    if ( diff.asMillis() > 0 && diff.asMillis() < preWarnMs )
                    {
                        LOGGER.debug( sessionLabel,
                                () -> "user " + userDN + " password will expire within "
                                        + diff.asCompactString()
                                        + ", marking as within warn period" );
                        passwordStatusBuilder.warnPeriod( true );
                    }
                }
            }

            passwordStatusBuilder.preExpired( preExpired );
        }

        LOGGER.debug( sessionLabel, () -> "completed user password status check for " + userDN + " " + passwordStatusBuilder
                + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")" );
        passwordStatusBuilder.expired( ldapPasswordExpired );
        return passwordStatusBuilder.build();
    }

    @Override
    public boolean isRequiresNewPassword( ) throws PwmUnrecoverableException
    {
        final PasswordStatus passwordStatus = selfCachedReference.getPasswordStatus();
        final List<UserPermission> updateProfilePermission = pwmApplication.getConfig().readSettingAsUserPermission(
                PwmSetting.QUERY_MATCH_CHANGE_PASSWORD );
        if ( !LdapPermissionTester.testUserPermissions( pwmApplication, sessionLabel, userIdentity, updateProfilePermission ) )
        {
            LOGGER.debug( sessionLabel,
                    () -> "checkPassword: " + userIdentity.toString() + " user does not have permission to change password" );
            return false;
        }

        if ( passwordStatus.isExpired() )
        {
            LOGGER.debug( sessionLabel, () -> "checkPassword: password is expired, marking new password as required" );
            return true;
        }

        if ( passwordStatus.isPreExpired() )
        {
            LOGGER.debug( sessionLabel, () -> "checkPassword: password is pre-expired, marking new password as required" );
            return true;
        }

        if ( passwordStatus.isWarnPeriod() )
        {
            LOGGER.debug( sessionLabel, () -> "checkPassword: password is within warn period, marking new password as required" );
            return true;
        }

        if ( passwordStatus.isViolatesPolicy() )
        {
            LOGGER.debug( sessionLabel, () -> "checkPassword: current password violates password policy, marking new password as required" );
            return true;
        }

        return false;
    }

    @Override
    public boolean isAccountEnabled( ) throws PwmUnrecoverableException
    {
        try
        {
            return chaiUser.isAccountEnabled();
        }
        catch ( final ChaiException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    @Override
    public boolean isAccountExpired( ) throws PwmUnrecoverableException
    {
        try
        {
            return chaiUser.isAccountExpired();
        }
        catch ( final ChaiException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    @Override
    public boolean isPasswordLocked( ) throws PwmUnrecoverableException
    {
        try
        {
            return chaiUser.isPasswordLocked();
        }
        catch ( final ChaiException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    @Override
    public boolean isRequiresResponseConfig( ) throws PwmUnrecoverableException
    {
        final CrService crService = pwmApplication.getCrService();
        try
        {
            return crService.checkIfResponseConfigNeeded(
                    pwmApplication,
                    sessionLabel,
                    getUserIdentity(),
                    selfCachedReference.getChallengeProfile().getChallengeSet(),
                    selfCachedReference.getResponseInfoBean() );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    @Override
    public boolean isRequiresOtpConfig( ) throws PwmUnrecoverableException
    {
        LOGGER.trace( sessionLabel, () ->  "checkOtp: beginning process to check if user OTP setup is required" );

        SetupOtpProfile setupOtpProfile = null;
        final Map<ProfileDefinition, String> profileIDs = selfCachedReference.getProfileIDs();
        if ( profileIDs.containsKey( ProfileDefinition.UpdateAttributes ) )
        {
            setupOtpProfile = pwmApplication.getConfig().getSetupOTPProfiles().get( profileIDs.get( ProfileDefinition.SetupOTPProfile ) );
        }

        if ( setupOtpProfile == null )
        {
            LOGGER.trace( sessionLabel, () -> "checkOtp: no otp setup profile assigned, user OTP setup is not required" );
            return false;
        }

        if ( !setupOtpProfile.readSettingAsBoolean( PwmSetting.OTP_ALLOW_SETUP ) )
        {
            LOGGER.trace( sessionLabel, () -> "checkOtp: OTP allow setup is not enabled" );
            return false;
        }

        final ForceSetupPolicy policy = setupOtpProfile.readSettingAsEnum( PwmSetting.OTP_FORCE_SETUP, ForceSetupPolicy.class );

        if ( policy == ForceSetupPolicy.SKIP )
        {
            LOGGER.trace( sessionLabel, () -> "checkOtp: OTP force setup policy is set to SKIP, user OTP setup is not required" );
            return false;
        }

        final OTPUserRecord otpUserRecord = selfCachedReference.getOtpUserRecord();
        final boolean hasStoredOtp = otpUserRecord != null && otpUserRecord.getSecret() != null;

        if ( hasStoredOtp )
        {
            LOGGER.trace( sessionLabel, () -> "checkOtp: user has existing valid otp record, user OTP setup is not required" );
            return false;
        }

        // hasStoredOtp is always true at this point, so if forced then update needed
        LOGGER.debug( sessionLabel, () -> "checkOtp: user does not have existing valid otp record, user OTP setup is required" );
        return policy == ForceSetupPolicy.FORCE || policy == ForceSetupPolicy.FORCE_ALLOW_SKIP;
    }

    @Override
    public boolean isRequiresUpdateProfile( ) throws PwmUnrecoverableException
    {
        final Configuration configuration = pwmApplication.getConfig();

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.UPDATE_PROFILE_ENABLE ) )
        {
            LOGGER.debug( sessionLabel, () -> "checkProfiles: " + userIdentity.toString() + " profile module is not enabled" );
            return false;
        }

        UpdateProfileProfile updateProfileProfile = null;
        final Map<ProfileDefinition, String> profileIDs = selfCachedReference.getProfileIDs();
        if ( profileIDs.containsKey( ProfileDefinition.UpdateAttributes ) )
        {
            updateProfileProfile = configuration.getUpdateAttributesProfile().get( profileIDs.get( ProfileDefinition.UpdateAttributes ) );
        }

        if ( updateProfileProfile == null )
        {
            return false;
        }

        if ( !updateProfileProfile.readSettingAsBoolean( PwmSetting.UPDATE_PROFILE_FORCE_SETUP ) )
        {
            LOGGER.debug( sessionLabel, () -> "checkProfiles: " + userIdentity.toString() + " profile force setup is not enabled" );
            return false;
        }

        final List<FormConfiguration> updateFormFields = updateProfileProfile.readSettingAsForm( PwmSetting.UPDATE_PROFILE_FORM );

        // populate the map from ldap

        try
        {
            final Map<FormConfiguration, List<String>> valueMap = FormUtility.populateFormMapFromLdap(
                    updateFormFields,
                    sessionLabel,
                    selfCachedReference,
                    FormUtility.Flag.ReturnEmptyValues
            );
            final Map<FormConfiguration, String> singleValueMap = FormUtility.multiValueMapToSingleValue( valueMap );
            FormUtility.validateFormValues( configuration, singleValueMap, locale );
            LOGGER.debug( sessionLabel, () -> "checkProfile: " + userIdentity + " has value for attributes, update profile will not be required" );
            return false;
        }
        catch ( final PwmDataValidationException e )
        {
            LOGGER.debug( sessionLabel, () -> "checkProfile: " + userIdentity + " does not have good attributes (" + e.getMessage() + "), update profile will be required" );
            return true;
        }
        catch ( final PwmUnrecoverableException e )
        {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public Instant getPasswordLastModifiedTime( ) throws PwmUnrecoverableException
    {
        try
        {
            return PasswordUtility.determinePwdLastModified( pwmApplication, sessionLabel, userIdentity );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    @Override
    public String getUserEmailAddress( ) throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = getUserIdentity().getLdapProfile( pwmApplication.getConfig() );
        final String ldapEmailAttribute = ldapProfile.readSettingAsString( PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE );
        return readStringAttribute( ldapEmailAttribute );
    }

    @Override
    public String getUserEmailAddress2( ) throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = getUserIdentity().getLdapProfile( pwmApplication.getConfig() );
        final String ldapEmailAttribute = ldapProfile.readSettingAsString( PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE_2 );
        return readStringAttribute( ldapEmailAttribute );
    }

    @Override
    public String getUserEmailAddress3( ) throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = getUserIdentity().getLdapProfile( pwmApplication.getConfig() );
        final String ldapEmailAttribute = ldapProfile.readSettingAsString( PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE_3 );
        return readStringAttribute( ldapEmailAttribute );
    }

    @Override
    public String getUserSmsNumber( ) throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = getUserIdentity().getLdapProfile( pwmApplication.getConfig() );
        final String ldapSmsAttribute = ldapProfile.readSettingAsString( PwmSetting.SMS_USER_PHONE_ATTRIBUTE );
        return readStringAttribute( ldapSmsAttribute );
    }

    @Override
    public String getUserSmsNumber2( ) throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = getUserIdentity().getLdapProfile( pwmApplication.getConfig() );
        final String ldapSmsAttribute = ldapProfile.readSettingAsString( PwmSetting.SMS_USER_PHONE_ATTRIBUTE_2 );
        return readStringAttribute( ldapSmsAttribute );
    }

    @Override
    public String getUserSmsNumber3( ) throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = getUserIdentity().getLdapProfile( pwmApplication.getConfig() );
        final String ldapSmsAttribute = ldapProfile.readSettingAsString( PwmSetting.SMS_USER_PHONE_ATTRIBUTE_3 );
        return readStringAttribute( ldapSmsAttribute );
    }

    @Override
    public String getUserGuid( ) throws PwmUnrecoverableException
    {
        return LdapOperationsHelper.readLdapGuidValue( pwmApplication, sessionLabel, userIdentity, false );
    }

    @Override
    public ResponseInfoBean getResponseInfoBean( ) throws PwmUnrecoverableException
    {
        final CrService crService = pwmApplication.getCrService();
        try
        {
            return crService.readUserResponseInfo( sessionLabel, getUserIdentity(), chaiUser );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    @Override
    public OTPUserRecord getOtpUserRecord( ) throws PwmUnrecoverableException
    {
        final OtpService otpService = pwmApplication.getOtpService();
        if ( otpService != null && otpService.status() == PwmService.STATUS.OPEN )
        {
            try
            {
                return otpService.readOTPUserConfiguration( sessionLabel, userIdentity );
            }
            catch ( final ChaiUnavailableException e )
            {
                throw PwmUnrecoverableException.fromChaiException( e );
            }
        }
        return null;
    }

    @Override
    public Instant getAccountExpirationTime( ) throws PwmUnrecoverableException
    {
        try
        {
            return chaiUser.readAccountExpirationDate();
        }
        catch ( final ChaiOperationException e )
        {
            LOGGER.warn( sessionLabel, () -> "error reading user's account expiration time: " + e.getMessage() );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        return null;
    }

    @Override
    public Map<ProfileDefinition, String> getProfileIDs( ) throws PwmUnrecoverableException
    {
        final Map<ProfileDefinition, String> returnMap = new HashMap<>();
        for ( final ProfileDefinition profileDefinition : ProfileDefinition.values() )
        {
            if ( profileDefinition.isAuthenticated() )
            {
                final String profileID = ProfileUtility.discoverProfileIDforUser( pwmApplication, sessionLabel, userIdentity, profileDefinition );
                returnMap.put( profileDefinition, profileID );
                if ( profileID != null )
                {
                    LOGGER.debug( sessionLabel, () -> "assigned " + profileDefinition.toString() + " profileID \"" + profileID + "\" to " + userIdentity.toDisplayString() );
                }
                else
                {
                    LOGGER.debug( sessionLabel, () -> profileDefinition.toString() + " has no matching profiles for user " + userIdentity.toDisplayString() );
                }
            }
        }
        return Collections.unmodifiableMap( returnMap );
    }

    private static Set<String> figurePasswordRuleAttributes(
            final UserInfo uiBean
    ) throws PwmUnrecoverableException
    {
        final Set<String> interestingUserAttributes = new HashSet<>();
        interestingUserAttributes.addAll( uiBean.getPasswordPolicy().getRuleHelper().getDisallowedAttributes() );
        if ( uiBean.getPasswordPolicy().getRuleHelper().getADComplexityLevel() == ADPolicyComplexity.AD2003
                || uiBean.getPasswordPolicy().getRuleHelper().getADComplexityLevel() == ADPolicyComplexity.AD2008 )
        {
            interestingUserAttributes.add( "sAMAccountName" );
            interestingUserAttributes.add( "displayName" );
            interestingUserAttributes.add( "fullname" );
            interestingUserAttributes.add( "cn" );
        }
        return interestingUserAttributes;
    }

    private final Map<String, List<String>> cacheMap = new HashMap<>();

    @Override
    public String readStringAttribute(
            final String attribute
    )
            throws PwmUnrecoverableException
    {
        final Map<String, String> results = readStringAttributes( Collections.singletonList( attribute ) );
        if ( results == null || results.isEmpty() )
        {
            return null;
        }

        return results.values().iterator().next();
    }

    @Override
    public byte[] readBinaryAttribute(
            final String attribute
    )
            throws PwmUnrecoverableException
    {
        try
        {
            final byte[][] value = chaiUser.readMultiByteAttribute( attribute );
            if ( value != null && value.length > 0 )
            {
                return value[0];
            }
        }
        catch ( final ChaiException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }

        return null;
    }

    @Override
    public Instant readDateAttribute( final String attribute )
            throws PwmUnrecoverableException
    {
        try
        {
            return chaiUser.readDateAttribute( attribute );
        }
        catch ( final ChaiException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    @Override
    public List<String> readMultiStringAttribute( final String attribute )
            throws PwmUnrecoverableException
    {
        return readMultiStringAttributesImpl( Collections.singletonList( attribute ) ).get( attribute );
    }

    @Override
    public Map<String, String> readStringAttributes(
            final Collection<String> attributes
    )
            throws PwmUnrecoverableException
    {
        final Map<String, List<String>> valueMap = readMultiStringAttributesImpl( attributes );
        final Map<String, String> returnValue = new LinkedHashMap<>();
        for ( final Map.Entry<String, List<String>> entry : valueMap.entrySet() )
        {
            final String key = entry.getKey();
            final List<String> values = entry.getValue();
            if ( values != null && !values.isEmpty() )
            {
                returnValue.put( key, values.iterator().next() );
            }
        }
        return returnValue;
    }

    private Map<String, List<String>> readMultiStringAttributesImpl(
            final Collection<String> attributes
    )
            throws PwmUnrecoverableException
    {
        if ( chaiUser == null || attributes == null || attributes.isEmpty() )
        {
            return Collections.emptyMap();
        }

        // figure out uncached attributes.
        final Set<String> uncachedAttributes = new HashSet<>( attributes );
        uncachedAttributes.removeAll( cacheMap.keySet() );

        // read uncached attributes into cache
        if ( !uncachedAttributes.isEmpty() )
        {
            final Map<String, Map<String, List<String>>> results;
            try
            {
                results = chaiUser.getChaiProvider().searchMultiValues(
                        chaiUser.getEntryDN(),
                        "(objectclass=*)",
                        uncachedAttributes,
                        SearchScope.BASE
                );
            }
            catch ( final ChaiOperationException e )
            {
                final String msg = "ldap operational error while reading user data" + e.getMessage();
                LOGGER.error( sessionLabel, () -> msg );
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_LDAP_DATA_ERROR, msg ) );
            }
            catch ( final ChaiUnavailableException e )
            {
                throw PwmUnrecoverableException.fromChaiException( e );
            }

            if ( results == null || results.size() != 1 )
            {
                final String msg = "ldap server did not return requested user entry "
                        + chaiUser.getEntryDN()
                        + " while attempting to read attribute data";
                LOGGER.error( sessionLabel, () -> msg );
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_LDAP_DATA_ERROR, msg ) );
            }

            final Map<String, List<String>> allAttributeValues = results.values().iterator().next();
            for ( final String attribute : uncachedAttributes )
            {
                final List<String> attributeValues = allAttributeValues.get( attribute );
                if ( attributeValues == null )
                {
                    cacheMap.put( attribute, Collections.emptyList() );
                }
                else
                {
                    cacheMap.put( attribute, Collections.unmodifiableList( attributeValues ) );
                }
            }
        }

        // build result data from cache
        final Map<String, List<String>> returnMap = new HashMap<>();
        for ( final String attribute : attributes )
        {
            final List<String> cachedValue = cacheMap.get( attribute );
            returnMap.put( attribute, cachedValue );
        }
        return Collections.unmodifiableMap( returnMap );
    }

    @Override
    public boolean isRequiresInteraction( ) throws PwmUnrecoverableException
    {
        return selfCachedReference.isRequiresNewPassword()
                || selfCachedReference.isRequiresResponseConfig()
                || selfCachedReference.isRequiresUpdateProfile()
                || selfCachedReference.isRequiresOtpConfig()
                || selfCachedReference.getPasswordStatus().isWarnPeriod();
    }

    @Override
    public boolean isWithinPasswordMinimumLifetime( ) throws PwmUnrecoverableException
    {
        return PasswordUtility.isPasswordWithinMinimumLifetimeImpl(
                this.chaiUser,
                this.sessionLabel,
                this.getPasswordPolicy(),
                this.getPasswordLastModifiedTime(),
                this.getPasswordStatus()
        );
    }

    @Override
    public String getLanguage() throws PwmUnrecoverableException
    {
        return locale == null ? null : LocaleHelper.getBrowserLocaleString( locale );
    }

    @Override
    public Instant getPasswordExpirationNoticeSendTime()
            throws PwmUnrecoverableException
    {
        if ( pwmApplication.getPwNotifyService().status() == PwmService.STATUS.OPEN )
        {
            final Optional<PwNotifyUserStatus> optionalState = pwmApplication.getPwNotifyService().readUserNotificationState( userIdentity, sessionLabel );
            if ( optionalState.isPresent() )
            {
                return optionalState.get().getExpireTime();
            }
        }
        return null;
    }

    @Override
    public String toString()
    {
        return "UserInfoReader: " + this.getUserIdentity().toDisplayString();
    }
}

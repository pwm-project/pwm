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

package password.pwm.http.servlet.helpdesk;

import com.novell.ldapchai.ChaiPasswordRule;
import com.novell.ldapchai.cr.Challenge;
import lombok.AccessLevel;
import lombok.Builder;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.HelpdeskUIMode;
import password.pwm.config.option.ViewStatusFields;
import password.pwm.config.profile.AccountInformationProfile;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.DisplayElement;
import password.pwm.http.servlet.accountinfo.AccountInformationBean;
import password.pwm.i18n.Display;
import password.pwm.ldap.ViewableUserInfoDisplayReader;
import password.pwm.user.UserInfo;
import password.pwm.util.form.FormUtility;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.PasswordRequirementViewableRuleGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public record HelpdeskUserDetail(
        String userKey,
        List<DisplayElement> userHistory,
        List<DisplayElement> passwordPolicyRules,
        List<String> passwordRequirements,
        String passwordPolicyDN,
        String passwordPolicyID,
        List<DisplayElement> statusData,
        List<DisplayElement> profileData,
        List<DisplayElement> helpdeskResponses,
        Set<HelpdeskDetailButton> visibleButtons,
        Set<HelpdeskDetailButton> enabledButtons,
        HelpdeskVerificationOptions verificationOptions
)
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskUserDetail.class );

    @Builder( access = AccessLevel.PRIVATE )
    public HelpdeskUserDetail
    {
    }

    static HelpdeskUserDetail makeHelpdeskDetailInfo(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final HelpdeskProfile helpdeskProfile = pwmRequest.getHelpdeskProfile();
        LOGGER.trace( pwmRequest, () -> "beginning to assemble detail data info for user " + userIdentity );

        final HelpdeskUserDetailBuilder builder = HelpdeskUserDetail.builder();

        final UserInfo userInfo = HelpdeskServletUtil.getTargetUserInfo( pwmRequest, userIdentity );

        final MacroRequest macroRequest = MacroRequest.forUser( pwmRequest.getPwmApplication(), pwmRequest.getLabel(), userInfo, null );

        builder.userHistory( makeUserHistory( pwmRequest, userInfo ) );

        builder.userKey( HelpdeskServletUtil.obfuscateUserIdentity( pwmRequest, userIdentity ) );

        builder.profileData( getProfileData( helpdeskProfile, userInfo, pwmRequest.getLabel(), pwmRequest.getLocale() ) );

        builder.passwordPolicyRules( makePasswordPolicyRules( userInfo, pwmRequest.getLocale(), pwmRequest.getDomainConfig() ) );

        {
            final List<String> requirementLines = PasswordRequirementViewableRuleGenerator.generate(
                    userInfo.getPasswordPolicy(),
                    pwmRequest.getDomainConfig(),
                    pwmRequest.getLocale(),
                    macroRequest
            );
            builder.passwordRequirements( requirementLines );
        }

        if ( ( userInfo.getPasswordPolicy() != null )
                && ( userInfo.getPasswordPolicy().getChaiPasswordPolicy() != null )
                && ( userInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry() != null )
                && ( userInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry().getEntryDN() != null ) )
        {
            builder.passwordPolicyDN( userInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry().getEntryDN() );
        }
        else
        {
            builder.passwordPolicyDN( LocaleHelper.getLocalizedMessage( Display.Value_NotApplicable, pwmRequest ) );
        }

        if ( ( userInfo.getPasswordPolicy() != null )
                && userInfo.getPasswordPolicy().getId() != null )
        {
            builder.passwordPolicyID( userInfo.getPasswordPolicy().getId().toString() );
        }
        else
        {
            builder.passwordPolicyID( LocaleHelper.getLocalizedMessage( Display.Value_NotApplicable, pwmRequest ) );
        }

        builder.helpdeskResponses = makeHelpdeskResponses( userInfo );

        {
            final Set<ViewStatusFields> viewStatusFields = helpdeskProfile.readSettingAsOptionList( PwmSetting.HELPDESK_VIEW_STATUS_VALUES, ViewStatusFields.class );
            builder.statusData( ViewableUserInfoDisplayReader.makeDisplayData(
                    viewStatusFields,
                    pwmRequest.getDomainConfig(),
                    userInfo,
                    null,
                    pwmRequest.getLocale()
            ) );
        }

        {
            final Set<HelpdeskDetailButton> visibleButtons = determineVisibleButtons( helpdeskProfile );
            builder.visibleButtons( visibleButtons );
            builder.enabledButtons( determineEnabledButtons( visibleButtons, userInfo ) );
        }

        builder.verificationOptions( HelpdeskVerificationOptions.fromConfig( pwmRequest, helpdeskProfile, userIdentity ) );

        final HelpdeskUserDetail helpdeskDetailInfoBean = builder.build();

        if ( pwmRequest.getAppConfig().isDevDebugMode() )
        {
            LOGGER.trace( pwmRequest, () -> "completed assembly of detail data info for user " + userIdentity
                            + ", contents: " + JsonFactory.get().serialize( helpdeskDetailInfoBean ),
                    TimeDuration.fromCurrent( startTime ) );
        }

        return builder.build();
    }

    private static List<DisplayElement> makeHelpdeskResponses(
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final ResponseInfoBean responseInfoBean = userInfo.getResponseInfoBean();
        if ( responseInfoBean == null )
        {
            return List.of();
        }

        final Map<Challenge, String> helpdeskCrMap = responseInfoBean.getHelpdeskCrMap();
        if ( helpdeskCrMap == null )
        {
            return List.of();
        }

        final List<DisplayElement> responseDisplay = new ArrayList<>(  helpdeskCrMap.size() );
        int counter = 0;

        for ( final Map.Entry<Challenge, String> entry : helpdeskCrMap.entrySet() )
        {
            counter++;
            responseDisplay.add( DisplayElement.create(
                    "item_" + counter,
                    DisplayElement.Type.string,
                    entry.getKey().getChallengeText(),
                    entry.getValue()
            ) );
        }

        return List.copyOf( responseDisplay );
    }

    private static List<DisplayElement> makeUserHistory(
            final PwmRequest pwmRequest,
            final UserInfo userInfo
    )
    {
        try
        {
            final AccountInformationProfile accountInformationProfile = pwmRequest.getAccountInfoProfile();

            final List<AccountInformationBean.ActivityRecord> userHistory = AccountInformationBean.makeAuditInfo(
                    pwmRequest.getPwmDomain(),
                    accountInformationProfile,
                    pwmRequest.getLabel(),
                    userInfo,
                    pwmRequest.getLocale() );

            final AtomicInteger counter = new AtomicInteger();

            return userHistory.stream()
                    .map( record -> DisplayElement.create(
                            String.valueOf( counter.incrementAndGet() ),
                            DisplayElement.Type.timestamp,
                            record.getLabel(),
                            record.getTimestamp().toString() ) )
                    .toList();

        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "unexpected error reading userHistory for user '"
                    + userInfo.getUserIdentity() + "', " + e.getMessage() );
        }

        return List.of();
    }

    private static Set<HelpdeskDetailButton> determineVisibleButtons(
            final HelpdeskProfile helpdeskProfile
    )
    {
        final Set<HelpdeskDetailButton> buttons = EnumSet.noneOf( HelpdeskDetailButton.class );

        {
            final HelpdeskUIMode uiMode =
                    helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_SET_PASSWORD_MODE, HelpdeskUIMode.class );
            if ( uiMode != HelpdeskUIMode.none )
            {
                buttons.add( HelpdeskDetailButton.changePassword );
            }
        }

        if ( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE_UNLOCK ) )
        {
            buttons.add( HelpdeskDetailButton.unlock );
        }

        if ( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_CLEAR_RESPONSES_BUTTON ) )
        {
            buttons.add( HelpdeskDetailButton.clearResponses );
        }

        if ( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_CLEAR_OTP_BUTTON ) )
        {
            buttons.add( HelpdeskDetailButton.clearOtpSecret );
        }

        if ( !helpdeskProfile.readOptionalVerificationMethods().isEmpty() )
        {
            buttons.add( HelpdeskDetailButton.verification );
        }

        if ( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_DELETE_USER_BUTTON ) )
        {
            buttons.add( HelpdeskDetailButton.deleteUser );
        }

        if ( helpdeskProfile.readSettingAsAction( PwmSetting.HELPDESK_ACTIONS ).isEmpty() )
        {
            buttons.add( HelpdeskDetailButton.executeAction );
        }

        return Collections.unmodifiableSet( buttons );
    }

    private static Set<HelpdeskDetailButton> determineEnabledButtons(
            final Set<HelpdeskDetailButton> visibleButtons,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final Set<HelpdeskDetailButton> buttons = EnumUtil.copyToEnumSet( visibleButtons, HelpdeskDetailButton.class );

        if ( buttons.contains( HelpdeskDetailButton.unlock ) )
        {
            final boolean enabled = userInfo.isPasswordLocked();
            if ( !enabled )
            {
                buttons.remove( HelpdeskDetailButton.unlock );
            }
        }

        if ( buttons.contains( HelpdeskDetailButton.clearResponses ) )
        {
            final boolean enabled = userInfo.getResponseInfoBean() != null;
            if ( !enabled )
            {
                buttons.remove( HelpdeskDetailButton.clearResponses );
            }
        }

        if ( buttons.contains( HelpdeskDetailButton.clearOtpSecret ) )
        {
            final boolean enabled = userInfo.getOtpUserRecord() != null;
            if ( !enabled )
            {
                buttons.remove( HelpdeskDetailButton.clearOtpSecret );
            }
        }

        return Collections.unmodifiableSet( buttons );
    }


    private static List<DisplayElement> getProfileData(
            final HelpdeskProfile helpdeskProfile,
            final UserInfo userInfo,
            final SessionLabel sessionLabel,
            final Locale actorLocale
    )
            throws PwmUnrecoverableException
    {
        final List<FormConfiguration> detailFormConfig = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_DETAIL_FORM );
        final Map<FormConfiguration, List<String>> formData = FormUtility.populateFormMapFromLdap( detailFormConfig, sessionLabel, userInfo );
        final List<DisplayElement> profileData = new ArrayList<>();
        for ( final Map.Entry<FormConfiguration, List<String>> entry : formData.entrySet() )
        {
            final FormConfiguration formConfiguration = entry.getKey();
            if ( formConfiguration.isMultivalue() )
            {
                profileData.add( DisplayElement.createMultiValue(
                        formConfiguration.getName(),
                        DisplayElement.Type.multiString,
                        formConfiguration.getLabel( actorLocale ),
                        normalizeMultiValues( entry.getValue() )
                ) );
            }
            else
            {
                profileData.add( DisplayElement.create(
                        formConfiguration.getName(),
                        DisplayElement.Type.string,
                        formConfiguration.getLabel( actorLocale ),
                        normalizeStringValue( entry.getValue() )
                ) );
            }
        }
        return profileData;
    }

    private static List<String> normalizeMultiValues( final List<String> values )
    {
        final List<String> multiValues = CollectionUtil.stripNulls( values )
                .stream().filter( s -> !StringUtil.isEmpty( s ) )
                .toList();

        return CollectionUtil.isEmpty( multiValues )
                ? List.of( "" )
                : multiValues;
    }

    private static String normalizeStringValue( final List<String> values )
    {
        final List<String> multiValues = CollectionUtil.stripNulls( values )
                .stream().filter( s -> !StringUtil.isEmpty( s ) )
                .toList();

        return CollectionUtil.isEmpty( multiValues )
                ? ""
                : values.iterator().next();
    }

    private static List<DisplayElement> makePasswordPolicyRules(
            final UserInfo userInfo,
            final Locale locale,
            final DomainConfig domainConfig
    )
            throws PwmUnrecoverableException
    {
        final List<DisplayElement> passwordRules = new ArrayList<>();
        if ( userInfo.getPasswordPolicy() != null )
        {
            for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
            {
                if ( userInfo.getPasswordPolicy().getValue( rule ) != null )
                {
                    if ( ChaiPasswordRule.RuleType.BOOLEAN == rule.getRuleType() )
                    {
                        final boolean value = Boolean.parseBoolean( userInfo.getPasswordPolicy().getValue( rule ) );
                        final String sValue = LocaleHelper.booleanString( value, locale, domainConfig );
                        passwordRules.add( DisplayElement.create(
                                rule.getKey(),
                                DisplayElement.Type.string,
                                rule.getLabel( locale, domainConfig ),
                                sValue ) );
                    }
                    else
                    {
                        final String sValue = userInfo.getPasswordPolicy().getValue( rule );
                        if ( !StringUtil.isEmpty( sValue ) )
                        {
                            passwordRules.add( DisplayElement.create(
                                    rule.getKey(),
                                    DisplayElement.Type.string,
                                    rule.getLabel( locale, domainConfig ),
                                    sValue ) );
                        }
                    }
                }
            }
        }
        return List.copyOf( passwordRules );
    }
}



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

package password.pwm.http.servlet.helpdesk;

import com.novell.ldapchai.ChaiPasswordRule;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Builder;
import lombok.Value;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.HelpdeskUIMode;
import password.pwm.config.option.ViewStatusFields;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.DisplayElement;
import password.pwm.http.servlet.accountinfo.AccountInformationBean;
import password.pwm.http.tag.PasswordRequirementsTag;
import password.pwm.i18n.Display;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.ViewableUserInfoDisplayReader;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Value
@Builder
public class HelpdeskDetailInfoBean implements Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskDetailInfoBean.class );

    private String userKey;

    private String userDisplayName;

    private List<AccountInformationBean.ActivityRecord> userHistory;

    private Map<String, String> passwordPolicyRules;
    private List<String> passwordRequirements;
    private String passwordPolicyDN;
    private String passwordPolicyID;

    private List<DisplayElement> statusData;
    private List<DisplayElement> profileData;
    private List<DisplayElement> helpdeskResponses;

    private Set<StandardButton> visibleButtons;
    private Set<StandardButton> enabledButtons;

    private HelpdeskVerificationOptionsBean verificationOptions;
    
    public enum StandardButton
    {
        back,
        refresh,
        changePassword,
        unlock,
        clearResponses,
        clearOtpSecret,
        verification,
        deleteUser,
    }

     static HelpdeskDetailInfoBean makeHelpdeskDetailInfo(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final HelpdeskDetailInfoBeanBuilder builder = HelpdeskDetailInfoBean.builder();
        final Instant startTime = Instant.now();
        LOGGER.trace( pwmRequest, () -> "beginning to assemble detail data report for user " + userIdentity );
        final Locale actorLocale = pwmRequest.getLocale();
        final ChaiUser theUser = HelpdeskServlet.getChaiUser( pwmRequest, helpdeskProfile, userIdentity );

        if ( !theUser.exists() )
        {
            return null;
        }

        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                actorLocale,
                userIdentity,
                theUser.getChaiProvider()
        );
        final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest.getPwmApplication(), pwmRequest.getLabel(), userInfo, null );

        try
        {
            final List<AccountInformationBean.ActivityRecord> userHistory = AccountInformationBean.makeAuditInfo(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLabel(),
                    userInfo,
                    pwmRequest.getLocale() );
            builder.userHistory( userHistory );
        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "unexpected error reading userHistory for user '" + userIdentity + "', " + e.getMessage() );
        }

        builder.userKey( userIdentity.toObfuscatedKey( pwmRequest.getPwmApplication() ) );

        builder.profileData( getProfileData( helpdeskProfile, userInfo, pwmRequest.getLabel(), pwmRequest.getLocale() ) );

        builder.passwordPolicyRules( makePasswordPolicyRules( userInfo, pwmRequest.getLocale(), pwmRequest.getConfig() ) );

        {
            final List<String> requirementLines = PasswordRequirementsTag.getPasswordRequirementsStrings(
                    userInfo.getPasswordPolicy(),
                    pwmRequest.getConfig(),
                    pwmRequest.getLocale(),
                    macroMachine
            );
            builder.passwordRequirements( Collections.unmodifiableList( requirementLines ) );
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
                && userInfo.getPasswordPolicy().getIdentifier() != null )
        {
            builder.passwordPolicyID( userInfo.getPasswordPolicy().getIdentifier() );
        }
        else
        {
            builder.passwordPolicyID( LocaleHelper.getLocalizedMessage( Display.Value_NotApplicable, pwmRequest ) );
        }

        {
            final ResponseInfoBean responseInfoBean = userInfo.getResponseInfoBean();
            if ( responseInfoBean != null && responseInfoBean.getHelpdeskCrMap() != null )
            {
                final List<DisplayElement> responseDisplay = new ArrayList<>();
                int counter = 0;
                for ( final Map.Entry<Challenge, String> entry : responseInfoBean.getHelpdeskCrMap().entrySet() )
                {
                    counter++;
                    responseDisplay.add( new DisplayElement(
                            "item_" + counter,
                            DisplayElement.Type.string,
                            entry.getKey().getChallengeText(),
                            entry.getValue()
                    ) );
                }
                builder.helpdeskResponses = responseDisplay;
            }

        }

        builder.userDisplayName( HelpdeskCardInfoBean.figureDisplayName( helpdeskProfile, macroMachine ) );

        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );

        {
            final Set<ViewStatusFields> viewStatusFields = helpdeskProfile.readSettingAsOptionList( PwmSetting.HELPDESK_VIEW_STATUS_VALUES, ViewStatusFields.class );
            builder.statusData( ViewableUserInfoDisplayReader.makeDisplayData(
                    viewStatusFields,
                    pwmRequest.getConfig(),
                    userInfo,
                    null,
                    pwmRequest.getLocale()
            ) );
        }

        {
            final Set<HelpdeskDetailInfoBean.StandardButton> visibleButtons = determineVisibleButtons( helpdeskProfile );
            builder.visibleButtons( visibleButtons );
            builder.enabledButtons( determineEnabledButtons( visibleButtons, userInfo ) );
        }

        builder.verificationOptions( HelpdeskVerificationOptionsBean.makeBean( pwmRequest, helpdeskProfile, userIdentity ) );

        final HelpdeskDetailInfoBean helpdeskDetailInfoBean = builder.build();

        if ( pwmRequest.getConfig().isDevDebugMode() )
        {
            LOGGER.trace( pwmRequest, () -> "completed assembly of detail data report for user " + userIdentity
                    + " in " + timeDuration.asCompactString() + ", contents: " + JsonUtil.serialize( helpdeskDetailInfoBean ) );
        }

        return builder.build();
    }

    private static Set<StandardButton> determineVisibleButtons(
            final HelpdeskProfile helpdeskProfile
    )
    {
        final Set<StandardButton> buttons = new LinkedHashSet<>();

        buttons.add( StandardButton.refresh );
        buttons.add( StandardButton.back );

        {
            final HelpdeskUIMode uiMode =
                    helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_SET_PASSWORD_MODE, HelpdeskUIMode.class );
            if ( uiMode != HelpdeskUIMode.none )
            {
                buttons.add( StandardButton.changePassword );
            }
        }

        if ( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE_UNLOCK ) )
        {
            buttons.add( StandardButton.unlock );
        }

        if ( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_CLEAR_RESPONSES_BUTTON ) )
        {
            buttons.add( StandardButton.clearResponses );
        }

        if ( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_CLEAR_OTP_BUTTON ) )
        {
            buttons.add( StandardButton.clearOtpSecret );
        }

        if ( !helpdeskProfile.readOptionalVerificationMethods().isEmpty() )
        {
            buttons.add( StandardButton.verification );
        }

        if ( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_DELETE_USER_BUTTON ) )
        {
            buttons.add( StandardButton.deleteUser );
        }

        return Collections.unmodifiableSet( buttons );
    }

    private static Set<StandardButton> determineEnabledButtons(
            final Set<StandardButton> visibleButtons,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final Set<StandardButton> buttons = new LinkedHashSet<>( visibleButtons );

        if ( buttons.contains( StandardButton.unlock ) )
        {
            final boolean enabled = userInfo.isPasswordLocked();
            if ( !enabled )
            {
                buttons.remove( StandardButton.unlock );
            }
        }

        if ( buttons.contains( StandardButton.clearResponses ) )
        {
            final boolean enabled = userInfo.getResponseInfoBean() != null;
            if ( !enabled )
            {
                buttons.remove( StandardButton.clearResponses );
            }
        }

        if ( buttons.contains( StandardButton.clearOtpSecret ) )
        {
            final boolean enabled = userInfo.getOtpUserRecord() != null;
            if ( !enabled )
            {
                buttons.remove( StandardButton.clearOtpSecret );
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
                profileData.add( new DisplayElement(
                        formConfiguration.getName(),
                        DisplayElement.Type.multiString,
                        formConfiguration.getLabel( actorLocale ),
                        entry.getValue()
                ) );
            }
            else
            {
                final String value = JavaHelper.isEmpty( entry.getValue() )
                        ? ""
                        : entry.getValue().iterator().next();
                profileData.add( new DisplayElement(
                        formConfiguration.getName(),
                        DisplayElement.Type.string,
                        formConfiguration.getLabel( actorLocale ),
                        value
                ) );
            }
        }
        return profileData;
    }

    private static Map<String, String> makePasswordPolicyRules(
            final UserInfo userInfo,
            final Locale locale,
            final Configuration configuration
    )
            throws PwmUnrecoverableException
    {
        final Map<String, String> passwordRules = new LinkedHashMap<>();
        if ( userInfo.getPasswordPolicy() != null )
        {
            for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
            {
                if ( userInfo.getPasswordPolicy().getValue( rule ) != null )
                {
                    if ( ChaiPasswordRule.RuleType.BOOLEAN == rule.getRuleType() )
                    {
                        final boolean value = Boolean.parseBoolean( userInfo.getPasswordPolicy().getValue( rule ) );
                        final String sValue = LocaleHelper.booleanString( value, locale, configuration );
                        passwordRules.put( rule.getLabel( locale, configuration ), sValue );
                    }
                    else
                    {
                        passwordRules.put( rule.getLabel( locale, configuration ),
                                userInfo.getPasswordPolicy().getValue( rule ) );
                    }
                }
            }
        }
        return Collections.unmodifiableMap( passwordRules );
    }
}



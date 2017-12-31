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

package password.pwm.http.servlet.helpdesk;

import com.novell.ldapchai.ChaiPasswordRule;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.HelpdeskUIMode;
import password.pwm.config.option.ViewStatusFields;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.value.data.ActionConfiguration;
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
import password.pwm.util.LocaleHelper;
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

@Getter
@Setter(AccessLevel.PRIVATE)
public class HelpdeskDetailInfoBean implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.forClass(HelpdeskDetailInfoBean.class);

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
    private List<ButtonInfo> customButtons;

    @Data
    @AllArgsConstructor
    public static class ButtonInfo implements Serializable {
        private String name;
        private String label;
        private String description;
    }

    public enum StandardButton {
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
        final Instant startTime = Instant.now();
        LOGGER.trace(pwmRequest, "beginning to assemble detail data report for user " + userIdentity);
        final Locale actorLocale = pwmRequest.getLocale();
        final ChaiUser theUser = HelpdeskServlet.getChaiUser(pwmRequest, helpdeskProfile, userIdentity);

        if (!theUser.exists()) {
            return null;
        }

        final HelpdeskDetailInfoBean detailInfo = new HelpdeskDetailInfoBean();
        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getSessionLabel(),
                actorLocale,
                userIdentity,
                theUser.getChaiProvider()
        );
        final MacroMachine macroMachine = new MacroMachine(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), userInfo, null);

        try {
            detailInfo.userHistory = AccountInformationBean.makeAuditInfo(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getSessionLabel(),
                    userInfo,
                    pwmRequest.getLocale()
            );
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading userHistory for user '" + userIdentity + "', " + e.getMessage());
        }

        {
            final List<FormConfiguration> detailFormConfig = helpdeskProfile.readSettingAsForm(PwmSetting.HELPDESK_DETAIL_FORM);
            final Map<FormConfiguration, List<String>> formData = FormUtility.populateFormMapFromLdap(detailFormConfig, pwmRequest.getPwmSession().getLabel(), userInfo);
            final List<DisplayElement> profileData = new ArrayList<>();
            for (final Map.Entry<FormConfiguration, List<String>> entry : formData.entrySet()) {
                final FormConfiguration formConfiguration = entry.getKey();
                if (formConfiguration.isMultivalue()) {
                    profileData.add(new DisplayElement(
                            formConfiguration.getName(),
                            DisplayElement.Type.multiString,
                            formConfiguration.getLabel(actorLocale),
                            entry.getValue()
                    ));
                } else {
                    final String value = JavaHelper.isEmpty(entry.getValue())
                            ? ""
                            : entry.getValue().iterator().next();
                    profileData.add(new DisplayElement(
                            formConfiguration.getName(),
                            DisplayElement.Type.string,
                            formConfiguration.getLabel(actorLocale),
                            value
                    ));
                }
            }
            detailInfo.profileData = profileData;
        }

        {
            final Map<String,String> passwordRules = new LinkedHashMap<>();
            if (userInfo.getPasswordPolicy() != null) {
                for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
                    if (userInfo.getPasswordPolicy().getValue(rule) != null) {
                        if (ChaiPasswordRule.RuleType.BOOLEAN == rule.getRuleType()) {
                            final boolean value = Boolean.parseBoolean(userInfo.getPasswordPolicy().getValue(rule));
                            final String sValue = LocaleHelper.booleanString(value, pwmRequest);
                            passwordRules.put(rule.getLabel(pwmRequest.getLocale(), pwmRequest.getConfig()), sValue);
                        } else {
                            passwordRules.put(rule.getLabel(pwmRequest.getLocale(), pwmRequest.getConfig()),
                                    userInfo.getPasswordPolicy().getValue(rule));
                        }
                    }
                }
            }
            detailInfo.setPasswordPolicyRules(Collections.unmodifiableMap(passwordRules));
        }

        {
            final List<String> requirementLines = PasswordRequirementsTag.getPasswordRequirementsStrings(
                    userInfo.getPasswordPolicy(),
                    pwmRequest.getConfig(),
                    pwmRequest.getLocale(),
                    macroMachine
            );
            detailInfo.setPasswordRequirements(Collections.unmodifiableList(requirementLines));
        }

        if ((userInfo.getPasswordPolicy() != null)
                && (userInfo.getPasswordPolicy().getChaiPasswordPolicy() != null)
                && (userInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry() != null)
                && (userInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry().getEntryDN() != null)) {
            detailInfo.setPasswordPolicyDN(userInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry().getEntryDN());
        } else {
            detailInfo.setPasswordPolicyDN(LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable, pwmRequest));
        }

        if ((userInfo.getPasswordPolicy() != null)
                && userInfo.getPasswordPolicy().getIdentifier() != null) {
            detailInfo.setPasswordPolicyID(userInfo.getPasswordPolicy().getIdentifier());
        } else {
            detailInfo.setPasswordPolicyID(LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable, pwmRequest));
        }

        {
            final ResponseInfoBean responseInfoBean = userInfo.getResponseInfoBean();
            if (responseInfoBean != null && responseInfoBean.getHelpdeskCrMap() != null) {
                final List<DisplayElement> responseDisplay = new ArrayList<>();
                int counter = 0;
                for (final Map.Entry<Challenge, String> entry : responseInfoBean.getHelpdeskCrMap().entrySet()) {
                    counter++;
                    responseDisplay.add(new DisplayElement(
                            "item_" + counter,
                            DisplayElement.Type.string,
                            entry.getKey().getChallengeText(),
                            entry.getValue()
                    ));
                }
                detailInfo.helpdeskResponses = responseDisplay;
            }

        }

        final String configuredDisplayName = helpdeskProfile.readSettingAsString(PwmSetting.HELPDESK_DETAIL_DISPLAY_NAME);
        if (configuredDisplayName != null && !configuredDisplayName.isEmpty()) {
            final String displayName = macroMachine.expandMacros(configuredDisplayName);
            detailInfo.setUserDisplayName(displayName);
        }

        final TimeDuration timeDuration = TimeDuration.fromCurrent(startTime);
        if (pwmRequest.getConfig().isDevDebugMode()) {
            LOGGER.trace(pwmRequest, "completed assembly of detail data report for user " + userIdentity
                    + " in " + timeDuration.asCompactString() + ", contents: " + JsonUtil.serialize(detailInfo));
        }

        {
            final Set<ViewStatusFields> viewStatusFields = helpdeskProfile.readSettingAsOptionList(PwmSetting.HELPDESK_VIEW_STATUS_VALUES, ViewStatusFields.class);
            detailInfo.statusData = ViewableUserInfoDisplayReader.makeDisplayData(
                    viewStatusFields,
                    pwmRequest.getConfig(),
                    userInfo,
                    null,
                    pwmRequest.getLocale()
            );
        }

        detailInfo.setVisibleButtons(determineVisibleButtons(pwmRequest, helpdeskProfile));
        detailInfo.setEnabledButtons(determineEnabledButtons(detailInfo.getVisibleButtons(), userInfo));
        detailInfo.setCustomButtons(determineCustomButtons(helpdeskProfile));

        return detailInfo;
    }

    static Set<StandardButton> determineVisibleButtons(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile
    )
    {
        final Set<StandardButton> buttons = new LinkedHashSet<>();

        buttons.add(StandardButton.refresh);
        buttons.add(StandardButton.back);

        {
            final HelpdeskUIMode uiMode =
                    helpdeskProfile.readSettingAsEnum(PwmSetting.HELPDESK_SET_PASSWORD_MODE, HelpdeskUIMode.class);
            if (uiMode != HelpdeskUIMode.none) {
                buttons.add(StandardButton.changePassword);
            }
        }

        if (helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) {
            buttons.add(StandardButton.unlock);
        }

        if (helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_CLEAR_RESPONSES_BUTTON)) {
            buttons.add(StandardButton.clearResponses);
        }

        if (pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.OTP_ENABLED)) {
            if (helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_CLEAR_OTP_BUTTON)) {
                buttons.add(StandardButton.clearOtpSecret);
            }
        }

        if (!helpdeskProfile.readOptionalVerificationMethods().isEmpty()) {
            buttons.add(StandardButton.verification);
        }

        if (helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_DELETE_USER_BUTTON)) {
            buttons.add(StandardButton.deleteUser);
        }

        return Collections.unmodifiableSet(buttons);
    }

    static Set<StandardButton> determineEnabledButtons(
            final Set<StandardButton> visibleButtons,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final Set<StandardButton> buttons = new LinkedHashSet<>(visibleButtons);

        if (buttons.contains(StandardButton.unlock)) {
            final boolean enabled = userInfo.isPasswordLocked();
            if (!enabled) {
                buttons.remove(StandardButton.unlock);
            }
        }

        if (buttons.contains(StandardButton.clearResponses)) {
            final boolean enabled = userInfo.getResponseInfoBean() != null;
            if (!enabled) {
                buttons.remove(StandardButton.clearResponses);
            }
        }

        if (buttons.contains(StandardButton.clearOtpSecret)) {
            final boolean enabled = userInfo.getOtpUserRecord() != null;
            if (!enabled) {
                buttons.remove(StandardButton.clearOtpSecret);
            }
        }

        return Collections.unmodifiableSet(buttons);
    }

    static List<ButtonInfo> determineCustomButtons(
            final HelpdeskProfile helpdeskProfile
    )
    {
        final List<ActionConfiguration> actions = helpdeskProfile.readSettingAsAction(PwmSetting.HELPDESK_ACTIONS);

        final List<ButtonInfo> buttons = new ArrayList<>();
        if (actions != null) {
            int count = 0;
            for (final ActionConfiguration action : actions) {
                buttons.add(new ButtonInfo(
                        "custom_" + count++,
                        action.getName(),
                        action.getDescription()
                ));
            }
        }

        return Collections.unmodifiableList(buttons);

    }

}


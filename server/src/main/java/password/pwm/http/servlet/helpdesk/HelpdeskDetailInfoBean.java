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
import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.pub.PublicUserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.tag.PasswordRequirementsTag;
import password.pwm.i18n.Display;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.util.LocaleHelper;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter(AccessLevel.PRIVATE)
public class HelpdeskDetailInfoBean implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.forClass(HelpdeskDetailInfoBean.class);

    private PublicUserInfoBean userInfo;
    private String userDisplayName;

    private boolean intruderLocked;
    private boolean accountEnabled;
    private boolean accountExpired;

    private Instant lastLoginTime;
    private List<UserAuditRecord> userHistory;
    private Map<FormConfiguration, List<String>> searchDetails;
    private String passwordSetDelta;

    private Map<String, String> passwordPolicyRules;
    private List<String> passwordRequirements;
    private String passwordPolicyDN;
    private String passwordPolicyID;

    private boolean hasOtpRecord;
    private String otpRecordTimestamp;

    private ResponseInfoBean responseInfoBean;

    private transient UserInfo backingUserInfo;

    static HelpdeskDetailInfoBean makeHelpdeskDetailInfo(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final Instant startTime = Instant.now();
        LOGGER.trace(pwmRequest, "beginning to assemble detail data report for user " + userIdentity);
        final Locale actorLocale = pwmRequest.getLocale();
        final ChaiUser theUser = HelpdeskServlet.getChaiUser(pwmRequest, helpdeskProfile, userIdentity);

        if (!theUser.isValid()) {
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

        detailInfo.setUserInfo(PublicUserInfoBean.fromUserInfoBean(userInfo, pwmRequest.getConfig(), pwmRequest.getLocale(), macroMachine));

        try {
            detailInfo.setIntruderLocked(theUser.isPasswordLocked());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading intruder lock status for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            detailInfo.setAccountEnabled(theUser.isAccountEnabled());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading account enabled status for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            detailInfo.setAccountExpired(theUser.isAccountExpired());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading account expired status for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            final Date lastLoginTime = theUser.readLastLoginTime();
            detailInfo.setLastLoginTime(lastLoginTime == null ? null : lastLoginTime.toInstant());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading last login time for user '" + userIdentity + "', " + e.getMessage());
        }

        try {
            detailInfo.setUserHistory(pwmRequest.getPwmApplication().getAuditManager().readUserHistory(userInfo));
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading userHistory for user '" + userIdentity + "', " + e.getMessage());
        }

        if (detailInfo.getUserInfo().getPasswordLastModifiedTime() != null) {
            final TimeDuration passwordSetDelta = TimeDuration.fromCurrent(detailInfo.getUserInfo().getPasswordLastModifiedTime());
            detailInfo.setPasswordSetDelta(passwordSetDelta.asLongString(pwmRequest.getLocale()));
        } else {
            detailInfo.setPasswordSetDelta(LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable, pwmRequest));
        }

        {
            final List<FormConfiguration> detailFormConfig = helpdeskProfile.readSettingAsForm(PwmSetting.HELPDESK_DETAIL_FORM);
            final Map<FormConfiguration, List<String>> formData = FormUtility.populateFormMapFromLdap(detailFormConfig, pwmRequest.getPwmSession().getLabel(), userInfo);
            detailInfo.setSearchDetails(formData);
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

        detailInfo.hasOtpRecord = userInfo.getOtpUserRecord() != null;

        detailInfo.otpRecordTimestamp = userInfo.getOtpUserRecord() != null && userInfo.getOtpUserRecord().getTimestamp() != null
                ? JavaHelper.toIsoDate(userInfo.getOtpUserRecord().getTimestamp())
                : LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable, pwmRequest);

        detailInfo.responseInfoBean = userInfo.getResponseInfoBean();

        detailInfo.setBackingUserInfo(userInfo);

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


        return detailInfo;
    }
}


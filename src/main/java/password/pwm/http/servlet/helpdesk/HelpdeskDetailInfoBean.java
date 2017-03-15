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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.FormConfiguration;
import password.pwm.config.FormUtility;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Display;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserStatusReader;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HelpdeskDetailInfoBean implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.forClass(HelpdeskDetailInfoBean.class);


    private UserInfoBean userInfoBean = new UserInfoBean();
    private String userDisplayName;

    private boolean intruderLocked;
    private boolean accountEnabled;
    private boolean accountExpired;

    private Instant lastLoginTime;
    private List<UserAuditRecord> userHistory;
    private Map<FormConfiguration, List<String>> searchDetails;
    private String passwordSetDelta;

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
        final UserInfoBean uiBean = detailInfo.getUserInfoBean();
        final UserStatusReader userStatusReader = new UserStatusReader(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel());
        userStatusReader.populateUserInfoBean(uiBean, actorLocale, userIdentity, theUser.getChaiProvider());

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
            detailInfo.setUserHistory(pwmRequest.getPwmApplication().getAuditManager().readUserHistory(uiBean));
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error reading userHistory for user '" + userIdentity + "', " + e.getMessage());
        }

        if (uiBean.getPasswordLastModifiedTime() != null) {
            final TimeDuration passwordSetDelta = TimeDuration.fromCurrent(uiBean.getPasswordLastModifiedTime());
            detailInfo.setPasswordSetDelta(passwordSetDelta.asLongString(pwmRequest.getLocale()));
        } else {
            detailInfo.setPasswordSetDelta(LocaleHelper.getLocalizedMessage(Display.Value_NotApplicable, pwmRequest));
        }

        final UserDataReader userDataReader = helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_USE_PROXY)
                ? LdapUserDataReader.appProxiedReader(pwmRequest.getPwmApplication(), userIdentity)
                : LdapUserDataReader.selfProxiedReader(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), userIdentity);

        {
            final List<FormConfiguration> detailFormConfig = helpdeskProfile.readSettingAsForm(PwmSetting.HELPDESK_DETAIL_FORM);
            final Map<FormConfiguration,List<String>> formData = FormUtility.populateFormMapFromLdap(detailFormConfig, pwmRequest.getPwmSession().getLabel(), userDataReader);
            detailInfo.setSearchDetails(formData);
        }

        final String configuredDisplayName = helpdeskProfile.readSettingAsString(PwmSetting.HELPDESK_DETAIL_DISPLAY_NAME);
        if (configuredDisplayName != null && !configuredDisplayName.isEmpty()) {
            final MacroMachine macroMachine = new MacroMachine(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), detailInfo.getUserInfoBean(), null, userDataReader);
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

    public String getUserDisplayName() {
        return userDisplayName;
    }

    public void setUserDisplayName(final String userDisplayName) {
        this.userDisplayName = userDisplayName;
    }

    public UserInfoBean getUserInfoBean() {
        return userInfoBean;
    }

    public void setUserInfoBean(final UserInfoBean userInfoBean) {
        this.userInfoBean = userInfoBean;
    }

    public boolean isIntruderLocked() {
        return intruderLocked;
    }

    public void setIntruderLocked(final boolean intruderLocked) {
        this.intruderLocked = intruderLocked;
    }

    public boolean isAccountEnabled() {
        return accountEnabled;
    }

    public void setAccountEnabled(final boolean accountEnabled) {
        this.accountEnabled = accountEnabled;
    }

    public Instant getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(final Instant lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public List<UserAuditRecord> getUserHistory() {
        return userHistory;
    }

    public void setUserHistory(final List<UserAuditRecord> userHistory) {
        this.userHistory = userHistory;
    }

    public Map<FormConfiguration, List<String>> getSearchDetails() {
        return searchDetails;
    }

    public void setSearchDetails(final Map<FormConfiguration, List<String>> searchDetails) {
        this.searchDetails = searchDetails;
    }

    public String getPasswordSetDelta() {
        return passwordSetDelta;
    }

    public void setPasswordSetDelta(final String passwordSetDelta) {
        this.passwordSetDelta = passwordSetDelta;
    }

    public boolean isAccountExpired() {
        return accountExpired;
    }

    public void setAccountExpired(final boolean accountExpired) {
        this.accountExpired = accountExpired;
    }
}

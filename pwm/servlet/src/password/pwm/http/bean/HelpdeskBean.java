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

package password.pwm.http.bean;

import password.pwm.bean.UserInfoBean;
import password.pwm.config.FormConfiguration;
import password.pwm.event.UserAuditRecord;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class HelpdeskBean implements PwmSessionBean {
    private String searchString;
    private HelpdeskDetailInfo heldpdeskDetailInfo = new HelpdeskDetailInfo();

    public static class HelpdeskDetailInfo implements Serializable {
        private UserInfoBean userInfoBean = new UserInfoBean();
        private String userDisplayName;

        private boolean intruderLocked;
        private boolean accountEnabled;
        private boolean accountExpired;

        private Date lastLoginTime;
        private List<UserAuditRecord> userHistory;
        private Map<FormConfiguration, String> searchDetails;
        private String passwordSetDelta;

        public String getUserDisplayName() {
            return userDisplayName;
        }

        public void setUserDisplayName(String userDisplayName) {
            this.userDisplayName = userDisplayName;
        }

        public UserInfoBean getUserInfoBean() {
            return userInfoBean;
        }

        public void setUserInfoBean(UserInfoBean userInfoBean) {
            this.userInfoBean = userInfoBean;
        }

        public boolean isIntruderLocked() {
            return intruderLocked;
        }

        public void setIntruderLocked(boolean intruderLocked) {
            this.intruderLocked = intruderLocked;
        }

        public boolean isAccountEnabled() {
            return accountEnabled;
        }

        public void setAccountEnabled(boolean accountEnabled) {
            this.accountEnabled = accountEnabled;
        }

        public Date getLastLoginTime() {
            return lastLoginTime;
        }

        public void setLastLoginTime(Date lastLoginTime) {
            this.lastLoginTime = lastLoginTime;
        }

        public List<UserAuditRecord> getUserHistory() {
            return userHistory;
        }

        public void setUserHistory(List<UserAuditRecord> userHistory) {
            this.userHistory = userHistory;
        }

        public Map<FormConfiguration,String> getSearchDetails() {
            return searchDetails;
        }

        public void setSearchDetails(Map<FormConfiguration,String> searchDetails) {
            this.searchDetails = searchDetails;
        }

        public String getPasswordSetDelta()
        {
            return passwordSetDelta;
        }

        public void setPasswordSetDelta(String passwordSetDelta)
        {
            this.passwordSetDelta = passwordSetDelta;
        }

        public boolean isAccountExpired() {
            return accountExpired;
        }

        public void setAccountExpired(boolean accountExpired) {
            this.accountExpired = accountExpired;
        }
    }

    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public HelpdeskDetailInfo getHeldpdeskDetailInfo() {
        return heldpdeskDetailInfo;
    }

    public void setHeldpdeskDetailInfo(HelpdeskDetailInfo heldpdeskDetailInfo) {
        this.heldpdeskDetailInfo = heldpdeskDetailInfo;
    }
}

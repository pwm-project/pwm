/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.bean;

import com.novell.ldapchai.cr.ResponseSet;
import password.pwm.UserHistory;

import java.io.Serializable;
import java.util.Date;

public class HelpdeskBean implements Serializable {
    private boolean userExists;
    private UserInfoBean userInfoBean = new UserInfoBean();
    private transient ResponseSet responseSet;
    private boolean intruderLocked;
    private boolean pwmIntruder;
    private boolean accountEnabled;
    private Date lastLoginTime;
    private UserHistory userHistory;

    public boolean isUserExists() {
        return userExists;
    }

    public void setUserExists(final boolean userExists) {
        this.userExists = userExists;
    }

    public UserInfoBean getUserInfoBean() {
        return userInfoBean;
    }

    public void setUserInfoBean(final UserInfoBean userInfoBean) {
        this.userInfoBean = userInfoBean;
    }

    public ResponseSet getResponseSet() {
        return responseSet;
    }

    public void setResponseSet(final ResponseSet responseSet) {
        this.responseSet = responseSet;
    }

    public boolean isIntruderLocked() {
        return intruderLocked;
    }

    public void setIntruderLocked(final boolean intruderLocked) {
        this.intruderLocked = intruderLocked;
    }

    public boolean isPwmIntruder() {
        return pwmIntruder;
    }

    public void setPwmIntruder(final boolean pwmIntruder) {
        this.pwmIntruder = pwmIntruder;
    }

    public Date getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(Date lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public UserHistory getUserHistory() {
        return userHistory;
    }

    public void setUserHistory(UserHistory userHistory) {
        this.userHistory = userHistory;
    }

    public boolean isAccountEnabled() {
        return accountEnabled;
    }

    public void setAccountEnabled(boolean accountEnabled) {
        this.accountEnabled = accountEnabled;
    }
}

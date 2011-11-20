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

package password.pwm.http.servlet.helpdesk;

import password.pwm.bean.UserInfoBean;
import password.pwm.config.FormConfiguration;
import password.pwm.event.UserAuditRecord;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class HelpdeskDetailInfoBean implements Serializable {
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

    public Map<FormConfiguration, String> getSearchDetails() {
        return searchDetails;
    }

    public void setSearchDetails(Map<FormConfiguration, String> searchDetails) {
        this.searchDetails = searchDetails;
    }

    public String getPasswordSetDelta() {
        return passwordSetDelta;
    }

    public void setPasswordSetDelta(String passwordSetDelta) {
        this.passwordSetDelta = passwordSetDelta;
    }

    public boolean isAccountExpired() {
        return accountExpired;
    }

    public void setAccountExpired(boolean accountExpired) {
        this.accountExpired = accountExpired;
    }
}

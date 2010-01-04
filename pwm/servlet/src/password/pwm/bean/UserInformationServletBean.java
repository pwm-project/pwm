package password.pwm.bean;

import com.novell.ldapchai.cr.ResponseSet;

import java.io.Serializable;
import java.util.Date;

public class UserInformationServletBean implements Serializable {
    private boolean userExists;
    private UserInfoBean userInfoBean = new UserInfoBean();
    private ResponseSet responseSet;
    private boolean intruderLocked;
    private boolean pwmIntruder;
    private boolean passwordRetrievable;
    private String passwordPolicyDN;
    private Date lastLoginTime;

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

    public boolean isPasswordRetrievable() {
        return passwordRetrievable;
    }

    public void setPasswordRetrievable(boolean passwordRetrievable) {
        this.passwordRetrievable = passwordRetrievable;
    }

    public String getPasswordPolicyDN() {
        return passwordPolicyDN;
    }

    public void setPasswordPolicyDN(String passwordPolicyDN) {
        this.passwordPolicyDN = passwordPolicyDN;
    }

    public Date getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(Date lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }
}

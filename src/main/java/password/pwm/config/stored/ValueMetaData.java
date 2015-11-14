package password.pwm.config.stored;

import password.pwm.bean.UserIdentity;

import java.util.Date;

public class ValueMetaData {
    private final Date modifyDate;
    private final UserIdentity modifyUser;

    public ValueMetaData(Date modifyDate, UserIdentity modifyUser) {
        this.modifyDate = modifyDate;
        this.modifyUser = modifyUser;
    }

    public Date getModifyDate() {
        return modifyDate;
    }

    public UserIdentity getModifyUser() {
        return modifyUser;
    }
}

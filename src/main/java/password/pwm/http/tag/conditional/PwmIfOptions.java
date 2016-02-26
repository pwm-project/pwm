package password.pwm.http.tag.conditional;

import password.pwm.Permission;
import password.pwm.config.PwmSetting;
import password.pwm.http.PwmRequestFlag;

class PwmIfOptions {
    private boolean negate;
    private Permission permission;
    private PwmSetting pwmSetting;
    private PwmRequestFlag requestFlag;

    public PwmIfOptions(final boolean negate, final PwmSetting pwmSetting, final Permission permission, final PwmRequestFlag pwmRequestFlag) {
        this.negate = negate;
        this.permission = permission;
        this.pwmSetting = pwmSetting;
        this.requestFlag = pwmRequestFlag;
    }

    public boolean isNegate() {
        return negate;
    }

    public Permission getPermission() {
        return permission;
    }

    public PwmRequestFlag getRequestFlag() {
        return requestFlag;
    }

    public PwmSetting getPwmSetting() {
        return pwmSetting;
    }
}

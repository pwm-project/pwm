package password.pwm.ws.server;

import java.io.Serializable;

public class ServicePermissions implements Serializable {
    private boolean authRequired = true;
    private boolean adminOnly = true;
    private boolean blockExternal = true;
    private boolean authAndAdminWhenRunningRequired = false;
    private boolean helpdeskPermitted = false;

    public boolean isAuthRequired() {
        return authRequired;
    }

    public void setAuthRequired(boolean authRequired) {
        this.authRequired = authRequired;
    }

    public boolean isBlockExternal() {
        return blockExternal;
    }

    public void setBlockExternal(boolean blockExternal) {
        this.blockExternal = blockExternal;
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public void setAdminOnly(boolean adminOnly) {
        this.adminOnly = adminOnly;
    }

    public boolean isAuthAndAdminWhenRunningRequired() {
        return authAndAdminWhenRunningRequired;
    }

    public void setAuthAndAdminWhenRunningRequired(boolean authAndAdminWhenRunningRequired) {
        this.authAndAdminWhenRunningRequired = authAndAdminWhenRunningRequired;
    }

    public boolean isHelpdeskPermitted()
    {
        return helpdeskPermitted;
    }

    public void setHelpdeskPermitted(boolean helpdeskPermitted)
    {
        this.helpdeskPermitted = helpdeskPermitted;
    }
}

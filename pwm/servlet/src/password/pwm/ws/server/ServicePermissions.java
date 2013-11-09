package password.pwm.ws.server;

import java.io.Serializable;

public class ServicePermissions implements Serializable {
    private boolean authRequired = true;
    private boolean adminOnly = true;
    private boolean blockExternal = true;

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
}

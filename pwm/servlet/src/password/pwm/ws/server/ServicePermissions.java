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

package password.pwm.ws.server;

import java.io.Serializable;

public class ServicePermissions implements Serializable {

    public static final ServicePermissions PUBLIC;
    public static final ServicePermissions ADMIN_OR_CONFIGMODE;
    public static final ServicePermissions ADMIN_LOCAL_OR_EXTERNAL;

    static {
        {
            final ServicePermissions permission = new ServicePermissions();
            permission.setAdminOnly(false);
            permission.setAuthRequired(false);
            permission.setBlockExternal(false);
            permission.setHelpdeskPermitted(true);
            permission.lock();
            PUBLIC = permission;
        }
        {
            final ServicePermissions permission = new ServicePermissions();
            permission.setAdminOnly(false);
            permission.setAuthRequired(false);
            permission.setBlockExternal(true);
            permission.setPublicDuringConfig(true);
            permission.lock();
            ADMIN_OR_CONFIGMODE = permission;
        }
        {
            final ServicePermissions permission = new ServicePermissions();
            permission.setAdminOnly(true);
            permission.setAuthRequired(true);
            permission.setBlockExternal(false);
            permission.lock();
            ADMIN_LOCAL_OR_EXTERNAL = permission;
        }
    }

    private boolean authRequired = true;
    private boolean adminOnly = true;
    private boolean blockExternal = true;
    private boolean publicDuringConfig = false;
    private boolean helpdeskPermitted = false;

    private boolean locked = false;

    private void preModifyCheck() {
        if (locked) {
            throw new UnsupportedOperationException("ServicePermission is locked");
        }
    }

    public boolean isAuthRequired() {
        return authRequired;
    }

    public void setAuthRequired(boolean authRequired) {
        preModifyCheck();
        this.authRequired = authRequired;
    }

    public boolean isBlockExternal() {
        return blockExternal;
    }

    public void setBlockExternal(boolean blockExternal) {
        preModifyCheck();
        this.blockExternal = blockExternal;
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public void setAdminOnly(boolean adminOnly) {
        preModifyCheck();
        this.adminOnly = adminOnly;
    }

    public boolean isHelpdeskPermitted()
    {
        return helpdeskPermitted;
    }

    public void setHelpdeskPermitted(boolean helpdeskPermitted)
    {
        preModifyCheck();
        this.helpdeskPermitted = helpdeskPermitted;
    }

    public boolean isLocked()
    {
        return locked;
    }

    public void lock() {
        locked = true;
    }

    public boolean isPublicDuringConfig()
    {
        return publicDuringConfig;
    }

    public void setPublicDuringConfig(boolean publicDuringConfig)
    {
        this.publicDuringConfig = publicDuringConfig;
    }
}

/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

import password.pwm.error.ErrorInformation;
import password.pwm.servlet.ChangePasswordServlet;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class ChangePasswordBean implements Serializable {
// ------------------------------ FIELDS ------------------------------

    // ------------------------- PUBLIC CONSTANTS -------------------------
    public static final int MAX_AGE_MS_NEW_PASSWORD = 30000;
    private String newPassword;
    private long newPasswordSetTime;
    private ErrorInformation passwordChangeError;
    private final Map<String, ChangePasswordServlet.PasswordCheckInfo> passwordTestCache = new LinkedHashMap<String, ChangePasswordServlet.PasswordCheckInfo>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, ChangePasswordServlet.PasswordCheckInfo> eldest) {
            return this.size() > ChangePasswordServlet.MAX_CACHE_SIZE;
        }
    };

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getNewPassword()
    {
        if ((System.currentTimeMillis() - newPasswordSetTime) > MAX_AGE_MS_NEW_PASSWORD) {
            newPassword = null;
        }
        return newPassword;
    }

    public ErrorInformation getPasswordChangeError()
    {
        return passwordChangeError;
    }

    public void setPasswordChangeError(final ErrorInformation passwordChangeError)
    {
        this.passwordChangeError = passwordChangeError;
    }

    public Map<String, ChangePasswordServlet.PasswordCheckInfo> getPasswordTestCache()
    {
        return passwordTestCache;
    }

// -------------------------- OTHER METHODS --------------------------

    public void clearPassword()
    {
        newPassword = null;
    }

    public void setNewPassword(final String newPassword)
    {
        this.newPassword = newPassword;
        this.newPasswordSetTime = System.currentTimeMillis();
    }
}


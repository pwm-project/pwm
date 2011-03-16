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

import password.pwm.config.FormConfiguration;

import java.io.Serializable;
import java.util.Map;

public class ForgottenUsernameBean implements Serializable {
    private String forgottenUserDN;

    private Map<String, FormConfiguration> forgottenUsernameForm;

// --------------------- GETTER / SETTER METHODS ---------------------

    public Map<String, FormConfiguration> getForgottenUsernameForm() {
        return forgottenUsernameForm;
    }

    public void setForgottenUsernameForm(final Map<String, FormConfiguration> forgottenUsernameForm) {
        this.forgottenUsernameForm = forgottenUsernameForm;
    }

    public String getForgottenUserDN() {
        return forgottenUserDN;
    }

    public void setForgottenUserDN(final String forgottenUserDN) {
        this.forgottenUserDN = forgottenUserDN;
    }
}

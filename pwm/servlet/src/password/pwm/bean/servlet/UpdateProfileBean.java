/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.bean.servlet;

import password.pwm.bean.PwmSessionBean;
import password.pwm.config.FormConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public class UpdateProfileBean implements PwmSessionBean {

    private boolean agreementPassed;
    private boolean confirmationPassed;
    private boolean formSubmitted;

    private final Map<FormConfiguration,String> formData = new LinkedHashMap<FormConfiguration,String>();

    public boolean isAgreementPassed() {
        return agreementPassed;
    }

    public void setAgreementPassed(final boolean agreementPassed) {
        this.agreementPassed = agreementPassed;
    }

    public Map<FormConfiguration, String> getFormData() {
        return formData;
    }

    public boolean isConfirmationPassed() {
        return confirmationPassed;
    }

    public void setConfirmationPassed(final boolean confirmationPassed) {
        this.confirmationPassed = confirmationPassed;
    }

    public boolean isFormSubmitted() {
        return formSubmitted;
    }

    public void setFormSubmitted(boolean formSubmitted) {
        this.formSubmitted = formSubmitted;
    }
}

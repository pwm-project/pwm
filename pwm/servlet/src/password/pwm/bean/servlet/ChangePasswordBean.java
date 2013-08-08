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

/**
 * @author Jason D. Rivard
 */
public class ChangePasswordBean implements PwmSessionBean {
// ------------------------------ FIELDS ------------------------------

    // ------------------------- PUBLIC CONSTANTS -------------------------
    private String newPassword;
    private boolean agreementPassed;
    private boolean currentPasswordRequired;
    private boolean currentPasswordPassed;
    private boolean formPassed;
    private boolean allChecksPassed;


// --------------------- GETTER / SETTER METHODS ---------------------

    public String getNewPassword()
    {
        return newPassword;
    }

    public boolean isAgreementPassed() {
        return agreementPassed;
    }

    public void setAgreementPassed(final boolean agreementPassed) {
        this.agreementPassed = agreementPassed;
    }

    public boolean isCurrentPasswordRequired() {
        return currentPasswordRequired;
    }

    public void setCurrentPasswordRequired(final boolean currentPasswordRequired) {
        this.currentPasswordRequired = currentPasswordRequired;
    }

    public boolean isCurrentPasswordPassed() {
        return currentPasswordPassed;
    }

    public void setCurrentPasswordPassed(boolean currentPasswordPassed) {
        this.currentPasswordPassed = currentPasswordPassed;
    }

    public boolean isFormPassed() {
        return formPassed;
    }

    public void setFormPassed(boolean formPassed) {
        this.formPassed = formPassed;
    }

    public boolean isAllChecksPassed() {
        return allChecksPassed;
    }

    public void setAllChecksPassed(boolean allChecksPassed) {
        this.allChecksPassed = allChecksPassed;
    }

    // -------------------------- OTHER METHODS --------------------------

    public void clearPassword()
    {
        newPassword = null;
    }

    public void setNewPassword(final String newPassword)
    {
        this.newPassword = newPassword;
    }
}


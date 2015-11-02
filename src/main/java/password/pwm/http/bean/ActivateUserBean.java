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

package password.pwm.http.bean;

import password.pwm.bean.UserIdentity;

public class ActivateUserBean implements PwmSessionBean {
    private boolean tokenIssued;
    private boolean tokenPassed;
    private boolean agreementPassed;
    private boolean formValidated;
    private String tokenDisplayText;
    private String agreementText;

    private UserIdentity userIdentity;

    public boolean isTokenIssued() {
        return tokenIssued;
    }

    public void setTokenIssued(boolean tokenIssued) {
        this.tokenIssued = tokenIssued;
    }

    public boolean isAgreementPassed() {
        return agreementPassed;
    }

    public void setAgreementPassed(boolean agreementPassed) {
        this.agreementPassed = agreementPassed;
    }

    public boolean isFormValidated() {
        return formValidated;
    }

    public void setFormValidated(boolean formValidated) {
        this.formValidated = formValidated;
    }

    public boolean isTokenPassed() {
        return tokenPassed;
    }

    public void setTokenPassed(boolean tokenPassed) {
        this.tokenPassed = tokenPassed;
    }

    public UserIdentity getUserIdentity() {
        return userIdentity;
    }

    public void setUserIdentity(UserIdentity userIdentity) {
        this.userIdentity = userIdentity;
    }

    public String getTokenDisplayText() {
        return tokenDisplayText;
    }

    public void setTokenDisplayText(final String tokenSendAddress) {
        this.tokenDisplayText = tokenSendAddress;
    }

    public String getAgreementText()
    {
        return agreementText;
    }

    public void setAgreementText(String agreementText)
    {
        this.agreementText = agreementText;
    }
}

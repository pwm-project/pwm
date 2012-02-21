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

package password.pwm.bean;

import java.io.Serializable;
import java.util.Map;

public class NewUserBean implements Serializable {
    private String tokenEmailAddress;
    private Map<String,String> formData;

    private boolean agreementPassed;
    private boolean tokenIssued;
    private boolean tokenPassed;
    private boolean allPassed;

    public NewUserBean() {
    }

    public boolean isTokenIssued() {
        return tokenIssued;
    }

    public void setTokenIssued(final boolean tokenIssued) {
        this.tokenIssued = tokenIssued;
    }

    public String getTokenEmailAddress() {
        return tokenEmailAddress;
    }

    public void setTokenEmailAddress(String tokenEmailAddress) {
        this.tokenEmailAddress = tokenEmailAddress;
    }

    public Map<String, String> getFormData() {
        return formData;
    }

    public void setFormData(Map<String, String> formData) {
        this.formData = formData;
    }

    public boolean isAgreementPassed() {
        return agreementPassed;
    }

    public void setAgreementPassed(boolean agreementPassed) {
        this.agreementPassed = agreementPassed;
    }

    public boolean isTokenPassed() {
        return tokenPassed;
    }

    public void setTokenPassed(final boolean tokenPassed) {
        this.tokenPassed = tokenPassed;
    }

    public boolean isAllPassed() {
        return allPassed;
    }

    public void setAllPassed(final boolean allPassed) {
        this.allPassed = allPassed;
    }
}

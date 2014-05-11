/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
import password.pwm.servlet.NewUserServlet;

import java.util.Date;
import java.util.Map;

public class NewUserBean implements PwmSessionBean {
    private String tokenDisplayText;
    private Map<String,String> formData;

    private boolean agreementPassed;
    private boolean emailTokenIssued;
    private boolean emailTokenPassed;
    private boolean smsTokenIssued;
    private boolean smsTokenPassed;
    private boolean formPassed;
    private NewUserVerificationPhase verificationPhase = NewUserVerificationPhase.NONE;
    private Date createStartTime;

    public NewUserBean() {
    }

    public boolean isEmailTokenIssued() {
        return emailTokenIssued;
    }

    public void setEmailTokenIssued(final boolean emailTokenIssued) {
        this.emailTokenIssued = emailTokenIssued;
    }

    public boolean isSmsTokenIssued() {
        return smsTokenIssued;
    }

    public void setSmsTokenIssued(final boolean smsTokenIssued) {
        this.smsTokenIssued = smsTokenIssued;
    }

    public String getTokenDisplayText()
    {
        return tokenDisplayText;
    }

    public void setTokenDisplayText(String tokenDisplayText)
    {
        this.tokenDisplayText = tokenDisplayText;
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

    public boolean isEmailTokenPassed() {
        return emailTokenPassed;
    }

    public void setEmailTokenPassed(final boolean emailTokenPassed) {
        this.emailTokenPassed = emailTokenPassed;
    }

    public boolean isSmsTokenPassed() {
        return smsTokenPassed;
    }

    public void setSmsTokenPassed(final boolean smsTokenPassed) {
        this.smsTokenPassed = smsTokenPassed;
    }

    public boolean isFormPassed() {
        return formPassed;
    }

    public void setFormPassed(final boolean formPassed) {
        this.formPassed = formPassed;
    }
    
    public void setVerificationPhase(NewUserVerificationPhase verificationPhase) {
    	this.verificationPhase = verificationPhase;
    }
    
    public NewUserVerificationPhase getVerificationPhase() {
    	return verificationPhase;
    }
    
    public enum NewUserVerificationPhase {
    	NONE,
    	EMAIL,
    	SMS,
        ;

        public String getTokenName() {
            return NewUserServlet.class.getName() + "_" + this.toString();
        }
    }

    public Date getCreateStartTime()
    {
        return createStartTime;
    }

    public void setCreateStartTime(Date createStartTime)
    {
        this.createStartTime = createStartTime;
    }
}

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

import password.pwm.config.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.NewUserServlet;
import password.pwm.util.PasswordData;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

public class NewUserBean implements PwmSessionBean {
    private String profileID;
    private NewUserForm newUserForm;

    private String tokenDisplayText;

    private boolean agreementPassed;
    private boolean emailTokenIssued;
    private boolean emailTokenPassed;
    private boolean smsTokenIssued;
    private boolean smsTokenPassed;
    private boolean formPassed;
    private NewUserVerificationPhase verificationPhase = NewUserVerificationPhase.NONE;
    private Date createStartTime;
    private NewUserServlet.Page currentPage;
    private boolean urlSpecifiedProfile;

    public static class NewUserForm implements Serializable {
        private Map<FormConfiguration,String> formData;
        private PasswordData newUserPassword;
        private PasswordData confirmPassword;

        public NewUserForm(
                Map<FormConfiguration, String> formData,
                PasswordData newUserPassword,
                PasswordData confirmPassword
        )
        {
            this.formData = formData;
            this.newUserPassword = newUserPassword;
            this.confirmPassword = confirmPassword;
        }

        public Map<FormConfiguration, String> getFormData()
        {
            return formData;
        }

        public PasswordData getNewUserPassword()
        {
            return newUserPassword;
        }

        public PasswordData getConfirmPassword()
        {
            return confirmPassword;
        }

        public boolean isConsistentWith(NewUserForm otherForm) throws PwmUnrecoverableException {
            if (otherForm == null) {
                return false;
            }

            if (newUserPassword != null && otherForm.newUserPassword == null || newUserPassword == null && otherForm.newUserPassword != null) {
                return false;
            }

            if (newUserPassword == null || !newUserPassword.getStringValue().equals(otherForm.newUserPassword.getStringValue())) {
                return false;
            }

            for (final FormConfiguration formConfiguration : formData.keySet()) {
                final String value = formData.get(formConfiguration);
                final String otherValue = otherForm.formData.get(formConfiguration);
                if (value != null && !value.equals(otherValue)) {
                    return false;
                }
            }

            return true;
        }
    }

    public String getProfileID() {
        return profileID;
    }

    public void setProfileID(String profileID) {
        this.profileID = profileID;
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
    }

    public Date getCreateStartTime()
    {
        return createStartTime;
    }

    public void setCreateStartTime(Date createStartTime)
    {
        this.createStartTime = createStartTime;
    }

    public NewUserForm getNewUserForm()
    {
        return newUserForm;
    }

    public void setNewUserForm(NewUserForm newUserForm)
    {
        this.newUserForm = newUserForm;
    }

    public NewUserServlet.Page getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(NewUserServlet.Page currentPage) {
        this.currentPage = currentPage;
    }

    public boolean isUrlSpecifiedProfile() {
        return urlSpecifiedProfile;
    }

    public void setUrlSpecifiedProfile(boolean urlSpecifiedProfile) {
        this.urlSpecifiedProfile = urlSpecifiedProfile;
    }
}

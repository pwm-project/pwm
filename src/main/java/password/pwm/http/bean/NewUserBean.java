/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import password.pwm.bean.TokenVerificationProgress;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class NewUserBean extends PwmSessionBean {
    private String profileID;
    private NewUserForm newUserForm;

    private boolean agreementPassed;
    private boolean formPassed;
    private Date createStartTime;
    private boolean urlSpecifiedProfile;
    private final TokenVerificationProgress tokenVerificationProgress = new TokenVerificationProgress();

    public static class NewUserForm implements Serializable {
        private Map<String,String> formData;
        private PasswordData newUserPassword;
        private PasswordData confirmPassword;

        public NewUserForm(
                Map<String, String> formData,
                PasswordData newUserPassword,
                PasswordData confirmPassword
        )
        {
            this.formData = formData;
            this.newUserPassword = newUserPassword;
            this.confirmPassword = confirmPassword;
        }

        public Map<String, String> getFormData()
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

            for (final String formKey : formData.keySet()) {
                final String value = formData.get(formKey);
                final String otherValue = otherForm.formData.get(formKey);
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

    public boolean isAgreementPassed() {
        return agreementPassed;
    }

    public void setAgreementPassed(boolean agreementPassed) {
        this.agreementPassed = agreementPassed;
    }

    public boolean isFormPassed() {
        return formPassed;
    }

    public void setFormPassed(final boolean formPassed) {
        this.formPassed = formPassed;
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

    public boolean isUrlSpecifiedProfile() {
        return urlSpecifiedProfile;
    }

    public void setUrlSpecifiedProfile(boolean urlSpecifiedProfile) {
        this.urlSpecifiedProfile = urlSpecifiedProfile;
    }

    public Type getType() {
        return Type.PUBLIC;
    }

    public Set<Flag> getFlags() {
        return Collections.emptySet();
    }

    public TokenVerificationProgress getTokenVerificationProgress() {
        return tokenVerificationProgress;
    }
}

/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import password.pwm.bean.TokenVerificationProgress;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;

import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class NewUserBean extends PwmSessionBean {
    private String profileID;
    private NewUserForm newUserForm;

    private boolean agreementPassed;
    private boolean formPassed;
    private Instant createStartTime;
    private boolean urlSpecifiedProfile;
    private final TokenVerificationProgress tokenVerificationProgress = new TokenVerificationProgress();

    @Getter
    @AllArgsConstructor
    public static class NewUserForm implements Serializable {
        private final Map<String,String> formData;
        private final PasswordData newUserPassword;
        private final PasswordData confirmPassword;

        public boolean isConsistentWith(final NewUserForm otherForm) throws PwmUnrecoverableException {
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

    public Type getType() {
        return Type.PUBLIC;
    }

    @Override
    public Set<SessionBeanMode> supportedModes() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(SessionBeanMode.LOCAL, SessionBeanMode.CRYPTCOOKIE)));
    }


    public TokenVerificationProgress getTokenVerificationProgress() {
        return tokenVerificationProgress;
    }
}

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

package password.pwm.bean;

import password.pwm.VerificationMethodSystem;

import java.io.Serializable;
import java.util.List;

public class RemoteVerificationResponseBean implements Serializable {
    private String displayInstructions;
    private VerificationMethodSystem.VerificationState verificationState;
    private List<VerificationMethodSystem.UserPromptBean> userPrompts;
    private String errorMessage;

    public String getDisplayInstructions() {
        return displayInstructions;
    }

    public void setDisplayInstructions(String displayInstructions) {
        this.displayInstructions = displayInstructions;
    }

    public VerificationMethodSystem.VerificationState getVerificationState() {
        return verificationState;
    }

    public void setVerificationState(VerificationMethodSystem.VerificationState verificationState) {
        this.verificationState = verificationState;
    }

    public List<VerificationMethodSystem.UserPromptBean> getUserPrompts() {
        return userPrompts;
    }

    public void setUserPrompts(List<VerificationMethodSystem.UserPromptBean> userPrompts) {
        this.userPrompts = userPrompts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

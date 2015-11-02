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

package password.pwm;

import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface RecoveryVerificationMethod {
    enum VerificationState {
        INPROGRESS,
        FAILED,
        COMPLETE,
    }

    interface UserPrompt {
        String getDisplayPrompt();
        String getIdentifier();
    }

    class UserPromptBean implements Serializable, UserPrompt {
        private String displayPrompt;
        private String identifier;

        public String getDisplayPrompt() {
            return displayPrompt;
        }

        public void setDisplayPrompt(String displayPrompt) {
            this.displayPrompt = displayPrompt;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }
    }

    public List<UserPrompt> getCurrentPrompts() throws PwmUnrecoverableException;

    public String getCurrentDisplayInstructions();

    public ErrorInformation respondToPrompts(final Map<String, String> answers) throws PwmUnrecoverableException;

    public VerificationState getVerificationState();

    public void init(final PwmApplication pwmApplication, final UserInfoBean userInfoBean, SessionLabel sessionLabel, Locale locale)
            throws PwmUnrecoverableException
            ;


}

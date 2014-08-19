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

package password.pwm.http.bean;

import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.FormConfiguration;
import password.pwm.config.option.MessageSendMethod;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

/**
 * @author Jason D. Rivard
 */
public class ForgottenPasswordBean implements PwmSessionBean {
// ------------------------------ FIELDS ------------------------------

    private UserInfoBean userInfo;
    private ResponseSet responseSet;
    private ChallengeSet presentableChallengeSet;
    private Locale userLocale;
    private List<FormConfiguration> attributeForm;

    private Progress progress = new Progress();
    private RecoveryFlags recoveryFlags = new RecoveryFlags();

    public UserInfoBean getUserInfo()
    {
        return userInfo;
    }

    public void setUserInfo(UserInfoBean userInfo)
    {
        this.userInfo = userInfo;
    }

    public Locale getUserLocale()
    {
        return userLocale;
    }

    public void setUserLocale(Locale userLocale)
    {
        this.userLocale = userLocale;
    }

    public Progress getProgress()
    {
        return progress;
    }

    public ResponseSet getResponseSet()
    {
        return responseSet;
    }

    public void setResponseSet(ResponseSet responseSet)
    {
        this.responseSet = responseSet;
    }

    public ChallengeSet getPresentableChallengeSet()
    {
        return presentableChallengeSet;
    }

    public void setPresentableChallengeSet(ChallengeSet presentableChallengeSet)
    {
        this.presentableChallengeSet = presentableChallengeSet;
    }

    public List<FormConfiguration> getAttributeForm()
    {
        return attributeForm;
    }

    public void setAttributeForm(List<FormConfiguration> attributeForm)
    {
        this.attributeForm = attributeForm;
    }

    public void setProgress(Progress progress)
    {
        this.progress = progress;
    }

    public RecoveryFlags getRecoveryFlags()
    {
        return recoveryFlags;
    }

    public void setRecoveryFlags(RecoveryFlags recoveryFlags)
    {
        this.recoveryFlags = recoveryFlags;
    }

    public static class Progress implements Serializable {
        private boolean responsesSatisfied;
        private boolean tokenSatisfied;
        private boolean tokenSent;
        private boolean otpSatisfied;
        private boolean allPassed;

        private MessageSendMethod tokenSendChoice;
        private String tokenSentAddress;

        public boolean isResponsesSatisfied()
        {
            return responsesSatisfied;
        }

        public void setResponsesSatisfied(boolean responsesSatisfied)
        {
            this.responsesSatisfied = responsesSatisfied;
        }

        public boolean isTokenSatisfied()
        {
            return tokenSatisfied;
        }

        public void setTokenSatisfied(boolean tokenSatisfied)
        {
            this.tokenSatisfied = tokenSatisfied;
        }

        public boolean isTokenSent()
        {
            return tokenSent;
        }

        public void setTokenSent(boolean tokenSent)
        {
            this.tokenSent = tokenSent;
        }

        public boolean isOtpSatisfied()
        {
            return otpSatisfied;
        }

        public void setOtpSatisfied(boolean otpSatisfied)
        {
            this.otpSatisfied = otpSatisfied;
        }

        public boolean isAllPassed()
        {
            return allPassed;
        }

        public void setAllPassed(boolean allPassed)
        {
            this.allPassed = allPassed;
        }

        public MessageSendMethod getTokenSendChoice()
        {
            return tokenSendChoice;
        }

        public void setTokenSendChoice(MessageSendMethod tokenSendChoice)
        {
            this.tokenSendChoice = tokenSendChoice;
        }

        public String getTokenSentAddress()
        {
            return tokenSentAddress;
        }

        public void setTokenSentAddress(String tokenSentAddress)
        {
            this.tokenSentAddress = tokenSentAddress;
        }
    }

    public static class RecoveryFlags implements Serializable {
        private boolean responsesRequired;
        private boolean tokenRequired;
        private boolean otpRequired;
        private boolean attributesRequired;
        private boolean allowWhenLdapIntruderLocked;

        public RecoveryFlags()
        {
        }

        public RecoveryFlags(
                boolean responsesRequired,
                boolean tokenRequired,
                boolean otpRequired,
                boolean attributesRequired,
                boolean allowWhenLdapIntruderLocked
        )
        {
            this.responsesRequired = responsesRequired;
            this.tokenRequired = tokenRequired;
            this.otpRequired = otpRequired;
            this.attributesRequired = attributesRequired;
            this.allowWhenLdapIntruderLocked = allowWhenLdapIntruderLocked;
        }

        public boolean isResponsesRequired()
        {
            return responsesRequired;
        }

        public boolean isTokenRequired()
        {
            return tokenRequired;
        }

        public boolean isOtpRequired()
        {
            return otpRequired;
        }

        public boolean isAttributesRequired()
        {
            return attributesRequired;
        }

        public boolean isAllowWhenLdapIntruderLocked()
        {
            return allowWhenLdapIntruderLocked;
        }
    }

}


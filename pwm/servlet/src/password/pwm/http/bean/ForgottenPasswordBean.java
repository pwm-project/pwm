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

import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import password.pwm.RecoveryVerificationMethod;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.FormConfiguration;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.RecoveryVerificationMethods;

import java.io.Serializable;
import java.util.*;

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
    private String forgottenPasswordProfileID;

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
        private boolean tokenSent;
        private boolean allPassed;
        private final Set<RecoveryVerificationMethods> satisfiedMethods = new HashSet<>();

        private MessageSendMethod tokenSendChoice;
        private String tokenSentAddress;
        private RecoveryVerificationMethods inProgressVerificationMethod;

        private transient RecoveryVerificationMethod naafRecoveryMethod;
        private transient RecoveryVerificationMethod remoteRecoveryMethod;

        public Set<RecoveryVerificationMethods> getSatisfiedMethods() {
            return satisfiedMethods;
        }

        public boolean isTokenSent()
        {
            return tokenSent;
        }

        public void setTokenSent(boolean tokenSent)
        {
            this.tokenSent = tokenSent;
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

        public RecoveryVerificationMethods getInProgressVerificationMethod() {
            return inProgressVerificationMethod;
        }

        public void setInProgressVerificationMethod(RecoveryVerificationMethods inProgressVerificationMethod) {
            this.inProgressVerificationMethod = inProgressVerificationMethod;
        }

        public void setNaafRecoveryMethod(RecoveryVerificationMethod naafRecoveryMethod) {
            this.naafRecoveryMethod = naafRecoveryMethod;
        }

        public RecoveryVerificationMethod getNaafRecoveryMethod() {
            return naafRecoveryMethod;
        }

        public RecoveryVerificationMethod getRemoteRecoveryMethod() {
            return remoteRecoveryMethod;
        }

        public void setRemoteRecoveryMethod(RecoveryVerificationMethod remoteRecoveryMethod) {
            this.remoteRecoveryMethod = remoteRecoveryMethod;
        }
    }

    public static class RecoveryFlags implements Serializable {
        private final boolean allowWhenLdapIntruderLocked;
        private final Set<RecoveryVerificationMethods> requiredAuthMethods;
        private final Set<RecoveryVerificationMethods> optionalAuthMethods;
        private final int minimumOptionalAuthMethods;
        private final MessageSendMethod tokenSendMethod;

        public RecoveryFlags()
        {
            this.requiredAuthMethods = Collections.emptySet();
            this.optionalAuthMethods = Collections.emptySet();
            this.allowWhenLdapIntruderLocked = false;
            this.minimumOptionalAuthMethods = 0;
            this.tokenSendMethod = MessageSendMethod.NONE;
        }

        public RecoveryFlags(
                final Set<RecoveryVerificationMethods> requiredAuthMethods,
                final Set<RecoveryVerificationMethods> optionalAuthMethods,
                final int minimumOptionalAuthMethods,
                final boolean allowWhenLdapIntruderLocked,
                final MessageSendMethod tokenSendMethod
        )
        {
            this.requiredAuthMethods = Collections.unmodifiableSet(requiredAuthMethods);
            this.optionalAuthMethods = Collections.unmodifiableSet(optionalAuthMethods);
            this.minimumOptionalAuthMethods = minimumOptionalAuthMethods;
            this.allowWhenLdapIntruderLocked = allowWhenLdapIntruderLocked;
            this.tokenSendMethod = tokenSendMethod;
        }

        public Set<RecoveryVerificationMethods> getRequiredAuthMethods() {
            return requiredAuthMethods;
        }

        public boolean isAllowWhenLdapIntruderLocked()
        {
            return allowWhenLdapIntruderLocked;
        }

        public MessageSendMethod getTokenSendMethod() {
            return tokenSendMethod;
        }

        public Set<RecoveryVerificationMethods> getOptionalAuthMethods() {
            return optionalAuthMethods;
        }

        public int getMinimumOptionalAuthMethods() {
            return minimumOptionalAuthMethods;
        }
    }

    public String getForgottenPasswordProfileID() {
        return forgottenPasswordProfileID;
    }

    public void setForgottenPasswordProfileID(String forgottenPasswordProfileID) {
        this.forgottenPasswordProfileID = forgottenPasswordProfileID;
    }
}


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

import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import password.pwm.VerificationMethodSystem;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.FormConfiguration;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.IdentityVerificationMethod;

import java.io.Serializable;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class ForgottenPasswordBean extends PwmSessionBean {
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
        private final Set<IdentityVerificationMethod> satisfiedMethods = new HashSet<>();

        private MessageSendMethod tokenSendChoice;
        private String tokenSentAddress;
        private IdentityVerificationMethod inProgressVerificationMethod;

        private transient VerificationMethodSystem naafRecoveryMethod;
        private transient VerificationMethodSystem remoteRecoveryMethod;

        public Set<IdentityVerificationMethod> getSatisfiedMethods() {
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

        public IdentityVerificationMethod getInProgressVerificationMethod() {
            return inProgressVerificationMethod;
        }

        public void setInProgressVerificationMethod(IdentityVerificationMethod inProgressVerificationMethod) {
            this.inProgressVerificationMethod = inProgressVerificationMethod;
        }

        public void setNaafRecoveryMethod(VerificationMethodSystem naafRecoveryMethod) {
            this.naafRecoveryMethod = naafRecoveryMethod;
        }

        public VerificationMethodSystem getNaafRecoveryMethod() {
            return naafRecoveryMethod;
        }

        public VerificationMethodSystem getRemoteRecoveryMethod() {
            return remoteRecoveryMethod;
        }

        public void setRemoteRecoveryMethod(VerificationMethodSystem remoteRecoveryMethod) {
            this.remoteRecoveryMethod = remoteRecoveryMethod;
        }
    }

    public static class RecoveryFlags implements Serializable {
        private final boolean allowWhenLdapIntruderLocked;
        private final Set<IdentityVerificationMethod> requiredAuthMethods;
        private final Set<IdentityVerificationMethod> optionalAuthMethods;
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
                final Set<IdentityVerificationMethod> requiredAuthMethods,
                final Set<IdentityVerificationMethod> optionalAuthMethods,
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

        public Set<IdentityVerificationMethod> getRequiredAuthMethods() {
            return requiredAuthMethods;
        }

        public boolean isAllowWhenLdapIntruderLocked()
        {
            return allowWhenLdapIntruderLocked;
        }

        public MessageSendMethod getTokenSendMethod() {
            return tokenSendMethod;
        }

        public Set<IdentityVerificationMethod> getOptionalAuthMethods() {
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

    public Type getType() {
        return Type.PUBLIC;
    }

    public Set<Flag> getFlags() {
        return Collections.singleton(Flag.ProhibitCookieSession);
    }
}


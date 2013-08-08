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

package password.pwm.bean.servlet;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import password.pwm.bean.PwmSessionBean;
import password.pwm.config.FormConfiguration;

import java.util.List;

/**
 * @author Jason D. Rivard
 */
public class ForgottenPasswordBean implements PwmSessionBean {
// ------------------------------ FIELDS ------------------------------

    private transient ChaiUser proxiedUser;
    private transient ResponseSet responseSet;
    private ChallengeSet challengeSet;
    private String tokenSendAddress;

    private boolean responsesSatisfied;
    private boolean tokenSatisfied;
    private boolean allPassed;
    
    private boolean passwordEmailSent = false;
    private boolean passwordSmsSent = false;

    private List<FormConfiguration> attributeForm;

// --------------------- GETTER / SETTER METHODS ---------------------


    public ResponseSet getResponseSet() {
        return responseSet;
    }

    public void setResponseSet(ResponseSet responseSet) {
        this.responseSet = responseSet;
    }

    public ChallengeSet getChallengeSet() {
        return challengeSet;
    }

    public void setChallengeSet(ChallengeSet challengeSet) {
        this.challengeSet = challengeSet;
    }

    public ChaiUser getProxiedUser() {
        return proxiedUser;
    }

    public void setProxiedUser(final ChaiUser proxiedUser) {
        this.proxiedUser = proxiedUser;
    }

    public boolean isResponsesSatisfied() {
        return responsesSatisfied;
    }

    public void setResponsesSatisfied(final boolean responsesSatisfied) {
        this.responsesSatisfied = responsesSatisfied;
    }

    public boolean isTokenSatisfied() {
        return tokenSatisfied;
    }

    public void setTokenSatisfied(final boolean passedToken) {
        this.tokenSatisfied = passedToken;
    }

    public boolean isAllPassed() {
        return allPassed;
    }

    public void setAllPassed(final boolean allPassed) {
        this.allPassed = allPassed;
    }

    public String getTokenSendAddress() {
        return tokenSendAddress;
    }

    public void setTokenSendAddress(final String tokenSendAddress) {
        this.tokenSendAddress = tokenSendAddress;
    }

    public List<FormConfiguration> getAttributeForm() {
        return attributeForm;
    }

    public void setAttributeForm(final List<FormConfiguration> attributeForm) {
        this.attributeForm = attributeForm;
    }

    public void setPasswordEmailSent(boolean passwordEmailSent) {
        this.passwordEmailSent = passwordEmailSent;
    }

    public boolean getPasswordEmailSent() {
        return passwordEmailSent;
    }

    public void setPasswordSmsSent(boolean passwordSmsSent) {
        this.passwordSmsSent = passwordSmsSent;
    }

    public boolean getPasswordSmsSent() {
        return passwordSmsSent;
    }
}


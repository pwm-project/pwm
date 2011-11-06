/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChallengeSet;

import java.io.Serializable;

/**
 * @author Jason D. Rivard
 */
public class ForgottenPasswordBean implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private transient ChaiUser proxiedUser;
    private transient ChallengeSet challengeSet;
    private String tokenEmailAddress;
    private String tokenSmsNumber;

    private boolean responsesSatisfied;
    private boolean tokenSatisfied;
    private boolean allPassed;

    private boolean commEmailUsed = false;
    private boolean commSmsUsed = false;

// --------------------- GETTER / SETTER METHODS ---------------------


    public ChallengeSet getChallengeSet() {
        return challengeSet;
    }

    public void setChallengeSet(final ChallengeSet challengeSet) {
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

    public String getTokenEmailAddress() {
        return tokenEmailAddress;
    }

    public void setTokenEmailAddress(final String tokenEmailAddress) {
        this.tokenEmailAddress = tokenEmailAddress;
    }

    public String getTokenSmsNumber() {
        return tokenSmsNumber;
    }

    public void setTokenSmsNumber(final String tokenSmsNumber) {
        this.tokenSmsNumber = tokenSmsNumber;
    }
    
    public void setEmailUsed(final boolean used) {
    	this.commEmailUsed = used;
    }
    
    public boolean getEmailUsed() {
    	return commEmailUsed;
    }

    public void setSmsUsed(final boolean used) {
    	this.commSmsUsed = used;
    }
    
    public boolean getSmsUsed() {
    	return commSmsUsed;
    }
}


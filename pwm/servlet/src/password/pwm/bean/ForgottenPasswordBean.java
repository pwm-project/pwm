/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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
import com.novell.ldapchai.cr.ResponseSet;

import java.io.Serializable;

/**
 * @author Jason D. Rivard
 */
public class ForgottenPasswordBean implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private transient ChaiUser proxiedUser;
    private transient ResponseSet responseSet;
    private ChallengeSet challengeSet;
    private boolean responsesSatisfied;

// --------------------- GETTER / SETTER METHODS ---------------------

    public ResponseSet getResponseSet() {
        return responseSet;
    }

    public void setResponseSet(ResponseSet responseSet) {
        this.responseSet = responseSet;
    }

    public ChaiUser getProxiedUser() {
        return proxiedUser;
    }

    public void setProxiedUser(ChaiUser proxiedUser) {
        this.proxiedUser = proxiedUser;
    }

    public boolean isResponsesSatisfied() {
        return responsesSatisfied;
    }

    public void setResponsesSatisfied(boolean responsesSatisfied) {
        this.responsesSatisfied = responsesSatisfied;
    }

    public ChallengeSet getChallengeSet() {
        return challengeSet;
    }

    public void setChallengeSet(ChallengeSet challengeSet) {
        this.challengeSet = challengeSet;
    }
}


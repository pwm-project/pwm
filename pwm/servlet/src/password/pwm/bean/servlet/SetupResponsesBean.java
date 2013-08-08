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

import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import password.pwm.bean.PwmSessionBean;

import java.io.Serializable;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public class SetupResponsesBean implements PwmSessionBean {
    private SetupData responseData;
    private SetupData helpdeskResponseData;
    private boolean responsesSatisfied;
    private boolean helpdeskResponsesSatisfied;
    private boolean confirmed;
    private Locale userLocale;

    public SetupResponsesBean() {
    }

    public SetupData getResponseData() {
        return responseData;
    }

    public void setResponseData(SetupData responseData) {
        this.responseData = responseData;
    }

    public SetupData getHelpdeskResponseData() {
        return helpdeskResponseData;
    }

    public void setHelpdeskResponseData(SetupData helpdeskResponseData) {
        this.helpdeskResponseData = helpdeskResponseData;
    }

    public boolean isResponsesSatisfied() {
        return responsesSatisfied;
    }

    public void setResponsesSatisfied(boolean responsesSatisfied) {
        this.responsesSatisfied = responsesSatisfied;
    }

    public boolean isHelpdeskResponsesSatisfied() {
        return helpdeskResponsesSatisfied;
    }

    public void setHelpdeskResponsesSatisfied(boolean helpdeskResponsesSatisfied) {
        this.helpdeskResponsesSatisfied = helpdeskResponsesSatisfied;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public Locale getUserLocale() {
        return userLocale;
    }

    public void setUserLocale(Locale userLocale) {
        this.userLocale = userLocale;
    }

    public static class SetupData implements Serializable {
        private ChallengeSet challengeSet;
        private Map<String, Challenge> indexedChallenges = Collections.emptyMap();
        private boolean simpleMode;
        private int minRandomSetup;
        private Map<Challenge, String> responseMap = Collections.emptyMap();

        public SetupData() {
        }

        public ChallengeSet getChallengeSet() {
            return challengeSet;
        }

        public void setChallengeSet(ChallengeSet challengeSet) {
            this.challengeSet = challengeSet;
        }

        public Map<String, Challenge> getIndexedChallenges() {
            return indexedChallenges;
        }

        public void setIndexedChallenges(Map<String, Challenge> indexedChallenges) {
            this.indexedChallenges = indexedChallenges;
        }

        public boolean isSimpleMode() {
            return simpleMode;
        }

        public void setSimpleMode(boolean simpleMode) {
            this.simpleMode = simpleMode;
        }

        public int getMinRandomSetup() {
            return minRandomSetup;
        }

        public void setMinRandomSetup(int minRandomSetup) {
            this.minRandomSetup = minRandomSetup;
        }

        public Map<Challenge, String> getResponseMap() {
            return responseMap;
        }

        public void setResponseMap(Map<Challenge, String> responseMap) {
            this.responseMap = responseMap;
        }
    }
}
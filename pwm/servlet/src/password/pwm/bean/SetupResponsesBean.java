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

import com.novell.ldapchai.cr.Challenge;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

public class SetupResponsesBean implements Serializable {
    private Map<String, Challenge> challengeList = Collections.emptyMap();
    private boolean simpleMode;
    private Map<Challenge, String> responseMap = Collections.emptyMap();

    public Map<String, Challenge> getIndexedChallenges() {
        return challengeList;
    }

    public void setChallengeList(Map<String, Challenge> challengeList) {
        this.challengeList = challengeList;
    }

    public boolean isSimpleMode() {
        return simpleMode;
    }

    public void setSimpleMode(boolean simpleMode) {
        this.simpleMode = simpleMode;
    }

    public Map<Challenge, String> getResponseMap() {
        return responseMap;
    }

    public void setResponseMap(Map<Challenge, String> responseMap) {
        this.responseMap = responseMap;
    }
}
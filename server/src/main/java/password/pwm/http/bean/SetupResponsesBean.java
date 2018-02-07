/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import password.pwm.config.option.SessionBeanMode;

import java.io.Serializable;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SetupResponsesBean extends PwmSessionBean
{
    private boolean hasExistingResponses;
    private SetupData responseData;
    private SetupData helpdeskResponseData;
    private boolean responsesSatisfied;
    private boolean helpdeskResponsesSatisfied;
    private boolean confirmed;
    private Locale userLocale;

    public Type getType( )
    {
        return Type.AUTHENTICATED;
    }

    public SetupData getResponseData( )
    {
        return responseData;
    }

    public void setResponseData( final SetupData responseData )
    {
        this.responseData = responseData;
    }

    public SetupData getHelpdeskResponseData( )
    {
        return helpdeskResponseData;
    }

    public void setHelpdeskResponseData( final SetupData helpdeskResponseData )
    {
        this.helpdeskResponseData = helpdeskResponseData;
    }

    public boolean isResponsesSatisfied( )
    {
        return responsesSatisfied;
    }

    public void setResponsesSatisfied( final boolean responsesSatisfied )
    {
        this.responsesSatisfied = responsesSatisfied;
    }

    public boolean isHelpdeskResponsesSatisfied( )
    {
        return helpdeskResponsesSatisfied;
    }

    public void setHelpdeskResponsesSatisfied( final boolean helpdeskResponsesSatisfied )
    {
        this.helpdeskResponsesSatisfied = helpdeskResponsesSatisfied;
    }

    public boolean isConfirmed( )
    {
        return confirmed;
    }

    public void setConfirmed( final boolean confirmed )
    {
        this.confirmed = confirmed;
    }

    public Locale getUserLocale( )
    {
        return userLocale;
    }

    public void setUserLocale( final Locale userLocale )
    {
        this.userLocale = userLocale;
    }

    public boolean isHasExistingResponses( )
    {
        return hasExistingResponses;
    }

    public void setHasExistingResponses( final boolean hasExistingResponses )
    {
        this.hasExistingResponses = hasExistingResponses;
    }

    public static class SetupData implements Serializable
    {
        private ChallengeSet challengeSet;
        private Map<String, Challenge> indexedChallenges = Collections.emptyMap();
        private boolean simpleMode;
        private int minRandomSetup;
        private Map<Challenge, String> responseMap = Collections.emptyMap();

        public SetupData( )
        {
        }

        public ChallengeSet getChallengeSet( )
        {
            return challengeSet;
        }

        public void setChallengeSet( final ChallengeSet challengeSet )
        {
            this.challengeSet = challengeSet;
        }

        public Map<String, Challenge> getIndexedChallenges( )
        {
            return indexedChallenges;
        }

        public void setIndexedChallenges( final Map<String, Challenge> indexedChallenges )
        {
            this.indexedChallenges = indexedChallenges;
        }

        public boolean isSimpleMode( )
        {
            return simpleMode;
        }

        public void setSimpleMode( final boolean simpleMode )
        {
            this.simpleMode = simpleMode;
        }

        public int getMinRandomSetup( )
        {
            return minRandomSetup;
        }

        public void setMinRandomSetup( final int minRandomSetup )
        {
            this.minRandomSetup = minRandomSetup;
        }

        public Map<Challenge, String> getResponseMap( )
        {
            return responseMap;
        }

        public void setResponseMap( final Map<Challenge, String> responseMap )
        {
            this.responseMap = responseMap;
        }
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.singleton( SessionBeanMode.LOCAL );
    }
}

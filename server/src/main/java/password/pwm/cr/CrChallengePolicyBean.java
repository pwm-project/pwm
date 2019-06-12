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

package password.pwm.cr;

import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.bean.ChallengeSetBean;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Value
@Builder
public class CrChallengePolicyBean implements Serializable, ChallengeSet
{
    private Locale locale;
    private List<Challenge> challenges;
    private List<Challenge> helpdeskChallenges;
    private int minRandomRequired;
    private int helpdeskMinRandomRequired;

    @Override
    public List<Challenge> getAdminDefinedChallenges()
    {
        return challenges.stream()
                .filter( Challenge::isAdminDefined )
                .collect( Collectors.toList() );
    }

    @Override
    public List<String> getChallengeTexts()
    {

        final List<String> returnList = new ArrayList<>();
        challenges.stream()
                .forEach( challenge -> returnList.add( challenge.getChallengeText() ) );
        return Collections.unmodifiableList( returnList );
    }

    @Override
    public List<Challenge> getRandomChallenges()
    {
        return challenges.stream()
                .filter( challenge -> !challenge.isRequired() )
                .collect( Collectors.toList() );
    }

    @Override
    public List<Challenge> getRequiredChallenges()
    {
        return challenges.stream()
                .filter( Challenge::isRequired )
                .collect( Collectors.toList() );
    }

    @Override
    public List<Challenge> getUserDefinedChallenges()
    {
        return challenges.stream()
                .filter( crChallengeItemBean -> !crChallengeItemBean.isAdminDefined() )
                .collect( Collectors.toList() );
    }

    @Override
    public int minimumResponses()
    {
        int mininimumResponses = 0;

        mininimumResponses += getRequiredChallenges().size();
        mininimumResponses += getMinRandomRequired();

        return mininimumResponses;
    }

    @Override
    public boolean isLocked()
    {
        return true;
    }

    @Override
    public void lock()
    {

    }

    @Override
    public String getIdentifier()
    {
        return null;
    }

    public static CrChallengePolicyBean fromChallengeSet( final ChallengeSet challengeSet )
    {
        return null;
    }

    @Override
    public ChallengeSetBean asChallengeSetBean()
    {
        return null;
    }
}

/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

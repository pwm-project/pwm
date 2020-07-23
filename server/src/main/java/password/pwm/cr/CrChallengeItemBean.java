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
import com.novell.ldapchai.cr.bean.ChallengeBean;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@Value
@Builder
public class CrChallengeItemBean implements Serializable, Challenge
{
    private String challengeText;
    private int minLength;
    private int maxLength;
    private boolean adminDefined;
    private boolean required;
    private int maxQuestionCharsInAnswer;
    private boolean enforceWordlist;

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
    public void setChallengeText( final String challengeText )
    {
        throw new IllegalStateException();
    }

    @Override
    public ChallengeBean asChallengeBean()
    {
        throw new IllegalStateException();
    }
}

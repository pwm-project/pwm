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

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

package password.pwm.config.value.data;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class ChallengeItemConfiguration implements Serializable
{
    private String text;
    private int minLength;
    private int maxLength;
    private boolean adminDefined;

    private boolean enforceWordlist;
    private int maxQuestionCharsInAnswer;
    private int points;
    private String setupGuide;
    private String regex;

    public ChallengeItemConfiguration(
            final String challengeText,
            final int minimumLength,
            final int maximumLength,
            final boolean adminDefined
    )
    {
        this.text = challengeText;
        this.minLength = minimumLength;
        this.maxLength = maximumLength;
        this.adminDefined = adminDefined;
    }
}

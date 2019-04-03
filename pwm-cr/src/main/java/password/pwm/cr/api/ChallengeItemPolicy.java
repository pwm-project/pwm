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

package password.pwm.cr.api;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@Builder
@Value
public class ChallengeItemPolicy implements Serializable
{
    @Builder.Default
    private final String questionText = "";

    @Builder.Default
    private int minLength = 1;

    @Builder.Default
    private int maxLength = 255;

    @Builder.Default
    private int maxQuestionCharsInAnswer = 0;

    @Builder.Default
    private boolean enforceWordList = false;

    @Builder.Default
    private QuestionSource questionSource = QuestionSource.ADMIN_DEFINED;

    @Builder.Default
    private ResponseLevel responseLevel = ResponseLevel.REQUIRED;

    public void validate( ) throws IllegalArgumentException
    {
        if ( questionSource == null )
        {
            throw new IllegalArgumentException( "questionSource can not be null" );
        }

        if ( responseLevel == null )
        {
            throw new IllegalArgumentException( "responseLevel can not be null" );
        }

        if ( questionText == null || questionText.isEmpty() )
        {
            if ( questionSource == QuestionSource.ADMIN_DEFINED )
            {
                throw new IllegalArgumentException( "questionText is required when questionSource is "
                        + QuestionSource.ADMIN_DEFINED.toString() );
            }
        }

        if ( minLength < 1 )
        {
            throw new IllegalArgumentException( "minLength must be greater than zero" );
        }

        if ( maxLength < 1 )
        {
            throw new IllegalArgumentException( "maxLength must be greater than zero" );
        }

        if ( maxQuestionCharsInAnswer < 0 )
        {
            throw new IllegalArgumentException( "maxQuestionCharsInAnswer must be zero or greater" );
        }
    }
}

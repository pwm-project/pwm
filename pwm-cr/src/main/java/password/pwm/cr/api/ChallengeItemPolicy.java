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

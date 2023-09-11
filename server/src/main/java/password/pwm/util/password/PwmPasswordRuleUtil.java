/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.util.password;

import org.apache.commons.lang3.StringUtils;
import password.pwm.util.java.StringUtil;

import java.util.List;

class PwmPasswordRuleUtil
{
    private PwmPasswordRuleUtil()
    {
    }

    public static boolean tooManyConsecutiveChars( final String str, final int maximumConsecutive )
    {
        if ( str != null && maximumConsecutive > 1 && str.length() >= maximumConsecutive )
        {
            final int[] codePoints = StringUtil.toCodePointArray( str.toLowerCase() );

            int lastCodePoint = -1;
            int consecutiveCharCount = 1;

            for ( final int codePoint : codePoints )
            {
                if ( codePoint == lastCodePoint + 1 )
                {
                    consecutiveCharCount++;
                }
                else
                {
                    consecutiveCharCount = 1;
                }

                lastCodePoint = codePoint;

                if ( consecutiveCharCount == maximumConsecutive )
                {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean containsDisallowedValue( final String password, final String disallowedValue, final int threshold )
    {
        if ( StringUtil.notEmpty( disallowedValue ) )
        {
            if ( threshold > 0 )
            {
                if ( disallowedValue.length() >= threshold )
                {
                    final List<String> disallowedValueChunks = StringUtil.createStringChunks( disallowedValue, threshold );
                    for ( final String chunk : disallowedValueChunks )
                    {
                        if ( StringUtils.containsIgnoreCase( password, chunk ) )
                        {
                            return true;
                        }
                    }
                }
            }
            else
            {
                // No threshold?  Then the password can't contain the whole disallowed value
                return StringUtils.containsIgnoreCase( password, disallowedValue );
            }
        }

        return false;
    }
}

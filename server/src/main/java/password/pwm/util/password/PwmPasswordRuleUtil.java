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

package password.pwm.util.password;

import org.apache.commons.lang3.StringUtils;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.util.PasswordCharCounter;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PwmPasswordRuleUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmPasswordRuleUtil.class );

    private PwmPasswordRuleUtil()
    {
    }

    /**
     * Check a supplied password for it's validity according to AD complexity rules.
     * - Not contain the user's account name or parts of the user's full name that exceed two consecutive characters
     * - Be at least six characters in length
     * - Contain characters from three of the following five categories:
     * - English uppercase characters (A through Z)
     * - English lowercase characters (a through z)
     * - Base 10 digits (0 through 9)
     * - Non-alphabetic characters (for example, !, $, #, %)
     * - Any character categorized as an alphabetic but is not uppercase or lowercase.
     * <p/>
     * See this article: http://technet.microsoft.com/en-us/library/cc786468%28WS.10%29.aspx
     *
     * @param userInfo    userInfoBean
     * @param password    password to test
     * @param charCounter associated charCounter for the password.
     * @return list of errors if the password does not meet requirements, or an empty list if the password complies
     *         with AD requirements
     */

    static List<ErrorInformation> checkPasswordForADComplexity(
            final ADPolicyComplexity complexityLevel,
            final UserInfo userInfo,
            final String password,
            final PasswordCharCounter charCounter,
            final int maxGroupViolationCount
    )
            throws PwmUnrecoverableException
    {
        final List<ErrorInformation> errorList = new ArrayList<>();

        if ( password == null || password.length() < 6 )
        {
            errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_SHORT ) );
            return errorList;
        }

        final int maxLength = complexityLevel == ADPolicyComplexity.AD2003 ? 128 : 512;
        if ( password.length() > maxLength )
        {
            errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_LONG ) );
            return errorList;
        }

        if ( userInfo != null && userInfo.getCachedPasswordRuleAttributes() != null )
        {
            final Map<String, String> userAttrs = userInfo.getCachedPasswordRuleAttributes();
            final String samAccountName = userAttrs.get( "sAMAccountName" );
            if ( samAccountName != null
                    && samAccountName.length() > 2
                    && samAccountName.length() >= password.length() )
            {
                if ( password.toLowerCase().contains( samAccountName.toLowerCase() ) )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_INWORDLIST ) );
                    LOGGER.trace( () -> "Password violation due to ADComplexity check: Password contains sAMAccountName" );
                }
            }
            final String displayName = userAttrs.get( "displayName" );
            if ( displayName != null && displayName.length() > 2 )
            {
                if ( checkContainsTokens( password, displayName ) )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_INWORDLIST ) );
                    LOGGER.trace( () -> "Password violation due to ADComplexity check: Tokens from displayName used in password" );
                }
            }
        }

        int complexityPoints = 0;
        if ( charCounter.getUpperCharCount() > 0 )
        {
            complexityPoints++;
        }
        if ( charCounter.getLowerCharCount() > 0 )
        {
            complexityPoints++;
        }
        if ( charCounter.getNumericCharCount() > 0 )
        {
            complexityPoints++;
        }
        switch ( complexityLevel )
        {
            case AD2003:
                if ( charCounter.getSpecialCharsCount() > 0 || charCounter.getOtherLetterCharCount() > 0 )
                {
                    complexityPoints++;
                }
                break;

            case AD2008:
                if ( charCounter.getSpecialCharsCount() > 0 )
                {
                    complexityPoints++;
                }
                if ( charCounter.getOtherLetterCharCount() > 0 )
                {
                    complexityPoints++;
                }
                break;

            default:
                JavaHelper.unhandledSwitchStatement( complexityLevel );
        }

        switch ( complexityLevel )
        {
            case AD2008:
                final int totalGroups = 5;
                final int violations = totalGroups - complexityPoints;
                if ( violations <= maxGroupViolationCount )
                {
                    return errorList;
                }
                break;

            case AD2003:
                if ( complexityPoints >= 3 )
                {
                    return errorList;
                }
                break;

            default:
                JavaHelper.unhandledSwitchStatement( complexityLevel );
        }

        if ( charCounter.getUpperCharCount() < 1 )
        {
            errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) );
        }
        if ( charCounter.getLowerCharCount() < 1 )
        {
            errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) );
        }
        if ( charCounter.getNumericCharCount() < 1 )
        {
            errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_NUM ) );
        }
        if ( charCounter.getSpecialCharsCount() < 1 )
        {
            errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) );
        }
        if ( charCounter.getOtherLetterCharCount() < 1 )
        {
            errorList.add( new ErrorInformation( PwmError.PASSWORD_UNKNOWN_VALIDATION ) );
        }

        return errorList;
    }

    // escape characters permitted because they match the exact AD specification
    @SuppressWarnings( "checkstyle:avoidescapedunicodecharacters" )
    private static boolean checkContainsTokens( final String baseValue, final String checkPattern )
    {
        if ( baseValue == null || baseValue.length() == 0 )
        {
            return false;
        }

        if ( checkPattern == null || checkPattern.length() == 0 )
        {
            return false;
        }

        final String baseValueLower = baseValue.toLowerCase();

        final String[] tokens = checkPattern.toLowerCase().split( "[,\\.\\-\u2013\u2014_ \u00a3\\t]+" );

        if ( tokens != null && tokens.length > 0 )
        {
            for ( final String token : tokens )
            {
                if ( token.length() > 2 )
                {
                    if ( baseValueLower.contains( token ) )
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean tooManyConsecutiveChars( final String str, final int maximumConsecutive )
    {
        if ( str != null && maximumConsecutive > 1 && str.length() >= maximumConsecutive )
        {
            final int[] codePoints = StringUtil.toCodePointArray( str.toLowerCase() );

            int lastCodePoint = -1;
            int consecutiveCharCount = 1;

            for ( int i = 0; i < codePoints.length; i++ )
            {
                if ( codePoints[ i ] == lastCodePoint + 1 )
                {
                    consecutiveCharCount++;
                }
                else
                {
                    consecutiveCharCount = 1;
                }

                lastCodePoint = codePoints[ i ];

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
        if ( !StringUtil.isEmpty( disallowedValue ) )
        {
            if ( threshold > 0 )
            {
                if ( disallowedValue.length() >= threshold )
                {
                    final String[] disallowedValueChunks = StringUtil.createStringChunks( disallowedValue, threshold );
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

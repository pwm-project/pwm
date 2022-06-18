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

import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.user.UserInfo;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AD Password Rule Utility for checking AD password complexity.
 */
class PwmPasswordAdRuleUtil
{
    private PwmPasswordAdRuleUtil()
    {
    }

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmPasswordAdRuleUtil.class );

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
        if ( password == null || password.length() < 6 )
        {
            return Collections.singletonList( new ErrorInformation( PwmError.PASSWORD_TOO_SHORT ) );
        }

        final int maxLength = complexityLevel == ADPolicyComplexity.AD2003 ? 128 : 512;
        if ( password.length() > maxLength )
        {
            return Collections.singletonList( new ErrorInformation( PwmError.PASSWORD_TOO_LONG ) );
        }

        final List<ErrorInformation> errorList = new ArrayList<>( AdCheckHelper.checkPasswordRuleAttributes( userInfo, password ) );

        final int complexityPoints = AdCheckHelper.calculateComplexity( charCounter, complexityLevel );

        errorList.addAll( AdCheckHelper.makeComplexityViolationErrors( complexityPoints, maxGroupViolationCount, charCounter, complexityLevel ) );

        return Collections.unmodifiableList( errorList );
    }

    private static class AdCheckHelper
    {
        private static List<ErrorInformation> makeComplexityViolationErrors(
                final int complexityPoints,
                final int maxGroupViolationCount,
                final PasswordCharCounter charCounter,
                final ADPolicyComplexity complexityLevel
        )
        {
            // exit if complexity violations < max
            switch ( complexityLevel )
            {
                case AD2008:
                    final int totalGroups = 5;
                    final int violations = totalGroups - complexityPoints;
                    if ( violations <= maxGroupViolationCount )
                    {
                        return Collections.emptyList();
                    }
                    break;

                case AD2003:
                    if ( complexityPoints >= 3 )
                    {
                        return Collections.emptyList();
                    }
                    break;

                default:
                    MiscUtil.unhandledSwitchStatement( complexityLevel );
            }

            final List<ErrorInformation> errorList = new ArrayList<>();

            // add errors complexity violations
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

            return Collections.unmodifiableList( errorList );
        }

        private static List<ErrorInformation> checkPasswordRuleAttributes(
                final UserInfo userInfo,
                final String password
        )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();

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
                    if ( AdCheckHelper.checkContainsTokens( password, displayName ) )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_INWORDLIST ) );
                        LOGGER.trace( () -> "Password violation due to ADComplexity check: Tokens from displayName used in password" );
                    }
                }
            }

            return Collections.unmodifiableList( errorList );
        }

        private static int calculateComplexity(
                final PasswordCharCounter charCounter,
                final ADPolicyComplexity complexityLevel
        )
        {
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
                    MiscUtil.unhandledSwitchStatement( complexityLevel );
            }

            return complexityPoints;
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

            if ( tokens.length > 0 )
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
    }
}

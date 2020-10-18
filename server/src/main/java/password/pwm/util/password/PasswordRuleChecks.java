/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.PwmService;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Contains validation logic for the most of the "internal" {@link PwmPasswordRule} rules.
 */
public class PasswordRuleChecks
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordRuleChecks.class );

    private static final boolean EXTRA_LOGGING = false;

    @Data
    @Builder
    private static class RuleCheckData
    {
        private PwmApplication pwmApplication;
        private PwmPasswordPolicy policy;
        private UserInfo userInfo;
        private PasswordRuleReaderHelper ruleHelper;
        private PasswordCharCounter charCounter;
        private MacroRequest macroRequest;
    }

    private interface RuleChecker
    {
        List<ErrorInformation> test(
                String password,
                String oldPassword,
                RuleCheckData ruleCheckData
        )
                throws PwmUnrecoverableException;
    }

    private static final List<RuleChecker> RULE_CHECKS = Collections.unmodifiableList( Arrays.asList(
            new OldPasswordRuleChecker(),
            new MinimumLengthRuleChecker(),
            new MaximumLengthRuleChecker(),
            new NumericLimitsRuleChecker(),
            new AlphaLimitsRuleChecker(),
            new CasingLimitsRuleChecker(),
            new SpecialLimitsRuleChecker(),
            new UniqueCharRuleChecker(),
            new CharSequenceRuleChecker(),
            new ActiveDirectoryRuleChecker(),
            new DisallowedValueRuleChecker(),
            new DisallowedAttributeRuleChecker(),
            new PasswordStrengthRuleChecker(),
            new RegexPatternsRuleChecker(),
            new CharGroupRuleChecker(),
            new DictionaryRuleChecker(),
            new SharedHistoryRuleChecker()
    ) );


    public static List<ErrorInformation> extendedPolicyRuleChecker(
            final PwmApplication pwmApplication,
            final PwmPasswordPolicy policy,
            final String password,
            final String oldPassword,
            final UserInfo userInfo,
            final PwmPasswordRuleValidator.Flag... flags

    )
            throws PwmUnrecoverableException
    {
        final boolean failFast = JavaHelper.enumArrayContainsValue( flags, PwmPasswordRuleValidator.Flag.FailFast );

        // null check
        if ( password == null )
        {
            return Collections.singletonList( new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "empty (null) new password" ) );
        }

        final List<ErrorInformation> errorList = new ArrayList<>();
        final MacroRequest macroRequest = userInfo == null || userInfo.getUserIdentity() == null
                ? MacroRequest.forNonUserSpecific( pwmApplication, SessionLabel.SYSTEM_LABEL )
                : MacroRequest.forUser(
                pwmApplication,
                PwmConstants.DEFAULT_LOCALE,
                SessionLabel.SYSTEM_LABEL,
                userInfo.getUserIdentity()
        );

        final RuleCheckData ruleCheckData = RuleCheckData.builder()
                .pwmApplication( pwmApplication )
                .policy( policy )
                .userInfo( userInfo )
                .ruleHelper( policy.getRuleHelper() )
                .macroRequest( macroRequest )
                .charCounter( new PasswordCharCounter( password ) )
                .build();

        for ( final RuleChecker ruleChecker : RULE_CHECKS )
        {
            errorList.addAll( ruleChecker.test( password, oldPassword, ruleCheckData ) );

            if ( failFast && !errorList.isEmpty() )
            {
                return errorList;
            }
        }

        return errorList;
    }

    private static class OldPasswordRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();

            //check against old password
            if ( !StringUtil.isEmpty( oldPassword ) && ruleHelper.readBooleanValue( PwmPasswordRule.DisallowCurrent ) )
            {
                if ( oldPassword.equalsIgnoreCase( password ) )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_SAMEASOLD ) );
                }

                //check chars from old password
                final int maxOldAllowed = ruleHelper.readIntValue( PwmPasswordRule.MaximumOldChars );
                if ( maxOldAllowed > 0 )
                {
                    final String lPassword = password.toLowerCase();
                    final Set<Character> dupeChars = new HashSet<>();

                    //add all dupes to the set.
                    for ( final char loopChar : oldPassword.toLowerCase().toCharArray() )
                    {
                        if ( lPassword.indexOf( loopChar ) != -1 )
                        {
                            dupeChars.add( loopChar );
                        }
                    }

                    //count the number of (unique) set elements.
                    if ( dupeChars.size() >= maxOldAllowed )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_OLD_CHARS ) );
                    }
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class MinimumLengthRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            //Check minimum length
            if ( password.length() < ruleCheckData.getRuleHelper().readIntValue( PwmPasswordRule.MinimumLength ) )
            {
                return Collections.singletonList( new ErrorInformation( PwmError.PASSWORD_TOO_SHORT ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MaximumLengthRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPasswordString, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            //Check maximum length
            {
                final int passwordMaximumLength = ruleCheckData.getRuleHelper().readIntValue( PwmPasswordRule.MaximumLength );

                if ( passwordMaximumLength > 0 && password.length() > passwordMaximumLength )
                {
                    return Collections.singletonList( new ErrorInformation( PwmError.PASSWORD_TOO_LONG ) );
                }
            }
            return Collections.emptyList();
        }
    }

    private static class NumericLimitsRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            //check number of numeric characters
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();
            final PasswordCharCounter charCounter = ruleCheckData.getCharCounter();
            {
                final int numberOfNumericChars = charCounter.getNumericCharCount();
                if ( ruleHelper.readBooleanValue( PwmPasswordRule.AllowNumeric ) )
                {
                    if ( numberOfNumericChars < ruleHelper.readIntValue( PwmPasswordRule.MinimumNumeric ) )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_NUM ) );
                    }

                    final int maxNumeric = ruleHelper.readIntValue( PwmPasswordRule.MaximumNumeric );
                    if ( maxNumeric > 0 && numberOfNumericChars > maxNumeric )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_NUMERIC ) );
                    }

                    if ( !ruleHelper.readBooleanValue(
                            PwmPasswordRule.AllowFirstCharNumeric ) && charCounter.isFirstNumeric() )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_FIRST_IS_NUMERIC ) );
                    }

                    if ( !ruleHelper.readBooleanValue(
                            PwmPasswordRule.AllowLastCharNumeric ) && charCounter.isLastNumeric() )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_LAST_IS_NUMERIC ) );
                    }
                }
                else
                {
                    if ( numberOfNumericChars > 0 )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_NUMERIC ) );
                    }
                }
            }
            return Collections.unmodifiableList( errorList );
        }
    }

    private static class CasingLimitsRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();
            final PasswordCharCounter charCounter = ruleCheckData.getCharCounter();

            //check number of upper characters
            {
                final int numberOfUpperChars = charCounter.getUpperCharCount();
                if ( numberOfUpperChars < ruleHelper.readIntValue( PwmPasswordRule.MinimumUpperCase ) )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) );
                }

                final int maxUpper = ruleHelper.readIntValue( PwmPasswordRule.MaximumUpperCase );
                if ( maxUpper > 0 && numberOfUpperChars > maxUpper )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_UPPER ) );
                }
            }

            //check number of lower characters
            {
                final int numberOfLowerChars = charCounter.getLowerCharCount();
                if ( numberOfLowerChars < ruleHelper.readIntValue( PwmPasswordRule.MinimumLowerCase ) )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) );
                }

                final int maxLower = ruleHelper.readIntValue( PwmPasswordRule.MaximumLowerCase );
                if ( maxLower > 0 && numberOfLowerChars > maxLower )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_LOWER ) );
                }
            }
            return Collections.unmodifiableList( errorList );
        }
    }

    private static class AlphaLimitsRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();
            final PasswordCharCounter charCounter = ruleCheckData.getCharCounter();

            //check number of alpha characters
            {
                final int numberOfAlphaChars = charCounter.getAlphaCharCount();
                if ( numberOfAlphaChars < ruleHelper.readIntValue( PwmPasswordRule.MinimumAlpha ) )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) );
                }

                final int maxAlpha = ruleHelper.readIntValue( PwmPasswordRule.MaximumAlpha );
                if ( maxAlpha > 0 && numberOfAlphaChars > maxAlpha )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_ALPHA ) );
                }
            }

            //check number of non-alpha characters
            {
                final int numberOfNonAlphaChars = charCounter.getNonAlphaCharCount();

                if ( ruleHelper.readBooleanValue( PwmPasswordRule.AllowNonAlpha ) )
                {
                    if ( numberOfNonAlphaChars < ruleHelper.readIntValue( PwmPasswordRule.MinimumNonAlpha ) )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) );
                    }

                    final int maxNonAlpha = ruleHelper.readIntValue( PwmPasswordRule.MaximumNonAlpha );
                    if ( maxNonAlpha > 0 && numberOfNonAlphaChars > maxNonAlpha )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );
                    }
                }
                else
                {
                    if ( numberOfNonAlphaChars > 0 )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );
                    }
                }
            }
            return Collections.unmodifiableList( errorList );
        }
    }

    private static class SpecialLimitsRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();
            final PasswordCharCounter charCounter = ruleCheckData.getCharCounter();

            //check number of special characters
            {
                final int numberOfSpecialChars = charCounter.getSpecialCharsCount();
                if ( ruleHelper.readBooleanValue( PwmPasswordRule.AllowSpecial ) )
                {
                    if ( numberOfSpecialChars < ruleHelper.readIntValue( PwmPasswordRule.MinimumSpecial ) )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) );
                    }

                    final int maxSpecial = ruleHelper.readIntValue( PwmPasswordRule.MaximumSpecial );
                    if ( maxSpecial > 0 && numberOfSpecialChars > maxSpecial )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_SPECIAL ) );
                    }

                    if ( !ruleHelper.readBooleanValue(
                            PwmPasswordRule.AllowFirstCharSpecial ) && charCounter.isFirstSpecial() )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_FIRST_IS_SPECIAL ) );
                    }

                    if ( !ruleHelper.readBooleanValue(
                            PwmPasswordRule.AllowLastCharSpecial ) && charCounter.isLastSpecial() )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_LAST_IS_SPECIAL ) );
                    }
                }
                else
                {
                    if ( numberOfSpecialChars > 0 )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_SPECIAL ) );
                    }
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class CharSequenceRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();
            final PasswordCharCounter charCounter = ruleCheckData.getCharCounter();

            //Check maximum character repeats (sequential)
            {
                final int maxSequentialRepeat = ruleHelper.readIntValue( PwmPasswordRule.MaximumSequentialRepeat );
                if ( maxSequentialRepeat > 0 && charCounter.getSequentialRepeatedChars() > maxSequentialRepeat )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_REPEAT ) );
                }

                //Check maximum character repeats (overall)
                final int maxRepeat = ruleHelper.readIntValue( PwmPasswordRule.MaximumRepeat );
                if ( maxRepeat > 0 && charCounter.getRepeatedChars() > maxRepeat )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_REPEAT ) );
                }
            }

            // check consecutive characters
            {
                final int maximumConsecutive = ruleHelper.readIntValue( PwmPasswordRule.MaximumConsecutive );
                if ( PwmPasswordRuleUtil.tooManyConsecutiveChars( password, maximumConsecutive ) )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_CONSECUTIVE ) );
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class UniqueCharRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();
            final PasswordCharCounter charCounter = ruleCheckData.getCharCounter();

            //Check minimum unique character
            {
                final int minUnique = ruleHelper.readIntValue( PwmPasswordRule.MinimumUnique );
                if ( minUnique > 0 && charCounter.getUniqueChars() < minUnique )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) );
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class ActiveDirectoryRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();
            final PasswordCharCounter charCounter = ruleCheckData.getCharCounter();

            // check ad-complexity
            {
                final ADPolicyComplexity complexityLevel = ruleHelper.getADComplexityLevel();
                if ( complexityLevel == ADPolicyComplexity.AD2003 || complexityLevel == ADPolicyComplexity.AD2008 )
                {
                    final int maxGroupViolations = ruleHelper.readIntValue( PwmPasswordRule.ADComplexityMaxViolations );
                    errorList.addAll( PwmPasswordRuleUtil.checkPasswordForADComplexity(
                            complexityLevel,
                            ruleCheckData.getUserInfo(),
                            password,
                            charCounter,
                            maxGroupViolations ) );
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class DisallowedValueRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();

            // check against disallowed values;
            if ( !ruleHelper.getDisallowedValues().isEmpty() )
            {
                final String lcasePwd = password.toLowerCase();
                final Set<String> paramValues = new HashSet<>( ruleHelper.getDisallowedValues() );

                for ( final String loopValue : paramValues )
                {
                    if ( loopValue != null && loopValue.length() > 0 )
                    {
                        final MacroRequest macroRequest = ruleCheckData.getMacroRequest();
                        final String expandedValue = macroRequest.expandMacros( loopValue );
                        if ( StringUtils.isNotBlank( expandedValue ) )
                        {
                            final String loweredLoop = expandedValue.toLowerCase();
                            if ( lcasePwd.contains( loweredLoop ) )
                            {
                                errorList.add( new ErrorInformation( PwmError.PASSWORD_USING_DISALLOWED ) );
                            }
                        }
                    }
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class DisallowedAttributeRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final UserInfo userInfo = ruleCheckData.getUserInfo();
            final PwmPasswordPolicy policy = ruleCheckData.getPolicy();

            // check disallowed attributes.
            if ( !policy.getRuleHelper().getDisallowedAttributes().isEmpty() )
            {
                final List<String> paramConfigs = policy.getRuleHelper().getDisallowedAttributes( PasswordRuleReaderHelper.Flag.KeepThresholds );
                if ( userInfo != null )
                {
                    final Map<String, String> userValues = userInfo.getCachedPasswordRuleAttributes();

                    for ( final String paramConfig : paramConfigs )
                    {
                        final String[] parts = paramConfig.split( ":" );

                        final String attrName = parts[ 0 ];
                        final String disallowedValue = StringUtils.defaultString( userValues.get( attrName ) );
                        final int threshold = parts.length > 1 ? NumberUtils.toInt( parts[ 1 ] ) : 0;

                        if ( PwmPasswordRuleUtil.containsDisallowedValue( password, disallowedValue, threshold ) )
                        {
                            LOGGER.trace( () -> "password rejected, same as user attr " + attrName );
                            errorList.add( new ErrorInformation( PwmError.PASSWORD_SAMEASATTR ) );
                        }
                    }
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class PasswordStrengthRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PwmApplication pwmApplication = ruleCheckData.getPwmApplication();

            // check password strength
            final int requiredPasswordStrength = ruleCheckData.getRuleHelper().readIntValue( PwmPasswordRule.MinimumStrength );
            if ( requiredPasswordStrength > 0 )
            {
                if ( pwmApplication != null )
                {
                    final int passwordStrength = PasswordUtility.judgePasswordStrength(
                            pwmApplication.getConfig(),
                            password
                    );
                    if ( passwordStrength < requiredPasswordStrength )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_TOO_WEAK ) );
                        if ( EXTRA_LOGGING )
                        {
                            LOGGER.trace( () -> "password rejected, password strength of "
                                    + passwordStrength + " is lower than policy requirement of "
                                    + requiredPasswordStrength );
                        }
                    }
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class RegexPatternsRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final MacroRequest macroRequest = ruleCheckData.getMacroRequest();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();

            // check regex matches.
            for ( final Pattern pattern : ruleHelper.getRegExMatch( macroRequest ) )
            {
                if ( !pattern.matcher( password ).matches() )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_INVALID_CHAR ) );
                    if ( EXTRA_LOGGING )
                    {
                        LOGGER.trace( () -> "password rejected, does not match configured regex pattern: " + pattern.toString() );
                    }
                }
            }

            // check no-regex matches.
            for ( final Pattern pattern : ruleHelper.getRegExNoMatch( macroRequest ) )
            {
                if ( pattern.matcher( password ).matches() )
                {
                    errorList.add( new ErrorInformation( PwmError.PASSWORD_INVALID_CHAR ) );
                    if ( EXTRA_LOGGING )
                    {
                        LOGGER.trace( () -> "password rejected, matches configured no-regex pattern: " + pattern.toString() );
                    }
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class CharGroupRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();

            // check char group matches
            if ( ruleHelper.readIntValue( PwmPasswordRule.CharGroupsMinMatch ) > 0 )
            {
                final List<Pattern> ruleGroups = ruleHelper.getCharGroupValues();
                if ( ruleGroups != null && !ruleGroups.isEmpty() )
                {
                    final int requiredMatches = ruleHelper.readIntValue( PwmPasswordRule.CharGroupsMinMatch );
                    int matches = 0;
                    for ( final Pattern pattern : ruleGroups )
                    {
                        if ( pattern.matcher( password ).find() )
                        {
                            matches++;
                        }
                    }
                    if ( matches < requiredMatches )
                    {
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_GROUPS ) );
                    }
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class DictionaryRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PwmApplication pwmApplication = ruleCheckData.getPwmApplication();
            final PasswordRuleReaderHelper ruleHelper = ruleCheckData.getRuleHelper();

            // check if the password is in the dictionary.
            if ( ruleHelper.readBooleanValue( PwmPasswordRule.EnableWordlist ) )
            {
                if ( pwmApplication != null )
                {
                    if ( pwmApplication.getWordlistService() != null && pwmApplication.getWordlistService().status() == PwmService.STATUS.OPEN )
                    {
                        final boolean found = pwmApplication.getWordlistService().containsWord( password );

                        if ( found )
                        {
                            //LOGGER.trace(pwmSession, "password rejected, in wordlist file");
                            errorList.add( new ErrorInformation( PwmError.PASSWORD_INWORDLIST ) );
                        }
                    }
                    else
                    {
                        final boolean failWhenClosed = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.PASSWORD_RULE_WORDLIST_FAIL_WHEN_CLOSED ) );
                        if ( failWhenClosed )
                        {
                            throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "wordlist service is not available" );
                        }
                    }
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }

    private static class SharedHistoryRuleChecker implements RuleChecker
    {
        @Override
        public List<ErrorInformation> test( final String password, final String oldPassword, final RuleCheckData ruleCheckData )
                throws PwmUnrecoverableException
        {
            final List<ErrorInformation> errorList = new ArrayList<>();
            final PwmApplication pwmApplication = ruleCheckData.getPwmApplication();

            // check for shared (global) password history
            if ( pwmApplication != null )
            {
                if ( pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PASSWORD_SHAREDHISTORY_ENABLE )
                        && pwmApplication.getSharedHistoryManager().status() == PwmService.STATUS.OPEN )
                {
                    final boolean found = pwmApplication.getSharedHistoryManager().containsWord( password );

                    if ( found )
                    {
                        //LOGGER.trace(pwmSession, "password rejected, in global shared history");
                        errorList.add( new ErrorInformation( PwmError.PASSWORD_INWORDLIST ) );
                    }
                }
            }

            return Collections.unmodifiableList( errorList );
        }
    }
}

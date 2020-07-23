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

package password.pwm.http.tag;

import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.PwmException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.servlet.newuser.NewUserServlet;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.password.PasswordRuleReaderHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

/**
 * @author Jason D. Rivard
 */
public class PasswordRequirementsTag extends TagSupport
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordRequirementsTag.class );
    private String separator;
    private String prepend;
    private String form;

    public static List<String> getPasswordRequirementsStrings(
            final PwmPasswordPolicy passwordPolicy,
            final Configuration config,
            final Locale locale,
            final MacroMachine macroMachine
    )
    {
        final List<String> ruleTexts = new ArrayList<>(  );
        final PolicyValues policyValues = new PolicyValues( passwordPolicy, passwordPolicy.getRuleHelper(), locale, config, macroMachine );
        for ( final RuleTextGenerator ruleTextGenerator : RULE_TEXT_GENERATORS )
        {
            ruleTexts.addAll( ruleTextGenerator.generate( policyValues ) );
        }

        return Collections.unmodifiableList( ruleTexts );
    }

    private static final List<RuleTextGenerator> RULE_TEXT_GENERATORS = Collections.unmodifiableList( Arrays.asList(
            new CaseSensitiveRuleTextGenerator(),
            new MinLengthRuleTextGenerator(),
            new MaxLengthRuleTextGenerator(),
            new MinAlphaRuleTextGenerator(),
            new MaxAlphaRuleTextGenerator(),
            new NumericCharsRuleTextGenerator(),
            new SpecialCharsRuleTextGenerator(),
            new MaximumRepeatRuleTextGenerator(),
            new MaximumSequentialRepeatRuleTextGenerator(),
            new MinimumLowerRuleTextGenerator(),
            new MaximumLowerRuleTextGenerator(),
            new MinimumUpperRuleTextGenerator(),
            new MaximumUpperRuleTextGenerator(),
            new MinimumUniqueRuleTextGenerator(),
            new DisallowedValuesRuleTextGenerator(),
            new WordlistRuleTextGenerator(),
            new DisallowedAttributesRuleTextGenerator(),
            new MaximumOldCharsRuleTextGenerator(),
            new MinimumLifetimeRuleTextGenerator(),
            new ADRuleTextGenerator(),
            new UniqueRequiredRuleTextGenerator()
    ) );

    private interface RuleTextGenerator
    {
        List<String> generate( PolicyValues policyValues );
    }

    @Value
    private static class PolicyValues
    {
        private PwmPasswordPolicy passwordPolicy;
        private PasswordRuleReaderHelper ruleHelper;
        private Locale locale;
        private Configuration config;
        private MacroMachine macroMachine;
    }

    private static class CaseSensitiveRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            if ( policyValues.getRuleHelper().readBooleanValue( PwmPasswordRule.CaseSensitive ) )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_CaseSensitive, null, policyValues ) );
            }
            else
            {
                return Collections.singletonList( getLocalString( Message.Requirement_NotCaseSensitive, null, policyValues ) );
            }
        }
    }

    private static class MinLengthRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MinimumLength );
            final ADPolicyComplexity adPolicyLevel = policyValues.getRuleHelper().getADComplexityLevel();

            if ( adPolicyLevel == ADPolicyComplexity.AD2003 || adPolicyLevel == ADPolicyComplexity.AD2008 )
            {
                if ( value < 6 )
                {
                    value = 6;
                }
            }
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MinLength, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MaxLengthRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MaximumLength );
            if ( value > 0 && value < 64 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxLength, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MinAlphaRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MinimumAlpha );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MinAlpha, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MaxAlphaRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MaximumAlpha );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxAlpha, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class NumericCharsRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final PasswordRuleReaderHelper ruleHelper = policyValues.getRuleHelper();
            if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowNumeric ) )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_AllowNumeric, null, policyValues ) );
            }
            else
            {
                final List<String> returnValues = new ArrayList<>(  );
                final int minValue = ruleHelper.readIntValue( PwmPasswordRule.MinimumNumeric );
                if ( minValue > 0 )
                {
                    returnValues.add( getLocalString( Message.Requirement_MinNumeric, minValue, policyValues ) );
                }

                final int maxValue = ruleHelper.readIntValue( PwmPasswordRule.MaximumNumeric );
                if ( maxValue > 0 )
                {
                    returnValues.add( getLocalString( Message.Requirement_MaxNumeric, maxValue, policyValues ) );
                }

                if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowFirstCharNumeric ) )
                {
                    returnValues.add( getLocalString( Message.Requirement_FirstNumeric, maxValue, policyValues ) );
                }

                if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowLastCharNumeric ) )
                {
                    returnValues.add( getLocalString( Message.Requirement_LastNumeric, maxValue, policyValues ) );
                }
                return returnValues;
            }
        }
    }

    private static class SpecialCharsRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final PasswordRuleReaderHelper ruleHelper = policyValues.getRuleHelper();
            if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowSpecial ) )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_AllowSpecial, null, policyValues ) );
            }
            else
            {
                final List<String> returnValues = new ArrayList<>(  );
                final int minValue = ruleHelper.readIntValue( PwmPasswordRule.MinimumSpecial );
                if ( minValue > 0 )
                {
                    returnValues.add( getLocalString( Message.Requirement_MinSpecial, minValue, policyValues ) );
                }

                final int maxValue = ruleHelper.readIntValue( PwmPasswordRule.MaximumSpecial );
                if ( maxValue > 0 )
                {
                    returnValues.add( getLocalString( Message.Requirement_MaxSpecial, maxValue, policyValues ) );
                }

                if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowFirstCharSpecial ) )
                {
                    returnValues.add( getLocalString( Message.Requirement_FirstSpecial, maxValue, policyValues ) );
                }

                if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowLastCharSpecial ) )
                {
                    returnValues.add( getLocalString( Message.Requirement_LastSpecial, maxValue, policyValues ) );
                }
                return returnValues;
            }
        }
    }

    private static class MaximumRepeatRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MaximumRepeat );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxRepeat, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MaximumSequentialRepeatRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MaximumSequentialRepeat );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxSeqRepeat, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MinimumLowerRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MinimumLowerCase );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MinLower, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MaximumLowerRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MaximumLowerCase );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxLower, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MinimumUpperRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MinimumUpperCase );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MinUpper, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MaximumUpperRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MaximumUpperCase );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxUpper, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MinimumUniqueRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MinimumUnique );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MinUnique, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class DisallowedValuesRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final List<String> setValue = policyValues.getRuleHelper().getDisallowedValues();
            if ( !setValue.isEmpty() )
            {
                final StringBuilder fieldValue = new StringBuilder();
                for ( final String loopValue : setValue )
                {
                    fieldValue.append( " " );

                    final String expandedValue = policyValues.getMacroMachine().expandMacros( loopValue );
                    fieldValue.append( StringUtil.escapeHtml( expandedValue ) );
                }
                return Collections.singletonList( getLocalString( Message.Requirement_DisAllowedValues, fieldValue.toString(), policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class WordlistRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            if ( policyValues.getRuleHelper().readBooleanValue( PwmPasswordRule.EnableWordlist ) )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_WordList, "", policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class DisallowedAttributesRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final List<String> setValue = policyValues.getRuleHelper().getDisallowedAttributes();
            final ADPolicyComplexity adPolicyLevel = policyValues.getRuleHelper().getADComplexityLevel();
            if ( !setValue.isEmpty() || adPolicyLevel == ADPolicyComplexity.AD2003 )
            {
                return Collections.singletonList(  getLocalString( Message.Requirement_DisAllowedAttributes, "", policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MaximumOldCharsRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MaximumOldChars );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_OldChar, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class MinimumLifetimeRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final int value = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.MinimumLifetime );
            if ( value > 0 )
            {
                final int secondsPerDay = 60 * 60 * 24;

                final String durationStr;
                if ( value % secondsPerDay == 0 )
                {
                    final int valueAsDays = value / ( 60 * 60 * 24 );
                    final Display key = valueAsDays <= 1 ? Display.Display_Day : Display.Display_Days;
                    durationStr = valueAsDays + " " + LocaleHelper.getLocalizedMessage( policyValues.getLocale(), key, policyValues.getConfig() );
                }
                else
                {
                    final int valueAsHours = value / ( 60 * 60 );
                    final Display key = valueAsHours <= 1 ? Display.Display_Hour : Display.Display_Hours;
                    durationStr = valueAsHours + " " + LocaleHelper.getLocalizedMessage( policyValues.getLocale(), key, policyValues.getConfig() );
                }

                final String userMsg = Message.getLocalizedMessage( policyValues.getLocale(), Message.Requirement_MinimumFrequency, policyValues.getConfig(), durationStr );
                return Collections.singletonList( userMsg );
            }
            return Collections.emptyList();
        }
    }

    private static class ADRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            final ADPolicyComplexity adPolicyLevel = policyValues.getRuleHelper().getADComplexityLevel();
            if ( adPolicyLevel == ADPolicyComplexity.AD2003 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_ADComplexity, "", policyValues ) );
            }
            else if ( adPolicyLevel == ADPolicyComplexity.AD2008 )
            {
                final int maxViolations = policyValues.getRuleHelper().readIntValue( PwmPasswordRule.ADComplexityMaxViolations );
                final int minGroups = 5 - maxViolations;
                return Collections.singletonList( getLocalString( Message.Requirement_ADComplexity2008, String.valueOf( minGroups ), policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static class UniqueRequiredRuleTextGenerator implements RuleTextGenerator
    {
        public List<String> generate( final PolicyValues policyValues )
        {
            if ( policyValues.getRuleHelper().readBooleanValue( PwmPasswordRule.UniqueRequired ) )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_UniqueRequired, "", policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static String getLocalString( final Message message, final int size, final PolicyValues policyValues )
    {
        final Message effectiveMessage = size > 1 && message.getPluralMessage() != null
                ? message.getPluralMessage()
                : message;

        try
        {
            return Message.getLocalizedMessage( policyValues.getLocale(), effectiveMessage, policyValues.getConfig(), String.valueOf( size ) );
        }
        catch ( final MissingResourceException e )
        {
            LOGGER.error( () -> "unable to display requirement tag for message '" + message.toString() + "': " + e.getMessage() );
        }
        return "UNKNOWN MESSAGE STRING";
    }

    private static String getLocalString( final Message message, final String field, final PolicyValues policyValues )
    {
        try
        {
            return Message.getLocalizedMessage( policyValues.getLocale(), message, policyValues.getConfig(), field );
        }
        catch ( final MissingResourceException e )
        {
            LOGGER.error( () -> "unable to display requirement tag for message '" + message.toString() + "': " + e.getMessage() );
        }
        return "UNKNOWN MESSAGE STRING";
    }

    public String getSeparator( )
    {
        return separator;
    }

    public void setSeparator( final String separator )
    {
        this.separator = separator;
    }

    public String getPrepend( )
    {
        return prepend;
    }

    public void setPrepend( final String prepend )
    {
        this.prepend = prepend;
    }

    public String getForm( )
    {
        return form;
    }

    public void setForm( final String form )
    {
        this.form = form;
    }

    public int doEndTag( )
            throws javax.servlet.jsp.JspTagException
    {
        try
        {
            final PwmRequest pwmRequest = PwmRequest.forRequest( ( HttpServletRequest ) pageContext.getRequest(), ( HttpServletResponse ) pageContext.getResponse() );
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
            final Configuration config = pwmApplication.getConfig();
            final Locale locale = pwmSession.getSessionStateBean().getLocale();

            pwmSession.getSessionManager().getMacroMachine( );

            final PwmPasswordPolicy passwordPolicy;
            if ( getForm() != null && getForm().equalsIgnoreCase( "newuser" ) )
            {
                final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );
                passwordPolicy = newUserProfile.getNewUserPasswordPolicy( pwmApplication, locale );
            }
            else
            {
                passwordPolicy = pwmSession.getUserInfo().getPasswordPolicy();
            }

            final String configuredRuleText = passwordPolicy.getRuleText();
            if ( configuredRuleText != null && configuredRuleText.length() > 0 )
            {
                pageContext.getOut().write( configuredRuleText );
            }
            else
            {
                final MacroMachine macroMachine = pwmSession.getSessionManager().getMacroMachine( );

                final String pre = prepend != null && prepend.length() > 0 ? prepend : "";
                final String sep = separator != null && separator.length() > 0 ? separator : "<br/>";
                final List<String> requirementsList = getPasswordRequirementsStrings( passwordPolicy, config, locale, macroMachine );

                final StringBuilder requirementsText = new StringBuilder();
                for ( final String requirementStatement : requirementsList )
                {
                    requirementsText.append( pre );
                    requirementsText.append( requirementStatement );
                    requirementsText.append( sep );
                }

                pageContext.getOut().write( requirementsText.toString() );
            }
        }
        catch ( final IOException | PwmException e )
        {
            LOGGER.error( () -> "unexpected error during password requirements generation: " + e.getMessage(), e );
            throw new JspTagException( e.getMessage() );
        }
        return EVAL_PAGE;
    }
}


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

package password.pwm.util.password;

import com.novell.ldapchai.ChaiPasswordRule;
import com.novell.ldapchai.util.DefaultChaiPasswordPolicy;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PasswordRuleReaderHelper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordRuleReaderHelper.class );

    public enum Flag
    {
        KeepThresholds
    }

    private final PwmPasswordPolicy passwordPolicy;
    private final com.novell.ldapchai.util.PasswordRuleHelper chaiRuleHelper;

    public PasswordRuleReaderHelper( final PwmPasswordPolicy passwordPolicy )
    {
        this.passwordPolicy = passwordPolicy;
        chaiRuleHelper = DefaultChaiPasswordPolicy.createDefaultChaiPasswordPolicy( passwordPolicy.getPolicyMap() ).getRuleHelper();
    }

    public List<String> getDisallowedValues( )
    {
        return chaiRuleHelper.getDisallowedValues();
    }

    public List<String> getDisallowedAttributes( final Flag... flags )
    {
        final List<String> disallowedAttributes = chaiRuleHelper.getDisallowedAttributes();

        if ( JavaHelper.enumArrayContainsValue( flags, Flag.KeepThresholds ) )
        {
            return disallowedAttributes;
        }
        else
        {
            // Strip off any thresholds from attribute (specified as: "attributeName:N", where N is a numeric value).
            final List<String> strippedDisallowedAttributes = new ArrayList<String>();

            if ( disallowedAttributes != null )
            {
                for ( final String disallowedAttribute : disallowedAttributes )
                {
                    if ( disallowedAttribute != null )
                    {
                        final int indexOfColon = disallowedAttribute.indexOf( ':' );
                        if ( indexOfColon > 0 )
                        {
                            strippedDisallowedAttributes.add( disallowedAttribute.substring( 0, indexOfColon ) );
                        }
                        else
                        {
                            strippedDisallowedAttributes.add( disallowedAttribute );
                        }
                    }
                }
            }

            return strippedDisallowedAttributes;
        }
    }

    public List<Pattern> getRegExMatch( final MacroMachine macroMachine )
    {
        return readRegExSetting( PwmPasswordRule.RegExMatch, macroMachine );
    }

    public List<Pattern> getRegExNoMatch( final MacroMachine macroMachine )
    {
        return readRegExSetting( PwmPasswordRule.RegExNoMatch, macroMachine );
    }

    public List<Pattern> getCharGroupValues( )
    {
        return readRegExSetting( PwmPasswordRule.CharGroupsValues, null );
    }


    public int readIntValue( final PwmPasswordRule rule )
    {
        if (
                ( rule.getRuleType() != ChaiPasswordRule.RuleType.MIN )
                        && ( rule.getRuleType() != ChaiPasswordRule.RuleType.MAX )
                        && ( rule.getRuleType() != ChaiPasswordRule.RuleType.NUMERIC )
                )
        {
            throw new IllegalArgumentException( "attempt to read non-numeric rule value as int for rule " + rule );
        }

        final String value = passwordPolicy.getPolicyMap().get( rule.getKey() );
        final int defaultValue = StringHelper.convertStrToInt( rule.getDefaultValue(), 0 );
        return StringHelper.convertStrToInt( value, defaultValue );
    }

    public boolean readBooleanValue( final PwmPasswordRule rule )
    {
        if ( rule.getRuleType() != ChaiPasswordRule.RuleType.BOOLEAN )
        {
            throw new IllegalArgumentException( "attempt to read non-boolean rule value as boolean for rule " + rule );
        }

        final String value = passwordPolicy.getPolicyMap().get( rule.getKey() );
        return StringHelper.convertStrToBoolean( value );
    }

    private List<Pattern> readRegExSetting( final PwmPasswordRule rule, final MacroMachine macroMachine )
    {
        final String input = passwordPolicy.getPolicyMap().get( rule.getKey() );

        return readRegExSetting( rule, macroMachine, input );
    }

    public List<Pattern> readRegExSetting( final PwmPasswordRule rule, final MacroMachine macroMachine, final String input )
    {
        if ( input == null )
        {
            return Collections.emptyList();
        }

        final String separator = ( rule == PwmPasswordRule.RegExMatch || rule == PwmPasswordRule.RegExNoMatch ) ? ";;;" : "\n";
        final List<String> values = new ArrayList<>( StringHelper.tokenizeString( input, separator ) );
        final List<Pattern> patterns = new ArrayList<>();

        for ( final String value : values )
        {
            if ( value != null && value.length() > 0 )
            {
                String valueToCompile = value;

                if ( macroMachine != null && readBooleanValue( PwmPasswordRule.AllowMacroInRegExSetting ) )
                {
                    valueToCompile = macroMachine.expandMacros( value );
                }

                try
                {
                    final Pattern loopPattern = Pattern.compile( valueToCompile );
                    patterns.add( loopPattern );
                }
                catch ( final PatternSyntaxException e )
                {
                    final String valueToCompileFinal = valueToCompile;
                    LOGGER.warn( () -> "reading password rule value '" + valueToCompileFinal + "' for rule " + rule.getKey()
                            + " is not a valid regular expression " + e.getMessage() );
                }
            }
        }

        return patterns;
    }

    public String getChangeMessage( )
    {
        final String changeMessage = passwordPolicy.getValue( PwmPasswordRule.ChangeMessage );
        return changeMessage == null ? "" : changeMessage;
    }

    public ADPolicyComplexity getADComplexityLevel( )
    {
        final String strLevel = passwordPolicy.getValue( PwmPasswordRule.ADComplexityLevel );
        if ( strLevel == null || strLevel.isEmpty() )
        {
            return ADPolicyComplexity.NONE;
        }
        return ADPolicyComplexity.valueOf( strLevel );
    }
}

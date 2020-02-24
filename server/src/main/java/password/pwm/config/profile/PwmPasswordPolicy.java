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

package password.pwm.config.profile;

import com.novell.ldapchai.ChaiPasswordPolicy;
import com.novell.ldapchai.ChaiPasswordRule;
import com.novell.ldapchai.util.StringHelper;
import lombok.Builder;
import lombok.Value;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.value.data.UserPermission;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordRuleReaderHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;


/**
 * @author Jason D. Rivard
 */
public class PwmPasswordPolicy implements Profile, Serializable
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmPasswordPolicy.class );

    private static final PwmPasswordPolicy DEFAULT_POLICY;

    private final Map<String, String> policyMap;

    private final transient ChaiPasswordPolicy chaiPasswordPolicy;

    private String profileID;
    private List<UserPermission> userPermissions;
    private String ruleText;

    public static PwmPasswordPolicy createPwmPasswordPolicy( final Map<String, String> policyMap )
    {
        return createPwmPasswordPolicy( policyMap, null );
    }

    public static PwmPasswordPolicy createPwmPasswordPolicy(
            final Map<String, String> policyMap,
            final ChaiPasswordPolicy chaiPasswordPolicy
    )
    {
        return new PwmPasswordPolicy( policyMap, chaiPasswordPolicy, null );
    }

    public static PwmPasswordPolicy createPwmPasswordPolicy(
            final Map<String, String> policyMap,
            final ChaiPasswordPolicy chaiPasswordPolicy,
            final PolicyMetaData policyMetaData
    )
    {
        return new PwmPasswordPolicy( policyMap, chaiPasswordPolicy, policyMetaData );
    }

    public String getIdentifier( )
    {
        return profileID;
    }

    public String getDisplayName( final Locale locale )
    {
        return getIdentifier();
    }

    static
    {
        PwmPasswordPolicy newDefaultPolicy = null;
        try
        {
            final Map<String, String> defaultPolicyMap = new HashMap<>();
            for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
            {
                defaultPolicyMap.put( rule.getKey(), rule.getDefaultValue() );
            }
            newDefaultPolicy = createPwmPasswordPolicy( defaultPolicyMap, null );
        }
        catch ( final Throwable t )
        {
            LOGGER.fatal( () -> "error initializing PwmPasswordPolicy class: " + t.getMessage(), t );
        }
        DEFAULT_POLICY = newDefaultPolicy;
    }

    public static PwmPasswordPolicy defaultPolicy( )
    {
        return DEFAULT_POLICY;
    }


    private PwmPasswordPolicy(
            final Map<String, String> policyMap,
            final ChaiPasswordPolicy chaiPasswordPolicy,
            final PolicyMetaData policyMetaData
    )
    {
        final Map<String, String> effectivePolicyMap = new HashMap<>();
        if ( policyMap != null )
        {
            effectivePolicyMap.putAll( policyMap );
        }
        if ( chaiPasswordPolicy != null )
        {
            if ( Boolean.parseBoolean( chaiPasswordPolicy.getValue( ChaiPasswordRule.ADComplexity ) ) )
            {
                effectivePolicyMap.put( PwmPasswordRule.ADComplexityLevel.getKey(), ADPolicyComplexity.AD2003.toString() );
            }
            else if ( Boolean.parseBoolean( chaiPasswordPolicy.getValue( ChaiPasswordRule.ADComplexity2008 ) ) )
            {
                effectivePolicyMap.put( PwmPasswordRule.ADComplexityLevel.getKey(), ADPolicyComplexity.AD2008.toString() );
            }
        }
        this.chaiPasswordPolicy = chaiPasswordPolicy;
        if ( policyMetaData != null )
        {
            this.ruleText = policyMetaData.getRuleText();
            this.userPermissions = policyMetaData.getUserPermissions();
            this.profileID = policyMetaData.getProfileID();
        }

        this.policyMap = Collections.unmodifiableMap( effectivePolicyMap );
    }

    @Override
    public String toString( )
    {
        return "PwmPasswordPolicy" + ": " + JsonUtil.serialize( this );
    }

    public ChaiPasswordPolicy getChaiPasswordPolicy( )
    {
        return chaiPasswordPolicy;
    }

    public PasswordRuleReaderHelper getRuleHelper( )
    {
        return new PasswordRuleReaderHelper( this );
    }

    public String getValue( final PwmPasswordRule rule )
    {
        return policyMap.get( rule.getKey() );
    }



    public List<UserPermission> getUserPermissions( )
    {
        return userPermissions;
    }

    public String getRuleText( )
    {
        return ruleText;
    }

    public PwmPasswordPolicy merge( final PwmPasswordPolicy otherPolicy )
    {
        if ( otherPolicy == null )
        {
            return this;
        }

        final Map<String, String> newPasswordPolicies = new HashMap<>();

        for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
        {
            final String ruleKey = rule.getKey();
            if ( this.policyMap.containsKey( ruleKey ) || otherPolicy.policyMap.containsKey( ruleKey ) )
            {

                switch ( rule )
                {
                    case DisallowedValues:
                    case DisallowedAttributes:
                    case RegExMatch:
                    case RegExNoMatch:
                    case CharGroupsValues:
                        final String seperator = ( rule == PwmPasswordRule.RegExMatch || rule == PwmPasswordRule.RegExNoMatch ) ? ";;;" : "\n";
                        final Set<String> combinedSet = new HashSet<>();
                        combinedSet.addAll( StringHelper.tokenizeString( this.policyMap.get( rule.getKey() ), seperator ) );
                        combinedSet.addAll( StringHelper.tokenizeString( otherPolicy.policyMap.get( rule.getKey() ), seperator ) );
                        newPasswordPolicies.put( ruleKey, StringHelper.stringCollectionToString( combinedSet, seperator ) );
                        break;

                    case ChangeMessage:
                        final String thisChangeMessage = getValue( PwmPasswordRule.ChangeMessage );
                        if ( thisChangeMessage == null || thisChangeMessage.length() < 1 )
                        {
                            newPasswordPolicies.put( ruleKey, otherPolicy.getValue( PwmPasswordRule.ChangeMessage ) );
                        }
                        else
                        {
                            newPasswordPolicies.put( ruleKey, getValue( PwmPasswordRule.ChangeMessage ) );
                        }
                        break;

                    case ExpirationInterval:
                        final String expirationIntervalLocalValue = StringUtil.defaultString( policyMap.get( ruleKey ), rule.getDefaultValue() );
                        final String expirationIntervalOtherValue = StringUtil.defaultString( otherPolicy.policyMap.get( ruleKey ), rule.getDefaultValue() );
                        newPasswordPolicies.put( ruleKey, mergeMin( expirationIntervalLocalValue, expirationIntervalOtherValue ) );
                        break;

                    case MinimumLifetime:
                        final String minimumLifetimeLocalValue = StringUtil.defaultString( policyMap.get( ruleKey ), rule.getDefaultValue() );
                        final String minimumLifetimeOtherValue = StringUtil.defaultString( otherPolicy.policyMap.get( ruleKey ), rule.getDefaultValue() );
                        newPasswordPolicies.put( ruleKey, mergeMin( minimumLifetimeLocalValue, minimumLifetimeOtherValue ) );
                        break;

                    case ADComplexityLevel:
                        newPasswordPolicies.put( ruleKey, mergeADComplexityLevel( policyMap.get( ruleKey ), otherPolicy.policyMap.get( ruleKey ) ) );
                        break;

                    default:
                        final String localValueString = StringUtil.defaultString( policyMap.get( ruleKey ), rule.getDefaultValue() );
                        final String otherValueString = StringUtil.defaultString( otherPolicy.policyMap.get( ruleKey ), rule.getDefaultValue() );

                        switch ( rule.getRuleType() )
                        {
                            case MIN:
                                newPasswordPolicies.put( ruleKey, mergeMin( localValueString, otherValueString ) );
                                break;

                            case MAX:
                                newPasswordPolicies.put( ruleKey, mergeMax( localValueString, otherValueString ) );
                                break;

                            case BOOLEAN:
                                final boolean localValue = StringHelper.convertStrToBoolean( localValueString );
                                final boolean otherValue = StringHelper.convertStrToBoolean( otherValueString );

                                if ( rule.isPositiveBooleanMerge() )
                                {
                                    newPasswordPolicies.put( ruleKey, String.valueOf( localValue || otherValue ) );
                                }
                                else
                                {
                                    newPasswordPolicies.put( ruleKey, String.valueOf( localValue && otherValue ) );
                                }
                                break;

                            default:
                                //continue processing
                                break;
                        }
                }
            }
        }

        final ChaiPasswordPolicy backingPolicy = this.chaiPasswordPolicy != null ? chaiPasswordPolicy : otherPolicy.chaiPasswordPolicy;
        final PwmPasswordPolicy returnPolicy = createPwmPasswordPolicy( newPasswordPolicies, backingPolicy );
        final String newRuleText = ( ruleText != null && !ruleText.isEmpty() ) ? ruleText : otherPolicy.ruleText;
        returnPolicy.ruleText = ( newRuleText );
        return returnPolicy;
    }

    private static String mergeADComplexityLevel( final String value1, final String value2 )
    {
        final TreeSet<ADPolicyComplexity> seenValues = new TreeSet<>();
        seenValues.add( JavaHelper.readEnumFromString( ADPolicyComplexity.class, ADPolicyComplexity.NONE, value1 ) );
        seenValues.add( JavaHelper.readEnumFromString( ADPolicyComplexity.class, ADPolicyComplexity.NONE, value2 ) );
        return seenValues.last().name();
    }

    protected static String mergeMin( final String value1, final String value2 )
    {
        final int iValue1 = StringHelper.convertStrToInt( value1, 0 );
        final int iValue2 = StringHelper.convertStrToInt( value2, 0 );

        // take the largest value
        return iValue1 > iValue2 ? value1 : value2;
    }

    protected static String mergeMax( final String value1, final String value2 )
    {
        final int iValue1 = StringHelper.convertStrToInt( value1, 0 );
        final int iValue2 = StringHelper.convertStrToInt( value2, 0 );

        final String returnValue;

        // if one of the values is zero, take the other one.
        if ( iValue1 == 0 || iValue2 == 0 )
        {
            returnValue = iValue1 > iValue2 ? value1 : value2;

            // else take the smaller value
        }
        else
        {
            returnValue = iValue1 < iValue2 ? value1 : value2;
        }

        return returnValue;
    }

    public Map<String, String> getPolicyMap( )
    {
        return Collections.unmodifiableMap( policyMap );
    }

    @Override
    public ProfileDefinition profileType( )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<UserPermission> getPermissionMatches( )
    {
        throw new UnsupportedOperationException();
    }

    public List<HealthRecord> health( final Locale locale )
    {
        final PasswordRuleReaderHelper ruleHelper = this.getRuleHelper();
        final List<HealthRecord> returnList = new ArrayList<>();
        final Map<PwmPasswordRule, PwmPasswordRule> rulePairs = new LinkedHashMap<>();
        rulePairs.put( PwmPasswordRule.MinimumLength, PwmPasswordRule.MaximumLength );
        rulePairs.put( PwmPasswordRule.MinimumLowerCase, PwmPasswordRule.MaximumLowerCase );
        rulePairs.put( PwmPasswordRule.MinimumUpperCase, PwmPasswordRule.MaximumUpperCase );
        rulePairs.put( PwmPasswordRule.MinimumNumeric, PwmPasswordRule.MaximumNumeric );
        rulePairs.put( PwmPasswordRule.MinimumSpecial, PwmPasswordRule.MaximumSpecial );
        rulePairs.put( PwmPasswordRule.MinimumAlpha, PwmPasswordRule.MaximumAlpha );
        rulePairs.put( PwmPasswordRule.MinimumNonAlpha, PwmPasswordRule.MaximumNonAlpha );
        rulePairs.put( PwmPasswordRule.MinimumUnique, PwmPasswordRule.MaximumUnique );

        for ( final Map.Entry<PwmPasswordRule, PwmPasswordRule> entry : rulePairs.entrySet() )
        {
            final PwmPasswordRule minRule = entry.getKey();
            final PwmPasswordRule maxRule = entry.getValue();

            final int minValue = ruleHelper.readIntValue( minRule );
            final int maxValue = ruleHelper.readIntValue( maxRule );
            if ( maxValue > 0 && minValue > maxValue )
            {
                final String detailMsg = minRule.getLabel( locale, null ) + " (" + minValue + ")"
                        + " > "
                        + maxRule.getLabel( locale, null ) + " (" + maxValue + ")";
                returnList.add( HealthRecord.forMessage( HealthMessage.Config_PasswordPolicyProblem, profileID, detailMsg ) );
            }
        }

        {
            final int minValue = ruleHelper.readIntValue( PwmPasswordRule.CharGroupsMinMatch );
            final List<Pattern> ruleGroups = ruleHelper.getCharGroupValues();
            final int maxValue = ruleGroups == null ? 0 : ruleGroups.size();

            if ( maxValue > 0 && minValue > maxValue )
            {
                final String detailMsg = PwmPasswordRule.CharGroupsValues.getLabel( locale, null ) + " (" + minValue + ")"
                        + " > "
                        + PwmPasswordRule.CharGroupsMinMatch.getLabel( locale, null ) + " (" + maxValue + ")";
                returnList.add( HealthRecord.forMessage( HealthMessage.Config_PasswordPolicyProblem, profileID, detailMsg ) );
            }
        }

        return Collections.unmodifiableList( returnList );
    }

    @Value
    @Builder
    public static class PolicyMetaData
    {
        private String profileID;
        private List<UserPermission> userPermissions;
        private String ruleText;
    }
}

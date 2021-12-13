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

package password.pwm.config.profile;

import com.novell.ldapchai.ChaiPasswordPolicy;
import com.novell.ldapchai.ChaiPasswordRule;
import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredSettingReader;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.value.data.UserPermission;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.LazySupplier;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Pattern;


/**
 * @author Jason D. Rivard
 */
public class PwmPasswordPolicy implements Profile, Serializable
{
    private static final long serialVersionUID = 1L;

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmPasswordPolicy.class );

    private static final PwmPasswordPolicy DEFAULT_POLICY = makeDefaultPolicy();

    private final transient Supplier<List<HealthRecord>> healthChecker = new LazySupplier<>( () -> doHealthChecks( this ) );
    private final transient ChaiPasswordPolicy chaiPasswordPolicy;

    private final DomainID domainID;
    private final Map<String, String> policyMap;
    private final PolicyMetaData policyMetaData;

    private PwmPasswordPolicy(
            final DomainID domainID,
            final Map<String, String> policyMap,
            final ChaiPasswordPolicy chaiPasswordPolicy,
            final PolicyMetaData policyMetaData
    )
    {
        final Map<String, String> effectivePolicyMap = new TreeMap<>();
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

        this.domainID = domainID;
        this.chaiPasswordPolicy = chaiPasswordPolicy;
        this.policyMetaData = policyMetaData == null ? PolicyMetaData.builder().build() : policyMetaData;
        this.policyMap = Map.copyOf( effectivePolicyMap );
    }

    public static PwmPasswordPolicy createPwmPasswordPolicy(
            final DomainID domainID, final Map<String, String> policyMap )
    {
        return createPwmPasswordPolicy( domainID, policyMap, null );
    }

    public static PwmPasswordPolicy createPwmPasswordPolicy(
            final DomainID domainID,
            final Map<String, String> policyMap,
            final ChaiPasswordPolicy chaiPasswordPolicy
    )
    {
        return new PwmPasswordPolicy( domainID, policyMap, chaiPasswordPolicy, null );
    }

    public static PwmPasswordPolicy createPwmPasswordPolicy(
            final DomainID domainID,
            final Map<String, String> policyMap,
            final ChaiPasswordPolicy chaiPasswordPolicy,
            final PolicyMetaData policyMetaData
    )
    {
        return new PwmPasswordPolicy( domainID, policyMap, chaiPasswordPolicy, policyMetaData );
    }

    public static PwmPasswordPolicy createPwmPasswordPolicy(
            final DomainConfig domainConfig,
            final String profileID
    )
    {
        final StoredSettingReader settingReader = new StoredSettingReader( domainConfig.getStoredConfiguration(), profileID,  domainConfig.getDomainID() );
        final Map<String, String> passwordPolicySettings = new LinkedHashMap<>();
        for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
        {
            if ( rule.getPwmSetting() != null || rule.getAppProperty() != null )
            {
                final String value;
                final PwmSetting pwmSetting = rule.getPwmSetting();
                switch ( rule )
                {
                    case DisallowedAttributes:
                    case DisallowedValues:
                    case CharGroupsValues:
                        value = StringUtil.collectionToString(
                                settingReader.readSettingAsStringArray( pwmSetting ), "\n" );
                        break;
                    case RegExMatch:
                    case RegExNoMatch:
                        value = StringUtil.collectionToString(
                                settingReader.readSettingAsStringArray( pwmSetting ), ";;;" );
                        break;
                    case ChangeMessage:
                        {
                            final String settingValue = settingReader.readSettingAsLocalizedString( pwmSetting, PwmConstants.DEFAULT_LOCALE );
                            value = settingValue == null ? "" : settingValue;
                        }
                        break;
                    case ADComplexityLevel:
                        value = settingReader.readSettingAsEnum( pwmSetting, ADPolicyComplexity.class ).toString();
                        break;
                    case AllowMacroInRegExSetting:
                        value = domainConfig.readAppProperty( AppProperty.ALLOW_MACRO_IN_REGEX_SETTING );
                        break;
                    default:
                        switch ( rule.getRuleType() )
                        {
                            case MAX:
                            case MIN:
                            case NUMERIC:
                                value = String.valueOf( settingReader.readSettingAsLong( pwmSetting ) );
                                break;

                            case BOOLEAN:
                                value = String.valueOf( settingReader.readSettingAsBoolean( pwmSetting ) );
                                break;

                            default:
                                value = settingReader.readSettingAsString( pwmSetting );
                                break;
                        }
                        break;
                }
                passwordPolicySettings.put( rule.getKey(), value );
            }
        }

        // set case sensitivity
        final String caseSensitivitySetting = domainConfig.readSettingAsString( PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY );
        if ( !"read".equals( caseSensitivitySetting ) )
        {
            passwordPolicySettings.put( PwmPasswordRule.CaseSensitive.getKey(), caseSensitivitySetting );
        }

        // set pwm-specific values
        final PwmPasswordPolicy.PolicyMetaData policyMetaData = PwmPasswordPolicy.PolicyMetaData.builder()
                .profileID( profileID )
                .userPermissions( settingReader.readSettingAsUserPermission( PwmSetting.PASSWORD_POLICY_QUERY_MATCH ) )
                .ruleText( readLocalizedSetting( PwmSetting.PASSWORD_POLICY_RULE_TEXT, domainConfig, settingReader ) )
                .changePasswordText( readLocalizedSetting( PwmSetting.PASSWORD_POLICY_CHANGE_MESSAGE, domainConfig, settingReader ) )
                .build();

        return PwmPasswordPolicy.createPwmPasswordPolicy( domainConfig.getDomainID(), passwordPolicySettings, null, policyMetaData );
    }

    private static Map<Locale, String> readLocalizedSetting(
            final PwmSetting pwmSetting,
            final DomainConfig domainConfig,
            final StoredSettingReader settingReader
    )
    {
        final List<Locale> knownLocales = domainConfig.getAppConfig().getKnownLocales();
        final String defaultLocaleValue = settingReader.readSettingAsLocalizedString( pwmSetting, PwmConstants.DEFAULT_LOCALE );
        final Map<Locale, String> returnMap = new HashMap<>();
        returnMap.put( PwmConstants.DEFAULT_LOCALE, defaultLocaleValue );
        for ( final Locale locale : knownLocales )
        {
            final String value = settingReader.readSettingAsLocalizedString( pwmSetting, locale );
            if ( !Objects.equals( defaultLocaleValue, value ) )
            {
                returnMap.put( locale, value );
            }
        }
        return Collections.unmodifiableMap( returnMap );
    }

    @Override
    public String getIdentifier( )
    {
        return policyMetaData.getProfileID();
    }

    @Override
    public String getDisplayName( final Locale locale )
    {
        return getIdentifier();
    }

    public DomainID getDomainID()
    {
        return domainID;
    }

    private static PwmPasswordPolicy makeDefaultPolicy()
    {
        PwmPasswordPolicy newDefaultPolicy = null;
        try
        {
            final Map<String, String> defaultPolicyMap = new HashMap<>();
            for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
            {
                defaultPolicyMap.put( rule.getKey(), rule.getDefaultValue() );
            }
            newDefaultPolicy = createPwmPasswordPolicy( DomainID.systemId(), defaultPolicyMap, null );
        }
        catch ( final Throwable t )
        {
            LOGGER.fatal( () -> "error initializing PwmPasswordPolicy class: " + t.getMessage(), t );
        }
        return newDefaultPolicy;
    }

    public static PwmPasswordPolicy defaultPolicy( )
    {
        return DEFAULT_POLICY;
    }

    @Override
    public String toString( )
    {
        return "PwmPasswordPolicy" + ": " + JsonFactory.get().serialize( this );
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
        return policyMetaData.getUserPermissions();
    }

    public Optional<String> getChangeMessage( final Locale locale )
    {
        if ( CollectionUtil.isEmpty( policyMetaData.getChangePasswordText() ) )
        {
            return Optional.ofNullable( getValue( PwmPasswordRule.ChangeMessage ) );
        }

        final Locale resolvedLocale = LocaleHelper.localeResolver( locale, policyMetaData.getChangePasswordText().keySet() );
        return Optional.ofNullable( policyMetaData.getChangePasswordText().get( resolvedLocale ) );
    }

    public Optional<String> getRuleText( final Locale locale )
    {
        if ( CollectionUtil.isEmpty( policyMetaData.getRuleText() ) )
        {
            return Optional.empty();
        }

        final Locale resolvedLocale = LocaleHelper.localeResolver( locale, policyMetaData.getRuleText().keySet() );
        return Optional.ofNullable( policyMetaData.getRuleText().get( resolvedLocale ) );
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
                        final String separator = ( rule == PwmPasswordRule.RegExMatch || rule == PwmPasswordRule.RegExNoMatch ) ? ";;;" : "\n";
                        final Set<String> combinedSet = new HashSet<>();
                        combinedSet.addAll( StringUtil.tokenizeString( this.policyMap.get( rule.getKey() ), separator ) );
                        combinedSet.addAll( StringUtil.tokenizeString( otherPolicy.policyMap.get( rule.getKey() ), separator ) );
                        newPasswordPolicies.put( ruleKey, StringUtil.collectionToString( combinedSet, separator ) );
                        break;

                    case ChangeMessage:
                        final String thisChangeMessage = getValue( PwmPasswordRule.ChangeMessage );
                        if ( StringUtil.isEmpty( thisChangeMessage ) )
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
                                final boolean localValue = StringUtil.convertStrToBoolean( localValueString );
                                final boolean otherValue = StringUtil.convertStrToBoolean( otherValueString );

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
        final PolicyMetaData metaData = getPolicyMetaData().merge( otherPolicy.getPolicyMetaData() );
        return new PwmPasswordPolicy( domainID, newPasswordPolicies, backingPolicy, metaData );
    }

    private PolicyMetaData getPolicyMetaData()
    {
        return policyMetaData;
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
        final int iValue1 = StringUtil.convertStrToInt( value1, 0 );
        final int iValue2 = StringUtil.convertStrToInt( value2, 0 );

        // take the largest value
        return iValue1 > iValue2 ? value1 : value2;
    }

    protected static String mergeMax( final String value1, final String value2 )
    {
        final int iValue1 = StringUtil.convertStrToInt( value1, 0 );
        final int iValue2 = StringUtil.convertStrToInt( value2, 0 );

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
    public List<UserPermission> profilePermissions( )
    {
        throw new UnsupportedOperationException();
    }

    public List<HealthRecord> health( final Locale locale )
    {
        return healthChecker.get();
    }


    private static List<HealthRecord> doHealthChecks( final PwmPasswordPolicy pwmPasswordPolicy )
    {
        final Locale locale = PwmConstants.DEFAULT_LOCALE;
        final PolicyMetaData policyMetaData = pwmPasswordPolicy.getPolicyMetaData();

        final PasswordRuleReaderHelper ruleHelper = pwmPasswordPolicy.getRuleHelper();
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
                returnList.add( HealthRecord.forMessage(
                        pwmPasswordPolicy.getDomainID(),
                        HealthMessage.Config_PasswordPolicyProblem, policyMetaData.getProfileID(), detailMsg ) );
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
                returnList.add( HealthRecord.forMessage(
                        pwmPasswordPolicy.getDomainID(),
                        HealthMessage.Config_PasswordPolicyProblem, policyMetaData.getProfileID(), detailMsg ) );
            }
        }

        return Collections.unmodifiableList( returnList );
    }



    @Value
    @Builder
    public static class PolicyMetaData implements Serializable
    {
        private final String profileID;

        @Builder.Default
        private final List<UserPermission> userPermissions = Collections.emptyList();

        private final Map<Locale, String> ruleText;

        private final Map<Locale, String> changePasswordText;


        private PolicyMetaData merge( final PolicyMetaData otherPolicy )
        {
            return PolicyMetaData.builder()
                    .ruleText( CollectionUtil.isEmpty( ruleText ) ? otherPolicy.ruleText : ruleText )
                    .changePasswordText( CollectionUtil.isEmpty( changePasswordText ) ? otherPolicy.changePasswordText : changePasswordText )
                    .userPermissions( CollectionUtil.isEmpty( userPermissions ) ? otherPolicy.userPermissions : userPermissions )
                    .profileID( StringUtil.isEmpty( profileID ) ? otherPolicy.profileID : profileID )
                    .build();
        }
    }
}

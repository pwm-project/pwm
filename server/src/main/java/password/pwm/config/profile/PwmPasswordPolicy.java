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
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredSettingReader;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.value.data.UserPermission;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.password.PasswordRuleReaderHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


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

    private final Map<String, String> policyMap;
    private final PolicyMetaData policyMetaData;

    private PwmPasswordPolicy(
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

        this.chaiPasswordPolicy = chaiPasswordPolicy;
        this.policyMetaData = policyMetaData == null ? PolicyMetaData.builder().build() : policyMetaData;
        this.policyMap = Collections.unmodifiableMap( new TreeMap<>( effectivePolicyMap ) );
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
        final PolicyMetaData policyMetaData = PolicyMetaData.builder().domainID( domainID ).build();
        return new PwmPasswordPolicy( policyMap, chaiPasswordPolicy, policyMetaData );
    }

    public static PwmPasswordPolicy createPwmPasswordPolicy(
            final Map<String, String> policyMap,
            final ChaiPasswordPolicy chaiPasswordPolicy,
            final PolicyMetaData policyMetaData
    )
    {
        return new PwmPasswordPolicy( policyMap, chaiPasswordPolicy, policyMetaData );
    }

    public static PwmPasswordPolicy createPwmPasswordPolicy(
            final DomainConfig domainConfig,
            final ProfileID profileID
    )
    {
        final StoredSettingReader settingReader = new StoredSettingReader( domainConfig.getStoredConfiguration(), profileID,  domainConfig.getDomainID() );
        final Map<String, String> passwordPolicySettings = new LinkedHashMap<>();
        for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
        {
            if ( rule.getPwmSetting() != null || rule.getAppProperty() != null )
            {
                final PwmPasswordRuleFunctions.NewRuleValueFunction generator
                        = PwmPasswordRuleFunctions.NEW_RULE_VALUE_FUNCTION.getOrDefault( rule, PwmPasswordRuleFunctions.DEFAULT_NEW_RULE_VALUE_FUNCTION );
                generator.apply( domainConfig, settingReader, rule ).ifPresent(
                        value -> passwordPolicySettings.put( rule.getKey(), value ) );
            }
        }

        // set case sensitivity
        final String caseSensitivitySetting = domainConfig.readSettingAsString( PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY );
        if ( !"read".equals( caseSensitivitySetting ) )
        {
            passwordPolicySettings.put( PwmPasswordRule.CaseSensitive.getKey(), caseSensitivitySetting );
        }

        // set pwm-specific values
        final PwmPasswordPolicy.PolicyMetaData policyMetaData = PolicyMetaData.builder()
                .profileID( profileID )
                .domainID( domainConfig.getDomainID() )
                .userPermissions( settingReader.readSettingAsUserPermission( PwmSetting.PASSWORD_POLICY_QUERY_MATCH ) )
                .ruleText( readLocalizedSetting( PwmSetting.PASSWORD_POLICY_RULE_TEXT, domainConfig, settingReader ) )
                .changePasswordText( readLocalizedSetting( PwmSetting.PASSWORD_POLICY_CHANGE_MESSAGE, domainConfig, settingReader ) )
                .build();

        return PwmPasswordPolicy.createPwmPasswordPolicy( passwordPolicySettings, null, policyMetaData );
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
    public ProfileID getId( )
    {
        return policyMetaData.getProfileID();
    }

    @Override
    public String getDisplayName( final Locale locale )
    {
        return getId() == null ? "[no-profile]" : getId().stringValue();
    }

    public DomainID getDomainID()
    {
        return policyMetaData == null ? null : policyMetaData.getDomainID();
    }

    private static PwmPasswordPolicy makeDefaultPolicy()
    {
        PwmPasswordPolicy newDefaultPolicy = null;
        try
        {
            final Map<String, String> defaultPolicyMap = CollectionUtil.enumStream( PwmPasswordRule.class )
                    .collect( Collectors.toUnmodifiableMap(
                            PwmPasswordRule::getKey,
                            PwmPasswordRule::getDefaultValue ) );

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

        final Map<String, String> newPasswordPolicies = CollectionUtil.enumStream( PwmPasswordRule.class )
                .map( rule -> Map.entry( rule, mergeValue( otherPolicy, rule ) ) )
                .filter( entry -> entry.getValue().isPresent() )
                .collect( Collectors.toUnmodifiableMap( entry -> entry.getKey().getKey(), entry -> entry.getValue().get() ) );

        final ChaiPasswordPolicy backingPolicy = this.chaiPasswordPolicy != null ? chaiPasswordPolicy : otherPolicy.chaiPasswordPolicy;
        final PolicyMetaData metaData = getPolicyMetaData().merge( otherPolicy.getPolicyMetaData() );
        return new PwmPasswordPolicy( newPasswordPolicies, backingPolicy, metaData );
    }

    private Optional<String> mergeValue( final PwmPasswordPolicy otherPolicy, final PwmPasswordRule rule )
    {
        final String ruleKey = rule.getKey();
        final String thisValue = this.policyMap.get( ruleKey );
        final String otherValue = otherPolicy.policyMap.get( ruleKey );

        if ( thisValue != null || otherValue != null )
        {
            final PwmPasswordRuleFunctions.RuleMergeFunction ruleMergeFunction
                    = PwmPasswordRuleFunctions.RULE_MERGE_FUNCTIONS.getOrDefault( rule, PwmPasswordRuleFunctions.DEFAULT_RULE_MERGE_SINGLETON );
            return ruleMergeFunction.apply( rule, thisValue, otherValue );
        }
        return Optional.empty();
    }

    private PolicyMetaData getPolicyMetaData()
    {
        return policyMetaData;
    }

    public Map<String, String> getPolicyMap( )
    {
        return policyMap;
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
                        HealthMessage.Config_PasswordPolicyProblem, policyMetaData.getProfileID().stringValue(), detailMsg ) );
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
                        HealthMessage.Config_PasswordPolicyProblem, policyMetaData.getProfileID().stringValue(), detailMsg ) );
            }
        }

        return Collections.unmodifiableList( returnList );
    }



    @Value
    @Builder
    public static class PolicyMetaData implements Serializable
    {
        private final DomainID domainID;

        private final ProfileID profileID;

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
                    .profileID( profileID == null ? otherPolicy.profileID : profileID )
                    .domainID( domainID == null ? otherPolicy.domainID : domainID )
                    .build();
        }
    }
}

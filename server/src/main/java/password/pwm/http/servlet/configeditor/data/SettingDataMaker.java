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

package password.pwm.http.servlet.configeditor.data;

import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SettingDataMaker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SettingDataMaker.class );

    public static SettingData generateSettingData(
            final DomainID domainID,
            final StoredConfiguration storedConfiguration,
            final SessionLabel sessionLabel,
            final Locale locale,
            final NavTreeSettings navTreeSettings
    )
            throws PwmUnrecoverableException
    {
        final Instant startGenerateTime = Instant.now();
        final PwmSettingTemplateSet templateSet = storedConfiguration.getTemplateSets().get( domainID );

        final Map<String, SettingInfo> settingMap;
        {
            final Set<PwmSetting> interestedSets = StoredConfigurationUtil.allPossibleSettingKeysForConfiguration( storedConfiguration ).stream()
                    .filter( k -> k.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                    .filter( k -> NavTreeDataMaker.settingMatcher( domainID, storedConfiguration, k.toPwmSetting(), k.getProfileID().orElse( null ), navTreeSettings ) )
                    .map( StoredConfigKey::toPwmSetting )
                    .collect( Collectors.toSet() );

            settingMap = interestedSets.stream()
                    .sorted()
                    .collect( Collectors.toMap(
                            PwmSetting::getKey,
                            pwmSetting -> SettingInfo.forSetting( pwmSetting, templateSet, locale ),
                            ( u, v ) ->
                            {
                                throw new IllegalStateException();
                            },
                            LinkedHashMap::new ) );
        }

        final Map<String, CategoryInfo> categoryInfoMap = Collections.unmodifiableMap( Arrays.stream( PwmSettingCategory.values() )
                .collect( Collectors.toMap(
                        PwmSettingCategory::getKey,
                        pwmSettingCategory -> CategoryInfo.forCategory( pwmSettingCategory, locale ),
                        ( u, v ) -> v,
                        LinkedHashMap::new ) ) );

        final Map<String, LocaleInfo> labelMap = Collections.unmodifiableMap( Arrays.stream( PwmLocaleBundle.values() )
                .collect( Collectors.toMap(
                        pwmLocaleBundle ->  pwmLocaleBundle.getTheClass().getSimpleName(),
                        LocaleInfo::forBundle,
                        ( u, v ) -> v,
                        LinkedHashMap::new ) ) );

        final List<ProfileID> profileIDList = StoredConfigurationUtil.profilesForSetting( domainID, PwmSetting.LDAP_PROFILE_LIST, storedConfiguration );
        final VarData varMap = VarData.builder()
                .ldapProfileIds( CollectionUtil.convertListType( profileIDList, ProfileID::toString ) )
                .domainIds( StoredConfigurationUtil.domainList( storedConfiguration ).stream()
                        .map( DomainID::stringValue ).sorted().collect( Collectors.toList() ) )
                .currentTemplate( templateSet )
                .build();

        final SettingData settingData = SettingData.builder()
                .settings( settingMap )
                .categories( categoryInfoMap )
                .locales( labelMap )
                .var( varMap )
                .build();

        LOGGER.trace( sessionLabel, () -> "generated settingData with "
                + settingData.getSettings().size() + " settings and "
                + settingData.getCategories().size() + " categories", TimeDuration.fromCurrent( startGenerateTime ) );

        return settingData;
    }
}

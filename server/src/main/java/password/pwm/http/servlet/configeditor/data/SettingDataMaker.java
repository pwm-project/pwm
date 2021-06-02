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

package password.pwm.http.servlet.configeditor.data;

import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SettingDataMaker
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SettingDataMaker.class );

    public static void initializeCache()
    {
            try
            {
                SettingDataMaker.generateSettingData( StoredConfigurationFactory.newConfig(), null, PwmConstants.DEFAULT_LOCALE );
            }
            catch ( final Exception e )
            {
                LOGGER.debug( () -> "error initializing generateSettingData: " + e.getMessage() );
            }
    }

    public static SettingData generateSettingData(
            final StoredConfiguration storedConfiguration,
            final SessionLabel sessionLabel,
            final Locale locale

    )
            throws PwmUnrecoverableException
    {
        final Instant startGenerateTime = Instant.now();
        final PwmSettingTemplateSet templateSet = storedConfiguration.getTemplateSet();

        final Map<String, SettingInfo> settingMap = Collections.unmodifiableMap( Arrays.stream( PwmSetting.values() )
                .collect( Collectors.toMap(
                        PwmSetting::getKey,
                        pwmSetting -> SettingInfo.forSetting( pwmSetting, templateSet, locale ),
                        ( u, v ) -> v,
                        LinkedHashMap::new ) ) );

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

        final VarData varMap = VarData.builder()
                .ldapProfileIds( ValueTypeConverter.valueToStringArray( storedConfiguration.readSetting( PwmSetting.LDAP_PROFILE_LIST, null ) ) )
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
                + settingData.getCategories().size() + " categories", () -> TimeDuration.fromCurrent( startGenerateTime ) );

        return settingData;
    }
}

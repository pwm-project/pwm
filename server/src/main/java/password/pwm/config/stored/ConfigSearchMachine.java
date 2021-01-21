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

package password.pwm.config.stored;

import password.pwm.bean.DomainID;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.value.StoredValue;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigSearchMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigSearchMachine.class );

    private final StoredConfiguration storedConfiguration;
    private final Locale locale;

    public ConfigSearchMachine(
            final StoredConfiguration storedConfiguration,
            final Locale locale
    )
    {
        this.storedConfiguration = storedConfiguration;
        this.locale = locale;
    }

    public static boolean matchSetting(
            final StoredConfiguration storedConfiguration,
            final PwmSetting setting,
            final StoredValue storedValue,
            final String term,
            final Locale defaultLocale )
    {
        return new ConfigSearchMachine( storedConfiguration, defaultLocale ).matchSetting( setting, storedValue, term );
    }

    public Set<StoredConfigKey> search( final String searchTerm, final Set<DomainID> domainScope )
    {
        if ( StringUtil.isEmpty( searchTerm ) )
        {
            return Collections.emptySet();
        }

        return StoredConfigurationUtil.allPossibleSettingKeysForConfiguration( storedConfiguration )
                .parallelStream()
                .filter( k -> k.getRecordType() == StoredConfigKey.RecordType.SETTING )
                .filter( k -> CollectionUtil.isEmpty( domainScope ) || domainScope.contains( k.getDomainID() ) )
                .filter( k -> matchSetting( k, searchTerm ) )
                .sorted()
                .collect( Collectors.toCollection( LinkedHashSet::new ) );
    }

    private boolean matchSetting(
            final StoredConfigKey storedConfigKey,
            final String searchTerm
    )
    {
        final PwmSetting pwmSetting = storedConfigKey.toPwmSetting();
        final Optional<StoredValue> value = storedConfiguration.readStoredValue( storedConfigKey );

        if ( value.isEmpty() )
        {
            return false;
        }

        return StringUtil.whitespaceSplit( searchTerm )
                .parallelStream()
                .allMatch( s -> matchSetting( pwmSetting, value.get(), s ) );
    }

    private boolean matchSetting(
            final PwmSetting setting,
            final StoredValue value,
            final String searchTerm
    )
    {
        if ( setting.isHidden() || setting.getCategory().isHidden() )
        {
            return false;
        }

        if ( searchTerm == null || searchTerm.isEmpty() )
        {
            return false;
        }

        final String lowerSearchTerm = searchTerm.toLowerCase();

        {
            final String key = setting.getKey();
            if ( key.toLowerCase().contains( lowerSearchTerm ) )
            {
                return true;
            }
        }
        {
            final String label = setting.getLabel( locale );
            if ( label.toLowerCase().contains( lowerSearchTerm ) )
            {
                return true;
            }
        }
        {
            final String descr = setting.getDescription( locale );
            if ( descr.toLowerCase().contains( lowerSearchTerm ) )
            {
                return true;
            }
        }
        {
            final String menuLocationString = setting.toMenuLocationDebug( null, locale );
            if ( menuLocationString.toLowerCase().contains( lowerSearchTerm ) )
            {
                return true;
            }
        }

        if ( setting.isConfidential() )
        {
            return false;
        }
        {
            final String valueDebug = value.toDebugString( locale );
            if ( valueDebug != null && valueDebug.toLowerCase().contains( lowerSearchTerm ) )
            {
                return true;
            }
        }
        if ( PwmSettingSyntax.SELECT == setting.getSyntax()
                || PwmSettingSyntax.OPTIONLIST == setting.getSyntax()
                || PwmSettingSyntax.VERIFICATION_METHOD == setting.getSyntax()
        )
        {
            for ( final String key : setting.getOptions().keySet() )
            {
                if ( key.toLowerCase().contains( lowerSearchTerm ) )
                {
                    return true;
                }
                final String optionValue = setting.getOptions().get( key );
                if ( optionValue != null && optionValue.toLowerCase().contains( lowerSearchTerm ) )
                {
                    return true;
                }
            }
        }
        return false;
    }
}

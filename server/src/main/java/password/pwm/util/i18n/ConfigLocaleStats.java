/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.util.i18n;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.StoredValue;
import password.pwm.config.value.ChallengeValue;
import password.pwm.config.value.data.ChallengeItemConfiguration;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.Percent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Value
@Builder
public class ConfigLocaleStats
{
    private List<Locale> defaultChallenges;
    private Map<Locale, String> descriptionPercentLocalizations;
    private Map<Locale, Integer> descriptionPresentLocalizations;
    private Map<Locale, Integer> descriptionMissingLocalizations;

    public static ConfigLocaleStats getConfigLocaleStats( )
            throws PwmUnrecoverableException, PwmOperationalException
    {

        final List<Locale> knownLocales = LocaleHelper.knownBuiltInLocales();

        final List<Locale> defaultChallenges = new ArrayList<>();
        final Map<Locale, String> descriptionPercentLocalizations = new LinkedHashMap<>();
        final Map<Locale, Integer> descriptionPresentLocalizations = new LinkedHashMap<>();
        final Map<Locale, Integer> descriptionMissingLocalizations = new LinkedHashMap<>();

        {
            final StoredValue storedValue = PwmSetting.CHALLENGE_RANDOM_CHALLENGES.getDefaultValue( PwmSettingTemplateSet.getDefault() );
            final Map<String, List<ChallengeItemConfiguration>> value = ( ( ChallengeValue ) storedValue ).toNativeObject();

            for ( final String localeStr : value.keySet() )
            {
                final Locale loopLocale = LocaleHelper.parseLocaleString( localeStr );
                defaultChallenges.add( loopLocale );
            }
        }

        for ( final Locale locale : knownLocales  )
        {
            descriptionPresentLocalizations.put( locale, 0 );
            descriptionMissingLocalizations.put( locale, 0 );
        }

        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            final String defaultValue = pwmSetting.getDescription( PwmConstants.DEFAULT_LOCALE );
            descriptionPresentLocalizations.put(
                    PwmConstants.DEFAULT_LOCALE,
                    descriptionPresentLocalizations.get( PwmConstants.DEFAULT_LOCALE ) + 1 );

            for ( final Locale locale : knownLocales )
            {
                if ( !PwmConstants.DEFAULT_LOCALE.equals( locale ) )
                {
                    final String localeValue = pwmSetting.getDescription( locale );
                    if ( defaultValue.equals( localeValue ) )
                    {
                        descriptionMissingLocalizations.put( PwmConstants.DEFAULT_LOCALE, descriptionMissingLocalizations.get( locale ) + 1 );
                    }
                    else
                    {
                        descriptionPresentLocalizations.put( PwmConstants.DEFAULT_LOCALE, descriptionPresentLocalizations.get( locale ) + 1 );
                    }
                }
            }
        }

        for ( final Locale locale : knownLocales )
        {
            final int totalCount = PwmSetting.values().length;
            final int presentCount = descriptionPresentLocalizations.get( locale );
            final Percent percent = new Percent( presentCount, totalCount );
            descriptionPercentLocalizations.put( locale, percent.pretty() );
        }

        return ConfigLocaleStats.builder()
                .defaultChallenges( Collections.unmodifiableList( defaultChallenges ) )
                .descriptionPercentLocalizations( Collections.unmodifiableMap( descriptionPercentLocalizations ) )
                .descriptionPresentLocalizations( Collections.unmodifiableMap( descriptionPresentLocalizations ) )
                .descriptionMissingLocalizations( Collections.unmodifiableMap( descriptionMissingLocalizations ) )
                .build();

    }

}

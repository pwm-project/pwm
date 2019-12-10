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

package password.pwm.util.i18n;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.Percent;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Value
@Builder
public class LocaleStats
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LocaleStats.class );
    private static final boolean DEBUG_FLAG = false;

    List<Locale> localesExamined;
    int totalKeys;
    int presentSlots;
    int missingSlots;
    int totalSlots;
    String totalPercentage;
    Map<Locale, String> perLocalePercentLocalizations;
    Map<Locale, Integer> perLocalePresentLocalizations;
    Map<Locale, Integer> perLocaleMissingLocalizations;

    public static Map<PwmLocaleBundle, LocaleStats> getAllLocaleStats()
    {
        final Map<PwmLocaleBundle, LocaleStats> returnMap = new LinkedHashMap<>(  );
        for ( final PwmLocaleBundle pwmLocaleBundle : PwmLocaleBundle.allValues() )
        {
            returnMap.put( pwmLocaleBundle, createLocaleStatsForBundle( pwmLocaleBundle ) );
        }
        return Collections.unmodifiableMap( returnMap );
    }


    public static LocaleStats getAllStats()
    {
        final Map<PwmLocaleBundle, LocaleStats> workingMap = getAllLocaleStats();
        return mergeLocaleStats( workingMap.values() );
    }

    public static LocaleStats getUserFacingStats()
    {
        final Map<PwmLocaleBundle, LocaleStats> workingMap = new LinkedHashMap<>( getAllLocaleStats() );
        workingMap.keySet().removeIf( PwmLocaleBundle::isAdminOnly );
        return mergeLocaleStats( workingMap.values() );
    }

    public static LocaleStats getAdminFacingStats()
    {
        final Map<PwmLocaleBundle, LocaleStats> workingMap = new LinkedHashMap<>( getAllLocaleStats() );
        workingMap.keySet().removeIf( pwmLocaleBundle -> !pwmLocaleBundle.isAdminOnly() );
        return mergeLocaleStats( workingMap.values() );
    }

    private static LocaleStats mergeLocaleStats(
            final Collection<LocaleStats> localeStats
    )
    {
        int totalKeys = 0;
        int totalSlots = 0;
        int missingSlots = 0;
        int presentSlots = 0;

        final Map<Locale, Integer> perLocaleMissingLocalizations = new LinkedHashMap<>( );
        final Map<Locale, Integer> perLocalePresentLocalizations = new LinkedHashMap<>( );
        final Map<Locale, String> perLocalePercentLocalizations = new LinkedHashMap<>();

        for ( final LocaleStats loopStats : localeStats )
        {
            totalKeys += loopStats.getTotalKeys();
            totalSlots += loopStats.getTotalSlots();
            missingSlots += loopStats.getMissingSlots();
            presentSlots += loopStats.getPresentSlots();

            for ( final Locale locale : loopStats.getLocalesExamined() )
            {
                final int combinedMissing = perLocaleMissingLocalizations.getOrDefault( locale, 0 )
                        + loopStats.getPerLocaleMissingLocalizations().getOrDefault( locale, 0 );
                perLocaleMissingLocalizations.put( locale, combinedMissing );


                final int combinedPresent = perLocalePresentLocalizations.getOrDefault( locale, 0 )
                        + loopStats.getPerLocalePresentLocalizations().getOrDefault( locale, 0 );
                perLocalePresentLocalizations.put( locale, combinedPresent );

                final Percent percent = new Percent( combinedPresent, totalKeys );
                perLocalePercentLocalizations.put( locale, percent.pretty( 0 ) );
            }

        }

        final Percent totalPercentage = new Percent( presentSlots, totalSlots );

        return LocaleStats.builder()
                .localesExamined( LocaleHelper.knownBuiltInLocales() )
                .totalKeys( totalKeys )
                .totalSlots( totalSlots )
                .presentSlots( presentSlots )
                .missingSlots( missingSlots )
                .perLocaleMissingLocalizations( perLocaleMissingLocalizations )
                .perLocalePresentLocalizations( perLocalePresentLocalizations )
                .perLocalePercentLocalizations( perLocalePercentLocalizations )
                .totalPercentage( totalPercentage.pretty( 0 ) )
                .build();
    }


    private static LocaleStats createLocaleStatsForBundle(
            final PwmLocaleBundle pwmLocaleBundle
    )
    {
        final List<Locale> knownLocales = LocaleHelper.knownBuiltInLocales();
        final int totalKeysInBundle = pwmLocaleBundle.getDisplayKeys().size();

        int totalSlots = 0;
        int missingSlots = 0;
        int presentSlots = 0;

        final Map<Locale, Integer> perLocaleMissingLocalizations = new LinkedHashMap<>( );
        final Map<Locale, Integer> perLocalePresentLocalizations = new LinkedHashMap<>( );
        final Map<Locale, String> perLocalePercentLocalizations = new LinkedHashMap<>();

        for ( final Locale locale : knownLocales )
        {
            final List<String> missingKeys = missingKeysForBundleAndLocale( pwmLocaleBundle, locale );
            final int presentKeys = totalKeysInBundle - missingKeys.size();

            totalSlots += totalKeysInBundle;
            missingSlots += missingKeys.size();
            presentSlots += presentKeys;
            perLocaleMissingLocalizations.put( locale, missingKeys.size() );
            perLocalePresentLocalizations.put( locale, totalKeysInBundle - missingKeys.size() );

            final Percent percent = new Percent( presentKeys, totalKeysInBundle );
            perLocalePercentLocalizations.put( locale, percent.pretty( 0 ) );
        }

        final Percent totalPercentage = new Percent( presentSlots, totalSlots );

        return LocaleStats.builder()
                .localesExamined( knownLocales )
                .totalKeys( totalKeysInBundle )
                .totalSlots( totalSlots )
                .presentSlots( presentSlots )
                .missingSlots( missingSlots )
                .perLocaleMissingLocalizations( perLocaleMissingLocalizations )
                .perLocalePresentLocalizations( perLocalePresentLocalizations )
                .perLocalePercentLocalizations( perLocalePercentLocalizations )
                .totalPercentage( totalPercentage.pretty( 0 ) )
                .build();
    }

    public static List<String> missingKeysForBundleAndLocale(
            final PwmLocaleBundle pwmLocaleBundle,
            final Locale locale
    )
    {
        final List<String> returnList = new ArrayList<>();

        final String bundleFilename = PwmConstants.DEFAULT_LOCALE.equals( locale )
                ? pwmLocaleBundle.getTheClass().getSimpleName() + ".properties"
                : pwmLocaleBundle.getTheClass().getSimpleName() + "_" + locale.toString() + ".properties";
        final Properties checkProperties = new Properties();

        try ( InputStream stream = pwmLocaleBundle.getTheClass().getResourceAsStream( bundleFilename ) )
        {
            if ( stream == null )
            {
                if ( DEBUG_FLAG )
                {
                    LOGGER.trace( () -> "missing resource bundle: bundle=" + pwmLocaleBundle.getTheClass().getName() + ", locale=" + locale.toString() );
                }
                returnList.addAll( pwmLocaleBundle.getDisplayKeys() );
            }
            else
            {
                LOGGER.trace( () -> "checking file " + bundleFilename );
                checkProperties.load( stream );
                for ( final String key : pwmLocaleBundle.getDisplayKeys() )
                {
                    if ( !checkProperties.containsKey( key ) )
                    {
                        if ( DEBUG_FLAG )
                        {
                            LOGGER.trace( () -> "missing resource: bundle=" + pwmLocaleBundle.getTheClass().toString() + ", locale=" + locale.toString() + "' key=" + key );
                        }
                        returnList.add( key );
                    }
                }
            }
        }
        catch ( final IOException e )
        {
            if ( DEBUG_FLAG )
            {
                LOGGER.trace( () -> "error loading resource bundle for class='" + pwmLocaleBundle.getTheClass().toString()
                        + ", locale=" + locale.toString() + "', error: " + e.getMessage() );
            }
        }
        Collections.sort( returnList );
        return returnList;
    }


}

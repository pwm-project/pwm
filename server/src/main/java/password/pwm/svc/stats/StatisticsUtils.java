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

package password.pwm.svc.stats;

import org.apache.commons.csv.CSVPrinter;
import password.pwm.bean.SessionLabel;
import password.pwm.http.bean.DisplayElement;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class StatisticsUtils
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StatisticsUtils.class );

    public enum CsvOutputFlag
    {
        includeHeader;
    }

    public static int outputStatsToCsv(
            final SessionLabel sessionLabel,
            final StatisticsService statisticsService,
            final OutputStream outputStream,
            final Locale locale,
            final CsvOutputFlag... flags
    )
            throws IOException
    {
        LOGGER.trace( sessionLabel, () -> "beginning output stats to csv process" );
        final Instant startTime = Instant.now();
        final boolean includeHeader = EnumUtil.enumArrayContainsValue( flags, CsvOutputFlag.includeHeader );

        final CSVPrinter csvPrinter = PwmUtil.makeCsvPrinter( outputStream );

        if ( includeHeader )
        {
            final List<String> headers = Statistic.asSet().stream()
                    .map( stat -> stat.getLabel( locale ) )
                    .collect( Collectors.toList() );

            headers.add( "KEY" );
            headers.add( "YEAR" );
            headers.add( "DAY" );

            csvPrinter.printRecord( headers );
        }

        int counter = 0;
        for ( final StatisticsBundleKey loopKey : allKeys( statisticsService ) )
        {
            counter++;
            final StatisticsBundle bundle = statisticsService.getStatBundleForKey( loopKey )
                    .orElseThrow();

            final List<String> lineOutput = new ArrayList<>( Statistic.asSet().size() );

            lineOutput.add( loopKey.toString() );

            if ( loopKey.getKeyType() == StatisticsBundleKey.KeyType.DAILY )
            {
                lineOutput.add( Integer.toString( loopKey.getYear() ) );
                lineOutput.add( Integer.toString( loopKey.getDay() ) );
            }
            else
            {
                lineOutput.add( "" );
                lineOutput.add( "" );
            }

            lineOutput.addAll( EnumUtil.enumStream( Statistic.class )
                    .map( bundle::getStatistic )
                    .collect( Collectors.toList() ) );

            csvPrinter.printRecord( lineOutput );
        }

        {
            final int finalCounter = counter;
            LOGGER.trace( sessionLabel, () -> "completed output stats to csv process; output "
                    + finalCounter + " records in "
                    + TimeDuration.compactFromCurrent( startTime ) );
        }

        return counter;
    }

    static SortedSet<StatisticsBundleKey> allKeys( final StatisticsService statisticsServices )
    {
        final SortedSet<StatisticsBundleKey> results = new TreeSet<>();
        results.add( StatisticsBundleKey.CUMULATIVE );
        results.add( StatisticsBundleKey.CURRENT );

        // if no historical data then we're done
        if ( statisticsServices.getInitialDailyKey().equals( statisticsServices.getCurrentDailyKey() ) )
        {
            return Collections.emptySortedSet();
        }

        results.addAll( StatisticsBundleKey.range(
                statisticsServices.getInitialDailyKey(),
                statisticsServices.getCurrentDailyKey() ) );

        return Collections.unmodifiableSortedSet( results );
    }

    public static List<DisplayElement> statsDisplayElementsForBundle(
            final String keyPrefix,
            final Locale locale,
            final StatisticsBundle statisticsBundle
    )
    {
        return Statistic.sortedValues( locale ).stream()
                .map( stat -> new DisplayElement(
                        keyPrefix + stat.getKey(),
                        DisplayElement.Type.number,
                        stat.getLabel( locale ),
                        statisticsBundle.getStatistic( stat )
                ) ).collect( Collectors.toUnmodifiableList() );
    }

    public static List<DisplayElement> avgStatsDisplayElementsForBundle(
            final String keyPrefix,
            final Locale locale,
            final StatisticsBundle statisticsBundle
    )
    {
        return EnumUtil.enumStream( AvgStatistic.class )
                .map( stat -> new DisplayElement(
                        keyPrefix + stat.getKey(),
                        DisplayElement.Type.string,
                        stat.getLabel( locale ),
                        stat.prettyValue( statisticsBundle.getAvgStatistic( stat ), locale )
                ) ).collect( Collectors.toUnmodifiableList() );
    }
}

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

import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmDomain;
import password.pwm.http.PwmRequest;
import password.pwm.svc.PwmService;
import password.pwm.util.logging.PwmLogger;

public class StatisticsClient
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StatisticsService.class );
    
    public static void incrementStat(
            final PwmDomain pwmDomain,
            final Statistic statistic
    )
    {
        incrementStat( pwmDomain.getPwmApplication(), statistic );
    }

    public static void incrementStat(
            final PwmRequest pwmRequest,
            final Statistic statistic
    )
    {
        incrementStat( pwmRequest.getPwmDomain(), statistic );
    }

    public static void updateEps( final PwmApplication pwmApplication, final EpsStatistic type )
    {
        updateEps( pwmApplication, type, 1 );
    }

    public static void updateEps( final PwmApplication pwmApplication, final EpsStatistic type, final int itemCount )
    {
        if ( pwmApplication != null && pwmApplication.getApplicationMode() == PwmApplicationMode.RUNNING )
        {
            final StatisticsService statisticsService = pwmApplication.getStatisticsService();
            if ( statisticsService != null && statisticsService.status() == PwmService.STATUS.OPEN )
            {
                statisticsService.updateEps( type, itemCount );
            }
        }
    }

    public static void updateAverageValue( final PwmApplication pwmApplication, final AvgStatistic statistic, final long value )
    {
        if ( pwmApplication == null )
        {
            LOGGER.error( () -> "skipping requested statistic increment of " + statistic + " due to null pwmApplication" );
            return;
        }

        final StatisticsService statisticsManager = pwmApplication.getStatisticsService();
        if ( statisticsManager == null )
        {
            LOGGER.error( () -> "skipping requested statistic increment of " + statistic + " due to null statisticsManager" );
            return;
        }

        statisticsManager.updateAverageValue( statistic, value );
    }


    public static void incrementStat(
            final PwmApplication pwmApplication,
            final Statistic statistic
    )
    {
        if ( pwmApplication == null )
        {
            LOGGER.error( () -> "skipping requested statistic increment of " + statistic + " due to null pwmApplication" );
            return;
        }

        final StatisticsService statisticsManager = pwmApplication.getStatisticsService();
        if ( statisticsManager == null )
        {
            LOGGER.error( () -> "skipping requested statistic increment of " + statistic + " due to null statisticsManager" );
            return;
        }

        if ( statisticsManager.status() != PwmService.STATUS.OPEN )
        {
            LOGGER.trace(
                    () -> "skipping requested statistic increment of " + statistic + " due to StatisticsManager being closed" );
            return;
        }

        statisticsManager.incrementValue( statistic );
    }
}

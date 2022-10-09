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

package password.pwm.svc.report;

import com.novell.ldapchai.cr.Answer;
import lombok.Builder;
import lombok.Value;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.util.java.CollectorUtil;
import password.pwm.util.java.EnumUtil;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Value
@Builder
public class ReportSummaryData
{
    private final Map<DataStorageMethod, Long> responseStorage;
    private final Map<Answer.FormatType, Long> responseFormatType;
    private final Map<DomainID, Map<ProfileID, Long>> ldapProfile;

    private final Map<SummaryCounterStat, Long> counterStats;
    private final Map<SummaryDailyStat, Map<Integer, Long>> dailyCounterStat;

    static ReportSummaryData fromCalculator( final ReportSummaryCalculator calculator )
    {
        final ReportSummaryData.ReportSummaryDataBuilder builder = ReportSummaryData.builder();

        builder.responseStorage( calculator.getResponseStorage() );
        builder.responseFormatType( calculator.getResponseFormatType() );
        builder.ldapProfile( makeLdapProfileFromCalculator( calculator ) );

        builder.counterStats( makeCounterStats( calculator ) );
        builder.dailyCounterStat( makeDailyStats( calculator ) );

        return builder.build();
    }

    private static Map<DomainID, Map<ProfileID, Long>> makeLdapProfileFromCalculator(  final ReportSummaryCalculator calculator )
    {
        return calculator.getLdapProfile().entrySet().stream()
                .collect( Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().entrySet().stream().collect( Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry2 -> entry2.getValue().longValue() ) ) ) );
    }

    private static Map<SummaryCounterStat, Long> makeCounterStats( final ReportSummaryCalculator calculator )
    {
        return EnumUtil.enumStream( SummaryCounterStat.class ).collect( CollectorUtil.toUnmodifiableEnumMap(
                SummaryCounterStat.class,
                Function.identity(),
                entry -> calculator.getCounterStats().get( entry ) ) );
    }

    private static Map<SummaryDailyStat, Map<Integer, Long>> makeDailyStats( final ReportSummaryCalculator calculator )
    {
        return EnumUtil.enumStream( SummaryDailyStat.class ).collect( CollectorUtil.toUnmodifiableEnumMap(
                SummaryDailyStat.class,
                Function.identity(),
                entry -> ReportSummaryCalculator.dailyStatsAsMap( calculator, entry ) ) );
    }

}

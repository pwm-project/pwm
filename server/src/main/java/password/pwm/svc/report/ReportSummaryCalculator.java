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
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.i18n.Admin;
import password.pwm.util.Percent;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.TimeDuration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Value
public class ReportSummaryCalculator
{
    private static final long MS_DAY = TimeDuration.DAY.asMillis();

    private final Map<DataStorageMethod, LongAdder> responseStorage = new ConcurrentHashMap<>();
    private final Map<Answer.FormatType, LongAdder> responseFormatType = new ConcurrentHashMap<>();
    private final Map<DomainID, Map<ProfileID, LongAdder>> ldapProfile = new ConcurrentHashMap<>();

    private final StatisticCounterBundle<SummaryCounterStat> counterStats = new StatisticCounterBundle<>( SummaryCounterStat.class );
    private final Map<Integer, StatisticCounterBundle<SummaryDailyStat>> dailyCounterStat = new ConcurrentHashMap<>();

    private ReportSummaryCalculator( )
    {
    }

    static ReportSummaryCalculator newSummaryData( final List<Integer> trackedDays )
    {
        final ReportSummaryCalculator reportSummaryData = new ReportSummaryCalculator();
        Objects.requireNonNull( trackedDays ).forEach( day ->
        {
            reportSummaryData.getDailyCounterStat().put( day, new StatisticCounterBundle<>( SummaryDailyStat.class ) );
        } );

        return reportSummaryData;
    }

    public Map<DataStorageMethod, Long> getResponseStorage( )
    {
        return Collections.unmodifiableMap( responseStorage.entrySet()
                .stream()
                .collect( Collectors.toMap( Map.Entry::getKey,
                        e -> e.getValue().sum() ) ) );
    }

    public Map<Answer.FormatType, Long> getResponseFormatType( )
    {
        return Collections.unmodifiableMap( responseFormatType.entrySet()
                .stream()
                .collect( Collectors.toMap( Map.Entry::getKey,
                        e -> e.getValue().sum() ) ) );
    }

    void update( final UserReportRecord userReportRecord )
    {
        counterStats.increment( SummaryCounterStat.totalUsers );

        Updaters.UPDATERS.forEach( updater -> updater.accept( userReportRecord, this ) );
    }

    private static class Updaters
    {
        private static final List<BiConsumer<UserReportRecord, ReportSummaryCalculator>> UPDATERS = List.of(
                new UpdateHasResponses(),
                new UpdateHasHelpdeskResponses(),
                new HasResponseSetTime(),
                new UpdatePasswordExpirationTime(),
                new UpdateAccountExpirationTime(),
                new UpdateLastLoginTime(),
                new UpdatePwChangeTime(),
                new UpdatePwExpiredNotification(),
                new UpdatePasswordStatus(),
                new UpdateResponseStorageMethod(),
                new UpdateLdapProfile(),
                new UpdateResponseFormatType(),
                new UpdateHasOtpSecret(),
                new UpdateOtpSecretSetTime()
        );

        private static class UpdateHasResponses implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.isHasResponses() )
                {
                    reportSummaryData.getCounterStats().increment( SummaryCounterStat.hasResponses );
                }

            }
        }

        private static class UpdateHasHelpdeskResponses implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.isHasHelpdeskResponses() )
                {
                    reportSummaryData.getCounterStats().increment( SummaryCounterStat.hasHelpdeskResponses );
                }

            }
        }

        private static class HasResponseSetTime implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getResponseSetTime() != null )
                {
                    reportSummaryData.getCounterStats().increment( SummaryCounterStat.hasResponseSetTime );
                    reportSummaryData.incrementIfWithinTimeWindow( userReportRecord, userReportRecord.getResponseSetTime(), SummaryDailyStat.responseSetDays );
                }
            }
        }

        private static class UpdatePasswordExpirationTime implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getPasswordExpirationTime() != null )
                {
                    reportSummaryData.getCounterStats().increment( SummaryCounterStat.hasPasswordExpirationTime );
                    reportSummaryData.incrementIfWithinTimeWindow( userReportRecord, userReportRecord.getPasswordExpirationTime(), SummaryDailyStat.pwExpireDays );
                }
            }
        }

        private static class UpdateAccountExpirationTime implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getAccountExpirationTime() != null )
                {
                    reportSummaryData.getCounterStats().increment( SummaryCounterStat.hasAccountExpirationTime );
                    reportSummaryData.incrementIfWithinTimeWindow( userReportRecord, userReportRecord.getAccountExpirationTime(), SummaryDailyStat.accountExpireDays );
                }
            }
        }

        private static class UpdateLastLoginTime implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getLastLoginTime() != null )
                {
                    reportSummaryData.getCounterStats().increment( SummaryCounterStat.hasLoginTime );
                    reportSummaryData.incrementIfWithinTimeWindow( userReportRecord, userReportRecord.getLastLoginTime(), SummaryDailyStat.loginDays );
                }
            }
        }

        private static class UpdatePwChangeTime implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getPasswordChangeTime() != null )
                {
                    reportSummaryData.getCounterStats().increment( SummaryCounterStat.hasChangePwTime );
                    reportSummaryData.incrementIfWithinTimeWindow( userReportRecord, userReportRecord.getPasswordChangeTime(), SummaryDailyStat.changePwDays );
                }
            }
        }

        private static class UpdatePwExpiredNotification implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getPasswordExpirationNoticeSendTime() != null )
                {
                    reportSummaryData.getCounterStats().increment( SummaryCounterStat.hasReceivedPwExpireNotification );
                    reportSummaryData.incrementIfWithinTimeWindow( userReportRecord, userReportRecord.getPasswordExpirationNoticeSendTime(),
                            SummaryDailyStat.pwExpireNotificationDays );
                }
            }
        }

        private static class UpdatePasswordStatus implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getPasswordStatus() != null )
                {
                    if ( userReportRecord.getPasswordStatus().isExpired() )
                    {
                        reportSummaryData.getCounterStats().increment( SummaryCounterStat.pwExpired );
                    }
                    if ( userReportRecord.getPasswordStatus().isPreExpired() )
                    {
                        reportSummaryData.getCounterStats().increment( SummaryCounterStat.pwPreExpired );
                    }
                    if ( userReportRecord.getPasswordStatus().isWarnPeriod() )
                    {
                        reportSummaryData.getCounterStats().increment( SummaryCounterStat.pwWarnPeriod );
                    }
                }
            }
        }

        private static class UpdateResponseStorageMethod implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getResponseStorageMethod() != null )
                {
                    final DataStorageMethod method = userReportRecord.getResponseStorageMethod();
                    reportSummaryData.responseStorage
                            .computeIfAbsent( method, dataStorageMethod -> new LongAdder() )
                            .increment();
                }

            }
        }

        private static class UpdateLdapProfile implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getLdapProfile() != null )
                {
                    final DomainID domainID = userReportRecord.getDomainID();
                    final ProfileID userProfile = userReportRecord.getLdapProfile();
                    reportSummaryData.ldapProfile
                            .computeIfAbsent( domainID, type -> new ConcurrentHashMap<>() )
                            .computeIfAbsent( userProfile, type -> new LongAdder() )
                            .increment();
                }
            }
        }

        private static class UpdateResponseFormatType implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getResponseFormatType() != null )
                {
                    final Answer.FormatType type = userReportRecord.getResponseFormatType();
                    reportSummaryData.responseFormatType
                            .computeIfAbsent( type, formatType -> new LongAdder() )
                            .increment();
                }
            }
        }

        private static class UpdateHasOtpSecret implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.isHasOtpSecret() )
                {
                    reportSummaryData.getCounterStats().increment( SummaryCounterStat.hasOtpSecret );
                }
            }
        }

        private static class UpdateOtpSecretSetTime implements BiConsumer<UserReportRecord, ReportSummaryCalculator>
        {
            @Override
            public void accept( final UserReportRecord userReportRecord, final ReportSummaryCalculator reportSummaryData )
            {
                if ( userReportRecord.getOtpSecretSetTime() != null )
                {
                    reportSummaryData.getCounterStats().increment( SummaryCounterStat.hasOtpSecretSetTime );
                    reportSummaryData.incrementIfWithinTimeWindow( userReportRecord, userReportRecord.getOtpSecretSetTime(), SummaryDailyStat.otpSetDays );
                }
            }
        }
    }

    private void incrementIfWithinTimeWindow(
            final UserReportRecord userReportRecord,
            final Instant eventDate,
            final SummaryDailyStat stat
    )
    {
        for ( final Map.Entry<Integer, StatisticCounterBundle<SummaryDailyStat>> entry : dailyCounterStat.entrySet() )
        {
            final int day = entry.getKey();
            final long timeWindow = MS_DAY * day;

            if ( eventDate != null )
            {
                final TimeDuration timeBoundary = TimeDuration.of( timeWindow, TimeDuration.Unit.MILLISECONDS );
                final TimeDuration eventDifference = TimeDuration.fromCurrent( eventDate );

                if (
                        ( timeWindow >= 0 && eventDate.isAfter( Instant.now() ) && eventDifference.isShorterThan( timeBoundary ) )
                                || ( timeWindow < 0 && eventDate.isBefore( Instant.now() ) && eventDifference.isShorterThan( timeBoundary ) )
                )
                {
                    entry.getValue().increment( stat );
                }
            }
        }
    }

    public List<PresentationRow> asPresentableCollection( final AppConfig config, final Locale locale )
    {
        final ArrayList<PresentationRow> returnCollection = new ArrayList<>();
        final PresentationRowBuilder builder = new PresentationRowBuilder(
                config,
                this.getCounterStats().get( SummaryCounterStat.totalUsers ),
                locale );

        final long totalUsers = getCounterStats().get( SummaryCounterStat.totalUsers );
        returnCollection.add( builder.makeNoPctRow( "Field_Report_Sum_Total",
                totalUsers, null ) );

        if ( totalUsers == 0 )
        {
            return List.copyOf( returnCollection );
        }

        for ( final Map.Entry<DomainID, Map<ProfileID, LongAdder>> domainIDMapEntry : new TreeMap<>( ldapProfile ).entrySet() )
        {
            for ( final Map.Entry<ProfileID, LongAdder> profileMapEntry : new TreeMap<>( domainIDMapEntry.getValue() ).entrySet() )
            {
                final DomainID domainID = domainIDMapEntry.getKey();
                final DomainConfig domainConfig = config.getDomainConfigs().get( domainID );
                final ProfileID userProfile = profileMapEntry.getKey();
                final LdapProfile ldapProfile = domainConfig.getLdapProfiles().get( userProfile );
                final long count = profileMapEntry.getValue().sum();

                final String displayName = ( config.getDomainConfigs().size() > 1 ? "[" + domainConfig.getDisplayName( locale ) + "] " : "" )
                        + ldapProfile.getDisplayName( locale );
                returnCollection.add(
                        builder.makeRow( "Field_Report_Sum_LdapProfile", count, displayName ) );
            }
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveLoginTime", this.getCounterStats().get( SummaryCounterStat.hasLoginTime ) ) );

        for ( final Map.Entry<Integer, Long> entry : dailyStatsAsMap( this, SummaryDailyStat.loginDays ).entrySet() )
        {
            if ( entry.getKey() < 0 )
            {
                returnCollection.add( builder.makeRow( "Field_Report_Sum_LoginTimePrevious", entry.getValue(), String.valueOf( Math.abs( entry.getKey() ) ) ) );
            }
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveAccountExpirationTime", this.getCounterStats().get( SummaryCounterStat.hasAccountExpirationTime ) ) );
        for ( final Map.Entry<Integer, Long> entry : dailyStatsAsMap( this, SummaryDailyStat.accountExpireDays ).entrySet() )
        {
            final String key = entry.getKey() < 0 ? "Field_Report_Sum_AccountExpirationPrevious" : "Field_Report_Sum_AccountExpirationNext";
            returnCollection.add( builder.makeRow( key, entry.getValue(), String.valueOf( Math.abs( entry.getKey() ) ) ) );
        }
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HavePwExpirationTime", this.getCounterStats().get( SummaryCounterStat.hasPasswordExpirationTime ) ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveExpiredPw", this.getCounterStats().get( SummaryCounterStat.pwExpired ) ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HavePreExpiredPw", this.getCounterStats().get( SummaryCounterStat.pwPreExpired ) ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveExpiredPwWarn", this.getCounterStats().get( SummaryCounterStat.pwWarnPeriod ) ) );

        for ( final Map.Entry<Integer, Long> entry : dailyStatsAsMap( this, SummaryDailyStat.pwExpireDays ).entrySet() )
        {
            final String key = entry.getKey() < 0 ? "Field_Report_Sum_PwExpirationPrevious" : "Field_Report_Sum_PwExpirationNext";
            returnCollection.add( builder.makeRow( key, entry.getValue(), String.valueOf( Math.abs( entry.getValue() ) ) ) );
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveChgPw", this.getCounterStats().get( SummaryCounterStat.hasChangePwTime ) ) );

        for ( final Map.Entry<Integer, Long> entry : dailyStatsAsMap( this, SummaryDailyStat.changePwDays ).entrySet() )
        {
            if ( entry.getKey() < 0 )
            {
                returnCollection.add( builder.makeRow( "Field_Report_Sum_ChgPwPrevious", entry.getValue(), String.valueOf( Math.abs( entry.getKey() ) ) ) );
            }
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveResponses", this.getCounterStats().get( SummaryCounterStat.hasResponses ) ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveHelpdeskResponses", this.getCounterStats().get( SummaryCounterStat.hasHelpdeskResponses ) ) );

        for ( final DataStorageMethod storageMethod : CollectionUtil.copyToEnumSet( this.getResponseStorage().keySet(), DataStorageMethod.class ) )
        {
            final long count = this.getResponseStorage().get( storageMethod );
            returnCollection.add( builder.makeRow( "Field_Report_Sum_StorageMethod", count, storageMethod.toString() ) );
        }
        for ( final Answer.FormatType formatType : CollectionUtil.copyToEnumSet( this.getResponseFormatType().keySet(), Answer.FormatType.class ) )
        {
            final long count = this.getResponseFormatType().get( formatType );
            returnCollection.add( builder.makeRow( "Field_Report_Sum_ResponseFormatType", count, formatType.toString() ) );
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveResponseTime", this.getCounterStats().get( SummaryCounterStat.hasResponseSetTime ) ) );
        for ( final Map.Entry<Integer, Long> entry : dailyStatsAsMap( this, SummaryDailyStat.responseSetDays ).entrySet() )
        {
            if ( entry.getKey() < 0 )
            {
                returnCollection.add( builder.makeRow( "Field_Report_Sum_ResponseTimePrevious", entry.getValue(), String.valueOf( Math.abs( entry.getKey() ) ) ) );
            }
        }

        if ( this.getCounterStats().get( SummaryCounterStat.hasOtpSecret ) > 0 )
        {
            returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveOtpSecret", this.getCounterStats().get( SummaryCounterStat.hasOtpSecret ) ) );
            returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveOtpSecretSetTime", this.getCounterStats().get( SummaryCounterStat.hasOtpSecretSetTime ) ) );
            for ( final Map.Entry<Integer, Long> entry : dailyStatsAsMap( this, SummaryDailyStat.otpSetDays ).entrySet() )
            {
                if ( entry.getKey() < 0 )
                {
                    returnCollection.add( builder.makeRow( "Field_Report_Sum_OtpSecretTimePrevious", entry.getValue(), String.valueOf( Math.abs( entry.getKey() ) ) ) );
                }
            }
        }

        if ( this.getCounterStats().get( SummaryCounterStat.hasReceivedPwExpireNotification ) > 0 )
        {
            returnCollection.add( new PresentationRow( "Has Received PwExpiry Notice",
                    Long.toString( this.getCounterStats().get( SummaryCounterStat.hasReceivedPwExpireNotification ) ), null ) );

            for ( final Map.Entry<Integer, Long> entry : dailyStatsAsMap( this, SummaryDailyStat.pwExpireNotificationDays ).entrySet() )
            {
                if ( entry.getKey() < 0 )
                {
                    returnCollection.add( new PresentationRow( "PwExpireNotice " + entry, Long.toString( entry.getValue() ), null ) );
                }
            }
        }


        return List.copyOf( returnCollection );
    }

    static SortedMap<Integer, Long> dailyStatsAsMap( final ReportSummaryCalculator calculator, final SummaryDailyStat dailyCounterStat )
    {
        final Map<Integer, Long> tempMap = calculator.getDailyCounterStat().entrySet().stream()
                .filter( value -> value.getValue().get( dailyCounterStat ) > 0 )
                .collect( Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get( dailyCounterStat )
                ) );

        return Collections.unmodifiableSortedMap( new TreeMap<>( tempMap ) );
    }

    @Value
    @Builder( toBuilder = true )
    public static class PresentationRow
    {
        private String label;
        private String count;
        private String pct;

        public List<String> toStringList()
        {
            final List<String> tempList = new ArrayList<>( 3 );
            tempList.add( label == null ? "" : label );
            tempList.add( count == null ? "" : count );

            if ( pct != null )
            {
                tempList.add( pct );
            }

            return List.copyOf( tempList );
        }
    }

    @Value
    public static class PresentationRowBuilder
    {
        private final AppConfig config;
        private final long totalUsers;
        private final Locale locale;

        PresentationRow makeRow( final String labelKey, final long valueCount )
        {
            return makeRow( labelKey, valueCount, null );
        }

        PresentationRow makeRow( final String labelKey, final long valueCount, final String replacement )
        {
            return makeRowImpl( labelKey, valueCount, replacement );
        }

        PresentationRow makeNoPctRow( final String labelKey, final long valueCount, final String replacement )
        {
            return makeRowImpl( labelKey, valueCount, replacement ).toBuilder().pct( "" ).build();
        }

        private PresentationRow makeRowImpl( final String labelKey, final long valueCount, final String replacement )
        {
            final String display = replacement == null
                    ? LocaleHelper.getLocalizedMessage( locale, labelKey, config, Admin.class )
                    : LocaleHelper.getLocalizedMessage( locale, labelKey, config, Admin.class, new String[]
                            {
                                    replacement,
                            }
                    );
            final String pct = valueCount > 0 ? Percent.of( valueCount, totalUsers ).pretty( 2 ) : "";
            final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale( locale );
            final String formattedCount = numberFormat.format( valueCount );
            return new PresentationRow( display, formattedCount, pct );
        }
    }
}

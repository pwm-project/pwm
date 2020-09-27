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

package password.pwm.svc.report;

import com.novell.ldapchai.cr.Answer;
import lombok.Builder;
import lombok.Value;
import password.pwm.config.Configuration;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.i18n.Admin;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.Percent;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.TimeDuration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

@Value
public class ReportSummaryData
{
    private static final long MS_DAY = TimeDuration.DAY.asMillis();

    private final LongAdder totalUsers = new LongAdder();
    private final LongAdder hasResponses = new LongAdder();
    private final LongAdder hasResponseSetTime = new LongAdder();
    private final LongAdder hasHelpdeskResponses = new LongAdder();
    private final LongAdder hasPasswordExpirationTime = new LongAdder();
    private final LongAdder hasAccountExpirationTime = new LongAdder();
    private final LongAdder hasLoginTime = new LongAdder();
    private final LongAdder hasChangePwTime = new LongAdder();
    private final LongAdder hasOtpSecret = new LongAdder();
    private final LongAdder hasOtpSecretSetTime = new LongAdder();
    private final LongAdder pwExpired = new LongAdder();
    private final LongAdder pwPreExpired = new LongAdder();
    private final LongAdder pwWarnPeriod = new LongAdder();
    private final LongAdder hasReceivedPwExpireNotification = new LongAdder();

    private final Map<DataStorageMethod, LongAdder> responseStorage = new ConcurrentHashMap<>();
    private final Map<Answer.FormatType, LongAdder> responseFormatType = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> ldapProfile = new ConcurrentHashMap<>();
    private final Map<Integer, LongAdder> pwExpireDays = new ConcurrentHashMap<>();
    private final Map<Integer, LongAdder> accountExpireDays = new ConcurrentHashMap<>();
    private final Map<Integer, LongAdder> changePwDays = new ConcurrentHashMap<>();
    private final Map<Integer, LongAdder> responseSetDays = new ConcurrentHashMap<>();
    private final Map<Integer, LongAdder> otpSetDays = new ConcurrentHashMap<>();
    private final Map<Integer, LongAdder> loginDays = new ConcurrentHashMap<>();
    private final Map<Integer, LongAdder> pwExpireNotificationDays = new ConcurrentHashMap<>();

    private ReportSummaryData( )
    {
    }

    static ReportSummaryData newSummaryData( final List<Integer> trackedDays )
    {
        final ReportSummaryData reportSummaryData = new ReportSummaryData();

        if ( trackedDays != null )
        {
            for ( final int day : trackedDays )
            {
                reportSummaryData.pwExpireDays.put( day, new LongAdder() );
                reportSummaryData.accountExpireDays.put( day, new LongAdder() );
                reportSummaryData.changePwDays.put( day, new LongAdder() );
                reportSummaryData.responseSetDays.put( day, new LongAdder() );
                reportSummaryData.otpSetDays.put( day, new LongAdder() );
                reportSummaryData.loginDays.put( day, new LongAdder() );
                reportSummaryData.pwExpireNotificationDays.put( day, new LongAdder() );
            }
        }

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

    void update( final UserCacheRecord userCacheRecord )
    {
        totalUsers.increment();

        if ( userCacheRecord.isHasResponses() )
        {
            hasResponses.increment();
        }

        if ( userCacheRecord.isHasHelpdeskResponses() )
        {
            hasHelpdeskResponses.increment();
        }

        if ( userCacheRecord.getResponseSetTime() != null )
        {
            hasResponseSetTime.increment();
            incrementIfWithinTimeWindow( userCacheRecord, responseSetDays );
        }

        if ( userCacheRecord.getPasswordExpirationTime() != null )
        {
            hasPasswordExpirationTime.increment();
            incrementIfWithinTimeWindow( userCacheRecord, pwExpireDays );
        }

        if ( userCacheRecord.getAccountExpirationTime() != null )
        {
            hasAccountExpirationTime.increment();
            incrementIfWithinTimeWindow( userCacheRecord, accountExpireDays );
        }

        if ( userCacheRecord.getLastLoginTime() != null )
        {
            hasLoginTime.increment();
            incrementIfWithinTimeWindow( userCacheRecord, loginDays );
        }

        if ( userCacheRecord.getPasswordChangeTime() != null )
        {
            hasChangePwTime.increment();
            incrementIfWithinTimeWindow( userCacheRecord, changePwDays );
        }

        if ( userCacheRecord.getPasswordExpirationNoticeSendTime() != null )
        {
            hasReceivedPwExpireNotification.increment();
            incrementIfWithinTimeWindow( userCacheRecord, pwExpireNotificationDays );
        }

        if ( userCacheRecord.getPasswordStatus() != null )
        {
            if ( userCacheRecord.getPasswordStatus().isExpired() )
            {
                pwExpired.increment();
            }
            if ( userCacheRecord.getPasswordStatus().isPreExpired() )
            {
                pwPreExpired.increment();
            }
            if ( userCacheRecord.getPasswordStatus().isWarnPeriod() )
            {
                pwWarnPeriod.increment();
            }
        }

        if ( userCacheRecord.getResponseStorageMethod() != null )
        {
            final DataStorageMethod method = userCacheRecord.getResponseStorageMethod();
            responseStorage
                    .computeIfAbsent( method, dataStorageMethod -> new LongAdder() )
                    .increment();
        }

        if ( userCacheRecord.getLdapProfile() != null )
        {
            final String userProfile = userCacheRecord.getLdapProfile();
            ldapProfile
                    .computeIfAbsent( userProfile, type -> new LongAdder() )
                    .increment();
        }

        if ( userCacheRecord.getResponseFormatType() != null )
        {
            final Answer.FormatType type = userCacheRecord.getResponseFormatType();
            responseFormatType
                    .computeIfAbsent( type, formatType -> new LongAdder() )
                    .increment();
        }

        if ( userCacheRecord.isHasOtpSecret() )
        {
            hasOtpSecret.increment();
        }

        if ( userCacheRecord.getOtpSecretSetTime() != null )
        {
            hasOtpSecretSetTime.increment();
            incrementIfWithinTimeWindow( userCacheRecord, otpSetDays );
        }
    }

    private void incrementIfWithinTimeWindow(
            final UserCacheRecord userCacheRecord,
            final Map<Integer, LongAdder> map
    )
    {
        for ( final Map.Entry<Integer, LongAdder> entry : map.entrySet() )
        {
            final int day = entry.getKey();
            final Instant eventDate = userCacheRecord.getOtpSecretSetTime();
            final long timeWindow = MS_DAY * day;
            final LongAdder number = entry.getValue();

            if ( eventDate != null )
            {
                final TimeDuration timeBoundary = TimeDuration.of( timeWindow, TimeDuration.Unit.MILLISECONDS );
                final TimeDuration eventDifference = TimeDuration.fromCurrent( eventDate );

                if (
                        ( timeWindow >= 0 && eventDate.isAfter( Instant.now() ) && eventDifference.isShorterThan( timeBoundary ) )
                                || ( timeWindow < 0 && eventDate.isBefore( Instant.now() ) && eventDifference.isShorterThan( timeBoundary ) )
                )
                {
                    number.increment();
                }
            }
        }
    }


    public List<PresentationRow> asPresentableCollection( final Configuration config, final Locale locale )
    {
        final ArrayList<PresentationRow> returnCollection = new ArrayList<>();
        final PresentationRowBuilder builder = new PresentationRowBuilder( config, this.totalUsers.sum(), locale );

        returnCollection.add( builder.makeNoPctRow( "Field_Report_Sum_Total", this.totalUsers.sum(), null ) );
        if ( totalUsers.sum() == 0 )
        {
            return returnCollection;
        }

        if ( config.getLdapProfiles().keySet().size() > 1 )
        {
            for ( final Map.Entry<String, LongAdder> entry : new TreeMap<>( ldapProfile ).entrySet() )
            {
                final String userProfile = entry.getKey();
                final long count = entry.getValue().sum();
                final String displayName = config.getLdapProfiles().containsKey( userProfile )
                        ? config.getLdapProfiles().get( userProfile ).getDisplayName( locale )
                        : userProfile;
                returnCollection.add(
                        builder.makeRow( "Field_Report_Sum_LdapProfile", count, displayName ) );
            }
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveLoginTime", this.hasLoginTime.sum() ) );
        for ( final Integer day : new TreeSet<>( loginDays.keySet() ) )
        {
            if ( day < 0 )
            {
                returnCollection.add( builder.makeRow( "Field_Report_Sum_LoginTimePrevious", this.loginDays.get( day ).sum(), String.valueOf( Math.abs( day ) ) ) );
            }
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveAccountExpirationTime", this.hasAccountExpirationTime.sum() ) );
        for ( final Integer day : new TreeSet<>( accountExpireDays.keySet() ) )
        {
            final String key = day < 0 ? "Field_Report_Sum_AccountExpirationPrevious" : "Field_Report_Sum_AccountExpirationNext";
            returnCollection.add( builder.makeRow( key, this.accountExpireDays.get( day ).sum(), String.valueOf( Math.abs( day ) ) ) );
        }
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HavePwExpirationTime", this.hasPasswordExpirationTime.sum() ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveExpiredPw", this.pwExpired.sum() ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HavePreExpiredPw", this.pwPreExpired.sum() ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveExpiredPwWarn", this.pwWarnPeriod.sum() ) );
        for ( final Integer day : new TreeSet<>( pwExpireDays.keySet() ) )
        {
            final String key = day < 0 ? "Field_Report_Sum_PwExpirationPrevious" : "Field_Report_Sum_PwExpirationNext";
            returnCollection.add( builder.makeRow( key, this.pwExpireDays.get( day ).sum(), String.valueOf( Math.abs( day ) ) ) );
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveChgPw", this.hasChangePwTime.sum() ) );
        for ( final Integer day : new TreeSet<>( changePwDays.keySet() ) )
        {
            if ( day < 0 )
            {
                returnCollection.add( builder.makeRow( "Field_Report_Sum_ChgPwPrevious", this.changePwDays.get( day ).sum(), String.valueOf( Math.abs( day ) ) ) );
            }
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveResponses", this.hasResponses.sum() ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveHelpdeskResponses", this.hasHelpdeskResponses.sum() ) );
        for ( final DataStorageMethod storageMethod : JavaHelper.copiedEnumSet( this.getResponseStorage().keySet(), DataStorageMethod.class ) )
        {
            final long count = this.getResponseStorage().get( storageMethod );
            returnCollection.add( builder.makeRow( "Field_Report_Sum_StorageMethod", count, storageMethod.toString() ) );
        }
        for ( final Answer.FormatType formatType : JavaHelper.copiedEnumSet( this.getResponseFormatType().keySet(), Answer.FormatType.class ) )
        {
            final long count = this.getResponseFormatType().get( formatType );
            returnCollection.add( builder.makeRow( "Field_Report_Sum_ResponseFormatType", count, formatType.toString() ) );
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveResponseTime", this.hasResponseSetTime.sum() ) );
        for ( final Integer day : new TreeSet<>( responseSetDays.keySet() ) )
        {
            if ( day < 0 )
            {
                returnCollection.add( builder.makeRow( "Field_Report_Sum_ResponseTimePrevious", this.responseSetDays.get( day ).sum(), String.valueOf( Math.abs( day ) ) ) );
            }
        }

        if ( this.hasOtpSecret.sum() > 0 )
        {
            returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveOtpSecret", this.hasOtpSecret.sum() ) );
            returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveOtpSecretSetTime", this.hasOtpSecretSetTime.sum() ) );
            for ( final Integer day : new TreeSet<>( otpSetDays.keySet() ) )
            {
                if ( day < 0 )
                {
                    returnCollection.add( builder.makeRow( "Field_Report_Sum_OtpSecretTimePrevious", this.otpSetDays.get( day ).sum(), String.valueOf( Math.abs( day ) ) ) );
                }
            }
        }

        if ( this.hasReceivedPwExpireNotification.sum() > 0 )
        {
            returnCollection.add( new PresentationRow( "Has Received PwExpiry Notice", Long.toString( this.hasReceivedPwExpireNotification.sum() ), null ) );
            for ( final Integer day : new TreeSet<>( pwExpireNotificationDays.keySet() ) )
            {
                if ( day < 0 )
                {
                    returnCollection.add( new PresentationRow( "PwExpireNotice " + day, Long.toString( this.pwExpireNotificationDays.get( day ).sum() ), null ) );
                }
            }
        }


        return returnCollection;
    }

    @Value
    @Builder( toBuilder = true )
    public static class PresentationRow
    {
        private String label;
        private String count;
        private String pct;
    }

    @Value
    public static class PresentationRowBuilder
    {
        private final Configuration config;
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
            return makeRowImpl( labelKey, valueCount, replacement ).toBuilder().pct( null ).build();
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
            final String pct = valueCount > 0 ? new Percent( valueCount, totalUsers ).pretty( 2 ) : "";
            final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale( locale );
            final String formattedCount = numberFormat.format( valueCount );
            return new PresentationRow( display, formattedCount, pct );
        }
    }
}

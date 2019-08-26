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

package password.pwm.svc.report;

import com.novell.ldapchai.cr.Answer;
import lombok.Builder;
import lombok.Value;
import password.pwm.config.Configuration;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.i18n.Admin;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.Percent;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.TimeDuration;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Value
public class ReportSummaryData
{
    private static final long MS_DAY = TimeDuration.DAY.asMillis();
    private static final BigInteger TWO = new BigInteger( "2" );

    private final AtomicInteger totalUsers = new AtomicInteger( 0 );
    private final AtomicInteger hasResponses = new AtomicInteger( 0 );
    private final AtomicInteger hasResponseSetTime = new AtomicInteger( 0 );
    private final AtomicInteger hasHelpdeskResponses = new AtomicInteger( 0 );
    private final AtomicInteger hasPasswordExpirationTime = new AtomicInteger( 0 );
    private final AtomicInteger hasAccountExpirationTime = new AtomicInteger( 0 );
    private final AtomicInteger hasLoginTime = new AtomicInteger( 0 );
    private final AtomicInteger hasChangePwTime = new AtomicInteger( 0 );
    private final AtomicInteger hasOtpSecret = new AtomicInteger( 0 );
    private final AtomicInteger hasOtpSecretSetTime = new AtomicInteger( 0 );
    private final AtomicInteger pwExpired = new AtomicInteger( 0 );
    private final AtomicInteger pwPreExpired = new AtomicInteger( 0 );
    private final AtomicInteger pwWarnPeriod = new AtomicInteger( 0 );
    private final AtomicInteger hasReceivedPwExpireNotification = new AtomicInteger( 0 );

    private final Map<DataStorageMethod, AtomicInteger> responseStorage = new ConcurrentHashMap<>();
    private final Map<Answer.FormatType, AtomicInteger> responseFormatType = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> ldapProfile = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> pwExpireDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> accountExpireDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> changePwDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> responseSetDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> otpSetDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> loginDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> pwExpireNotificationDays = new ConcurrentHashMap<>();

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
                reportSummaryData.pwExpireDays.put( day, new AtomicInteger( 0 ) );
                reportSummaryData.accountExpireDays.put( day, new AtomicInteger( 0 ) );
                reportSummaryData.changePwDays.put( day, new AtomicInteger( 0 ) );
                reportSummaryData.responseSetDays.put( day, new AtomicInteger( 0 ) );
                reportSummaryData.otpSetDays.put( day, new AtomicInteger( 0 ) );
                reportSummaryData.loginDays.put( day, new AtomicInteger( 0 ) );
                reportSummaryData.pwExpireNotificationDays.put( day, new AtomicInteger( 0 ) );
            }
        }

        return reportSummaryData;
    }

    public Map<DataStorageMethod, Integer> getResponseStorage( )
    {
        return Collections.unmodifiableMap( responseStorage.entrySet()
                .stream()
                .collect( Collectors.toMap( Map.Entry::getKey,
                        e -> e.getValue().get() ) ) );
    }

    public Map<Answer.FormatType, Integer> getResponseFormatType( )
    {
        return Collections.unmodifiableMap( responseFormatType.entrySet()
                .stream()
                .collect( Collectors.toMap( Map.Entry::getKey,
                        e -> e.getValue().get() ) ) );
    }

    void update( final UserCacheRecord userCacheRecord )
    {
        totalUsers.incrementAndGet();

        if ( userCacheRecord.isHasResponses() )
        {
            hasResponses.incrementAndGet();
        }

        if ( userCacheRecord.isHasHelpdeskResponses() )
        {
            hasHelpdeskResponses.incrementAndGet();
        }

        if ( userCacheRecord.getResponseSetTime() != null )
        {
            hasResponseSetTime.incrementAndGet();
            incrementIfWithinTimeWindow( userCacheRecord, responseSetDays );
        }

        if ( userCacheRecord.getPasswordExpirationTime() != null )
        {
            hasPasswordExpirationTime.incrementAndGet();
            incrementIfWithinTimeWindow( userCacheRecord, pwExpireDays );
        }

        if ( userCacheRecord.getAccountExpirationTime() != null )
        {
            hasAccountExpirationTime.incrementAndGet();
            incrementIfWithinTimeWindow( userCacheRecord, accountExpireDays );
        }

        if ( userCacheRecord.getLastLoginTime() != null )
        {
            hasLoginTime.incrementAndGet();
            incrementIfWithinTimeWindow( userCacheRecord, loginDays );
        }

        if ( userCacheRecord.getPasswordChangeTime() != null )
        {
            hasChangePwTime.incrementAndGet();
            incrementIfWithinTimeWindow( userCacheRecord, changePwDays );
        }

        if ( userCacheRecord.getPasswordExpirationNoticeSendTime() != null )
        {
            hasReceivedPwExpireNotification.incrementAndGet();
            incrementIfWithinTimeWindow( userCacheRecord, pwExpireNotificationDays );
        }

        if ( userCacheRecord.getPasswordStatus() != null )
        {
            if ( userCacheRecord.getPasswordStatus().isExpired() )
            {
                pwExpired.incrementAndGet();
            }
            if ( userCacheRecord.getPasswordStatus().isPreExpired() )
            {
                pwPreExpired.incrementAndGet();
            }
            if ( userCacheRecord.getPasswordStatus().isWarnPeriod() )
            {
                pwWarnPeriod.incrementAndGet();
            }
        }

        if ( userCacheRecord.getResponseStorageMethod() != null )
        {
            final DataStorageMethod method = userCacheRecord.getResponseStorageMethod();
            responseStorage.putIfAbsent( method, new AtomicInteger( 0 ) );
            responseStorage.get( method ).incrementAndGet();
        }

        if ( userCacheRecord.getLdapProfile() != null )
        {
            final String userProfile = userCacheRecord.getLdapProfile();
            if ( !ldapProfile.containsKey( userProfile ) )
            {
                ldapProfile.put( userProfile, new AtomicInteger( 0 ) );
            }
            ldapProfile.get( userProfile ).incrementAndGet();
        }

        if ( userCacheRecord.getResponseFormatType() != null )
        {
            final Answer.FormatType type = userCacheRecord.getResponseFormatType();
            responseFormatType.putIfAbsent( type, new AtomicInteger( 0 ) );
            responseFormatType.get( type ).incrementAndGet();
        }

        if ( userCacheRecord.isHasOtpSecret() )
        {
            hasOtpSecret.incrementAndGet();
        }

        if ( userCacheRecord.getOtpSecretSetTime() != null )
        {
            hasOtpSecretSetTime.incrementAndGet();
            incrementIfWithinTimeWindow( userCacheRecord, otpSetDays );
        }
    }

    private void incrementIfWithinTimeWindow(
            final UserCacheRecord userCacheRecord,
            final Map<Integer, AtomicInteger> map
    )
    {
        for ( final Map.Entry<Integer, AtomicInteger> entry : map.entrySet() )
        {
            final int day = entry.getKey();
            final Instant eventDate = userCacheRecord.getOtpSecretSetTime();
            final long timeWindow = MS_DAY * day;
            final AtomicInteger number = entry.getValue();

            if ( eventDate != null )
            {
                final TimeDuration timeBoundary = TimeDuration.of( timeWindow, TimeDuration.Unit.MILLISECONDS );
                final TimeDuration eventDifference = TimeDuration.fromCurrent( eventDate );

                if (
                        ( timeWindow >= 0 && eventDate.isAfter( Instant.now() ) && eventDifference.isShorterThan( timeBoundary ) )
                                || ( timeWindow < 0 && eventDate.isBefore( Instant.now() ) && eventDifference.isShorterThan( timeBoundary ) )
                )
                {
                    number.incrementAndGet();
                }
            }
        }
    }


    public List<PresentationRow> asPresentableCollection( final Configuration config, final Locale locale )
    {
        final ArrayList<PresentationRow> returnCollection = new ArrayList<>();
        final PresentationRowBuilder builder = new PresentationRowBuilder( config, this.totalUsers.get(), locale );

        returnCollection.add( builder.makeNoPctRow( "Field_Report_Sum_Total", this.totalUsers.get(), null ) );
        if ( totalUsers.get() == 0 )
        {
            return returnCollection;
        }

        if ( config.getLdapProfiles().keySet().size() > 1 )
        {
            for ( final Map.Entry<String, AtomicInteger> entry : new TreeMap<>( ldapProfile ).entrySet() )
            {
                final String userProfile = entry.getKey();
                final int count = entry.getValue().get();
                final String displayName = config.getLdapProfiles().containsKey( userProfile )
                        ? config.getLdapProfiles().get( userProfile ).getDisplayName( locale )
                        : userProfile;
                returnCollection.add(
                        builder.makeRow( "Field_Report_Sum_LdapProfile", count, displayName ) );
            }
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveLoginTime", this.hasLoginTime.get() ) );
        for ( final Integer day : new TreeSet<>( loginDays.keySet() ) )
        {
            if ( day < 0 )
            {
                returnCollection.add( builder.makeRow( "Field_Report_Sum_LoginTimePrevious", this.loginDays.get( day ).get(), String.valueOf( Math.abs( day ) ) ) );
            }
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveAccountExpirationTime", this.hasAccountExpirationTime.get() ) );
        for ( final Integer day : new TreeSet<>( accountExpireDays.keySet() ) )
        {
            final String key = day < 0 ? "Field_Report_Sum_AccountExpirationPrevious" : "Field_Report_Sum_AccountExpirationNext";
            returnCollection.add( builder.makeRow( key, this.accountExpireDays.get( day ).get(), String.valueOf( Math.abs( day ) ) ) );
        }
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HavePwExpirationTime", this.hasPasswordExpirationTime.get() ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveExpiredPw", this.pwExpired.get() ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HavePreExpiredPw", this.pwPreExpired.get() ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveExpiredPwWarn", this.pwWarnPeriod.get() ) );
        for ( final Integer day : new TreeSet<>( pwExpireDays.keySet() ) )
        {
            final String key = day < 0 ? "Field_Report_Sum_PwExpirationPrevious" : "Field_Report_Sum_PwExpirationNext";
            returnCollection.add( builder.makeRow( key, this.pwExpireDays.get( day ).get(), String.valueOf( Math.abs( day ) ) ) );
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveChgPw", this.hasChangePwTime.get() ) );
        for ( final Integer day : new TreeSet<>( changePwDays.keySet() ) )
        {
            if ( day < 0 )
            {
                returnCollection.add( builder.makeRow( "Field_Report_Sum_ChgPwPrevious", this.changePwDays.get( day ).get(), String.valueOf( Math.abs( day ) ) ) );
            }
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveResponses", this.hasResponses.get() ) );
        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveHelpdeskResponses", this.hasHelpdeskResponses.get() ) );
        for ( final DataStorageMethod storageMethod : new TreeSet<>( this.getResponseStorage().keySet() ) )
        {
            final int count = this.getResponseStorage().get( storageMethod );
            returnCollection.add( builder.makeRow( "Field_Report_Sum_StorageMethod", count, storageMethod.toString() ) );
        }
        for ( final Answer.FormatType formatType : new TreeSet<>( this.getResponseFormatType().keySet() ) )
        {
            final int count = this.getResponseFormatType().get( formatType );
            returnCollection.add( builder.makeRow( "Field_Report_Sum_ResponseFormatType", count, formatType.toString() ) );
        }

        returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveResponseTime", this.hasResponseSetTime.get() ) );
        for ( final Integer day : new TreeSet<>( responseSetDays.keySet() ) )
        {
            if ( day < 0 )
            {
                returnCollection.add( builder.makeRow( "Field_Report_Sum_ResponseTimePrevious", this.responseSetDays.get( day ).get(), String.valueOf( Math.abs( day ) ) ) );
            }
        }

        if ( this.hasOtpSecret.get() > 0 )
        {
            returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveOtpSecret", this.hasOtpSecret.get() ) );
            returnCollection.add( builder.makeRow( "Field_Report_Sum_HaveOtpSecretSetTime", this.hasOtpSecretSetTime.get() ) );
            for ( final Integer day : new TreeSet<>( otpSetDays.keySet() ) )
            {
                if ( day < 0 )
                {
                    returnCollection.add( builder.makeRow( "Field_Report_Sum_OtpSecretTimePrevious", this.otpSetDays.get( day ).get(), String.valueOf( Math.abs( day ) ) ) );
                }
            }
        }

        if ( this.hasReceivedPwExpireNotification.get() > 0 )
        {
            returnCollection.add( new PresentationRow( "Has Received PwExpiry Notice", Integer.toString( this.hasReceivedPwExpireNotification.get() ), null ) );
            for ( final Integer day : new TreeSet<>( pwExpireNotificationDays.keySet() ) )
            {
                if ( day < 0 )
                {
                    returnCollection.add( new PresentationRow( "PwExpireNotice " + day, Integer.toString( this.pwExpireNotificationDays.get( day ).get() ), null ) );
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
        private final int totalUsers;
        private final Locale locale;

        PresentationRow makeRow( final String labelKey, final int valueCount )
        {
            return makeRow( labelKey, valueCount, null );
        }

        PresentationRow makeRow( final String labelKey, final int valueCount, final String replacement )
        {
            return makeRowImpl( labelKey, valueCount, replacement );
        }

        PresentationRow makeNoPctRow( final String labelKey, final int valueCount, final String replacement )
        {
            return makeRowImpl( labelKey, valueCount, replacement ).toBuilder().pct( null ).build();
        }

        private PresentationRow makeRowImpl( final String labelKey, final int valueCount, final String replacement )
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

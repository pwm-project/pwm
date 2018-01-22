/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.svc.report;

import com.novell.ldapchai.cr.Answer;
import password.pwm.config.Configuration;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.i18n.Admin;
import password.pwm.util.LocaleHelper;
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

public class ReportSummaryData
{
    private static final long MS_DAY = TimeDuration.DAY.getTotalMilliseconds();
    private static final BigInteger TWO = new BigInteger( "2" );

    private Instant meanCacheTime;
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

    private final Map<DataStorageMethod, AtomicInteger> responseStorage = new ConcurrentHashMap<>();
    private final Map<Answer.FormatType, AtomicInteger> responseFormatType = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> ldapProfile = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> pwExpireDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> accountExpireDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> changePwDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> responseSetDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> otpSetDays = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> loginDays = new ConcurrentHashMap<>();

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
            }
        }

        return reportSummaryData;
    }

    public int getTotalUsers( )
    {
        return totalUsers.get();
    }

    public int getHasResponses( )
    {
        return hasResponses.get();
    }

    public int getHasPasswordExpirationTime( )
    {
        return hasPasswordExpirationTime.get();
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

    public Instant getMeanCacheTime( )
    {
        return meanCacheTime;
    }

    void update( final UserCacheRecord userCacheRecord )
    {
        update( userCacheRecord, true );
    }

    void remove( final UserCacheRecord userCacheRecord )
    {
        update( userCacheRecord, false );
    }

    @SuppressWarnings( "checkstyle:MethodLength" )
    private void update( final UserCacheRecord userCacheRecord, final boolean adding )
    {
        final int modifier = adding ? 1 : -1;

        totalUsers.addAndGet( modifier );

        updateMeanTime( userCacheRecord.cacheTimestamp, adding );

        if ( userCacheRecord.hasResponses )
        {
            hasResponses.addAndGet( modifier );
        }

        if ( userCacheRecord.hasHelpdeskResponses )
        {
            hasHelpdeskResponses.addAndGet( modifier );
        }

        if ( userCacheRecord.responseSetTime != null )
        {
            hasResponseSetTime.addAndGet( modifier );

            for ( final Map.Entry<Integer, AtomicInteger> entry : responseSetDays.entrySet() )
            {
                final Integer day = entry.getKey();
                entry.getValue().addAndGet( calcTimeWindow( userCacheRecord.responseSetTime, MS_DAY * day, adding ) );
            }
        }

        if ( userCacheRecord.passwordExpirationTime != null )
        {
            hasPasswordExpirationTime.addAndGet( modifier );

            for ( final Map.Entry<Integer, AtomicInteger> entry : pwExpireDays.entrySet() )
            {
                final Integer day = entry.getKey();
                entry.getValue().addAndGet( calcTimeWindow( userCacheRecord.passwordExpirationTime, MS_DAY * day, adding ) );
            }
        }

        if ( userCacheRecord.accountExpirationTime != null )
        {
            hasAccountExpirationTime.addAndGet( modifier );

            for ( final Map.Entry<Integer, AtomicInteger> entry : accountExpireDays.entrySet() )
            {
                final Integer day = entry.getKey();
                entry.getValue().addAndGet( calcTimeWindow( userCacheRecord.accountExpirationTime, MS_DAY * day, adding ) );
            }
        }

        if ( userCacheRecord.lastLoginTime != null )
        {
            hasLoginTime.addAndGet( modifier );

            for ( final Map.Entry<Integer, AtomicInteger> entry : loginDays.entrySet() )
            {
                final Integer day = entry.getKey();
                entry.getValue().addAndGet( calcTimeWindow( userCacheRecord.lastLoginTime, MS_DAY * day, adding ) );
            }
        }

        if ( userCacheRecord.passwordChangeTime != null )
        {
            hasChangePwTime.addAndGet( modifier );

            for ( final Map.Entry<Integer, AtomicInteger> entry : changePwDays.entrySet() )
            {
                final Integer day = entry.getKey();
                entry.getValue().addAndGet( calcTimeWindow( userCacheRecord.passwordChangeTime, MS_DAY * day, adding ) );
            }
        }

        if ( userCacheRecord.passwordStatus != null )
        {
            if ( adding )
            {
                if ( userCacheRecord.passwordStatus.isExpired() )
                {
                    pwExpired.incrementAndGet();
                }
                if ( userCacheRecord.passwordStatus.isPreExpired() )
                {
                    pwPreExpired.incrementAndGet();
                }
                if ( userCacheRecord.passwordStatus.isWarnPeriod() )
                {
                    pwWarnPeriod.incrementAndGet();
                }
            }
            else
            {
                if ( userCacheRecord.passwordStatus.isExpired() )
                {
                    pwExpired.decrementAndGet();
                }
                if ( userCacheRecord.passwordStatus.isPreExpired() )
                {
                    pwPreExpired.decrementAndGet();
                }
                if ( userCacheRecord.passwordStatus.isWarnPeriod() )
                {
                    pwWarnPeriod.decrementAndGet();
                }
            }
        }

        if ( userCacheRecord.responseStorageMethod != null )
        {
            final DataStorageMethod method = userCacheRecord.responseStorageMethod;
            responseStorage.putIfAbsent( method, new AtomicInteger( 0 ) );
            if ( adding )
            {
                responseStorage.get( method ).incrementAndGet();
            }
            else
            {
                responseStorage.get( method ).decrementAndGet();
            }
        }

        if ( userCacheRecord.getLdapProfile() != null )
        {
            final String userProfile = userCacheRecord.getLdapProfile();
            if ( !ldapProfile.containsKey( userProfile ) )
            {
                ldapProfile.put( userProfile, new AtomicInteger( 0 ) );
            }
            if ( adding )
            {
                ldapProfile.get( userProfile ).incrementAndGet();
            }
            else
            {
                ldapProfile.get( userProfile ).decrementAndGet();
            }
        }

        if ( userCacheRecord.responseFormatType != null )
        {
            final Answer.FormatType type = userCacheRecord.responseFormatType;
            responseFormatType.putIfAbsent( type, new AtomicInteger( 0 ) );
            if ( adding )
            {
                responseFormatType.get( type ).incrementAndGet();
            }
            else
            {
                responseFormatType.get( type ).decrementAndGet();
            }
        }

        if ( userCacheRecord.isHasOtpSecret() )
        {
            hasOtpSecret.addAndGet( modifier );
        }

        if ( userCacheRecord.getOtpSecretSetTime() != null )
        {
            hasOtpSecretSetTime.addAndGet( modifier );

            for ( final Map.Entry<Integer, AtomicInteger> entry : otpSetDays.entrySet() )
            {
                final int day = entry.getKey();
                entry.getValue().addAndGet( calcTimeWindow( userCacheRecord.getOtpSecretSetTime(), MS_DAY * day, adding ) );
            }
        }
    }

    private void updateMeanTime( final Instant newTime, final boolean adding )
    {
        if ( meanCacheTime == null )
        {
            if ( adding )
            {
                meanCacheTime = newTime;
            }
            return;
        }

        final BigInteger currentMillis = BigInteger.valueOf( meanCacheTime.toEpochMilli() );
        final BigInteger newMillis = BigInteger.valueOf( newTime.toEpochMilli() );
        final BigInteger combinedMillis = currentMillis.add( newMillis );
        final BigInteger halvedMillis = combinedMillis.divide( TWO );
        meanCacheTime = Instant.ofEpochMilli( halvedMillis.longValue() );
    }

    private int calcTimeWindow( final Instant eventDate, final long timeWindow, final boolean adding )
    {
        if ( eventDate == null )
        {
            return 0;
        }

        final TimeDuration timeBoundary = new TimeDuration( 0, timeWindow );
        final TimeDuration eventDifference = TimeDuration.fromCurrent( eventDate );

        if ( timeWindow >= 0 && eventDate.isAfter( Instant.now() ) && eventDifference.isShorterThan( timeBoundary ) )
        {
            return adding ? 1 : -1;
        }

        if ( timeWindow < 0 && eventDate.isBefore( Instant.now() ) && eventDifference.isShorterThan( timeBoundary ) )
        {
            return adding ? 1 : -1;
        }

        return 0;
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

        return returnCollection;
    }

    public static class PresentationRow
    {
        private String label;
        private String count;
        private String pct;

        public PresentationRow(
                final String label,
                final String count,
                final String pct
        )
        {
            this.label = label;
            this.count = count;
            this.pct = pct;
        }

        public String getLabel( )
        {
            return label;
        }

        public String getCount( )
        {
            return count;
        }

        public String getPct( )
        {
            return pct;
        }
    }

    public static class PresentationRowBuilder
    {
        private final Configuration config;
        private final int totalUsers;
        private final Locale locale;

        public PresentationRowBuilder(
                final Configuration config,
                final int totalUsers,
                final Locale locale
        )
        {
            this.config = config;
            this.totalUsers = totalUsers;
            this.locale = locale;
        }

        public PresentationRow makeRow( final String labelKey, final int valueCount )
        {
            return makeRow( labelKey, valueCount, null );
        }

        public PresentationRow makeRow( final String labelKey, final int valueCount, final String replacement )
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

        public PresentationRow makeNoPctRow( final String labelKey, final int valueCount, final String replacement )
        {
            final String display = replacement == null
                    ? LocaleHelper.getLocalizedMessage( locale, labelKey, config, Admin.class )
                    : LocaleHelper.getLocalizedMessage( locale, labelKey, config, Admin.class, new String[]
                    {
                            replacement,
                    }
            );
            final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale( locale );
            final String formattedCount = numberFormat.format( valueCount );
            return new PresentationRow( display, formattedCount, null );
        }
    }
}

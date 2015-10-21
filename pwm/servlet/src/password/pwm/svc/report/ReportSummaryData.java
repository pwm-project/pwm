/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import password.pwm.util.Percent;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.*;

public class ReportSummaryData {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ReportSummaryData.class);

    private static final long MS_DAY = 24 * 60 * 60 * 1000;

    private String epoch;

    private Date meanCacheTime;
    private int totalUsers;
    private int hasResponses;
    private int hasResponseSetTime;
    private int hasHelpdeskResponses;
    private int hasPasswordExpirationTime;
    private int hasAccountExpirationTime;
    private int hasLoginTime;
    private int hasChangePwTime;
    private int hasOtpSecret;
    private int hasOtpSecretSetTime;
    private Map<DataStorageMethod, Integer> responseStorage = new HashMap<>();
    private Map<Answer.FormatType, Integer> responseFormatType = new HashMap<>();
    private Map<String, Integer> ldapProfile = new HashMap<>();
    private int pwExpired;
    private int pwPreExpired;
    private int pwWarnPeriod;
    private Map<Integer,Integer> pwExpireDays = new TreeMap<>();
    private Map<Integer,Integer> accountExpireDays = new TreeMap<>();
    private Map<Integer,Integer> changePwDays = new TreeMap<>();
    private Map<Integer,Integer> responseSetDays = new TreeMap<>();
    private Map<Integer,Integer> otpSetDays = new TreeMap<>();
    private Map<Integer,Integer> loginDays = new TreeMap<>();

    private ReportSummaryData() {
    }

    public String getEpoch()
    {
        return epoch;
    }

    public static ReportSummaryData newSummaryData(final List<Integer> trackedDays) {
        final ReportSummaryData reportSummaryData = new ReportSummaryData();
        reportSummaryData.epoch = Long.toHexString(System.currentTimeMillis());
        
        if (trackedDays != null) {
            for (final int day : trackedDays) {
                reportSummaryData.pwExpireDays.put(day, 0);
                reportSummaryData.accountExpireDays.put(day, 0);
                reportSummaryData.changePwDays.put(day, 0);
                reportSummaryData.responseSetDays.put(day, 0);
                reportSummaryData.otpSetDays.put(day, 0);
                reportSummaryData.loginDays.put(day, 0);
            }
        }
        
        return reportSummaryData;
    }

    public int getTotalUsers()
    {
        return totalUsers;
    }

    public int getHasResponses()
    {
        return hasResponses;
    }

    public int getHasPasswordExpirationTime()
    {
        return hasPasswordExpirationTime;
    }

    public Map<DataStorageMethod, Integer> getResponseStorage()
    {
        return Collections.unmodifiableMap(responseStorage);
    }

    public Map<Answer.FormatType, Integer> getResponseFormatType()
    {
        return responseFormatType;
    }

    public Date getMeanCacheTime()
    {
        return meanCacheTime;
    }

    void update(UserCacheRecord userCacheRecord) {
        update(userCacheRecord, true);
    }

    void remove(UserCacheRecord userCacheRecord) {
        update(userCacheRecord,false);
    }

    private synchronized void update(UserCacheRecord userCacheRecord, boolean adding) {
        final int modifier = adding ? 1 : -1;

        totalUsers += modifier;

        updateMeanTime(userCacheRecord.cacheTimestamp,adding);

        if (userCacheRecord.hasResponses) {
            hasResponses += modifier;
        }

        if (userCacheRecord.hasHelpdeskResponses) {
            hasHelpdeskResponses += modifier;
        }

        if (userCacheRecord.responseSetTime != null) {
            hasResponseSetTime += modifier;

            for (final int day : responseSetDays.keySet()) {
                responseSetDays.put(day, responseSetDays.get(day) + calcTimeWindow(userCacheRecord.responseSetTime, MS_DAY * day, adding));
            }
        }

        if (userCacheRecord.passwordExpirationTime != null) {
            hasPasswordExpirationTime += modifier;

            for (final int day : pwExpireDays.keySet()) {
                pwExpireDays.put(day, pwExpireDays.get(day) + calcTimeWindow(userCacheRecord.passwordExpirationTime, MS_DAY * day, adding));
            }
        }

        if (userCacheRecord.accountExpirationTime != null) {
            hasAccountExpirationTime += modifier;

            for (final int day : accountExpireDays.keySet()) {
                accountExpireDays.put(day, accountExpireDays.get(day) + calcTimeWindow(userCacheRecord.accountExpirationTime, MS_DAY * day, adding));
            }
        }

        if (userCacheRecord.lastLoginTime != null) {
            hasLoginTime += modifier;

            for (final int day : loginDays.keySet()) {
                loginDays.put(day, loginDays.get(day) + calcTimeWindow(userCacheRecord.lastLoginTime, MS_DAY * day, adding));
            }
        }

        if (userCacheRecord.passwordChangeTime != null) {
            hasChangePwTime += modifier;

            for (final int day : changePwDays.keySet()) {
                changePwDays.put(day, changePwDays.get(day) + calcTimeWindow(userCacheRecord.passwordChangeTime, MS_DAY * day, adding));
            }
        }

        if (userCacheRecord.passwordStatus != null) {
            if (adding) {
                if (userCacheRecord.passwordStatus.isExpired()) {
                    pwExpired++;
                }
                if (userCacheRecord.passwordStatus.isPreExpired()) {
                    pwPreExpired++;
                }
                if (userCacheRecord.passwordStatus.isWarnPeriod()) {
                    pwWarnPeriod++;
                }
            } else {
                if (userCacheRecord.passwordStatus.isExpired()) {
                    pwExpired--;
                }
                if (userCacheRecord.passwordStatus.isPreExpired()) {
                    pwPreExpired--;
                }
                if (userCacheRecord.passwordStatus.isWarnPeriod()) {
                    pwWarnPeriod--;
                }
            }
        }

        if (userCacheRecord.responseStorageMethod != null) {
            final DataStorageMethod method = userCacheRecord.responseStorageMethod;
            if (!responseStorage.containsKey(method)) {
                responseStorage.put(method,0);
            }
            if (adding) {
                responseStorage.put(method, responseStorage.get(method) + 1);
            } else {
                responseStorage.put(method, responseStorage.get(method) - 1);
            }
        }

        if (userCacheRecord.getLdapProfile() != null) {
            final String userProfile = userCacheRecord.getLdapProfile();
            if (!ldapProfile.containsKey(userProfile)) {
                ldapProfile.put(userProfile,0);
            }
            if (adding) {
                ldapProfile.put(userProfile, ldapProfile.get(userProfile) + 1);
            } else {
                ldapProfile.put(userProfile, ldapProfile.get(userProfile) - 1);
            }
        }

        if (userCacheRecord.responseFormatType != null) {
            final Answer.FormatType type = userCacheRecord.responseFormatType;
            if (!responseFormatType.containsKey(type)) {
                responseFormatType.put(type,0);
            }
            if (adding) {
                responseFormatType.put(type, responseFormatType.get(type) + 1);
            } else {
                responseFormatType.put(type, responseFormatType.get(type) + 1);
            }
        }

        if (userCacheRecord.isHasOtpSecret()) {
            hasOtpSecret += modifier;
        }

        if (userCacheRecord.getOtpSecretSetTime() != null) {
            hasOtpSecretSetTime += modifier;

            for (final int day : otpSetDays.keySet()) {
                otpSetDays.put(day, otpSetDays.get(day) + calcTimeWindow(userCacheRecord.getOtpSecretSetTime(), MS_DAY * day, adding));
            }
        }

    }

    private void updateMeanTime(final Date newTime, final boolean adding) {
        if (meanCacheTime == null) {
            if (adding) {
                meanCacheTime = newTime;
            }
            return;
        }

        final BigInteger currentMillis = BigInteger.valueOf(meanCacheTime.getTime());
        final BigInteger newMillis = BigInteger.valueOf(newTime.getTime());
        final BigInteger combinedMillis = currentMillis.add(newMillis);
        final BigInteger halvedMillis = combinedMillis.divide(new BigInteger("2"));
        meanCacheTime = new Date(halvedMillis.longValue());
    }

    private int calcTimeWindow(Date eventDate, final long timeWindow, boolean adding) {
        if (eventDate == null) {
            return 0;
        }
        
        final TimeDuration timeBoundary = new TimeDuration(0,timeWindow);
        final TimeDuration eventDifference = new TimeDuration(System.currentTimeMillis(), eventDate);

        if (timeWindow >= 0 && eventDate.after(new Date()) && eventDifference.isShorterThan(timeBoundary)) {
                return adding ? 1 : -1;
        }

        if (timeWindow < 0 && eventDate.before(new Date()) && eventDifference.isShorterThan(timeBoundary)) {
            return adding ? 1 : -1;
        } 

        return 0;
    }


    public List<PresentationRow> asPresentableCollection(final Configuration config, final Locale locale) {
        final ArrayList<PresentationRow> returnCollection = new ArrayList<>();
        final PresentationRowBuilder builder = new PresentationRowBuilder(config,this.totalUsers,locale);

        returnCollection.add(builder.makeNoPctRow("Field_Report_Sum_Total", this.totalUsers, null));
        if (totalUsers == 0) {
            return returnCollection;
        }

        if (config.getLdapProfiles().keySet().size() > 1) {
            for (final String userProfile : ldapProfile.keySet()) {
                final int count = this.ldapProfile.get(userProfile);
                final String displayName = config.getLdapProfiles().containsKey(userProfile)
                        ? config.getLdapProfiles().get(userProfile).getDisplayName(locale)
                        : userProfile;
                returnCollection.add(
                        builder.makeRow("Field_Report_Sum_LdapProfile", count, displayName));
            }
        }

        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveLoginTime", this.hasLoginTime));
        for (final Integer day : loginDays.keySet()) {
            if (day < 0) {
                returnCollection.add(builder.makeRow("Field_Report_Sum_LoginTimePrevious", this.loginDays.get(day), String.valueOf(Math.abs(day))));
            }
        }

        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveAccountExpirationTime", this.hasAccountExpirationTime));
        for (final Integer day : accountExpireDays.keySet()) {
            final String key = day < 0 ? "Field_Report_Sum_AccountExpirationPrevious" : "Field_Report_Sum_AccountExpirationNext";
            returnCollection.add(builder.makeRow(key, this.accountExpireDays.get(day), String.valueOf(Math.abs(day))));
        }
        returnCollection.add(builder.makeRow("Field_Report_Sum_HavePwExpirationTime", this.hasPasswordExpirationTime));
        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveExpiredPw",this.pwExpired));
        returnCollection.add(builder.makeRow("Field_Report_Sum_HavePreExpiredPw",this.pwPreExpired));
        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveExpiredPwWarn",this.pwWarnPeriod));
        for (final Integer day : pwExpireDays.keySet()) {
            final String key = day < 0 ? "Field_Report_Sum_PwExpirationPrevious" : "Field_Report_Sum_PwExpirationNext";
            returnCollection.add(builder.makeRow(key, this.pwExpireDays.get(day), String.valueOf(Math.abs(day))));
        }

        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveChgPw", this.hasChangePwTime));
        for (final Integer day : changePwDays.keySet()) {
            if (day < 0) {
                returnCollection.add(builder.makeRow("Field_Report_Sum_ChgPwPrevious", this.changePwDays.get(day), String.valueOf(Math.abs(day))));
            }
        }

        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveResponses", this.hasResponses));
        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveHelpdeskResponses", this.hasHelpdeskResponses));
        for (final DataStorageMethod storageMethod : this.getResponseStorage().keySet()) {
            final int count = this.getResponseStorage().get(storageMethod);
            returnCollection.add(builder.makeRow("Field_Report_Sum_StorageMethod", count, storageMethod.toString()));
        }
        for (final Answer.FormatType formatType : this.getResponseFormatType().keySet()) {
            final int count = this.getResponseFormatType().get(formatType);
            returnCollection.add(builder.makeRow("Field_Report_Sum_ResponseFormatType", count, formatType.toString()));
        }

        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveResponseTime", this.hasResponseSetTime));
        for (Integer day : responseSetDays.keySet()) {
            if (day < 0) {
                returnCollection.add(builder.makeRow("Field_Report_Sum_ResponseTimePrevious", this.responseSetDays.get(day), String.valueOf(Math.abs(day))));
            }
        }

        if (this.hasOtpSecret > 0) {
            returnCollection.add(builder.makeRow("Field_Report_Sum_HaveOtpSecret", this.hasOtpSecret));
            returnCollection.add(builder.makeRow("Field_Report_Sum_HaveOtpSecretSetTime", this.hasOtpSecretSetTime));
            for (final Integer day : otpSetDays.keySet()) {
                if (day < 0) {
                    returnCollection.add(builder.makeRow("Field_Report_Sum_OtpSecretTimePrevious", this.otpSetDays.get(day), String.valueOf(Math.abs(day))));
                }
            }
        }

        return returnCollection;
    }

    public static class PresentationRow {
        private String label;
        private String count;
        private String pct;

        public PresentationRow(
                String label,
                String count,
                String pct
        )
        {
            this.label = label;
            this.count = count;
            this.pct = pct;
        }

        public String getLabel()
        {
            return label;
        }

        public String getCount()
        {
            return count;
        }

        public String getPct()
        {
            return pct;
        }
    }

    public static class PresentationRowBuilder {
        private final Configuration config;
        private final int totalUsers;
        private final Locale locale;

        public PresentationRowBuilder(
                Configuration config,
                int totalUsers,
                Locale locale
        )
        {
            this.config = config;
            this.totalUsers = totalUsers;
            this.locale = locale;
        }

        public PresentationRow makeRow(final String labelKey, final int valueCount) {
            return makeRow(labelKey, valueCount, null);
        }

        public PresentationRow makeRow(final String labelKey, final int valueCount, final String replacement)
        {
            final String display = replacement == null
                    ? LocaleHelper.getLocalizedMessage(locale, labelKey, config, Admin.class)
                    : LocaleHelper.getLocalizedMessage(locale, labelKey, config, Admin.class, new String[]{replacement});
            final String pct = valueCount > 0 ? new Percent(valueCount,totalUsers).pretty(2) : "";
            final NumberFormat numberFormat = NumberFormat.getInstance(locale);
            final String formattedCount = numberFormat.format(valueCount);
            return new PresentationRow(display, formattedCount, pct);
        }

        public PresentationRow makeNoPctRow(final String labelKey, final int valueCount, final String replacement)
        {
            final String display = replacement == null
                    ? LocaleHelper.getLocalizedMessage(locale, labelKey, config, Admin.class)
                    : LocaleHelper.getLocalizedMessage(locale, labelKey, config, Admin.class, new String[]{replacement});
            final NumberFormat numberFormat = NumberFormat.getInstance(locale);
            final String formattedCount = numberFormat.format(valueCount);
            return new PresentationRow(display, formattedCount, null);
        }
    }
}

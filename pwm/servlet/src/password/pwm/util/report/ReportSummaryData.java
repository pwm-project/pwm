/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.util.report;

import com.novell.ldapchai.cr.Answer;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.i18n.Admin;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.Percent;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.*;

public class ReportSummaryData {
    private static final long MS_DAY = 24 * 60 * 60 * 1000;
    public static final int VERSION = 1;

    private int version;
    private String epoch;
    private List<Integer> trackedDays;

    private Date meanCacheTime;
    private int totalUsers;
    private int hasResponses;
    private int hasResponseSetTime;
    private int hasExpirationTime;
    private int hasLoginTime;
    private int hasChangePwTime;
    private int hasOtpSecret;
    private int hasOtpSecretSetTime;
    private Map<DataStorageMethod, Integer> responseStorage = new HashMap<DataStorageMethod, Integer>();
    private Map<Answer.FormatType, Integer> responseFormatType = new HashMap<Answer.FormatType, Integer>();
    private Map<String, Integer> ldapProfile = new HashMap<String, Integer>();
    private int pwExpired;
    private int pwPreExpired;
    private int pwWarnPeriod;
    private Map<Integer,Integer> pwExpireNext = new TreeMap<Integer, Integer>();
    private Map<Integer,Integer> pwExpirePrevious = new TreeMap<Integer, Integer>();
    private Map<Integer,Integer> changePwPrevious = new TreeMap<Integer, Integer>();
    private Map<Integer,Integer> responseSetPrevious = new TreeMap<Integer, Integer>();
    private Map<Integer,Integer> otpSetPrevious = new TreeMap<Integer, Integer>();
    private Map<Integer,Integer> loginPrevious = new TreeMap<Integer, Integer>();

    private ReportSummaryData() {
    }

    public String getEpoch()
    {
        return epoch;
    }

    public static ReportSummaryData newSummaryData(final Configuration config) {
        final ReportSummaryData reportSummaryData = new ReportSummaryData();
        reportSummaryData.epoch = Long.toHexString(System.currentTimeMillis());
        reportSummaryData.version = VERSION;

        final String dayIntervalsStr;
        dayIntervalsStr = config != null
                ? config.readAppProperty(AppProperty.REPORTING_SUMMARY_DAY_INTERVALS)
                : AppProperty.REPORTING_SUMMARY_DAY_INTERVALS.getDefaultValue();
        reportSummaryData.trackedDays = parseDayIntervalStr(dayIntervalsStr);

        return reportSummaryData;
    }

    private static List<Integer> parseDayIntervalStr(final String dayIntervalStr) {
        final List<Integer> returnValue = new ArrayList<Integer>();
        final String[] splitDays = dayIntervalStr.split(",");
        for (final String splitDay : splitDays) {
            final int dayValue = Integer.parseInt(splitDay);
            returnValue.add(dayValue);
        }
        return returnValue;
    }

    public int getTotalUsers()
    {
        return totalUsers;
    }

    public int getHasResponses()
    {
        return hasResponses;
    }

    public int getHasExpirationTime()
    {
        return hasExpirationTime;
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

    private void update(UserCacheRecord userCacheRecord, boolean adding) {
        if (adding) {
            totalUsers++;
        } else {
            totalUsers--;
        }

        updateMeanTime(userCacheRecord.cacheTimestamp,adding);

        if (userCacheRecord.hasResponses) {
            if (adding) {
                hasResponses++;
            } else {
                hasResponses--;
            }
        }

        if (userCacheRecord.responseSetTime != null) {
            if (adding) {
                hasResponseSetTime++;
            } else {
                hasResponseSetTime--;
            }

            for (final int days : trackedDays) {
                if (!responseSetPrevious.containsKey(days)) {
                    responseSetPrevious.put(days,0);
                }
                responseSetPrevious.put(days,responseSetPrevious.get(days) + calcTimeWindow(userCacheRecord.responseSetTime,MS_DAY * days,false,adding));
            }
        }

        if (userCacheRecord.passwordExpirationTime != null) {
            if (adding) {
                hasExpirationTime++;
            } else {
                hasExpirationTime--;
            }

            for (final int days : trackedDays) {
                if (!pwExpirePrevious.containsKey(days)) {
                    pwExpirePrevious.put(days,0);
                }
                pwExpirePrevious.put(days,pwExpirePrevious.get(days) + calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY * days,false,adding));
            }

            for (final int days : trackedDays) {
                if (!pwExpireNext.containsKey(days)) {
                    pwExpireNext.put(days,0);
                }
                pwExpireNext.put(days,pwExpireNext.get(days) + calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY * days,true,adding));
            }
        }

        if (userCacheRecord.lastLoginTime != null) {
            if (adding) {
                hasLoginTime++;
            } else {
                hasLoginTime--;
            }

            for (final int days : trackedDays) {
                if (!loginPrevious.containsKey(days)) {
                    loginPrevious.put(days,0);
                }
                loginPrevious.put(days,loginPrevious.get(days) + calcTimeWindow(userCacheRecord.lastLoginTime,MS_DAY * days,false,adding));
            }
        }

        if (userCacheRecord.passwordChangeTime != null) {
            if (adding) {
                hasChangePwTime++;
            } else {
                hasChangePwTime--;
            }

            for (final int days : trackedDays) {
                if (!changePwPrevious.containsKey(days)) {
                    changePwPrevious.put(days,0);
                }
                changePwPrevious.put(days,changePwPrevious.get(days) + calcTimeWindow(userCacheRecord.passwordChangeTime,MS_DAY * days,false,adding));
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
            if (adding) {
                hasOtpSecret++;
            } else {
                hasOtpSecret--;
            }
        }

        if (userCacheRecord.getOtpSecretSetTime() != null) {
            if (adding) {
                hasOtpSecretSetTime++;
            } else {
                hasOtpSecretSetTime--;
            }

            for (final int days : trackedDays) {
                if (!otpSetPrevious.containsKey(days)) {
                    otpSetPrevious.put(days,0);
                }
                otpSetPrevious.put(days,otpSetPrevious.get(days) + calcTimeWindow(userCacheRecord.getOtpSecretSetTime(),MS_DAY * days,false,adding));
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

    private int calcTimeWindow(Date eventDate, final long timeWindow, boolean future, boolean adding) {
        if (eventDate == null) {
            return 0;
        }

        if (future) {
            if (eventDate.after(new Date()) && eventDate.before(new Date(System.currentTimeMillis() + timeWindow))) {
                return adding ? 1 : -1;
            }
        } else {
            if (eventDate.before(new Date()) && eventDate.after(new Date(System.currentTimeMillis() - timeWindow))) {
                return adding ? 1 : -1;
            }
        }
        return 0;
    }


    public List<PresentationRow> asPresentableCollection(final Configuration config, final Locale locale) {
        final ArrayList<PresentationRow> returnCollection = new ArrayList<PresentationRow>();
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
        for (final Integer days : loginPrevious.keySet()) {
            returnCollection.add(builder.makeRow("Field_Report_Sum_LoginTimePrevious", this.loginPrevious.get(days), String.valueOf(days)));
        }

        returnCollection.add(builder.makeRow("Field_Report_Sum_HavePwExpirationTime", this.hasExpirationTime));
        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveExpiredPw",this.pwExpired));
        returnCollection.add(builder.makeRow("Field_Report_Sum_HavePreExpiredPw",this.pwPreExpired));
        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveExpiredPwWarn",this.pwWarnPeriod));
        for (final Integer days : pwExpireNext.keySet()) {
            returnCollection.add(builder.makeRow("Field_Report_Sum_PwExpirationNext", this.pwExpireNext.get(days), String.valueOf(days)));
        }
        for (final Integer days : pwExpirePrevious.keySet()) {
            returnCollection.add(builder.makeRow("Field_Report_Sum_PwExpirationPrevious", this.pwExpirePrevious.get(days), String.valueOf(days)));
        }

        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveChgPw", this.hasChangePwTime));
        for (final Integer days : changePwPrevious.keySet()) {
            returnCollection.add(builder.makeRow("Field_Report_Sum_ChgPwPrevious", this.changePwPrevious.get(days), String.valueOf(days)));
        }

        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveResponses", this.hasResponses));
        for (final DataStorageMethod storageMethod : this.getResponseStorage().keySet()) {
            final int count = this.getResponseStorage().get(storageMethod);
            returnCollection.add(builder.makeRow("Field_Report_Sum_StorageMethod", count, storageMethod.toString()));
        }
        for (final Answer.FormatType formatType : this.getResponseFormatType().keySet()) {
            final int count = this.getResponseFormatType().get(formatType);
            returnCollection.add(builder.makeRow("Field_Report_Sum_ResponseFormatType", count, formatType.toString()));
        }

        returnCollection.add(builder.makeRow("Field_Report_Sum_HaveResponseTime", this.hasResponseSetTime));
        for (final Integer days : responseSetPrevious.keySet()) {
            returnCollection.add(builder.makeRow("Field_Report_Sum_ResponseTimePrevious", this.responseSetPrevious.get(days), String.valueOf(days)));
        }

        if (this.hasOtpSecret > 0) {
            returnCollection.add(builder.makeRow("Field_Report_Sum_HaveOtpSecret", this.hasOtpSecret));
            returnCollection.add(builder.makeRow("Field_Report_Sum_HaveOtpSecretSetTime", this.hasOtpSecretSetTime));
            for (final Integer days : otpSetPrevious.keySet()) {
                returnCollection.add(builder.makeRow("Field_Report_Sum_OtpSecretTimePrevious", this.otpSetPrevious.get(days), String.valueOf(days)));
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

    public boolean isCurrentVersion() {
        return (version == VERSION);
    }

}

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

import password.pwm.config.option.DataStorageMethod;

import java.io.Serializable;
import java.util.*;

public class ReportSummaryData {
    private static long MS_DAY = 24 * 60 * 60 * 1000;

    private String epoch;
    private int totalUsers;
    private int hasResponses;
    private int hasExpirationTime;
    private int hasLoginTime;
    private int hasChangePwTime;
    private Map<DataStorageMethod,Integer> responseStorage = new HashMap<DataStorageMethod, Integer>();
    private int pwExpired;
    private int pwPreExpired;
    private int pwWarnPeriod;
    private int expireNext_3;
    private int expireNext_7;
    private int expireNext_14;
    private int expireNext_30;
    private int expireNext_60;
    private int expireNext_90;
    private int expirePrevious_3;
    private int expirePrevious_7;
    private int expirePrevious_14;
    private int expirePrevious_30;
    private int expirePrevious_60;
    private int expirePrevious_90;
    private int changePrevious_3;
    private int changePrevious_7;
    private int changePrevious_14;
    private int changePrevious_30;
    private int changePrevious_60;
    private int changePrevious_90;
    private Map<String,Integer> last30PwExpires = new TreeMap<String, Integer>();
    private Map<String,Integer> next30PwExpires = new TreeMap<String, Integer>();

    public void cleanup() {

    }

    private ReportSummaryData() {
    }

    public String getEpoch()
    {
        return epoch;
    }

    public static ReportSummaryData newSummaryData() {
        final ReportSummaryData reportSummaryData = new ReportSummaryData();
        reportSummaryData.epoch = Long.toHexString(System.currentTimeMillis());
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

    public int getHasExpirationTime()
    {
        return hasExpirationTime;
    }

    public Map<DataStorageMethod, Integer> getResponseStorage()
    {
        return Collections.unmodifiableMap(responseStorage);
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

        if (userCacheRecord.hasResponses) {
            if (adding) {
                hasResponses++;
            } else {
                hasResponses--;
            }
        }

        if (userCacheRecord.passwordExpirationTime != null) {
            if (adding) {
                hasExpirationTime++;
            } else {
                hasExpirationTime--;
            }

            expirePrevious_3 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*3,false,adding);
            expirePrevious_7 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*7,false,adding);
            expirePrevious_14 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*14,false,adding);
            expirePrevious_30 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*30,false,adding);
            expirePrevious_60 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*60,false,adding);
            expirePrevious_90 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*90,false,adding);
            expireNext_3 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*3,true,adding);
            expireNext_7 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*7,true,adding);
            expireNext_14 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*14,true,adding);
            expireNext_30 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*30,true,adding);
            expireNext_60 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*60,true,adding);
            expireNext_90 += calcTimeWindow(userCacheRecord.passwordExpirationTime,MS_DAY*90,true,adding);

            final Day maxDaysBefore = Day.daysBefore(30);
            final Day maxDaysAfter = Day.daysAfter(30);
            final Day changeDay = Day.fromDate(
                    userCacheRecord.passwordExpirationTime);

            final int dbg_ComapreBefore = changeDay.compareTo(maxDaysBefore);
            final int dbg_ComapreAfter = changeDay.compareTo(maxDaysAfter);
            final int dbg_ComapreNow = changeDay.compareTo(Day.now());
            if (dbg_ComapreBefore >= 0 && dbg_ComapreNow <= 0 ) {
                if (!last30PwExpires.containsKey(changeDay.toString())) {
                    last30PwExpires.put(changeDay.toString(), 0);
                }
                if (adding) {
                    last30PwExpires.put(changeDay.toString(), last30PwExpires.get(changeDay.toString()) + 1);
                } else {
                    last30PwExpires.put(changeDay.toString(), last30PwExpires.get(changeDay.toString()) - 1);
                }
            } else if (dbg_ComapreNow >= 0 && dbg_ComapreAfter <= 0) {
                if (!next30PwExpires.containsKey(changeDay.toString())) {
                    next30PwExpires.put(changeDay.toString(), 0);
                }
                if (adding) {
                    next30PwExpires.put(changeDay.toString(), next30PwExpires.get(changeDay.toString()) + 1);
                } else {
                    next30PwExpires.put(changeDay.toString(), next30PwExpires.get(changeDay.toString()) - 1);
                }
            }
        }

        if (userCacheRecord.lastLoginTime != null) {
            if (adding) {
                hasLoginTime++;
            } else {
                hasLoginTime--;
            }
        }

        if (userCacheRecord.passwordChangeTime != null) {
            if (adding) {
                hasChangePwTime++;
            } else {
                hasChangePwTime--;
            }

            changePrevious_3 += calcTimeWindow(userCacheRecord.passwordChangeTime,MS_DAY*3,false,adding);
            changePrevious_7 += calcTimeWindow(userCacheRecord.passwordChangeTime,MS_DAY*7,false,adding);
            changePrevious_14 += calcTimeWindow(userCacheRecord.passwordChangeTime,MS_DAY*14,false,adding);
            changePrevious_30 += calcTimeWindow(userCacheRecord.passwordChangeTime,MS_DAY*30,false,adding);
            changePrevious_60 += calcTimeWindow(userCacheRecord.passwordChangeTime,MS_DAY*60,false,adding);
            changePrevious_90 += calcTimeWindow(userCacheRecord.passwordChangeTime,MS_DAY*90,false,adding);
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
    }

    public static class Day implements Serializable,Comparable<Day> {
        private static final TimeZone TZ = TimeZone.getTimeZone("Zulu");
        private int year;
        private int day;

        private Day(
                int year,
                int day
        )
        {
            this.year = year;
            this.day = day;
        }

        public int getYear()
        {
            return year;
        }

        public int getDay()
        {
            return day;
        }

        static Day fromString(final String input) {
            final String[] values = input.split("-");
            int year = Integer.parseInt(values[0]);
            int day = Integer.parseInt(values[1]);
            return new Day(year,day);
        }

        static Day fromDate(final Date input) {
            if (input==null) {
                return null;
            }

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeZone(TZ);
            calendar.setTime(input);
            return new Day(calendar.get(Calendar.YEAR),calendar.get(Calendar.DAY_OF_YEAR));
        }

        static Day daysAfter(int daysAfter) {
            final Calendar daysAgoCal = GregorianCalendar.getInstance(TZ);
            daysAgoCal.add(Calendar.DAY_OF_YEAR,daysAfter);
            return fromDate(daysAgoCal.getTime());
        }

        static Day daysBefore(int daysBefore) {
            final Calendar daysAgoCal = GregorianCalendar.getInstance(TZ);
            daysAgoCal.add(Calendar.DAY_OF_YEAR,daysBefore * -1);
            return fromDate(daysAgoCal.getTime());
        }

        static Day now() {
            return fromDate(new Date());
        }

        private Date getZuluZeroDate() {
            final Calendar zuluZeroDate = GregorianCalendar.getInstance(TZ);
            zuluZeroDate.set(Calendar.HOUR_OF_DAY, 0);
            zuluZeroDate.set(Calendar.MINUTE, 0);
            zuluZeroDate.set(Calendar.SECOND, 0);
            zuluZeroDate.set(Calendar.DAY_OF_YEAR, day);
            zuluZeroDate.set(Calendar.YEAR, year);
            return zuluZeroDate.getTime();
        }

        @Override
        public int compareTo(Day o)
        {
            return getZuluZeroDate().compareTo(o.getZuluZeroDate());
        }

        @Override
        public String toString()
        {
            return year + "-" + day;
        }
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
}

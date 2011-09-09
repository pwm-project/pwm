/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm.util.stats;

import password.pwm.AlertHandler;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.util.PwmLogger;
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class StatisticsManager {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(StatisticsManager.class);

    private static final int DB_WRITE_FREQUENCY_MS = 60 * 1000;  // 1 minutes

    private static final String DB_KEY_VERSION = "STATS_VERSION";
    private static final String DB_KEY_CUMULATIVE = "CUMULATIVE";
    private static final String DB_KEY_INITIAL_DAILY_KEY = "INITIAL_DAILY_KEY";
    private static final String DB_KEY_PREFIX_DAILY = "DAILY_";

    private static final String DB_VALUE_VERSION = "1";

    public static final String KEY_CURRENT = "CURRENT";
    public static final String KEY_CUMULATIVE = "CUMULATIVE";

    private final PwmDB pwmDB;

    private DailyKey currentDailyKey = new DailyKey(new Date());
    private DailyKey initialDailyKey = new DailyKey(new Date());

    private Timer daemonTimer;

    private final StatisticsBundle statsCurrent = new StatisticsBundle();
    private StatisticsBundle statsDaily = new StatisticsBundle();
    private StatisticsBundle statsCummulative = new StatisticsBundle();

    final private PwmApplication pwmApplication;

    private final Map<String,StatisticsBundle> cachedStoredStats = new LinkedHashMap<String,StatisticsBundle>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, StatisticsBundle> eldest) {
            return this.size() > 50;
        }
    };

    public StatisticsManager(final PwmDB pwmDB, final PwmApplication pwmApplication) {
        this.pwmDB = pwmDB;
        this.pwmApplication = pwmApplication;

        try {
            initialize(pwmDB);
        } catch (PwmDBException e) {
            LOGGER.error("error loading db statistics values: " + e.getMessage());
        }
    }

    public synchronized void incrementValue(final Statistic statistic) {
        statsCurrent.incrementValue(statistic);
        statsDaily.incrementValue(statistic);
        statsCummulative.incrementValue(statistic);
    }

    public synchronized void updateAverageValue(final Statistic statistic, final long value) {
        statsCurrent.updateAverageValue(statistic,value);
        statsDaily.updateAverageValue(statistic,value);
        statsCummulative.updateAverageValue(statistic,value);
    }

    public Map<String,String> getStatHistory(final Statistic statistic, final int days) {
        final Map<String,String> returnMap = new LinkedHashMap<String,String>();
        DailyKey loopKey = currentDailyKey;
        int counter = days;
        while (counter > 0) {
            final StatisticsBundle bundle = getStatBundleForKey(loopKey.toString());
            if (bundle != null) {
                final String key = (new SimpleDateFormat("MMM dd")).format(loopKey.calendar().getTime());
                final String value = bundle.getStatistic(statistic);
                returnMap.put(key,value);
            }
            loopKey = loopKey.previous();
            counter--;
        }
        return returnMap;
    }

    public StatisticsBundle getStatBundleForKey(final String key) {
        if (key == null || key.length() < 1 || KEY_CUMULATIVE.equals(key) ) {
            return statsCummulative;
        }

        if (KEY_CURRENT.equals(key)) {
            return statsCurrent;
        }

        if (currentDailyKey.toString().equals(key)) {
            return statsDaily;
        }

        if (cachedStoredStats.containsKey(key)) {
            return cachedStoredStats.get(key);
        }

        if (pwmDB == null) {
            return null;
        }

        try {
            final String storedStat = pwmDB.get(PwmDB.DB.PWM_STATS, key);
            final StatisticsBundle returnBundle;
            if (storedStat != null && storedStat.length() > 0) {
                returnBundle = StatisticsBundle.input(storedStat);
            } else {
                returnBundle = new StatisticsBundle();
            }
            cachedStoredStats.put(key, returnBundle);
            return returnBundle;
        } catch (PwmDBException e) {
            LOGGER.error("error retrieving stored stat for " + key + ": " + e.getMessage());
        }

        return null;
    }

    public Map<DailyKey,String> getAvailableKeys(final Locale locale) {
        if (currentDailyKey.equals(initialDailyKey)) {
            return Collections.emptyMap();
        }

        final DateFormat dateFormatter = SimpleDateFormat.getDateInstance(SimpleDateFormat.DEFAULT, locale);
        final Map<DailyKey,String> returnMap = new LinkedHashMap<DailyKey,String>();
        DailyKey loopKey = currentDailyKey;
        int safetyCounter = 0;
        while (!loopKey.equals(initialDailyKey) && safetyCounter < 5000) {
            final Calendar c = loopKey.calendar();
            final String display = dateFormatter.format(c.getTime());
            returnMap.put(loopKey,display);
            loopKey = loopKey.previous();
            safetyCounter++;
        }
        return returnMap;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();

        for (final Statistic m : Statistic.values()) {
            sb.append(m.toString());
            sb.append("=");
            sb.append(statsCurrent.getStatistic(m));
            sb.append(", ");
        }

        if (sb.length() > 2) {
            sb.delete(sb.length() -2 , sb.length());
        }

        return sb.toString();
    }

    private void initialize(final PwmDB pwmDB)
            throws PwmDBException
    {
        if (pwmDB == null) {
            return;
        }

        {
            final String storedCummulativeBundleStr = pwmDB.get(PwmDB.DB.PWM_STATS, DB_KEY_CUMULATIVE);
            if (storedCummulativeBundleStr != null && storedCummulativeBundleStr.length() > 0) {
                statsCummulative = StatisticsBundle.input(storedCummulativeBundleStr);
            }
        }

        {
            final String storedInitialString = pwmDB.get(PwmDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY);

            if (storedInitialString != null && storedInitialString.length() > 0) {
                initialDailyKey = new DailyKey(storedInitialString);
            } else {
                pwmDB.put(PwmDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY, initialDailyKey.toString());
            }

        }

        {
            currentDailyKey = new DailyKey(new Date());
            final String storedDailyStr = pwmDB.get(PwmDB.DB.PWM_STATS, currentDailyKey.toString());
            if (storedDailyStr != null && storedDailyStr.length() > 0) {
                statsDaily = StatisticsBundle.input(storedDailyStr);
            }
        }

        pwmDB.put(PwmDB.DB.PWM_STATS, DB_KEY_VERSION, DB_VALUE_VERSION);

        {
            daemonTimer = new Timer("pwm-StatisticsManager timer",true);
            daemonTimer.schedule(new FlushTask(), 10 * 1000, DB_WRITE_FREQUENCY_MS);
            daemonTimer.schedule(new NightlyTask(), nextDate());
        }
    }

    private static Date nextDate() {
        final Calendar nextZuluMidnight = GregorianCalendar.getInstance(TimeZone.getTimeZone("Zulu"));
        nextZuluMidnight.set(Calendar.HOUR_OF_DAY,0);
        nextZuluMidnight.set(Calendar.MINUTE,0);
        nextZuluMidnight.set(Calendar.SECOND,0);
        nextZuluMidnight.add(Calendar.HOUR,24);
        LOGGER.trace("scheduled next nightly rotate at " + StatisticsBundle.STORED_DATETIME_FORMATTER.format(nextZuluMidnight.getTime()));
        return nextZuluMidnight.getTime();
    }

    private void writeDbValues() {
        if (pwmDB != null) {
            try {
                pwmDB.put(PwmDB.DB.PWM_STATS, DB_KEY_CUMULATIVE, statsCummulative.output());
                pwmDB.put(PwmDB.DB.PWM_STATS, currentDailyKey.toString(), statsDaily.output());
            } catch (PwmDBException e) {
                LOGGER.error("error outputting pwm statistics: " + e.getMessage());
            }
        }

    }

    private void resetDailyStats() {
        final Map<String,String> emailValues = new LinkedHashMap<String,String>();
        for (final Statistic statistic : Statistic.values()) {
            final String key = statistic.getLabel(PwmConstants.DEFAULT_LOCALE);
            final String value = statsDaily.getStatistic(statistic);
            emailValues.put(key,value);
        }

        AlertHandler.alertDailyStats(pwmApplication, emailValues);

        currentDailyKey = new DailyKey(new Date());
        statsDaily = new StatisticsBundle();
        LOGGER.debug("reset daily statistics");
    }

    public void close() {
        writeDbValues();
        daemonTimer.cancel();
    }


    private class NightlyTask extends TimerTask {
        public void run() {
            writeDbValues();
            resetDailyStats();
            daemonTimer.schedule(new NightlyTask(), nextDate());
        }
    }

    private class FlushTask extends TimerTask {
        public void run() {
            writeDbValues();
        }
    }

    public static class DailyKey {
        int year;
        int day;

        public DailyKey(final Date date) {
            final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Zulu"));
            calendar.setTime(date);
            year = calendar.get(Calendar.YEAR);
            day = calendar.get(Calendar.DAY_OF_YEAR);
        }

        public DailyKey(final String value) {
            final String strippedValue = value.substring(DB_KEY_PREFIX_DAILY.length(),value.length());
            final String[] splitValue = strippedValue.split("_");
            year = Integer.valueOf(splitValue[0]);
            day = Integer.valueOf(splitValue[1]);
        }

        private DailyKey() {
        }

        @Override
        public String toString() {
            return DB_KEY_PREFIX_DAILY + String.valueOf(year) + "_" + String.valueOf(day);
        }

        public DailyKey previous() {
            final Calendar calendar = calendar();
            calendar.add(Calendar.HOUR,-24);
            final DailyKey newKey = new DailyKey();
            newKey.year = calendar.get(Calendar.YEAR);
            newKey.day = calendar.get(Calendar.DAY_OF_YEAR);
            return newKey;
        }

        public Calendar calendar() {
            final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Zulu"));
            calendar.set(Calendar.YEAR,year);
            calendar.set(Calendar.DAY_OF_YEAR,day);
            return calendar;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final DailyKey key = (DailyKey) o;

            if (day != key.day) return false;
            if (year != key.year) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = year;
            result = 31 * result + day;
            return result;
        }
    }
}

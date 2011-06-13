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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import password.pwm.util.PwmLogger;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;

public class StatisticsBundle {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(StatisticsBundle.class);

    private static final int MAX_AVG_HISTORY_SIZE = 10;
    final static SimpleDateFormat STORED_DATETIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    static {
        STORED_DATETIME_FORMATTER.setTimeZone(TimeZone.getTimeZone("Zulu"));
    }


    private final Map<Statistic, String> valueMap = new HashMap<Statistic, String>();

    public StatisticsBundle() {
    }

    public String output() {
        final Gson gson = new Gson();
        return gson.toJson(valueMap);
    }

    public static StatisticsBundle input(final String inputString) {
        final Map<Statistic, String> srcMap = new HashMap<Statistic,String>();
        try {
            final Gson gson = new Gson();
            final HashMap<String, String> loadedMap = gson.fromJson(inputString, new TypeToken<Map<String, String>>() {
            }.getType());
            for (final String key : loadedMap.keySet()) {
                try {
                    srcMap.put(Statistic.valueOf(key),loadedMap.get(key));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("error parsing statistic key '" + key + "', reason: " + e.getMessage());
                }
            }
        } catch (JsonParseException e) {
            LOGGER.error("unable to load statistics bundle: " + e.getMessage());
        }
        final StatisticsBundle bundle = new StatisticsBundle();

        for (final Statistic loopStat : Statistic.values()) {
            final String value = srcMap.get(loopStat);
            if (value != null && !value.equals("")) {
                bundle.valueMap.put(loopStat, value);
            }
        }

        return bundle;
    }

    public synchronized void incrementValue(final Statistic statistic) {
        if (Statistic.Type.INCREMENTOR != statistic.getType()) {
            LOGGER.error("attempt to increment non-counter/incremental stat " + statistic);
            return;
        }

        BigInteger currentValue = BigInteger.ZERO;
        try {
            if (valueMap.containsKey(statistic)) {
                currentValue = new BigInteger(valueMap.get(statistic));
            } else {
                currentValue = BigInteger.ZERO;
            }
        } catch (NumberFormatException e) {
            LOGGER.error("error reading counter/incremental stat " + statistic);
        }
        final BigInteger newValue = currentValue.add(BigInteger.ONE);
        valueMap.put(statistic, newValue.toString());
    }

    public synchronized void updateAverageValue(final Statistic statistic, final long timeDuration) {
        if (Statistic.Type.AVERAGE != statistic.getType()) {
            LOGGER.error("attempt to update average value of non-average stat " + statistic);
            return;
        }

        final List<BigInteger> workingList = parseAverageValue(valueMap.get(statistic));

        workingList.add(BigInteger.valueOf(timeDuration));

        while (workingList.size() > MAX_AVG_HISTORY_SIZE) {
            workingList.remove(0);
        }

        final StringBuilder newValue = new StringBuilder();
        for (final BigInteger loopValue : workingList) {
            newValue.append(loopValue);
            newValue.append(",");
        }

        valueMap.put(statistic, newValue.toString());
    }

    public String getStatistic(final Statistic statistic) {
        switch (statistic.getType()) {
            case INCREMENTOR:
                return valueMap.containsKey(statistic) ? valueMap.get(statistic) : "0";

            case AVERAGE:
                final List<BigInteger> averageValues = parseAverageValue(valueMap.get(statistic));
                if (averageValues == null || averageValues.isEmpty()) {
                    return "0";
                } else {
                    BigInteger totalTime = BigInteger.ZERO;
                    for (final BigInteger loopInt : averageValues) {
                        totalTime = totalTime.add(loopInt);
                    }
                    return totalTime.divide(BigInteger.valueOf(valueMap.size())).toString();
                }

            default:
                return "";
        }
    }

    private static List<BigInteger> parseAverageValue(final String currentValue) {
        final List<BigInteger> workingList = new ArrayList<BigInteger>();
        if (currentValue != null) {
            for (final String splitValue : currentValue.split(",")) {
                final BigInteger loopValue = new BigInteger(splitValue);
                workingList.add(loopValue);
            }
        }
        return workingList;
    }
}

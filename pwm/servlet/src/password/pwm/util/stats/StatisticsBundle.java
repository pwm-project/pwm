package password.pwm.util.stats;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import password.pwm.util.PwmLogger;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;

public class StatisticsBundle {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(StatisticsBundle.class);

    private static final int MAX_AVG_HISTORY_SIZE = 10;
    private final static SimpleDateFormat STORED_DATETIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    static {
        STORED_DATETIME_FORMATTER.setTimeZone(TimeZone.getTimeZone("Zulu"));
    }



    private final Map<Statistic, String> valueMap = new HashMap<Statistic, String>();

    public StatisticsBundle() {
    }

    public String output() {
        return JSONObject.toJSONString(valueMap);
    }

    public static StatisticsBundle input(final String inputString) {
        final JSONObject srcMap = (JSONObject) JSONValue.parse(inputString);
        final StatisticsBundle bundle = new StatisticsBundle();

        for (final Statistic loopStat : Statistic.values()) {
            final String value = (String)srcMap.get(loopStat.toString());
            if (value != null && !value.equals("")) {
                bundle.valueMap.put(loopStat,value);
            }
        }

        return bundle;
    }

    public synchronized void incrementValue(final Statistic statistic) {
        if (Statistic.Type.INCREMENTOR != statistic.getType()) {
            LOGGER.error("attempt to increment non-counter/incrementor stat " + statistic);
            return;
        }

        BigInteger currentValue = BigInteger.ZERO;
        try {
            if (valueMap.containsKey(statistic)) {
                currentValue = new BigInteger(valueMap.get(statistic));
            } else {
                currentValue = BigInteger.ZERO;
            }
        } catch(NumberFormatException e) {
            LOGGER.error("error reading counter/incrementor stat " + statistic);
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

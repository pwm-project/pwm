package password.pwm.util.stats;

import password.pwm.util.TimeDuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

class EventRateMeter {
    final ConcurrentLinkedQueue<EventElement> tracker = new ConcurrentLinkedQueue<EventElement>();
    private final TimeDuration maxDuration;

    EventRateMeter(final TimeDuration maxDuration) {
        if (maxDuration == null) {
            throw new NullPointerException("maxDuration cannot be null");
        }
        this.maxDuration = maxDuration;
    }

    public void markEvents(final int eventCount) {
        tracker.add(new EventElement(eventCount,new Date()));
        clean();
    }

    public BigDecimal readEventRate(final TimeDuration duration, final TimeDuration unit) {
        if (duration == null || duration.isLongerThan(maxDuration)) {
            throw new IllegalArgumentException("invalid duration (must be less than " + maxDuration.getTotalMilliseconds()+ "ms)");
        }

        long total = 0;
        for (final EventElement element : tracker) {
            final TimeDuration loopDuration = TimeDuration.fromCurrent(element.timestamp);
            if (duration.isLongerThan(loopDuration)) {
                total += element.eventCount;
            }
        }
        if (unit != null && unit.getTotalMilliseconds() > 0) {
            final int precision = 1;
            final BigDecimal numerator = (new BigDecimal(duration.getTotalMilliseconds())).divide(new BigDecimal(unit.getTotalMilliseconds()), precision, RoundingMode.HALF_UP);
            return (new BigDecimal(total)).divide(numerator, precision, RoundingMode.HALF_UP);
        } else {
            return new BigDecimal(total);
        }
    }

    private void clean() {
        for (Iterator<EventElement> iterator = tracker.iterator(); iterator.hasNext(); ) {
            final EventElement element = iterator.next();
            if (maxDuration.isShorterThan(TimeDuration.fromCurrent(element.timestamp))) {
                iterator.remove();
            }
        }
    }

    private static class EventElement {
        private final int eventCount;
        private final Date timestamp;

        private EventElement(final int eventCount, final Date timestamp) {
            this.eventCount = eventCount;
            this.timestamp = timestamp;
        }
    }

}

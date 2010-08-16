/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

package password.pwm.util;

import com.novell.ldapchai.util.StringHelper;
import password.pwm.Helper;
import password.pwm.PwmSession;
import password.pwm.util.db.PwmDB;
import password.pwm.util.db.PwmDBException;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Saves a recent copy of PWM events in the pwmDB.
 *
 * @author Jason D. Rivard
 */
public class PwmDBLogger {
// ------------------------------ FIELDS ------------------------------

    private final static String KEY_HEAD_POSITION = "HEAD_POSITION";
    private final static String KEY_TAIL_POSITION = "TAIL_POSITION";
    private final static String KEY_VERSION = "VERSION";
    private final static String VALUE_VERSION = "4";

    private final static PwmDB.DB META_DB = PwmDB.DB.EVENTLOG_META;
    private final static PwmDB.DB EVENT_DB = PwmDB.DB.EVENTLOG_EVENTS;

    private final static PwmLogger LOGGER = PwmLogger.getLogger(PwmDBLogger.class);

    private final static int MAXIMUM_POSITION = Integer.MAX_VALUE - 1;
    private final static int MINIMUM_POSITION = Integer.MIN_VALUE + 1;

    private final static int MINIMUM_MAXIMUM_EVENTS = 100;

    private final static int MAX_WRITES_PER_CYCLE = 3581;
    private final static int MAX_REMOVALS_PER_CYCLE = 5053;

    private final static int CYCLE_INTERVAL_MS =  5023; // 5  seconds
    private final static int DB_WARN_THRESHOLD = MAX_WRITES_PER_CYCLE + MAX_REMOVALS_PER_CYCLE;
    private final static int MAX_QUEUE_SIZE = 10 * 1000;

    private final PwmDB pwmDB;
    private volatile int headPosition;
    private volatile int tailPosition;

    private volatile long tailTimestampMs = -1L;
    private long lastQueueFlushTimestamp = System.currentTimeMillis();

    private final int setting_maxEvents;
    private final long setting_maxAgeMs;
    private final int setting_bulkAddEvents;

    private final Queue<PwmLogEvent> eventQueue = new ConcurrentLinkedQueue<PwmLogEvent>();

    private volatile boolean open = true;
    private volatile boolean writerThreadActive = false;

    private boolean hasShownReadError = false;


// --------------------------- CONSTRUCTORS ---------------------------

    public PwmDBLogger(final PwmDB pwmDB, final int maxEvents, final int maxAge, final int bulkAddEvents) {

        this.setting_maxAgeMs = ((long)maxAge) * 1000L;
        this.pwmDB = pwmDB;
        this.setting_bulkAddEvents = bulkAddEvents;

        if (maxEvents == 0) {
            LOGGER.info("maxEvents sent to zero, clearing PwmDBLogger history and PwmDBLogger will remain closed");
            try {
                initializeNewSystem();
            } catch (Exception e) {
                LOGGER.warn("error while initializing/clearing PwmDBLogger history: " + e.getMessage());
            }
            throw new IllegalArgumentException("maxEvents=0, will remain closed");
        }

        if (maxEvents < MINIMUM_MAXIMUM_EVENTS) {
            LOGGER.warn("maxEvents less than required minimum of " + MINIMUM_MAXIMUM_EVENTS + ", resetting maxEvents=" + MINIMUM_MAXIMUM_EVENTS);
            this.setting_maxEvents = MINIMUM_MAXIMUM_EVENTS;
        } else {
            this.setting_maxEvents = maxEvents;
        }

        if (pwmDB == null) {
            throw new IllegalArgumentException("pwmDB cannot be null");
        }

        try {
            init();
        } catch (Exception e) {
            LOGGER.error("error initializing PwmDBLogger: " + e.getMessage(),e);
        }
    }

    private void init()
            throws PwmDBException
    {
        final long startTime = System.currentTimeMillis();

        checkVersion();

        headPosition = StringHelper.convertStrToInt(pwmDB.get(META_DB,KEY_HEAD_POSITION), 0);
        tailPosition = StringHelper.convertStrToInt(pwmDB.get(META_DB,KEY_TAIL_POSITION), 0);

        tailTimestampMs = readTailTimestamp();

        LOGGER.debug("initializing; headPosition=" + headPosition + ", tailPosition=" + tailPosition + ", maxEvents=" + setting_maxEvents + ", maxAgeMs=" + setting_maxAgeMs);

        {
            final Thread purgerThread = new Thread(new Runnable() {
                public void run() {
                    secondaryInit(startTime);
                }
            },"pwm-PwmDBLogger initializer");
            purgerThread.setDaemon(true);
            purgerThread.start();
        }
    }

    private void checkVersion() throws PwmDBException {
        final String storedVersion = pwmDB.get(META_DB,KEY_VERSION);
        if (storedVersion == null || !VALUE_VERSION.equals(storedVersion)) {
            LOGGER.warn("events in db use an outdated format, the stored events will be purged");
            initializeNewSystem();
            LOGGER.info("PwmDB event history has been purged");
        }
    }

    private void initializeNewSystem()
            throws PwmDBException
    {
        pwmDB.truncate(EVENT_DB);
        pwmDB.truncate(META_DB);

        headPosition = 0;
        tailPosition = 0;
        pwmDB.put(META_DB,KEY_HEAD_POSITION,String.valueOf(headPosition));
        pwmDB.put(META_DB,KEY_TAIL_POSITION,String.valueOf(tailPosition));

        writeEvent(new PwmLogEvent(new Date(),this.getClass().getSimpleName(),"PwmDBLogger initialized",null,null,null,PwmLogLevel.INFO));

        pwmDB.put(META_DB, KEY_VERSION, VALUE_VERSION);
    }

    private long readTailTimestamp() {
        final PwmLogEvent loopEvent = readEvent(tailPosition);

        try {
            if (loopEvent != null) {
                return loopEvent.getDate().getTime();
            }
        } catch (Exception e) {
            LOGGER.error("unexpected error attempting to determine tail event timestamp: " + e.getMessage());
        }

        return -1;
    }

    private void secondaryInit(final long startTime)
    {
        final int itemCount = this.figureItemCount();
        if (itemCount > setting_maxEvents + DB_WARN_THRESHOLD) {
            LOGGER.warn("PwmDBLogger item count (" + itemCount + ") is larger than the maximum configured size (" + setting_maxEvents + "), process may have high utilization while items are purged");
        }

        final TimeDuration timeDuration = TimeDuration.fromCurrent(startTime);
        LOGGER.info("open in " + timeDuration.asCompactString() + ", " + debugStats());

        { // start the writer thread
            final Thread writerThread = new Thread(new WriterThread(),"pwm-PwmDBLogger writer");
            writerThread.setDaemon(true);
            writerThread.start();
        }

        /*
        try { // verify the db size.
            LOGGER.debug("beginning validation of size counts");
            final int figuredItemCount = figureItemCount();
            final int realItemCount = pwmDB.size(EVENT_DB);
            final int observedDifference = Math.abs(figuredItemCount - realItemCount);
            if ( observedDifference > DB_WARN_THRESHOLD) {
                LOGGER.warn("event count discrepancy: pwmDb.count()=" + realItemCount + " figuredItemCount()=" + figureItemCount());
            } else {
                LOGGER.debug("size count verification complete");
            }
        } catch (Exception e) {
            LOGGER.error("error verifying item count: " + e.getMessage());
        }
        */

        if (setting_bulkAddEvents > 0) {
            Helper.pause(10 * 1000);
            LOGGER.warn("beginning bulk add events testing process for " + setting_bulkAddEvents + " events!");
            bulkAddEvents( setting_bulkAddEvents );
        }
    }

    /**
     * Determines the item count based on difference between the tail position and head position
     * @return calculated item count;
     */
    private int figureItemCount()
    {
        final int itemCount;
        if (tailPosition > headPosition) {
            itemCount = (headPosition - MINIMUM_POSITION) + (MAXIMUM_POSITION - tailPosition);
        } else {
            itemCount = headPosition - tailPosition;
        }
        return itemCount;
    }

    private String debugStats()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("events=").append(figureItemCount());
        sb.append(", tailAge=").append(TimeDuration.fromCurrent(tailTimestampMs).asCompactString());
        sb.append(", headPosition=").append(keyForPosition(headPosition));
        sb.append(", tailPosition=").append(keyForPosition(tailPosition));
        sb.append(", maxEvents=").append(setting_maxEvents);
        sb.append(", maxAge=").append(setting_maxAgeMs > 1 ? new TimeDuration(setting_maxAgeMs).asCompactString() : "none");
        sb.append(", pwmDBSize=").append(Helper.formatDiskSize(pwmDB.diskSpaceUsed()));
        return sb.toString();
    }

    private void bulkAddEvents(final int size)
    {
        int eventsRemaining = size;
        while (eventsRemaining > 0 && open) {
            while (eventQueue.size() >= MAX_WRITES_PER_CYCLE) Helper.pause(100);

            final Collection<PwmLogEvent> events = makeBulkEvents(MAX_WRITES_PER_CYCLE + 1);
            eventQueue.addAll(events);
            eventsRemaining = eventsRemaining - events.size();
        }
    }

    private static Collection<PwmLogEvent> makeBulkEvents(final int count) {

        final Collection<PwmLogEvent> events = new ArrayList<PwmLogEvent>();
        final PwmRandom random = PwmRandom.getInstance();

        for(int i = 0; i < count; i++) {
            final StringBuilder description = new StringBuilder();
            description.append("bulk insert event: ").append(System.currentTimeMillis()).append(" ");
            description.append(random.alphaNumericString(random.nextInt(1024)));

            final PwmLogEvent event = new PwmLogEvent(
                    new Date(),
                    PwmDBLogger.class.getName(),
                    description.toString(), "", "", null, PwmLogLevel.TRACE);
            events.add(event);
        }

        return events;
    }

// -------------------------- OTHER METHODS --------------------------

    public void close() {
        LOGGER.debug("PwmDBLogger closing... (" + debugStats() + ")");
        open = false;

        { // wait for the writer to die.
            final long startTime = System.currentTimeMillis();
            while (writerThreadActive && (System.currentTimeMillis() - startTime) < 60 * 1000) {
                Helper.pause(100);
            }

            if (writerThreadActive) {
                LOGGER.warn("logger thread still open");
            }
        }

        if (!writerThreadActive) { // try to close the queue
            final long startTime = System.currentTimeMillis();
            while (!eventQueue.isEmpty() && (System.currentTimeMillis() - startTime) < 30 * 1000) {
                flushQueue(MAX_WRITES_PER_CYCLE);
            }
        }

        if (!eventQueue.isEmpty()) {
            LOGGER.warn("abandoning " + eventQueue.size() + " events waiting to be written to pwmDB log");
        }

        LOGGER.debug("PwmDBLogger close completed (" + debugStats() + ")");
    }

    private void flushQueue(final int maxEvents) {
        final List<PwmLogEvent> tempList = new ArrayList<PwmLogEvent>();
        while (!eventQueue.isEmpty() && tempList.size() < maxEvents ) {
            final PwmLogEvent nextEvent = eventQueue.poll();
            if (nextEvent != null) {
                tempList.add(nextEvent);
            }
        }

        if (!tempList.isEmpty()) {
            doWrite(tempList);
            lastQueueFlushTimestamp = System.currentTimeMillis();
        }
    }

    private synchronized void doWrite(final Collection<PwmLogEvent> events)
    {
        final long startTime = System.currentTimeMillis();
        final Map<String,String> transactions = new HashMap<String,String>();
        int nextPosition = figureNextPosition(headPosition);
        try {
            for (final PwmLogEvent event : events) {
                final String encodedString = event.toEncodedString();
                if (encodedString.length() < PwmDB.MAX_VALUE_LENGTH) {
                    transactions.put(keyForPosition(nextPosition),encodedString);
                    nextPosition = figureNextPosition(nextPosition);
                }
            }

            pwmDB.putAll(EVENT_DB, transactions);
            pwmDB.put(META_DB, KEY_HEAD_POSITION, String.valueOf(nextPosition));
            headPosition = nextPosition;

            if (transactions.size() >= (MAX_WRITES_PER_CYCLE - 100)) {
                LOGGER.trace("added " + transactions.size() + " in " + TimeDuration.compactFromCurrent(startTime) + " " + debugStats());
            }
        } catch (Exception e) {
            LOGGER.error("error writing to pwmDBLogger: " + e.getMessage(),e);
        }
    }

    public long getTailTimestamp() {
        return tailTimestampMs;
    }

    public int getEventCount() {
        return figureItemCount();
    }

    public int getPendingEventCount() {
        return eventQueue.size();
    }

    private int determineTailRemovalCount()
    {
        final int currentItemCount = figureItemCount();

        // must keep at least one position populated
        if (currentItemCount <= 1) {
            return 0;
        }

        // purge excess events by count
        if (currentItemCount > setting_maxEvents) {
            return currentItemCount - setting_maxEvents;
        }

        // purge the tail if it is missing or has invalid timestamp
        if (tailTimestampMs == -1) {
            return 1;
        }

        // purge excess events by age;
        if (setting_maxAgeMs > 0) {
            final long ageOfTail = System.currentTimeMillis() - tailTimestampMs;
            if ((tailTimestampMs > 0) && (ageOfTail > setting_maxAgeMs)) {

                // if the tail is old, peek forward a ways to see if a large chunk needs to be purged.
                if (figureItemCount() > MAX_REMOVALS_PER_CYCLE) {

                    //figure out the tail + MAX_REMOVALS_PER_CYCLE + position
                    int checkPosition = tailPosition;
                    for (int i = 0; i < MAX_REMOVALS_PER_CYCLE; i++) {
                        checkPosition = figureNextPosition(checkPosition);
                    }

                    final PwmLogEvent checkEvent = readEvent(checkPosition);
                    if (checkEvent != null && checkEvent.getDate() != null) {
                        final long checkEventAgeMS = System.currentTimeMillis() - checkEvent.getDate().getTime();
                        if (checkEventAgeMS > setting_maxAgeMs) {
                            return MAX_REMOVALS_PER_CYCLE;
                        }
                    }
                }
                return 1;
            }
        }
        return 0;
    }

    private void removeTail(final int count)
    {
        final long startTime = System.currentTimeMillis();
        final List<String> removalKeys = new ArrayList<String>();
        try {
            int nextTailPosition = tailPosition;
            while ((removalKeys.size() < count) && (removalKeys.size() <= MAX_REMOVALS_PER_CYCLE) && open) {
                removalKeys.add(keyForPosition(nextTailPosition));
                nextTailPosition = figureNextPosition(nextTailPosition);
            }
            if (open) {
                pwmDB.removeAll(EVENT_DB, removalKeys);
                pwmDB.put(META_DB, KEY_TAIL_POSITION, String.valueOf(nextTailPosition));
                tailPosition = nextTailPosition;
            }

            if (removalKeys.size() >= (MAX_REMOVALS_PER_CYCLE - 100)) {
                LOGGER.trace("removed " + removalKeys.size() + " in " + TimeDuration.compactFromCurrent(startTime) + " " + debugStats());
            }
        } catch (PwmDBException e) {
            LOGGER.error("error trimming pwmDBLogger: " + e.getMessage(),e);
        }
    }

    public enum EventType { User, System, Both }

    public SearchResults readStoredEvents(
            final PwmSession pwmSession,
            final PwmLogLevel minimumLevel,
            final int count,
            final String username,
            final String text,
            final long maxQueryTime,
            final EventType eventType
    )
    {
        final long startTime = System.currentTimeMillis();
        final int maxReturnedEvents = count > this.setting_maxEvents ? this.setting_maxEvents : count;
        final int eventsInDb = this.figureItemCount();

        Pattern pattern = null;
        try{
            if (username != null && username.length() > 0) {
                pattern = Pattern.compile(username);
            }
        } catch (PatternSyntaxException e) {
            LOGGER.trace("invalid regex syntax for " + username + ", reverting to plaintext search");
        }

        final List<PwmLogEvent> returnList = new ArrayList<PwmLogEvent>();
        boolean timeExceeded = false;
        int readPosition = this.headPosition;
        int examinedPositions = 0;
        while (open && returnList.size() < maxReturnedEvents && examinedPositions < eventsInDb) {
            final PwmLogEvent loopEvent = readEvent(readPosition);
            if (loopEvent != null) {
                if (checkEventForParams(loopEvent, minimumLevel, username, text, pattern, eventType)) {
                    returnList.add(loopEvent);
                }
            }

            if ((System.currentTimeMillis() - startTime) > maxQueryTime) {
                timeExceeded = true;
                break;
            }

            examinedPositions++;
            readPosition = figurePreviousPosition(readPosition);
        }

        Collections.sort(returnList);
        Collections.reverse(returnList);
        final TimeDuration searchTime = TimeDuration.fromCurrent(startTime);

        {
            final StringBuilder debugMsg = new StringBuilder();
            debugMsg.append("dredged ").append(NumberFormat.getInstance().format(examinedPositions)).append(" events");
            debugMsg.append(" to return ").append(NumberFormat.getInstance().format(returnList.size())).append(" events");
            debugMsg.append(" for query (minimumLevel=").append(minimumLevel).append(", count=").append(count);
            if (username != null && username.length() > 0) {
                debugMsg.append(", username=").append(username);
            }
            if (text != null && text.length() > 0) {
                debugMsg.append(", text=").append(text);
            }
            debugMsg.append(")");
            debugMsg.append(" in ").append(searchTime.asCompactString());
            if (timeExceeded) {
                debugMsg.append(" (maximum query time reached)");
            }
            LOGGER.trace(pwmSession, debugMsg.toString());
        }

        return new SearchResults(returnList, examinedPositions, searchTime);
    }

    public TimeDuration getDirtyQueueTime() {
        if (eventQueue.isEmpty()) {
            return TimeDuration.ZERO;
        }

        return TimeDuration.fromCurrent(lastQueueFlushTimestamp);
    }

    private PwmLogEvent readEvent(final int position) {
        try {
            final String strValue = pwmDB.get(EVENT_DB, keyForPosition(position));
            if (strValue != null && strValue.length() > 0) {
                return PwmLogEvent.fromEncodedString(strValue);
            }
        } catch (Throwable e) {
            if (!hasShownReadError) {
                hasShownReadError = true;
                LOGGER.error("error reading pwmDBLogger event: " + e.getMessage());
            }
        }
        return null;
    }

    private boolean checkEventForParams(
            final PwmLogEvent event,
            final PwmLogLevel level,
            final String username,
            final String text,
            final Pattern pattern,
            final EventType eventType
    )
    {
        if (event == null) {
            return false;
        }

        boolean eventMatchesParams = true;

        if (level != null) {
            if (event.getLevel().compareTo(level) <= -1) {
                eventMatchesParams = false;
            }
        }

        if (pattern != null) {
            final Matcher matcher = pattern.matcher(event.getActor());
            if (!matcher.find()) {
                eventMatchesParams = false;
            }
        } else  if (eventMatchesParams && (username != null && username.length() > 1)) {
            final String eventUsername = event.getActor();
            if (eventUsername == null || !eventUsername.equalsIgnoreCase(username)) {
                eventMatchesParams = false;
            }
        }

        if (eventMatchesParams && (text != null && text.length() > 0)) {
            final String eventMessage = event.getMessage();
            final String textLowercase = text.toLowerCase();
            boolean isAMatch = false;
            if (eventMessage != null && eventMessage.length() > 0) {
                if (eventMessage.toLowerCase().contains(textLowercase)) {
                    isAMatch = true;
                } else if (event.getTopic() != null && event.getTopic().length() > 0) {
                    if (event.getTopic().toLowerCase().contains(textLowercase)) {
                        isAMatch = true;
                    }
                }
                if (!isAMatch) {
                    eventMatchesParams = false;
                }
            }
        }

        if (eventType != null) {
            if (eventType == EventType.System) {
                if (event.getActor() != null && event.getActor().length() > 0) {
                    eventMatchesParams = false;
                }
            } else if (eventType == EventType.User) {
                if (event.getActor() == null || event.getActor().length() < 1) {
                    eventMatchesParams = false;
                }
            }
        }

        return eventMatchesParams;
    }


    private static int figureNextPosition(final int position) {
        int nextPosition = position + 1;
        if (nextPosition > MAXIMUM_POSITION) {
            nextPosition = MINIMUM_POSITION;
        }
        return nextPosition;
    }

    private static int figurePreviousPosition(final int position) {
        int previousPosition = position - 1;
        if (previousPosition < MINIMUM_POSITION) {
            previousPosition = MAXIMUM_POSITION;
        }
        return previousPosition;
    }

    public synchronized void writeEvent(final PwmLogEvent event) {
        if (open) {
            if (setting_maxEvents > 0) {
                if (eventQueue.isEmpty()) {
                    lastQueueFlushTimestamp = System.currentTimeMillis();
                }
                if (eventQueue.size() > MAX_QUEUE_SIZE) {
                    LOGGER.warn("discarding event due to full write queue: " + event.toString());
                    return;
                }
                eventQueue.add(event);
            }
        }
    }

    private static String keyForPosition(final int position) {
        final StringBuilder sb = new StringBuilder();
        sb.append(Integer.toHexString(position));
        while (sb.length() < 8) {
            sb.insert(0,"0");
        }
        return sb.toString().toUpperCase();
    }

// -------------------------- INNER CLASSES --------------------------

    private class WriterThread implements Runnable {
        public void run() {
            LOGGER.debug("writer thread open");
            writerThreadActive = true;
            try {
                doLoop();
            } catch (Exception e) {
                LOGGER.fatal("unexpected fatal error during PwmDBLogger log event writing; logging to pwmDB will be suspended.",e);
            }
            writerThreadActive = false;
        }

        private void doLoop() {
            while (open) {
                boolean workDone = false;

                if (!eventQueue.isEmpty()) {
                    flushQueue(MAX_WRITES_PER_CYCLE);
                    workDone = true;
                }

                final int purgeCount = determineTailRemovalCount();
                if (purgeCount > 0) {
                    removeTail(purgeCount > MAX_REMOVALS_PER_CYCLE ? MAX_REMOVALS_PER_CYCLE : purgeCount);
                    tailTimestampMs = readTailTimestamp();
                    workDone = true;
                }

                if (!workDone) {
                    final long startSleepTime = System.currentTimeMillis();
                    while (open && ((System.currentTimeMillis() - startSleepTime) < CYCLE_INTERVAL_MS) && (eventQueue.size() < (MAX_QUEUE_SIZE / 50))) {
                        Helper.pause(101);
                    }
                }
            }
        }
    }

    public static class SearchResults implements Serializable {
        final private List<PwmLogEvent> events;
        final private int searchedEvents;
        final private TimeDuration searchTime;

        private SearchResults(final List<PwmLogEvent> events, final int searchedEvents, final TimeDuration searchTime) {
            this.events = events;
            this.searchedEvents = searchedEvents;
            this.searchTime = searchTime;
        }

        public List<PwmLogEvent> getEvents() {
            return events;
        }

        public int getSearchedEvents() {
            return searchedEvents;
        }

        public TimeDuration getSearchTime() {
            return searchTime;
        }
    }
}


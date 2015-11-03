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

package password.pwm.util.logging;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.svc.PwmService;
import password.pwm.util.*;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Saves a recent copy of PWM events in the pwmDB.
 *
 * @author Jason D. Rivard
 */
public class LocalDBLogger implements PwmService {
// ------------------------------ FIELDS ------------------------------

    private final static PwmLogger LOGGER = PwmLogger.forClass(LocalDBLogger.class);

    private final static int MINIMUM_MAXIMUM_EVENTS = 100;

    private volatile long tailTimestampMs = -1L;
    private long lastQueueFlushTimestamp = System.currentTimeMillis();

    private final LocalDB localDB;
    private final Settings settings;
    private final Queue<PwmLogEvent> eventQueue = new LinkedBlockingQueue<>(PwmConstants.LOCALDB_LOGGER_MAX_QUEUE_SIZE);
    private final LocalDBStoredQueue localDBListQueue;

    private volatile STATUS status = STATUS.NEW;
    private volatile boolean writerThreadActive = false;
    private boolean hasShownReadError = false;

    private final TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(
            2049, // transaction time goal
            5,
            PwmConstants.LOCALDB_LOGGER_MAX_QUEUE_SIZE
    );

// --------------------------- CONSTRUCTORS ---------------------------

    public LocalDBLogger(final PwmApplication pwmApplication, final LocalDB localDB, final Settings settings)
            throws LocalDBException
    {
        final long startTime = System.currentTimeMillis();
        status = STATUS.OPENING;
        this.settings = settings.copy();
        this.localDB = localDB;
        this.localDBListQueue = LocalDBStoredQueue.createLocalDBStoredQueue(pwmApplication,
                this.localDB, LocalDB.DB.EVENTLOG_EVENTS);

        if (settings.maxEvents == 0) {
            LOGGER.info("maxEvents set to zero, clearing LocalDBLogger history and LocalDBLogger will remain closed");
            localDBListQueue.clear();
            throw new IllegalArgumentException("maxEvents=0, will remain closed");
        }

        if (settings.getMaxEvents() < MINIMUM_MAXIMUM_EVENTS) {
            LOGGER.warn("maxEvents less than required minimum of " + MINIMUM_MAXIMUM_EVENTS + ", resetting maxEvents=" + MINIMUM_MAXIMUM_EVENTS);
            settings.setMaxEvents(MINIMUM_MAXIMUM_EVENTS);
        }

        if (localDB == null) {
            throw new IllegalArgumentException("LocalDB is not available");
        }

        this.tailTimestampMs = readTailTimestamp();
        status = STATUS.OPEN;

        { // start the writer thread
            final Thread writerThread = new Thread(new WriterThread());
            writerThread.setName(Helper.makeThreadName(pwmApplication, LocalDBLogger.class));
            writerThread.setDaemon(true);
            writerThread.start();
        }

        final TimeDuration timeDuration = TimeDuration.fromCurrent(startTime);
        LOGGER.info("open in " + timeDuration.asCompactString() + ", " + debugStats());
    }


    private long readTailTimestamp() {
        final PwmLogEvent loopEvent;
        try {
            loopEvent = readEvent(localDBListQueue.getLast());
            if (loopEvent != null) {
                final Date tailDate = loopEvent.getDate();
                if (tailDate != null) {
                    return tailDate.getTime();
                }
            }
        } catch (Exception e) {
            LOGGER.error("unexpected error attempting to determine tail event timestamp: " + e.getMessage());
        }

        return -1;
    }


    private String debugStats() {
        final StringBuilder sb = new StringBuilder();
        sb.append("events=").append(localDBListQueue.size());
        sb.append(", tailAge=").append(TimeDuration.fromCurrent(tailTimestampMs).asCompactString());
        sb.append(", maxEvents=").append(settings.getMaxEvents());
        sb.append(", maxAge=").append(settings.getMaxAgeMs() > 1 ? new TimeDuration(settings.getMaxAgeMs()).asCompactString() : "none");
        sb.append(", localDBSize=").append(Helper.formatDiskSize(FileSystemUtility.getFileDirectorySize(localDB.getFileLocation())));
        return sb.toString();
    }

// -------------------------- OTHER METHODS --------------------------

    public void close() {
        LOGGER.debug("LocalDBLogger closing... (" + debugStats() + ")");
        status = STATUS.CLOSED;

        { // wait for the writer to die.
            final long startTime = System.currentTimeMillis();
            while (writerThreadActive && TimeDuration.fromCurrent(startTime).isShorterThan(60 * 1000)) {
                Helper.pause(1000);
                if (writerThreadActive) {
                    LOGGER.debug("waiting for writer thread to close...");
                }
            }

            if (writerThreadActive) {
                LOGGER.warn("logger thread still open");
            }
        }

        if (!writerThreadActive) { // try to close the queue
            final long startTime = System.currentTimeMillis();
            while (!eventQueue.isEmpty() && TimeDuration.fromCurrent(startTime).isShorterThan(30 * 1000)) {
                flushQueue();
            }
        }

        if (!eventQueue.isEmpty()) {
            LOGGER.warn("abandoning " + eventQueue.size() + " events waiting to be written to LocalDB log");
        }

        LOGGER.debug("LocalDBLogger close completed (" + debugStats() + ")");
    }

    private int flushQueue() {
        final List<PwmLogEvent> tempList = new ArrayList<>();
        final int desiredTransactionSize = transactionCalculator.getTransactionSize();
        PwmLogEvent nextEvent = eventQueue.poll();
        while (nextEvent != null && tempList.size() < desiredTransactionSize) {
            tempList.add(nextEvent);
            nextEvent = eventQueue.poll();
        }

        if (!tempList.isEmpty()) {
            doWrite(tempList);
            lastQueueFlushTimestamp = System.currentTimeMillis();
            //System.out.println("flush size: " + tempList.size());
        }

        return tempList.size();
    }

    private void doWrite(final Collection<PwmLogEvent> events) {
        final List<String> transactions = new ArrayList<>();
        try {
            for (final PwmLogEvent event : events) {
                final String encodedString = event.toEncodedString();
                if (encodedString.length() < LocalDB.MAX_VALUE_LENGTH) {
                    transactions.add(encodedString);
                }
            }

            localDBListQueue.addAll(transactions);
        } catch (Exception e) {
            LOGGER.error("error writing to localDBLogger: " + e.getMessage(), e);
        }
    }

    public Date getTailDate() {
        return new Date(tailTimestampMs);
    }

    public int getStoredEventCount() {
        return localDBListQueue.size();
    }

    public int getPendingEventCount() {
        return eventQueue.size();
    }

    private int determineTailRemovalCount() {
        final int currentItemCount = localDBListQueue.size();

        // must keep at least one position populated
        if (currentItemCount <= 1) {
            return 0;
        }

        // purge excess events by count
        if (currentItemCount > settings.getMaxEvents()) {
            return currentItemCount - settings.getMaxEvents();
        }

        // purge the tail if it is missing or has invalid timestamp
        if (tailTimestampMs == -1) {
            return 1;
        }

        // purge excess events by age;
        if (settings.getMaxAgeMs() > 0 && tailTimestampMs > 0) {
            final TimeDuration tailAge = TimeDuration.fromCurrent(tailTimestampMs);
            if (tailAge.isLongerThan(settings.getMaxAgeMs())) {
                final long maxRemovalPercentageOfSize = getStoredEventCount() / 500;
                if (maxRemovalPercentageOfSize > 100) {
                    return 500;
                } else {
                    return 1;
                }
            }
        }
        return 0;
    }


    public enum EventType {
        User, System, Both
    }

    public static class SearchParameters {
        final private PwmLogLevel minimumLevel;
        final private int maxEvents;
        final private String username;
        final private String text;
        final private long maxQueryTime;
        final private EventType eventType;

        public SearchParameters(
                final PwmLogLevel minimumLevel,
                final int count,
                final String username,
                final String text,
                final long maxQueryTime,
                final EventType eventType        )
        {
            this.eventType = eventType;
            this.maxQueryTime = maxQueryTime;
            this.text = text;
            this.username = username;
            this.maxEvents = count;
            this.minimumLevel = minimumLevel;
        }

        public PwmLogLevel getMinimumLevel()
        {
            return minimumLevel;
        }

        public int getMaxEvents()
        {
            return maxEvents;
        }

        public String getUsername()
        {
            return username;
        }

        public String getText()
        {
            return text;
        }

        public long getMaxQueryTime()
        {
            return maxQueryTime;
        }

        public EventType getEventType()
        {
            return eventType;
        }
    }

    public SearchResults readStoredEvents(
            final SearchParameters searchParameters
    ) {
        return new SearchResults(localDBListQueue.iterator(), searchParameters);
    }

    public TimeDuration getDirtyQueueTime() {
        if (eventQueue.isEmpty()) {
            return TimeDuration.ZERO;
        }

        return TimeDuration.fromCurrent(lastQueueFlushTimestamp);
    }

    private PwmLogEvent readEvent(final String value) {
        try {
            return PwmLogEvent.fromEncodedString(value);
        } catch (Throwable e) {
            if (!hasShownReadError) {
                hasShownReadError = true;
                LOGGER.error("error reading localDBLogger event: " + e.getMessage());
            }
        }
        return null;
    }

    private boolean checkEventForParams(
            final PwmLogEvent event,
            final SearchParameters searchParameters
    ) {
        if (event == null) {
            return false;
        }

        boolean eventMatchesParams = true;

        if (searchParameters.getMinimumLevel()!= null) {
            if (event.getLevel().compareTo(searchParameters.getMinimumLevel()) <= -1) {
                eventMatchesParams = false;
            }
        }

        Pattern pattern = null;
        try {
            if (searchParameters.getUsername() != null && searchParameters.getUsername().length() > 0) {
                pattern = Pattern.compile(searchParameters.getUsername());
            }
        } catch (PatternSyntaxException e) {
            LOGGER.trace("invalid regex syntax for " + searchParameters.getUsername() + ", reverting to plaintext search");
        }
        if (pattern != null) {
            final Matcher matcher = pattern.matcher(event.getActor() == null ? "" : event.getActor());
            if (!matcher.find()) {
                eventMatchesParams = false;
            }
        } else if (eventMatchesParams && (searchParameters.getUsername() != null && searchParameters.getUsername().length() > 1)) {
            final String eventUsername = event.getActor();
            if (eventUsername == null || !eventUsername.equalsIgnoreCase(searchParameters.getUsername())) {
                eventMatchesParams = false;
            }
        }

        if (eventMatchesParams && (searchParameters.getText() != null && searchParameters.getText().length() > 0)) {
            final String eventMessage = event.getMessage();
            final String textLowercase = searchParameters.getText() .toLowerCase();
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

        if (searchParameters.getEventType() != null) {
            if (searchParameters.getEventType() == EventType.System) {
                if (event.getActor() != null && event.getActor().length() > 0) {
                    eventMatchesParams = false;
                }
            } else if (searchParameters.getEventType() == EventType.User) {
                if (event.getActor() == null || event.getActor().length() < 1) {
                    eventMatchesParams = false;
                }
            }
        }

        return eventMatchesParams;
    }


    public void writeEvent(final PwmLogEvent event) {
        if (status == STATUS.OPEN) {
            if (settings.getMaxEvents() > 0) {
                boolean success = eventQueue.offer(event);
                if (!success) { // if event queue isn't overflowed, simply add event to write queue
                    final long startEventTime = System.currentTimeMillis();
                    while (TimeDuration.fromCurrent(startEventTime).isShorterThan(30 * 1000) && !success) {
                        Helper.pause(100);
                        success = eventQueue.offer(event);
                    }
                    if (!success) {
                        LOGGER.warn("discarding event due to full write queue: " + event.toString());
                    }
                }
            }
        }
    }

// -------------------------- INNER CLASSES --------------------------

    private class WriterThread implements Runnable {
        public void run() {
            LOGGER.debug("writer thread open");
            writerThreadActive = true;
            try {
                loop();
            } catch (Exception e) {
                LOGGER.fatal("unexpected fatal error during LocalDBLogger log event writing; logging to localDB will be suspended.", e);
            }
            writerThreadActive = false;
        }

        private void loop() throws LocalDBException {
            LOGGER.debug("starting writer thread loop");

            while (status == STATUS.OPEN) {
                long startLoopTime = System.currentTimeMillis();
                final int writesDone = flushQueue();

                final int purgeCount = determineTailRemovalCount();
                int purgesDone = 0;
                if (purgeCount > 0) {
                    int removalCount = purgeCount > transactionCalculator.getTransactionSize() + 1 ? transactionCalculator.getTransactionSize() + 1 : purgeCount;
                    localDBListQueue.removeLast(removalCount);
                    tailTimestampMs = readTailTimestamp();
                    purgesDone = removalCount;
                }

                final int totalWork = writesDone + purgesDone;
                if (totalWork == 0) {
                    Helper.pause(settings.getMaxDirtyQueueAgeMs());
                    if (settings.isDevDebug()) {
                        LOGGER.trace("no work on last cycle, sleeping for " + new TimeDuration(settings.getMaxDirtyQueueAgeMs()).asCompactString() + " queue size=" + getPendingEventCount());
                    }
                } else if (totalWork < 5) {
                    Helper.pause(settings.getMaxDirtyQueueAgeMs());
                    if (settings.isDevDebug()) {
                        LOGGER.trace("minor work on last cycle, sleeping for " + new TimeDuration(settings.getMaxDirtyQueueAgeMs()).asCompactString() + " queue size=" + getPendingEventCount());
                    }
                } else {
                    final TimeDuration txnDuration = TimeDuration.fromCurrent(startLoopTime);
                    transactionCalculator.recordLastTransactionDuration(txnDuration);
                    if (settings.isDevDebug()) {
                        LOGGER.trace("tick writes=" + writesDone+ ", purges=" + purgesDone + ", queue=" + getPendingEventCount() + ", txnCalcSize=" + transactionCalculator.getTransactionSize() + ", txnDuration=" + txnDuration.getTotalMilliseconds());
                    }
                }
            }
            LOGGER.debug("writer thread exiting");
        }
    }

    public class SearchResults implements Serializable, Iterator<PwmLogEvent> {
        final private Iterator<String> localDBIterator;
        final private SearchParameters searchParameters;

        private final Date startTime;

        private PwmLogEvent nextEvent;
        private int eventCount = 0;
        private Date finishTime;

        private SearchResults(
                final Iterator<String> localDBIterator,
                final SearchParameters searchParameters
        ) {
            startTime = new Date();
            this.localDBIterator = localDBIterator;
            this.searchParameters = searchParameters;
            nextEvent = readNextEvent();
        }

        @Override
        public boolean hasNext()
        {
            return nextEvent != null;
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        public PwmLogEvent next() {
            if (nextEvent == null) {
                throw new NoSuchElementException();
            }

            final PwmLogEvent returnEvent = nextEvent;
            nextEvent = readNextEvent();
            return returnEvent;
        }

        private boolean isTimedout() {
            //return false;
            return TimeDuration.fromCurrent(startTime).isLongerThan(new TimeDuration(searchParameters.getMaxQueryTime()));
        }

        private PwmLogEvent readNextEvent()
        {
            if (eventCount >= searchParameters.getMaxEvents() || isTimedout()) {
                finishTime = new Date();
                return null;
            }

            while (!isTimedout() && localDBIterator.hasNext()) {
                final String nextDbValue = localDBIterator.next();
                if (nextDbValue == null) {
                    finishTime = new Date();
                    return null;
                }

                final PwmLogEvent logEvent = readEvent(nextDbValue);
                if (logEvent != null && checkEventForParams(logEvent, searchParameters)) {
                    eventCount++;
                    return logEvent;
                }
            }

            finishTime = new Date();
            return null;
        }

        public int getReturnedEvents()
        {
            return eventCount;
        }


        public TimeDuration getSearchTime()
        {
            return finishTime == null ? TimeDuration.fromCurrent(startTime) : new TimeDuration(startTime,finishTime);
        }
    }

    public STATUS status() {
        return status;
    }

    public List<HealthRecord> healthCheck() {
        final List<HealthRecord> healthRecords = new ArrayList<>();

        if (status != STATUS.OPEN) {
            healthRecords.add(new HealthRecord(HealthStatus.WARN, HealthTopic.Application, "LocalDBLogger is not open, status is " + status.toString()));
            return healthRecords;
        }

        final int eventCount = getStoredEventCount();
        if (eventCount > settings.getMaxEvents() + 5000) {
            healthRecords.add(new HealthRecord(HealthStatus.CAUTION, HealthTopic.Application, "Record count of " + NumberFormat.getInstance().format(eventCount) + " records, is more than the configured maximum of " + NumberFormat.getInstance().format(settings.getMaxEvents())));
        }

        final Date tailDate = getTailDate();
        final TimeDuration timeDuration = TimeDuration.fromCurrent(tailDate);
        if (timeDuration.isLongerThan(settings.getMaxAgeMs())) { // older than max age
            healthRecords.add(new HealthRecord(HealthStatus.CAUTION, HealthTopic.Application, "Oldest record is " + timeDuration.asCompactString() + ", configured maximum is " + new TimeDuration(settings.getMaxAgeMs()).asCompactString()));
        }

        return healthRecords;
    }

    public void init(PwmApplication pwmApplication) throws PwmException {
    }


    public String sizeToDebugString() {
        final int storedEvents = this.getStoredEventCount();
        final int maxEvents = settings.getMaxEvents();
        final double percentFull = (double)storedEvents / (double)maxEvents * 100f;
        final NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setMaximumFractionDigits(3);
        numberFormat.setMinimumFractionDigits(3);

        final StringBuilder sb = new StringBuilder();
        sb.append(this.getStoredEventCount()).append(" / ").append(maxEvents);
        sb.append(" (").append(numberFormat.format(percentFull)).append("%)");
        return sb.toString();
    }

    public ServiceInfo serviceInfo()
    {
        return new ServiceInfo(Collections.singletonList(DataStorageMethod.LOCALDB));
    }

    public static class Settings implements Serializable {
        private int maxEvents = 100 * 1000;
        private long maxAgeMs = (long)4 * (long)7 * (long)24 * (long)60 * (long)60 * (long)1000; // 4 weeks
        private long maxDirtyQueueAgeMs = PwmConstants.LOCALDB_LOGGER_MAX_DIRTY_BUFFER_MS;
        private boolean devDebug = false;

        public int getMaxEvents()
        {
            return maxEvents;
        }

        public void setMaxEvents(int maxEvents)
        {
            this.maxEvents = maxEvents;
        }

        public long getMaxAgeMs()
        {
            return maxAgeMs;
        }

        public void setMaxAgeMs(long maxAgeMs)
        {
            this.maxAgeMs = maxAgeMs;
        }

        public long getMaxDirtyQueueAgeMs()
        {
            return maxDirtyQueueAgeMs;
        }

        public void setMaxDirtyQueueAgeMs(long maxDirtyQueueAgeMs)
        {
            this.maxDirtyQueueAgeMs = maxDirtyQueueAgeMs;
        }

        public boolean isDevDebug()
        {
            return devDebug;
        }

        public void setDevDebug(boolean devDebug)
        {
            this.devDebug = devDebug;
        }

        private Settings copy() {
            return JsonUtil.deserialize(JsonUtil.serialize(this),this.getClass());
        }
    }
}


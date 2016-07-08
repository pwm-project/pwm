/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.io.Serializable;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A work item queue manager.   Items submitted to the queue will eventually be worked on by the client side @code {@link ItemProcessor}.
 * @param <W>
 */
public class WorkQueueProcessor<W extends Serializable> {

    private final Deque<String> queue;
    private final Settings settings;
    private final ItemProcessor<W> itemProcessor;

    private final PwmLogger LOGGER;

    private volatile WorkerThread workerThread;

    private IDGenerator idGenerator = new IDGenerator();
    private Date eldestItem = null;

    public enum ProcessResult {
        SUCCESS,
        FAILED,
        RETRY,
    }

    private static class IDGenerator {
        private int currentID;

        IDGenerator() {
            currentID = PwmRandom.getInstance().nextInt();
        }

        synchronized String nextID() {
            currentID += 1;
            return Integer.toString(Math.abs(currentID), 36);
        }
    }

    public WorkQueueProcessor(
            final PwmApplication pwmApplication,
            final Deque<String> queue,
            final Settings settings,
            final ItemProcessor<W> itemProcessor,
            final Class sourceClass
    ) {
        this.settings = JsonUtil.cloneUsingJson(settings, Settings.class);
        this.queue = queue;
        this.itemProcessor = itemProcessor;
        this.LOGGER = PwmLogger.getLogger(sourceClass.getName() + "_" + this.getClass().getSimpleName());

        if (!queue.isEmpty()) {
            LOGGER.debug("opening with " + queue.size() + " items in work queue");
        }

        this.workerThread = new WorkerThread();
        workerThread.setDaemon(true);
        workerThread.setName(Helper.makeThreadName(pwmApplication, sourceClass) + "-worker-");
        workerThread.start();
    }

    public void close() {
        if (workerThread == null) {
            return;
        }
        final WorkerThread localWorkerThread = workerThread;
        workerThread = null;

        localWorkerThread.flushQueueAndClose();
        final Date shutdownStartTime = new Date();

        if (queueSize() > 0) {
            LOGGER.debug("attempting to flush queue prior to shutdown, items in queue=" + queueSize());
        }
        while (localWorkerThread.isRunning() && TimeDuration.fromCurrent(shutdownStartTime).isLongerThan(settings.getMaxShutdownWaitTime())) {
            Helper.pause(100);
        }

        if (!queue.isEmpty()) {
            LOGGER.warn("shutting down with " + queue.size() + " items remaining in work queue");
        }
    }

    public synchronized void submit(final W workItem) throws PwmOperationalException {
        if (workerThread == null) {
            final String errorMsg = this.getClass().getName() + " has been closed, unable to submit new item";
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
        }
        final ItemWrapper<W> itemWrapper = new ItemWrapper<W>(new Date(), workItem, idGenerator.nextID());
        final String asString = JsonUtil.serialize(itemWrapper);

        if (settings.getMaxEvents() > 0) {
            final Date startTime = new Date();
            while (!queue.offerLast(asString)) {
                if (TimeDuration.fromCurrent(startTime).isLongerThan(settings.getMaxSubmitWaitTime())) {
                    final String errorMsg = "unable to submit item to worker queue after " + settings.getMaxSubmitWaitTime().asCompactString()
                            + ", item=" + itemProcessor.convertToDebugString(workItem);
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
                }
                Helper.pause(100);
            }

            eldestItem = itemWrapper.getDate();
            workerThread.notifyWorkPending();

            LOGGER.trace("item submitted: " + makeDebugText(itemWrapper));
        }
    }

    public int queueSize() {
        return queue.size();
    }

    public Date eldestItem() {
        return eldestItem;
    }

    private String makeDebugText(ItemWrapper itemWrapper) throws PwmOperationalException {
        final int itemsInQueue = WorkQueueProcessor.this.queueSize();
        String traceMsg = "[" + itemWrapper.toDebugString(itemProcessor) + "]";
        if (itemsInQueue > 0) {
            traceMsg += ", " + itemsInQueue + " items in queue";
        }
        return traceMsg;
    }

    private class WorkerThread extends Thread {

        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean shutdownFlag = new AtomicBoolean(false);
        private final AtomicBoolean pendingWork = new AtomicBoolean(true);

        private Date retryWakeupTime;

        @Override
        public void run() {
            running.set(true);
            try {
                while (!shutdownFlag.get()) {
                    processNextItem();
                    waitForWork();
                }
            } catch (Throwable t) {
                LOGGER.error("unexpected error processing work item queue: " + Helper.readHostileExceptionMessage(t), t);
            }

            try {
                final Date shutdownStartTime = new Date();
                while (retryWakeupTime == null && !queue.isEmpty() && TimeDuration.fromCurrent(shutdownStartTime).isLongerThan(settings.getMaxShutdownWaitTime())) {
                    processNextItem();
                }
            } catch (Throwable t) {
                LOGGER.error("unexpected error processing work item queue: " + Helper.readHostileExceptionMessage(t), t);
            }
            running.set(false);
        }

        void flushQueueAndClose() {
            shutdownFlag.set(true);
        }

        void notifyWorkPending() {
            pendingWork.set(true);
        }

        private void waitForWork() {
            if (queue.isEmpty()) {
                pendingWork.set(false);
                eldestItem = null;
                if (queue.isEmpty()) { // extra queue check in case submit() comes in after the pendingWork is set to false here;
                    pendingWork.set(true);
                }
                while (!shutdownFlag.get() && !pendingWork.get()) { // sleep until shutdown or work arrives.
                    Helper.pause(103);
                }
            }

            if (retryWakeupTime != null) {
                while (!shutdownFlag.get() && new Date().before(retryWakeupTime)) {
                    Helper.pause(103);
                }
                retryWakeupTime = null;
            }
        }

        public boolean isRunning() {
            return running.get();
        }

        void processNextItem() {
            final String nextStrValue = queue.peekFirst();
            if (nextStrValue == null) {
                return;
            }

            final ItemWrapper<W> itemWrapper;
            try {
                itemWrapper = JsonUtil.<ItemWrapper<W>>deserialize(nextStrValue, ItemWrapper.class);
                if (TimeDuration.fromCurrent(itemWrapper.getDate()).isLongerThan(settings.getRetryDiscardAge())) {
                    LOGGER.warn("discarding queued item due to age, item=" + makeDebugText(itemWrapper));
                    removeQueueTop();
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("error reading queued item: " + e.getMessage(), e);
                removeQueueTop();
                return;
            }

            final ProcessResult processResult;
            try {
                processResult = itemProcessor.process(itemWrapper.getWorkItem());
                if (processResult == null) {
                    LOGGER.warn("itemProcessor.process() returned null, removing; item=" + makeDebugText(itemWrapper));
                    removeQueueTop();
                } else {
                    switch (processResult) {
                        case FAILED: {
                            LOGGER.error("discarding item after process failure, item=" + makeDebugText(itemWrapper));
                            removeQueueTop();
                        }
                        break;

                        case RETRY: {
                            retryWakeupTime = new Date(System.currentTimeMillis() + settings.getRetryInterval().getTotalMilliseconds());
                            LOGGER.debug("will retry item after failure, item=" + makeDebugText(itemWrapper));
                        }
                        break;

                        case SUCCESS: {
                            LOGGER.trace("successfully processed item=" + makeDebugText(itemWrapper));
                            removeQueueTop();
                        }
                        break;

                        default:
                            throw new IllegalStateException("unexpected processResult type " + processResult);
                    }
                }
            } catch(PwmOperationalException e){
                LOGGER.error("unexpected error while processing work queue: " + e.getErrorInformation());
                removeQueueTop();
            }

        }

        private void removeQueueTop() {
            queue.removeFirst();
            retryWakeupTime = null;
        }
    }

    private static class ItemWrapper<W extends Serializable> implements Serializable {
        private final Date date;
        private final String item;
        private final String className;
        private final String id;

        ItemWrapper(final Date submitDate, final W workItem, final String itemId) {
            this.date = submitDate;
            this.item = JsonUtil.serialize(workItem);
            this.className = workItem.getClass().getName();
            this.id = itemId;
        }

        Date getDate() {
            return date;
        }

        W getWorkItem() throws PwmOperationalException {
            try {
                final Class clazz = Class.forName(className);
                final Object o = JsonUtil.deserialize(item, clazz);
                return (W)o;
            } catch (Exception e) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN, "unexpected error deserializing work queue item: " + e.getMessage()));
            }
        }

        String getId() {
            return id;
        }

        String toDebugString(ItemProcessor<W> itemProcessor) throws PwmOperationalException {
            final Map<String,String> debugOutput = new LinkedHashMap<>();
            debugOutput.put("date", PwmConstants.DEFAULT_DATETIME_FORMAT.format(getDate()));
            debugOutput.put("id", getId());
            debugOutput.put("item", itemProcessor.convertToDebugString(getWorkItem()));
            return StringUtil.mapToString(debugOutput,"=",",");
        }
    }

    /**
     * Implementation of {@link ItemProcessor} must be included with the construction of a {@link WorkQueueProcessor}.
     * @param <W>
     */
    public interface ItemProcessor<W extends Serializable> {
        ProcessResult process(W workItem);

        String convertToDebugString(W workItem);
    }

    public static class Settings implements Serializable {
        private int maxEvents = 1000;
        private TimeDuration maxSubmitWaitTime = new TimeDuration(20, TimeUnit.SECONDS);
        private TimeDuration retryInterval = new TimeDuration(30, TimeUnit.SECONDS);
        private TimeDuration retryDiscardAge = new TimeDuration(1, TimeUnit.HOURS);
        private TimeDuration maxShutdownWaitTime = new TimeDuration(30, TimeUnit.SECONDS);

        public int getMaxEvents() {
            return maxEvents;
        }

        public void setMaxEvents(int maxEvents) {
            this.maxEvents = maxEvents;
        }

        public TimeDuration getMaxSubmitWaitTime() {
            return maxSubmitWaitTime;
        }

        public void setMaxSubmitWaitTime(TimeDuration maxSubmitWaitTime) {
            this.maxSubmitWaitTime = maxSubmitWaitTime;
        }

        public TimeDuration getRetryInterval() {
            return retryInterval;
        }

        public void setRetryInterval(TimeDuration retryInterval) {
            this.retryInterval = retryInterval;
        }

        public TimeDuration getRetryDiscardAge() {
            return retryDiscardAge;
        }

        public void setRetryDiscardAge(TimeDuration retryDiscardAge) {
            this.retryDiscardAge = retryDiscardAge;
        }

        public TimeDuration getMaxShutdownWaitTime() {
            return maxShutdownWaitTime;
        }

        public void setMaxShutdownWaitTime(TimeDuration maxShutdownWaitTime) {
            this.maxShutdownWaitTime = maxShutdownWaitTime;
        }
    }
}
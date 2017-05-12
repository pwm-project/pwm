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

package password.pwm.util.localdb;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;
import password.pwm.PwmApplication;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.svc.stats.EventRateMeter;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * A work item queue manager.   Items submitted to the queue will eventually be worked on by the client side @code {@link ItemProcessor}.
 * @param <W>
 */
public class WorkQueueProcessor<W extends Serializable> {

    private static final TimeDuration SUBMIT_QUEUE_FULL_RETRY_CYCLE_INTERVAL = new TimeDuration(100, TimeUnit.MILLISECONDS);
    private static final TimeDuration CLOSE_RETRY_CYCLE_INTERVAL = new TimeDuration(100, TimeUnit.MILLISECONDS);

    private final Deque<String> queue;
    private final Settings settings;
    private final ItemProcessor<W> itemProcessor;

    private final PwmLogger logger;

    private volatile WorkerThread workerThread;

    private IDGenerator idGenerator = new IDGenerator();
    private Instant eldestItem = null;

    private ThreadPoolExecutor executorService;

    private final EventRateMeter.MovingAverage avgLagTime = new EventRateMeter.MovingAverage(60 * 60 * 1000);
    private final EventRateMeter sendRate = new EventRateMeter(new TimeDuration(1, TimeUnit.HOURS));

    private final AtomicInteger preQueueSubmit = new AtomicInteger(0);
    private final AtomicInteger preQueueBypass = new AtomicInteger(0);
    private final AtomicInteger preQueueFallback = new AtomicInteger(0);
    private final AtomicInteger queueProcessItems = new AtomicInteger(0);

    public enum ProcessResult {
        SUCCESS,
        FAILED,
        RETRY,
        NOOP,
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
        this.logger = PwmLogger.getLogger(sourceClass.getName() + "_" + this.getClass().getSimpleName());

        if (!queue.isEmpty()) {
            logger.debug("opening with " + queue.size() + " items in work queue");
        }
        logger.trace("initializing worker thread with settings " + JsonUtil.serialize(settings));

        this.workerThread = new WorkerThread();
        workerThread.setDaemon(true);
        workerThread.setName(JavaHelper.makeThreadName(pwmApplication, sourceClass) + "-worker-");
        workerThread.start();

        if (settings.getPreThreads() > 0) {
            final ThreadFactory threadFactory = JavaHelper.makePwmThreadFactory(JavaHelper.makeThreadName(pwmApplication, sourceClass), true);
            executorService = new ThreadPoolExecutor(
                    1,
                    settings.getPreThreads(),
                    1,
                    TimeUnit.MINUTES,
                    new ArrayBlockingQueue<>(settings.getPreThreads()),
                    threadFactory
            );
        }
    }

    public void close() {
        if (workerThread == null) {
            return;
        }

        if (executorService != null) {
            executorService.shutdown();
        }

        final WorkerThread localWorkerThread = workerThread;
        workerThread = null;

        localWorkerThread.flushQueueAndClose();
        final Instant shutdownStartTime = Instant.now();

        if (queueSize() > 0) {
            logger.debug("attempting to flush queue prior to shutdown, items in queue=" + queueSize());
        }
        while (localWorkerThread.isRunning() && TimeDuration.fromCurrent(shutdownStartTime).isLongerThan(settings.getMaxShutdownWaitTime())) {
            JavaHelper.pause(CLOSE_RETRY_CYCLE_INTERVAL.getTotalMilliseconds());
        }

        if (!queue.isEmpty()) {
            logger.warn("shutting down with " + queue.size() + " items remaining in work queue");
        }
    }

    public void submit(final W workItem)
            throws PwmOperationalException
    {
        final ItemWrapper<W> itemWrapper = new ItemWrapper<>(Instant.now(), workItem, idGenerator.nextID());

        if (settings.getPreThreads() < 0) {
            sendAndQueueIfNecessary(itemWrapper);
            return;
        }

        if (settings.getPreThreads() > 0) {
            try {
                executorService.execute(() -> sendAndQueueIfNecessary(itemWrapper));
                preQueueSubmit.incrementAndGet();
            } catch(RejectedExecutionException e) {
                submitToQueue(itemWrapper);
                preQueueBypass.incrementAndGet();
            }
        } else {
            submitToQueue(itemWrapper);
        }
    }

    private void sendAndQueueIfNecessary(final ItemWrapper<W> itemWrapper)
    {
        try {
            final ProcessResult processResult = itemProcessor.process(itemWrapper.getWorkItem());
            if (processResult == ProcessResult.SUCCESS) {
                logAndStatUpdateForSuccess(itemWrapper);
            } else if (processResult == ProcessResult.RETRY || processResult == ProcessResult.NOOP) {
                preQueueFallback.incrementAndGet();
                try {
                    submitToQueue(itemWrapper);
                } catch (Exception e) {
                    logger.error("error submitting to work queue after executor returned retry status: " + e.getMessage());
                }
            }
        } catch (PwmOperationalException e) {
            logger.error("unexpected error while processing itemWrapper: " + e.getMessage(),e);
        }
    }

    private synchronized void submitToQueue(final ItemWrapper<W> itemWrapper) throws PwmOperationalException {
        if (workerThread == null) {
            final String errorMsg = this.getClass().getName() + " has been closed, unable to submit new item";
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
        }

        final String asString = JsonUtil.serialize(itemWrapper);

        if (settings.getMaxEvents() > 0) {
            final Instant startTime = Instant.now();
            while (!queue.offerLast(asString)) {
                if (TimeDuration.fromCurrent(startTime).isLongerThan(settings.getMaxSubmitWaitTime())) {
                    final String errorMsg = "unable to submit item to worker queue after " + settings.getMaxSubmitWaitTime().asCompactString()
                            + ", item=" + itemProcessor.convertToDebugString(itemWrapper.getWorkItem());
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
                }
                JavaHelper.pause(SUBMIT_QUEUE_FULL_RETRY_CYCLE_INTERVAL.getTotalMilliseconds());
            }

            eldestItem = itemWrapper.getDate();
            workerThread.notifyWorkPending();

            logger.trace("item submitted: " + makeDebugText(itemWrapper));
        }
    }

    public int queueSize() {
        return queue.size();
    }

    public Instant eldestItem() {
        return eldestItem;
    }

    private String makeDebugText(final ItemWrapper<W> itemWrapper) throws PwmOperationalException {
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
        private final AtomicBoolean notifyWorkFlag = new AtomicBoolean(true);

        private Instant retryWakeupTime;

        @Override
        public void run() {
            running.set(true);
            try {
                while (!shutdownFlag.get()) {
                    processNextItem();
                    waitForWork();
                }
            } catch (Throwable t) {
                logger.error("unexpected error processing work item queue: " + JavaHelper.readHostileExceptionMessage(t), t);
            }

            logger.trace("worker thread beginning shutdown...");

            if (!queue.isEmpty()) {
                logger.trace("processing remaining " + queue.size() + " items");

                try {
                    final Instant shutdownStartTime = Instant.now();
                    while (retryWakeupTime == null && !queue.isEmpty() && TimeDuration.fromCurrent(shutdownStartTime).isLongerThan(settings.getMaxShutdownWaitTime())) {
                        processNextItem();
                    }
                } catch (Throwable t) {
                    logger.error("unexpected error processing work item queue: " + JavaHelper.readHostileExceptionMessage(t), t);
                }
            }

            logger.trace("thread exiting...");
            running.set(false);
        }

        void flushQueueAndClose() {
            shutdownFlag.set(true);
            logger.trace("shutdown flag set");
            notifyWorkPending();

            // rest until not running for up to 3 seconds....
            JavaHelper.pause(3000, 50, o -> !running.get());
        }

        void notifyWorkPending() {
            notifyWorkFlag.set(true);
            LockSupport.unpark(this);
        }

        private void waitForWork() {
            if (!shutdownFlag.get()) {
                if (retryWakeupTime != null) {
                    while (retryWakeupTime.isAfter(Instant.now()) && !shutdownFlag.get()) {
                        LockSupport.parkUntil(this, retryWakeupTime.toEpochMilli());
                    }
                    retryWakeupTime = null;
                } else {
                    if (queue.isEmpty() && !notifyWorkFlag.get()) {
                        eldestItem = null;
                        LockSupport.park(this);
                    }
                }
            }

            notifyWorkFlag.set(false);
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
                    removeQueueTop();
                    logger.warn("discarding queued item due to age, item=" + makeDebugText(itemWrapper));
                    return;
                }
            } catch (Throwable e) {
                removeQueueTop();
                logger.warn("discarding stored record due to parsing error: " + e.getMessage() + ", record=" + nextStrValue);
                return;
            }

            final ProcessResult processResult;
            try {
                queueProcessItems.incrementAndGet();
                processResult = itemProcessor.process(itemWrapper.getWorkItem());
                if (processResult == null) {
                    removeQueueTop();
                    logger.warn("itemProcessor.process() returned null, removing; item=" + makeDebugText(itemWrapper));
                } else {
                    switch (processResult) {
                        case FAILED: {
                            removeQueueTop();
                            logger.error("discarding item after process failure, item=" + makeDebugText(itemWrapper));
                        }
                        break;

                        case RETRY: {
                            retryWakeupTime = Instant.ofEpochMilli(System.currentTimeMillis() + settings.getRetryInterval().getTotalMilliseconds());
                            logger.debug("will retry item after failure, item=" + makeDebugText(itemWrapper));
                        }
                        break;

                        case SUCCESS: {
                            removeQueueTop();
                            logAndStatUpdateForSuccess(itemWrapper);
                        }
                        break;

                        case NOOP:
                            break;


                        default:
                            throw new IllegalStateException("unexpected processResult type " + processResult);
                    }
                }
            } catch(Throwable e) {
                if (!shutdownFlag.get()) {
                    removeQueueTop();
                    logger.error("unexpected error while processing work queue: " + e.getMessage());
                }
            }

        }

        private void removeQueueTop() {
            queue.removeFirst();
            retryWakeupTime = null;
        }
    }

    private static class ItemWrapper<W extends Serializable> implements Serializable {
        @SerializedName("t")
        private final Instant timestamp;

        @SerializedName("m")
        private final String item;

        @SerializedName("c")
        private final String className;

        @SerializedName("i")
        private final String id;

        ItemWrapper(final Instant submitDate, final W workItem, final String itemId) {
            this.timestamp = submitDate;
            this.item = JsonUtil.serialize(workItem);
            this.className = workItem.getClass().getName();
            this.id = itemId;
        }

        Instant getDate() {
            return timestamp;
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

        String toDebugString(final ItemProcessor<W> itemProcessor) throws PwmOperationalException {
            final Map<String,String> debugOutput = new LinkedHashMap<>();
            debugOutput.put("date", getDate().toString());
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

    @Getter
    @Builder
    public static class Settings implements Serializable {
        private int maxEvents = 1000;
        private int preThreads = 0;
        private TimeDuration maxSubmitWaitTime = new TimeDuration(5, TimeUnit.SECONDS);
        private TimeDuration retryInterval = new TimeDuration(30, TimeUnit.SECONDS);
        private TimeDuration retryDiscardAge = new TimeDuration(1, TimeUnit.HOURS);
        private TimeDuration maxShutdownWaitTime = new TimeDuration(30, TimeUnit.SECONDS);
    }

    private void logAndStatUpdateForSuccess(final ItemWrapper<W> itemWrapper)
            throws PwmOperationalException
    {
        final TimeDuration lagTime = TimeDuration.fromCurrent(itemWrapper.getDate());
        avgLagTime.update(lagTime.getTotalMilliseconds());
        sendRate.markEvents(1);
        logger.trace("successfully processed item=" + makeDebugText(itemWrapper) + "; lagTime=" + lagTime.asCompactString()
                + "; " + StringUtil.mapToString(debugInfo()));
    }

    public Map<String,String> debugInfo() {
        final Map<String,String> output = new HashMap<>();
        output.put("avgLagTime", new TimeDuration((long)avgLagTime.getAverage()).asCompactString());
        output.put("sendRate", sendRate.readEventRate() + "/s");
        output.put("preQueueSubmit", String.valueOf(preQueueSubmit.get()));
        output.put("preQueueBypass", String.valueOf(preQueueBypass.get()));
        output.put("preQueueFallback", String.valueOf(preQueueFallback.get()));
        output.put("queueProcessItems", String.valueOf(queueProcessItems.get()));
        if (executorService != null) {
            output.put("activeThreads", String.valueOf(executorService.getActiveCount()));
        }
        return Collections.unmodifiableMap(output);
    }
}

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

package password.pwm.util.queue;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmService;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBStoredQueue;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public abstract class AbstractQueueManager implements PwmService {
    protected PwmLogger LOGGER = PwmLogger.getLogger(AbstractQueueManager.class);

    private static final long QUEUE_POLL_INTERVAL = 30 * 1003;

    protected PwmApplication pwmApplication;
    protected STATUS status = PwmService.STATUS.NEW;
    protected Timer timerThread;
    protected Settings settings;

    protected HealthRecord lastSendFailure;
    protected Date lastSendFailureTime;
    private LocalDBStoredQueue sendQueue;
    protected int itemIDCounter;
    protected PwmApplication.AppAttribute itemCountAppAttribute;
    protected String serviceName = AbstractQueueManager.class.getSimpleName();


    public STATUS status() {
        return status;
    }

    public int queueSize() {
        if (sendQueue == null || status != STATUS.OPEN) {
            return 0;
        }

        return this.sendQueue.size();
    }

    protected void add(final Serializable input)
            throws PwmUnrecoverableException
    {
        final String gsonInput = Helper.getGson().toJson(input);
        final int nextItemID = getNextItemCount();
        final QueueEvent event = new QueueEvent(gsonInput, new Date(), nextItemID);

        if (status != PwmService.STATUS.OPEN) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CLOSING));
        }

        if (sendQueue.size() >= settings.getMaxQueueItemCount()) {
            LOGGER.warn("queue full, discarding item send request: " + event.getItem());
            return;
        }

        final String jsonEvent = Helper.getGson().toJson(event);
        sendQueue.addLast(jsonEvent);
        LOGGER.trace("submitted item to queue: " + queueItemToDebugString(event) + ", queue size: " + sendQueue.size());

        timerThread.schedule(new QueueProcessorTask(), 1);
    }

    protected static class QueueEvent implements Serializable {
        private String item;
        private Date timestamp;
        private int itemID;

        protected QueueEvent(
                final String item,
                final Date timestamp,
                final int itemID
        ) {
            this.item = item;
            this.timestamp = timestamp;
            this.itemID = itemID;
        }

        public String getItem() {
            return item;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public int getItemID()
        {
            return itemID;
        }
    }

    boolean sendIsRetryable(final Exception e) {
        if (e != null) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                LOGGER.trace("message send failure cause is due to an IOException: " + e.getMessage());
                return true;
            }
        }
        return false;
    }

    public void init(
            final PwmApplication pwmApplication,
            final LocalDB.DB DB,
            final Settings settings,
            final PwmApplication.AppAttribute itemCountAppAttribute,
            final String serviceName
    )
            throws PwmException
    {
        this.serviceName = serviceName;
        this.pwmApplication = pwmApplication;
        this.itemCountAppAttribute = itemCountAppAttribute;
        this.settings = settings;

        final LocalDB localDB = this.pwmApplication.getLocalDB();

        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            status = STATUS.CLOSED;
            lastSendFailure = HealthRecord.forMessage(HealthMessage.ServiceClosed_LocalDBUnavail,serviceName);
            return;
        }

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.READ_ONLY) {
            status = STATUS.CLOSED;
            lastSendFailure = HealthRecord.forMessage(HealthMessage.ServiceClosed_AppReadOnly,serviceName);
            return;
        }

        itemIDCounter = readItemIDCounter();
        sendQueue = LocalDBStoredQueue.createLocalDBStoredQueue(pwmApplication, localDB, DB);
        final String threadName = Helper.makeThreadName(pwmApplication, this.getClass()) + " timer thread";
        timerThread = new Timer(threadName,true);
        status = PwmService.STATUS.OPEN;
        LOGGER.debug(settings.getDebugName() + " is now open, " + sendQueue.size() + " items in queue");
        timerThread.schedule(new QueueProcessorTask(),1,QUEUE_POLL_INTERVAL);
    }

    protected int readItemIDCounter() {
        final String itemCountStr = pwmApplication.readAppAttribute(itemCountAppAttribute);
        if (itemCountStr != null) {
            try {
                return Integer.parseInt(itemCountStr);
            } catch (Exception e) {
                LOGGER.error("error reading stored item counter app attribute: " + e.getMessage());
            }
        }
        return 0;
    }

    protected void storeItemCounter() {
        try {
            pwmApplication.writeAppAttribute(itemCountAppAttribute, String.valueOf(itemIDCounter));
        } catch (Exception e) {
            LOGGER.error("error writing stored item counter app attribute: " + e.getMessage());
        }
    }

    protected synchronized int getNextItemCount() {
        itemIDCounter++;
        if (itemIDCounter < 0) {
            itemIDCounter = 0;
        }
        storeItemCounter();
        return itemIDCounter;
    }

    public synchronized void close() {
        status = PwmService.STATUS.CLOSED;
        final Date startTime = new Date();
        final int maxCloseWaitMs = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_MAX_CLOSE_TIMEOUT_MS));

        if (sendQueue != null && !sendQueue.isEmpty()) {
            if (timerThread != null) {
                timerThread.schedule(new QueueProcessorTask(),1);
                LOGGER.warn("waiting up to 5 seconds for " + sendQueue.size() + " items in the queue to process");
                while (!sendQueue.isEmpty() && TimeDuration.fromCurrent(startTime).isShorterThan(maxCloseWaitMs)) {
                    Helper.pause(100);
                }
            }
            if (!sendQueue.isEmpty()) {
                LOGGER.warn("closing queue with " + sendQueue.size() + " message in queue");
            }
        }

        if (timerThread != null) {
            timerThread.cancel();
        }
        timerThread = null;
    }

    public List<HealthRecord> healthCheck() {
        if (lastSendFailure == null) {
            return null;
        }

        return Collections.singletonList(lastSendFailure);
    }

    private void processQueue() {
        if (lastSendFailureTime != null) {
            final TimeDuration timeSinceFailure = TimeDuration.fromCurrent(lastSendFailureTime);
            if (timeSinceFailure.isShorterThan(settings.getErrorRetryWaitTime())) {
                return;
            }
        }

        lastSendFailureTime = null;

        while (sendQueue.peekFirst() != null && lastSendFailureTime == null) {
            final String jsonEvent = sendQueue.peekFirst();
            if (jsonEvent != null) {
                final QueueEvent event = Helper.getGson().fromJson(jsonEvent, QueueEvent.class);

                if (event == null || event.getTimestamp() == null) {
                    sendQueue.pollFirst();
                } else if (TimeDuration.fromCurrent(event.getTimestamp()).isLongerThan(
                        settings.getMaxQueueItemAge())) {
                    LOGGER.debug("discarding event due to maximum retry age: " + queueItemToDebugString(event));
                    sendQueue.pollFirst();
                    noteDiscardedItem(event);
                } else {
                    final String item = event.getItem();

                    LOGGER.trace("preparing to send item in queue: " + queueItemToDebugString(
                            event) + ", queue size: " + sendQueue.size());

                    // execute operation
                    final boolean success = sendItem(item);

                    if (success) {
                        sendQueue.pollFirst();
                        LOGGER.trace(
                                "queued item successfully sent and removed from queue: " + queueItemToDebugString(
                                        event) + ", queue size: " + sendQueue.size());
                    } else {
                        lastSendFailureTime = new Date();
                        LOGGER.debug("queued item was not successfully sent, will retry: " + queueItemToDebugString(
                                event) + ", queue size: " + sendQueue.size());
                    }
                }
            }
        }
    }


    abstract boolean sendItem(String item);


    // -------------------------- INNER CLASSES --------------------------

    protected class QueueProcessorTask extends TimerTask {
        public void run() {
            try {
                processQueue();
            } catch (Exception e) {
                LOGGER.error("unexpected exception while processing " + settings.getDebugName() + " queue: " + e.getMessage(), e);
            }
        }
    }

    protected static class Settings {
        private TimeDuration maxQueueItemAge;
        private TimeDuration errorRetryWaitTime;
        private int maxQueueItemCount;
        private String debugName;

        public Settings(TimeDuration maxQueueItemAge, TimeDuration errorRetryWaitTime, int maxQueueItemCount, String debugName) {
            this.maxQueueItemAge = maxQueueItemAge;
            this.errorRetryWaitTime = errorRetryWaitTime;
            this.maxQueueItemCount = maxQueueItemCount;
            this.debugName = debugName;
        }

        public TimeDuration getMaxQueueItemAge() {
            return maxQueueItemAge;
        }

        public TimeDuration getErrorRetryWaitTime() {
            return errorRetryWaitTime;
        }

        public int getMaxQueueItemCount() {
            return maxQueueItemCount;
        }

        public String getDebugName() {
            return debugName;
        }
    }

    public ServiceInfo serviceInfo()
    {
        if (status() == STATUS.OPEN) {
            return new ServiceInfo(Collections.singletonList(DataStorageMethod.LOCALDB));
        } else {
            return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
        }
    }

    protected abstract String queueItemToDebugString(QueueEvent queueEvent);

    protected abstract void noteDiscardedItem(QueueEvent queueEvent);
}

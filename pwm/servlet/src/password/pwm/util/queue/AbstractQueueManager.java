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

import password.pwm.PwmApplication;
import password.pwm.PwmService;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBStoredQueue;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public abstract class AbstractQueueManager implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(AbstractQueueManager.class);

    private static final long QUEUE_POLL_INTERVAL = 30 * 1003; // 10 seconds

    protected PwmApplication pwmApplication;
    protected STATUS status = PwmService.STATUS.NEW;
    protected Timer timerThread;
    protected Settings settings;

    protected HealthRecord lastSendFailure;
    protected Date lastSendFailureTime;
    protected LocalDBStoredQueue sendQueue;

    public STATUS status() {
        return status;
    }

    public int queueSize() {
        if (sendQueue == null || status != STATUS.OPEN) {
            return 0;
        }

        return this.sendQueue.size();
    }

    protected void add(QueueEvent event)
            throws PwmUnrecoverableException
    {
        if (status != PwmService.STATUS.OPEN) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CLOSING));
        }

        if (sendQueue.size() >= settings.getMaxQueueItemCount()) {
            LOGGER.warn("queue full, discarding email send request: " + event.getItem());
            return;
        }

        final String jsonEvent = Helper.getGson().toJson(event);
        sendQueue.add(jsonEvent);

        timerThread.schedule(new QueueProcessorTask(), 1);
    }

    protected static class QueueEvent implements Serializable {
        private String item;
        private Date timestamp;

        protected QueueEvent(final String item, final Date timestamp) {
            this.item = item;
            this.timestamp = timestamp;
        }

        public String getItem() {
            return item;
        }

        public Date getTimestamp() {
            return timestamp;
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

    public void init(final PwmApplication pwmApplication, final LocalDB.DB DB, final Settings settings)
            throws PwmException
    {
        this.pwmApplication = pwmApplication;
        this.settings = settings;

        final LocalDB localDB = this.pwmApplication.getLocalDB();

        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            status = STATUS.CLOSED;
            lastSendFailure = new HealthRecord(HealthStatus.WARN, this.getClass().getSimpleName(), "unable to start, LocalDB is not open");
            return;
        }

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.READ_ONLY) {
            status = STATUS.CLOSED;
            lastSendFailure = new HealthRecord(HealthStatus.WARN, this.getClass().getSimpleName(), "unable to start, Application is in read-only mode");
            return;
        }

        sendQueue = LocalDBStoredQueue.createLocalDBStoredQueue(localDB, DB);
        final String threadName = Helper.makeThreadName(pwmApplication, this.getClass()) + " timer thread";
        timerThread = new Timer(threadName,true);
        status = PwmService.STATUS.OPEN;
        LOGGER.debug(settings.getDebugName() + " is now open");
        timerThread.schedule(new QueueProcessorTask(),1,QUEUE_POLL_INTERVAL);
    }

    public synchronized void close() {
        status = PwmService.STATUS.CLOSED;
        final Date startTime = new Date();

        if (sendQueue != null && !sendQueue.isEmpty()) {
            if (timerThread != null) {
                timerThread.schedule(new QueueProcessorTask(),1);
                LOGGER.warn("waiting up to 5 seconds for " + sendQueue.size() + " items in the queue to process");
                while (!sendQueue.isEmpty() && TimeDuration.fromCurrent(startTime).isShorterThan(5000)) {
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
                } else if (TimeDuration.fromCurrent(event.getTimestamp()).isLongerThan(settings.getMaxQueueItemAge())) {
                    LOGGER.debug("discarding event due to maximum retry age: " + event.getItem());
                    sendQueue.pollFirst();
                } else {
                    final String item = event.getItem();

                    final boolean success = sendItem(item);
                    if (success) {
                        sendQueue.pollFirst();
                    } else {
                        lastSendFailureTime = new Date();
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
            return new ServiceInfo(Collections.<DataStorageMethod>singletonList(DataStorageMethod.LOCALDB));
        } else {
            return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
        }
    }
}

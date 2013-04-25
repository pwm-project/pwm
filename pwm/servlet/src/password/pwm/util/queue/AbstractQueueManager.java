/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import com.google.gson.Gson;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBStoredQueue;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public abstract class AbstractQueueManager implements PwmService {

    static final int ERROR_RETRY_WAIT_TIME_MS = 60 * 1000;

    static final PwmLogger LOGGER = PwmLogger.getLogger(SmsQueueManager.class);

    static String SERVICE_NAME = "AbstractQueueManager";


    PwmApplication pwmApplication;
    STATUS status = PwmService.STATUS.NEW;
    volatile boolean threadActive;
    long maxErrorWaitTimeMS = 5 * 60 * 1000;

    HealthRecord lastSendFailure;
    LocalDBStoredQueue sendQueue;

    public STATUS status() {
        return status;
    }

    public int queueSize() {
        if (sendQueue == null || status != STATUS.OPEN) {
            return 0;
        }

        return this.sendQueue.size();
    }

    static class QueueEvent implements Serializable {
        private String item;
        private long queueInsertTimestamp;

        public QueueEvent() {
        }

        public QueueEvent(final String item, final long queueInsertTimestamp) {
            this.item = item;
            this.queueInsertTimestamp = queueInsertTimestamp;
        }

        public String getItem() {
            return item;
        }

        public long getQueueInsertTimestamp() {
            return queueInsertTimestamp;
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

    public void init(final PwmApplication pwmApplication, final LocalDB.DB DB)
            throws PwmException
    {
        this.pwmApplication = pwmApplication;
        this.maxErrorWaitTimeMS = this.pwmApplication.getConfig().readSettingAsLong(PwmSetting.EMAIL_MAX_QUEUE_AGE) * 1000;

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

        sendQueue = LocalDBStoredQueue.createPwmDBStoredQueue(localDB, DB);

        {
            final QueueThread emailSendThread = new QueueThread();
            emailSendThread.setDaemon(true);
            emailSendThread.setName(PwmConstants.PWM_APP_NAME + "-" + SERVICE_NAME);
            emailSendThread.start();
            threadActive = true;
        }


        status = PwmService.STATUS.OPEN;
        LOGGER.debug(SERVICE_NAME + " is now open");
    }

    public void close() {
        status = PwmService.STATUS.CLOSED;

        {
            final long startTime = System.currentTimeMillis();
            while (threadActive && (System.currentTimeMillis() - startTime) < 300) {
                Helper.pause(100);
            }
        }

        if (threadActive) {
            final long startTime = System.currentTimeMillis();
            LOGGER.info("waiting up to 30 seconds for email sender thread to close....");

            while (threadActive && (System.currentTimeMillis() - startTime) < 30 * 1000) {
                Helper.pause(100);
            }

            try {
                if (!sendQueue.isEmpty()) {
                    LOGGER.warn("closing queue with " + sendQueue.size() + " message in queue");
                }
            } catch (Exception e) {
                LOGGER.error("unexpected exception while shutting down: " + e.getMessage());
            }
        }

        LOGGER.debug("closed");
    }

    public List<HealthRecord> healthCheck() {
        if (lastSendFailure == null) {
            return null;
        }

        return Collections.singletonList(lastSendFailure);
    }

    abstract boolean sendItem(String item);


        // -------------------------- INNER CLASSES --------------------------

    class QueueThread extends Thread {
        public void run() {
            LOGGER.trace("starting up queue processing thread, queue size is " + sendQueue.size());

            while (status == PwmService.STATUS.OPEN) {
                boolean success = false;
                try {
                    success = processQueue();
                    if (success) {
                        lastSendFailure = null;
                    }
                } catch (Exception e) {
                    LOGGER.error("unexpected exception while processing queue: " + e.getMessage(), e);
                    LOGGER.error("unable to process queue successfully; sleeping for " + TimeDuration.asCompactString(ERROR_RETRY_WAIT_TIME_MS));
                }

                final long startTime = System.currentTimeMillis();
                final long sleepTime = success ? 1000 : ERROR_RETRY_WAIT_TIME_MS;
                while (PwmService.STATUS.OPEN == status && (System.currentTimeMillis() - startTime) < sleepTime) {
                    Helper.pause(100);
                }
            }

            // try to clear out the queue before the thread exits...
            try {
                processQueue();
            } catch (Exception e) {
                LOGGER.error("unexpected exception while processing queue: " + e.getMessage(), e);
            }

            threadActive = false;
            LOGGER.trace("closing queue processing thread");
        }

        private boolean processQueue() {
            while (sendQueue.peekFirst() != null) {
                final String jsonEvent = sendQueue.peekFirst();
                if (jsonEvent != null) {
                    final QueueEvent event = (new Gson()).fromJson(jsonEvent, QueueEvent.class);

                    if ((System.currentTimeMillis() - maxErrorWaitTimeMS) > event.getQueueInsertTimestamp()) {
                        LOGGER.debug("discarding event due to maximum retry age: " + event.getItem());
                        sendQueue.pollFirst();
                    } else {
                        final String item = event.getItem();

                        final boolean success = sendItem(item);
                        if (!success) {
                            return false;
                        }
                        sendQueue.pollFirst();
                    }
                }
            }
            return true;
        }
    }
}

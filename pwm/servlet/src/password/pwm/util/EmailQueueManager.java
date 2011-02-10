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

import com.google.gson.Gson;
import password.pwm.ContextManager;
import password.pwm.Helper;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.bean.EmailItemBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.util.db.PwmDB;
import password.pwm.util.db.PwmDBException;
import password.pwm.util.db.PwmDBStoredQueue;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Jason D. Rivard
 */
public class EmailQueueManager implements PwmService {
// ------------------------------ FIELDS ------------------------------

    private static final int ERROR_RETRY_WAIT_TIME = 30;

    private static final PwmLogger LOGGER = PwmLogger.getLogger(EmailQueueManager.class);

    private final PwmDBStoredQueue mailSendQueue;
    private final ContextManager theManager;

    private STATUS status = PwmService.STATUS.NEW;
    private boolean threadActive;
    private long maxErrorWaitTimeMS = 5 * 60 * 1000;

// --------------------------- CONSTRUCTORS ---------------------------

    public EmailQueueManager(final ContextManager theManager)
            throws PwmDBException {
        this.theManager = theManager;
        this.maxErrorWaitTimeMS = theManager.getConfig().readSettingAsInt(PwmSetting.EMAIL_MAX_QUEUE_AGE) * 1000;

        final PwmDB pwmDB = theManager.getPwmDB();
        mailSendQueue = PwmDBStoredQueue.createPwmDBStoredQueue(pwmDB, PwmDB.DB.EMAIL_QUEUE);

        {
            final EmailSendThread emailSendThread = new EmailSendThread();
            emailSendThread.setDaemon(true);
            emailSendThread.setName("pwm-EmailQueueManager");
            emailSendThread.start();
            threadActive = true;
        }

        status = PwmService.STATUS.OPEN;
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmService ---------------------

    public void init(final ContextManager contextManager) throws PwmException {
    }

    public STATUS status() {
        return status;
    }

    public void close() {
        status = PwmService.STATUS.CLOSED;

        if (threadActive) {
            final long startTime = System.currentTimeMillis();
            LOGGER.info("waiting up to 30 seconds for email thread to close....");

            while (threadActive && (System.currentTimeMillis() - startTime) < 30 * 1000) {
                Helper.pause(100);
            }

            try {
                if (!mailSendQueue.isEmpty()) {
                    LOGGER.warn("unable to close email queue, abandoning queue with " + mailSendQueue.size() + " messages");
                }
            } catch (Exception e) {
                LOGGER.error("unexpected exception while shutting down: " + e.getMessage());
            }
        }

        LOGGER.debug("closed");
    }

    public List<HealthRecord> doHealthCheck(ContextManager contextManager) {
        return null;
    }

// -------------------------- OTHER METHODS --------------------------

    public void addMailToQueue(final EmailItemBean emailItem)
            throws PwmException {
        if (status != PwmService.STATUS.OPEN) {
            throw PwmException.createPwmException(new ErrorInformation(PwmError.ERROR_CLOSING));
        }

        final String serverAddress = theManager.getConfig().readSettingAsString(PwmSetting.EMAIL_SERVER_ADDRESS);

        if (serverAddress == null || serverAddress.length() < 1) {
            LOGGER.debug("discarding email send event (no SMTP server address configured) " + emailItem.toString());
            return;
        }

        if (emailItem.getFrom() == null || emailItem.getFrom().length() < 1) {
            LOGGER.error("discarding email event (no from address): " + emailItem.toString());
            return;
        }

        if (emailItem.getTo() == null || emailItem.getTo().length() < 1) {
            LOGGER.error("discarding email event (no to address): " + emailItem.toString());
            return;
        }

        if (emailItem.getSubject() == null || emailItem.getSubject().length() < 1) {
            LOGGER.error("discarding email event (no subject): " + emailItem.toString());
            return;
        }

        if ((emailItem.getBodyPlain() == null || emailItem.getBodyPlain().length() < 1) && (emailItem.getBodyHtml() == null || emailItem.getBodyHtml().length() < 1)) {
            LOGGER.error("discarding email event (no body): " + emailItem.toString());
            return;
        }


        final EmailEvent event = new EmailEvent(emailItem, System.currentTimeMillis());
        if (mailSendQueue.size() < PwmConstants.MAX_EMAIL_QUEUE_SIZE) {
            try {
                final String jsonEvent = (new Gson()).toJson(event);
                mailSendQueue.addLast(jsonEvent);
            } catch (Exception e) {
                LOGGER.error("error writing to pwmDB queue, discarding email send request: " + e.getMessage());
            }
        } else {
            LOGGER.warn("email queue full, discarding email send request: " + emailItem);
        }
    }

    private boolean processQueue() {
        while (mailSendQueue.peekFirst() != null) {
            final String jsonEvent = mailSendQueue.peekFirst();
            if (jsonEvent != null) {
                final EmailEvent event = (new Gson()).fromJson(jsonEvent, EmailEvent.class);

                if ((System.currentTimeMillis() - maxErrorWaitTimeMS) > event.getQueueInsertTimestamp()) {
                    LOGGER.debug("discarding email event due to maximum retry age: " + event.getEmailItem().toString());
                    mailSendQueue.pollFirst();
                } else {
                    final boolean success = sendEmail(event.getEmailItem());
                    if (!success) {
                        return false;
                    }
                    mailSendQueue.pollFirst();
                }
            }
        }
        return true;
    }


    /**
     * Attempt to send an email based on the emailItemBean.  If the send successfully sends OR fails, it will return true, however
     * if the send fails due to an IO error or some other type of re-tryable error, this method returns false.
     *
     * @param emailItemBean
     * @return
     */
    private boolean sendEmail(final EmailItemBean emailItemBean) {
        final StatisticsManager statsMgr = theManager.getStatisticsManager();

        // createSharedHistoryManager a new MimeMessage object (using the Session created above)
        try {
            final Message message = convertEmailItemToMessage(emailItemBean, this.theManager.getConfig());

            Transport.send(message);
            LOGGER.debug("successfully sent email: " + emailItemBean.toString());
            statsMgr.incrementValue(Statistic.EMAIL_SEND_SUCCESSES);

            return true;
        } catch (MessagingException e) {
            statsMgr.incrementValue(Statistic.EMAIL_SEND_FAILURES);

            LOGGER.error("error during email send attempt: " + e);

            if (sendIsRetryable(e)) {
                LOGGER.error("error sending email (" + e.getMessage() + ") " + emailItemBean.toString() + " will retry in " + ERROR_RETRY_WAIT_TIME + " seconds.");
                return false;
            } else {
                LOGGER.error("error sending email (" + e.getMessage() + ") " + emailItemBean.toString() + ", permanent failure, discarding");
                return true;
            }
        }
    }

    private Message convertEmailItemToMessage(final EmailItemBean emailItemBean, final Configuration config)
            throws MessagingException {
        final boolean hasPlainText = emailItemBean.getBodyPlain() != null && emailItemBean.getBodyPlain().length() > 0;
        final boolean hasHtml = emailItemBean.getBodyHtml() != null && emailItemBean.getBodyHtml().length() > 0;

        //Create a properties item to start setting up the mail
        final Properties props = new Properties();

        // createSharedHistoryManager a new Session object for the message
        final javax.mail.Session session = javax.mail.Session.getInstance(props, null);

        //Specify the desired SMTP server
        props.put("mail.smtp.host", config.readSettingAsString(PwmSetting.EMAIL_SERVER_ADDRESS));

        //Specify configured advanced settings.
        final Map<String, String> advancedSettingValues = Configuration.convertStringListToNameValuePair(config.readStringArraySetting(PwmSetting.EMAIL_ADVANCED_SETTINGS), "=");
        for (final String key : advancedSettingValues.keySet()) {
            props.put(key, advancedSettingValues.get(key));
        }

        final Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(emailItemBean.getFrom()));
        message.setRecipients(Message.RecipientType.TO, new InternetAddress[]{new InternetAddress(emailItemBean.getTo())});
        message.setSubject(emailItemBean.getSubject());

        if (hasPlainText && hasHtml) {
            final MimeMultipart content = new MimeMultipart("alternative");
            final MimeBodyPart text = new MimeBodyPart();
            final MimeBodyPart html = new MimeBodyPart();
            text.setContent(emailItemBean.getBodyPlain(), "text/plain; charset=utf-8");
            html.setContent(emailItemBean.getBodyHtml(), "text/html");
            content.addBodyPart(text);
            content.addBodyPart(html);
            message.setContent(content);
        } else if (hasPlainText) {
            message.setContent(emailItemBean.getBodyPlain(), "text/plain; charset=utf-8");
        } else if (hasHtml) {
            message.setContent(emailItemBean.getBodyHtml(), "text/html");
        }

        return message;
    }

    private static boolean sendIsRetryable(final MessagingException e) {
        if (e != null) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                LOGGER.trace("email failure cause is due to an IOException: " + e.getMessage());
                return true;
            }
        }
        return false;
    }

// -------------------------- INNER CLASSES --------------------------

    private class EmailSendThread extends Thread {
        public void run() {
            LOGGER.trace("starting up email queue processing thread");

            if (mailSendQueue.size() > 0) {
                LOGGER.debug("email queue size: " + mailSendQueue.size());
            }

            try {
                final TimeDuration errorSleepTime = new TimeDuration(60 * 1000);
                long lastErrorTime;
                while (status == PwmService.STATUS.OPEN) {
                    final boolean success = processQueue();
                    if (success) {
                        lastErrorTime = 0;
                    } else {
                        LOGGER.error("unable to process email queue successfully; sleeping for " + errorSleepTime.asCompactString());
                        lastErrorTime = System.currentTimeMillis();
                    }
                    Helper.pause(1000);
                    while (lastErrorTime != 0 && status == PwmService.STATUS.OPEN && (System.currentTimeMillis() - lastErrorTime) < errorSleepTime.getTotalMilliseconds()) {
                        Helper.pause(1000);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("unexpected exception while processing mail queue: " + e.getMessage(), e);
            }
            threadActive = false;
            LOGGER.trace("closing email queue processing thread");
        }
    }

    private static class EmailEvent implements Serializable {
        private EmailItemBean emailItem;
        private long queueInsertTimestamp;

        private EmailEvent() {
        }

        private EmailEvent(EmailItemBean emailItem, long queueInsertTimestamp) {
            this.emailItem = emailItem;
            this.queueInsertTimestamp = queueInsertTimestamp;
        }

        public EmailItemBean getEmailItem() {
            return emailItem;
        }

        public long getQueueInsertTimestamp() {
            return queueInsertTimestamp;
        }
    }
}


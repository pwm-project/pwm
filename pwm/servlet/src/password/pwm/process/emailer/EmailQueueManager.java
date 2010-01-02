/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.process.emailer;

import password.pwm.Constants;
import password.pwm.ContextManager;
import password.pwm.config.PwmSetting;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class EmailQueueManager extends TimerTask {
// ------------------------------ FIELDS ------------------------------

    private static final int ERROR_RETRY_WAIT_TIME = 30;

    private static final PwmLogger LOGGER = PwmLogger.getLogger(EmailQueueManager.class);
    private final List<EmailEvent> queueList = Collections.synchronizedList(new LinkedList<EmailEvent>());

    private long lastErrorTime;

    private final ContextManager theManager;

// --------------------------- CONSTRUCTORS ---------------------------

    public EmailQueueManager(final ContextManager theManager)
    {
        this.theManager = theManager;
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Runnable ---------------------

    public void run()
    {
        this.processQueue();
    }

// -------------------------- OTHER METHODS --------------------------

    public void addMailToQueue(final EmailEvent emailEvent)
    {
        queueList.add(emailEvent);
    }

    public void addMailToQueue(final String to, final String from, final String subject, final String body)
    {
        final EmailEvent event = new EmailEvent(to, from, subject, body);
        if (queueList.size() < Constants.MAX_EMAIL_QUEUE_SIZE) {
            queueList.add(event);
        } else {
            LOGGER.warn("email queue full, discarding email send request: ");
        }
    }

    private synchronized void processQueue()
    {
        if (System.currentTimeMillis() - lastErrorTime > (ERROR_RETRY_WAIT_TIME * 1000)) {
            while (!queueList.isEmpty()) {
                final EmailEvent event = queueList.get(0);
                final boolean success = this.sendEmail(event);
                if (success) {
                    queueList.remove(0);
                } else {
                    break;
                }
            }
        }
    }

    private boolean sendEmail(final EmailEvent emailEvent)
    {
        final StatisticsManager statsMgr = theManager.getStatisticsManager();

        final String serverAddress = theManager.getConfig().readSettingAsString(PwmSetting.EMAIL_SERVER_ADDRESS);

        if (emailEvent.getFrom() == null || emailEvent.getFrom().length() < 1) {
            LOGGER.debug("discarding email event (no from address): " + emailEvent.toString());
            return true;
        }

        if (emailEvent.getTo() == null || emailEvent.getTo().length() < 1) {
            LOGGER.debug("discarding email event (no to address): " + emailEvent.toString());
            return true;
        }

        if (emailEvent.getSubject() == null || emailEvent.getSubject().length() < 1) {
            LOGGER.debug("discarding email event (no subject): " + emailEvent.toString());
            return true;
        }

        if (serverAddress == null || serverAddress.length() < 1) {
            LOGGER.debug("discarding email send event (no SMTP server address configured) " + emailEvent.toString());
            return true;
        }

        //Create a propertieds item to start setting up the mail
        final Properties props = new Properties();

        // createSharedHistoryManager a new Session object for the message
        final javax.mail.Session session = javax.mail.Session.getInstance(props, null);

        //Specify the desired SMTP server
        props.put("mail.smtp.host", serverAddress);

        // createSharedHistoryManager a new MimeMessage object (using the Session created above)
        try {
            final Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(emailEvent.getFrom()));
            message.setRecipients(Message.RecipientType.TO, new InternetAddress[]{new InternetAddress(emailEvent.getTo())});
            message.setSubject(emailEvent.getSubject());
            message.setContent(emailEvent.getBody(), "text/plain; charset=utf-8");

            Transport.send(message);
            LOGGER.debug("successfully sent email: " + emailEvent.toString());
            statsMgr.incrementValue(Statistic.EMAIL_SEND_SUCCESSES);

            return true;
        } catch (MessagingException e) {
            statsMgr.incrementValue(Statistic.EMAIL_SEND_FAILURES);
            this.lastErrorTime = System.currentTimeMillis();

            LOGGER.error("error during email send attempt: " + e); //todo explain _which_ email failed.

            if (sendIsRetryable(e)) {
                LOGGER.warn("error sending email (" + e.getMessage() + ") " + emailEvent.toString() + " will retry in " + ERROR_RETRY_WAIT_TIME + " seconds.");
                return false;
            } else {
                LOGGER.warn("error sending email (" + e.getMessage() + ") " + emailEvent.toString() + ", permanant failure, discarding");
                return true;
            }
        }
    }

    private static boolean sendIsRetryable(final MessagingException e)
    {
        return false;
    }
}


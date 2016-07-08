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

package password.pwm.util.queue;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.*;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class EmailQueueManager implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(EmailQueueManager.class);

    private PwmApplication pwmApplication;
    private Properties javaMailProps = new Properties();
    private WorkQueueProcessor<EmailItemBean> workQueueProcessor;

    private PwmService.STATUS status = STATUS.NEW;
    private ErrorInformation lastError;

    public void init(final PwmApplication pwmApplication)
            throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;
        javaMailProps = makeJavaMailProps(pwmApplication.getConfig());
        final WorkQueueProcessor.Settings settings = new WorkQueueProcessor.Settings();
        settings.setMaxEvents(Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_EMAIL_MAX_COUNT)));
        settings.setRetryDiscardAge(new TimeDuration(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_EMAIL_MAX_AGE_MS))));
        settings.setRetryInterval(new TimeDuration(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_EMAIL_RETRY_TIMEOUT_MS))));
        final LocalDBStoredQueue localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue(pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.EMAIL_QUEUE);

        workQueueProcessor = new WorkQueueProcessor<>(pwmApplication, localDBStoredQueue, settings, new EmailItemProcessor(), this.getClass());
        status = STATUS.OPEN;
    }

    public void close() {
        status = STATUS.CLOSED;
        workQueueProcessor.close();
    }

    @Override
    public STATUS status() {
        return status;
    }

    public List<HealthRecord> healthCheck() {
        if (pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN) {
            return Collections.singletonList(HealthRecord.forMessage(HealthMessage.ServiceClosed_LocalDBUnavail, this.getClass().getSimpleName()));
        }

        if (pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY) {
            return Collections.singletonList(HealthRecord.forMessage(HealthMessage.ServiceClosed_AppReadOnly, this.getClass().getSimpleName()));
        }

        if (lastError != null) {
            return Collections.singletonList(HealthRecord.forMessage(HealthMessage.Email_SendFailure, lastError.toDebugStr()));
        }

        return Collections.emptyList();
    }

    @Override
    public ServiceInfo serviceInfo() {
        if (status() == STATUS.OPEN) {
            return new ServiceInfo(Collections.singletonList(DataStorageMethod.LOCALDB));
        } else {
            return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
        }
    }

    public int queueSize() {
        return workQueueProcessor.queueSize();
    }

    public Date eldestItem() {
        return workQueueProcessor.eldestItem();
    }

    private class EmailItemProcessor implements  WorkQueueProcessor.ItemProcessor<EmailItemBean>  {
        @Override
        public WorkQueueProcessor.ProcessResult process(EmailItemBean workItem) {
                return sendItem(workItem);
        }

        public String convertToDebugString(EmailItemBean emailItemBean) {
            return emailItemToDebugString(emailItemBean);
        }
    }

    private static String emailItemToDebugString(EmailItemBean emailItemBean) {
        final Map<String,Object> debugOutputMap = new LinkedHashMap<>();
        debugOutputMap.put("to", emailItemBean.getTo());
        debugOutputMap.put("from", emailItemBean.getFrom());
        debugOutputMap.put("subject", emailItemBean.getSubject());
        return JsonUtil.serializeMap(debugOutputMap);
    }

    private boolean determineIfItemCanBeDelivered(final EmailItemBean emailItem) {
        final String serverAddress = pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_SERVER_ADDRESS);

        if (serverAddress == null || serverAddress.length() < 1) {
            LOGGER.debug("discarding email send event (no SMTP server address configured) " + emailItem.toString());
            return false;
        }

        if (emailItem.getFrom() == null || emailItem.getFrom().length() < 1) {
            LOGGER.error("discarding email event (no from address): " + emailItem.toString());
            return false;
        }

        if (emailItem.getTo() == null || emailItem.getTo().length() < 1) {
            LOGGER.error("discarding email event (no to address): " + emailItem.toString());
            return false;
        }

        if (emailItem.getSubject() == null || emailItem.getSubject().length() < 1) {
            LOGGER.error("discarding email event (no subject): " + emailItem.toString());
            return false;
        }

        if ((emailItem.getBodyPlain() == null || emailItem.getBodyPlain().length() < 1) && (emailItem.getBodyHtml() == null || emailItem.getBodyHtml().length() < 1)) {
            LOGGER.error("discarding email event (no body): " + emailItem.toString());
            return false;
        }

        return true;
    }

    public void submitEmail(
            final EmailItemBean emailItem,
            final UserInfoBean uiBean,
            final MacroMachine macroMachine
    )
    {
        if (emailItem == null) {
            return;
        }

        EmailItemBean workingItemBean = emailItem;

        if ((emailItem.getTo() == null || emailItem.getTo().isEmpty()) && uiBean != null) {
            final String toAddress = uiBean.getUserEmailAddress();
            workingItemBean = newEmailToAddress(workingItemBean, toAddress);
        }

        if (macroMachine != null) {
            workingItemBean = applyMacrosToEmail(workingItemBean, macroMachine);
        }

        if (workingItemBean.getTo() == null || workingItemBean.getTo().length() < 1) {
            LOGGER.error("no destination address available for email, skipping; email: " + emailItem.toString());
        }

        if (!determineIfItemCanBeDelivered(emailItem)) {
            return;
        }

        try {
            workQueueProcessor.submit(workingItemBean);
        } catch (PwmOperationalException e) {
            LOGGER.warn("unable to add email to queue: " + e.getMessage());
        }
    }

    private WorkQueueProcessor.ProcessResult sendItem(final EmailItemBean emailItemBean) {

        // create a new MimeMessage object (using the Session created above)
        try {
            final List<Message> messages = convertEmailItemToMessages(emailItemBean, this.pwmApplication.getConfig());
            final String mailUser = this.pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_USERNAME);
            final PasswordData mailPassword = this.pwmApplication.getConfig().readSettingAsPassword(PwmSetting.EMAIL_PASSWORD);

            // Login to SMTP server first if both username and password is given
            final String logText;
            if (mailUser == null || mailUser.length() < 1 || mailPassword == null) {

                logText = "plaintext";

                for (Message message : messages) {
                    Transport.send(message);
                }
            } else {
                // create a new Session object for the message
                final javax.mail.Session session = javax.mail.Session.getInstance(javaMailProps, null);

                final String mailhost = this.pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_SERVER_ADDRESS);
                final int mailport = (int)this.pwmApplication.getConfig().readSettingAsLong(PwmSetting.EMAIL_SERVER_PORT);

                final Transport tr = session.getTransport("smtp");
                tr.connect(mailhost, mailport, mailUser, mailPassword.getStringValue());

                for (Message message : messages) {
                    message.saveChanges();
                    tr.sendMessage(message, message.getAllRecipients());
                }

                tr.close();
                logText = "authenticated ";
                lastError = null;
            }

            LOGGER.debug("successfully sent " + logText + "email: " + emailItemBean.toString());
            StatisticsManager.incrementStat(pwmApplication, Statistic.EMAIL_SEND_SUCCESSES);
            return WorkQueueProcessor.ProcessResult.SUCCESS;
        } catch (Exception e) {

            final ErrorInformation errorInformation;
            if (e instanceof PwmException) {
                errorInformation = ((PwmException) e).getErrorInformation();
            } else {
                final String errorMsg = "error sending email: " + e.getMessage();
                errorInformation = new ErrorInformation(
                        PwmError.ERROR_EMAIL_SEND_FAILURE,
                        errorMsg,
                        new String[]{ emailItemToDebugString(emailItemBean), Helper.readHostileExceptionMessage(e)}
                );
            }
            LOGGER.error(errorInformation);

            if (sendIsRetryable(e)) {
                LOGGER.error("error sending email (" + e.getMessage() + ") " + emailItemBean.toString() + ", will retry");
                StatisticsManager.incrementStat(pwmApplication, Statistic.EMAIL_SEND_FAILURES);
                return WorkQueueProcessor.ProcessResult.RETRY;
            } else {
                LOGGER.error(
                        "error sending email (" + e.getMessage() + ") " + emailItemBean.toString() + ", permanent failure, discarding message");
                StatisticsManager.incrementStat(pwmApplication, Statistic.EMAIL_SEND_DISCARDS);
                return WorkQueueProcessor.ProcessResult.FAILED;
            }
        }
    }

    public List<Message> convertEmailItemToMessages(final EmailItemBean emailItemBean, final Configuration config)
            throws MessagingException
    {
        final List<Message> messages = new ArrayList<>();
        final boolean hasPlainText = emailItemBean.getBodyPlain() != null && emailItemBean.getBodyPlain().length() > 0;
        final boolean hasHtml = emailItemBean.getBodyHtml() != null && emailItemBean.getBodyHtml().length() > 0;
        final String subjectEncodingCharset = config.readAppProperty(AppProperty.SMTP_SUBJECT_ENCODING_CHARSET);

        // create a new Session object for the message
        final javax.mail.Session session = javax.mail.Session.getInstance(javaMailProps, null);

        String emailTo = emailItemBean.getTo();
        if (emailTo != null) {
            InternetAddress[] recipients = InternetAddress.parse(emailTo);
            for (InternetAddress recipient : recipients) {
                final MimeMessage message = new MimeMessage(session);
                message.setFrom();
                message.setFrom(makeInternetAddress(emailItemBean.getFrom()));
                message.setRecipient(Message.RecipientType.TO, recipient);
                {
                    if (subjectEncodingCharset != null && !subjectEncodingCharset.isEmpty()) {
                        message.setSubject(emailItemBean.getSubject(), subjectEncodingCharset);
                    } else {
                        message.setSubject(emailItemBean.getSubject());
                    }
                }
                message.setSentDate(new Date());

                if (hasPlainText && hasHtml) {
                    final MimeMultipart content = new MimeMultipart("alternative");
                    final MimeBodyPart text = new MimeBodyPart();
                    final MimeBodyPart html = new MimeBodyPart();
                    text.setContent(emailItemBean.getBodyPlain(), PwmConstants.ContentTypeValue.plain.getHeaderValue());
                    html.setContent(emailItemBean.getBodyHtml(), PwmConstants.ContentTypeValue.html.getHeaderValue());
                    content.addBodyPart(text);
                    content.addBodyPart(html);
                    message.setContent(content);
                } else if (hasPlainText) {
                    message.setContent(emailItemBean.getBodyPlain(), PwmConstants.ContentTypeValue.plain.getHeaderValue());
                } else if (hasHtml) {
                    message.setContent(emailItemBean.getBodyHtml(), PwmConstants.ContentTypeValue.html.getHeaderValue());
                }

                messages.add(message);
            }
        }

        return messages;
    }

    private static Properties makeJavaMailProps(final Configuration config) {
        //Create a properties item to start setting up the mail
        final Properties props = new Properties();

        //Specify the desired SMTP server
        props.put("mail.smtp.host", config.readSettingAsString(PwmSetting.EMAIL_SERVER_ADDRESS));

        //Specify SMTP server port
        props.put("mail.smtp.port",(int)config.readSettingAsLong(PwmSetting.EMAIL_SERVER_PORT));

        //Specify configured advanced settings.
        final Map<String, String> advancedSettingValues = StringUtil.convertStringListToNameValuePair(config.readSettingAsStringArray(PwmSetting.EMAIL_ADVANCED_SETTINGS), "=");
        for (final String key : advancedSettingValues.keySet()) {
            props.put(key, advancedSettingValues.get(key));
        }

        return props;
    }

    private static InternetAddress makeInternetAddress(final String input)
            throws AddressException
    {
        if (input == null) {
            return null;
        }

        if (input.matches("^.*<.*>$")) { // check for format like: John Doe <jdoe@example.com>
            final String[] splitString = input.split("<|>");
            if (splitString.length < 2) {
                return new InternetAddress(input);
            }

            final InternetAddress address = new InternetAddress();
            address.setAddress(splitString[1].trim());
            try {
                address.setPersonal(splitString[0].trim(), PwmConstants.DEFAULT_CHARSET.toString());
            } catch (UnsupportedEncodingException e) {
                LOGGER.error("unsupported encoding error while parsing internet address '" + input + "', error: " + e.getMessage());
            }
            return address;
        }
        return new InternetAddress(input);
    }

    private static EmailItemBean applyMacrosToEmail(final EmailItemBean emailItem, final MacroMachine macroMachine) {
        final EmailItemBean expandedEmailItem;
        expandedEmailItem = new EmailItemBean(
                macroMachine.expandMacros(emailItem.getTo()),
                macroMachine.expandMacros(emailItem.getFrom()),
                macroMachine.expandMacros(emailItem.getSubject()),
                macroMachine.expandMacros(emailItem.getBodyPlain()),
                macroMachine.expandMacros(emailItem.getBodyHtml())
        );
        return expandedEmailItem;
    }

    private static EmailItemBean newEmailToAddress(final EmailItemBean emailItem, final String toAddress) {
        final EmailItemBean expandedEmailItem;
        expandedEmailItem = new EmailItemBean(
                toAddress,
                emailItem.getFrom(),
                emailItem.getSubject(),
                emailItem.getBodyPlain(),
                emailItem.getBodyHtml()
        );
        return expandedEmailItem;
    }

    private static boolean sendIsRetryable(final Exception e) {
        if (e != null) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                LOGGER.trace("message send failure cause is due to an IOException: " + e.getMessage());
                return true;
            }
        }
        return false;
    }

}


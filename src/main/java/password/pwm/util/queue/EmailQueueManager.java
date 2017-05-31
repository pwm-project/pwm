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
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.localdb.WorkQueueProcessor;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final ThreadLocal<Transport> threadLocalTransport = new ThreadLocal<>();

    public void init(final PwmApplication pwmApplication)
            throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;
        javaMailProps = makeJavaMailProps(pwmApplication.getConfig());

        if (pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN) {
            LOGGER.warn("localdb is not open, EmailQueueManager will remain closed");
            status = STATUS.CLOSED;
            return;
        }

        final WorkQueueProcessor.Settings settings = WorkQueueProcessor.Settings.builder()
                .maxEvents(Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_EMAIL_MAX_COUNT)))
                .retryDiscardAge(new TimeDuration(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_EMAIL_MAX_AGE_MS))))
                .retryInterval(new TimeDuration(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_EMAIL_RETRY_TIMEOUT_MS))))
                .preThreads(Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_EMAIL_MAX_THREADS)))
                .build();
        final LocalDBStoredQueue localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue(pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.EMAIL_QUEUE);

        workQueueProcessor = new WorkQueueProcessor<>(pwmApplication, localDBStoredQueue, settings, new EmailItemProcessor(), this.getClass());
        status = STATUS.OPEN;
    }

    public void close() {
        status = STATUS.CLOSED;
        if (workQueueProcessor != null) {
            workQueueProcessor.close();
        }
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
            return new ServiceInfo(Collections.emptyList());
        }
    }

    public int queueSize() {
        return workQueueProcessor == null
                ? 0
                : workQueueProcessor.queueSize();
    }

    public Instant eldestItem() {
        return workQueueProcessor == null
                ? null
                : workQueueProcessor.eldestItem();
    }

    private class EmailItemProcessor implements WorkQueueProcessor.ItemProcessor<EmailItemBean>  {
        @Override
        public WorkQueueProcessor.ProcessResult process(final EmailItemBean workItem) {
            return sendItem(workItem);
        }

        public String convertToDebugString(final EmailItemBean emailItemBean) {
            return emailItemBean.toDebugString();
        }
    }

    private boolean determineIfItemCanBeDelivered(final EmailItemBean emailItem) {
        final String serverAddress = pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_SERVER_ADDRESS);

        if (serverAddress == null || serverAddress.length() < 1) {
            LOGGER.debug("discarding email send event (no SMTP server address configured) " + emailItem.toDebugString());
            return false;
        }

        if (emailItem.getFrom() == null || emailItem.getFrom().length() < 1) {
            LOGGER.error("discarding email event (no from address): " + emailItem.toDebugString());
            return false;
        }

        if (emailItem.getTo() == null || emailItem.getTo().length() < 1) {
            LOGGER.error("discarding email event (no to address): " + emailItem.toDebugString());
            return false;
        }

        if (emailItem.getSubject() == null || emailItem.getSubject().length() < 1) {
            LOGGER.error("discarding email event (no subject): " + emailItem.toDebugString());
            return false;
        }

        if ((emailItem.getBodyPlain() == null || emailItem.getBodyPlain().length() < 1) && (emailItem.getBodyHtml() == null || emailItem.getBodyHtml().length() < 1)) {
            LOGGER.error("discarding email event (no body): " + emailItem.toDebugString());
            return false;
        }

        return true;
    }

    public void submitEmail(
            final EmailItemBean emailItem,
            final UserInfo userInfo,
            final MacroMachine macroMachine
    )
            throws PwmUnrecoverableException
    {
        submitEmailImpl(emailItem, userInfo, macroMachine, false);
    }

    public void submitEmailImmediate(
            final EmailItemBean emailItem,
            final UserInfo userInfo,
            final MacroMachine macroMachine
    )
            throws PwmUnrecoverableException
    {
        submitEmailImpl(emailItem, userInfo, macroMachine, true);
    }

    private void submitEmailImpl(
            final EmailItemBean emailItem,
            final UserInfo userInfo,
            final MacroMachine macroMachine,
            final boolean immediate
    )
            throws PwmUnrecoverableException
    {
        if (emailItem == null) {
            return;
        }

        final EmailItemBean finalBean;
        {
            EmailItemBean workingItemBean = emailItem;
            if ((emailItem.getTo() == null || emailItem.getTo().isEmpty()) && userInfo != null) {
                final String toAddress = userInfo.getUserEmailAddress();
                workingItemBean = newEmailToAddress(workingItemBean, toAddress);
            }

            if (macroMachine != null) {
                workingItemBean = applyMacrosToEmail(workingItemBean, macroMachine);
            }

            if (workingItemBean.getTo() == null || workingItemBean.getTo().length() < 1) {
                LOGGER.error("no destination address available for email, skipping; email: " + emailItem.toDebugString());
            }

            if (!determineIfItemCanBeDelivered(emailItem)) {
                return;
            }
            finalBean = workingItemBean;
        }

        try {
            if (immediate) {
                workQueueProcessor.submitImmediate(finalBean);
            } else {
                workQueueProcessor.submit(finalBean);
            }
        } catch (PwmOperationalException e) {
            LOGGER.warn("unable to add email to queue: " + e.getMessage());
        }
    }

    private final AtomicInteger newThreadLocalTransport = new AtomicInteger();
    private final AtomicInteger useExistingConnection = new AtomicInteger();
    private final AtomicInteger useExistingTransport = new AtomicInteger();
    private final AtomicInteger newConnectionCounter = new AtomicInteger();

    private String stats() {
        final Map<String,Integer> map = new HashMap<>();
        map.put("newThreadLocalTransport", newThreadLocalTransport.get());
        map.put("useExistingConnection", newThreadLocalTransport.get());
        map.put("useExistingTransport", useExistingTransport.get());
        map.put("newConnectionCounter", newConnectionCounter.get());
        return StringUtil.mapToString(map);
    }

    private WorkQueueProcessor.ProcessResult sendItem(final EmailItemBean emailItemBean) {

        // create a new MimeMessage object (using the Session created above)
        try {
            if (threadLocalTransport.get() == null) {
                LOGGER.trace("initializing new threadLocal transport, stats: " + stats());
                threadLocalTransport.set(getSmtpTransport());
                newThreadLocalTransport.getAndIncrement();
            } else {
                LOGGER.trace("using existing threadLocal transport, stats: " + stats());
                useExistingTransport.getAndIncrement();
            }
            final Transport transport = threadLocalTransport.get();
            if (!transport.isConnected()) {
                LOGGER.trace("connecting threadLocal transport, stats: " + stats());
                transport.connect();
                newConnectionCounter.getAndIncrement();
            } else {
                LOGGER.trace("using existing threadLocal: stats: " + stats());
                useExistingConnection.getAndIncrement();
            }

            final List<Message> messages = convertEmailItemToMessages(emailItemBean, this.pwmApplication.getConfig());

            for (final Message message : messages) {
                message.saveChanges();
                transport.sendMessage(message, message.getAllRecipients());
            }

            lastError = null;

            LOGGER.debug("sent email: " + emailItemBean.toDebugString());
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
                        new String[]{ emailItemBean.toDebugString(), JavaHelper.readHostileExceptionMessage(e)}
                );
            }

            lastError = errorInformation;
            LOGGER.error(errorInformation);

            if (sendIsRetryable(e)) {
                LOGGER.error("error sending email (" + e.getMessage() + ") " + emailItemBean.toDebugString() + ", will retry");
                StatisticsManager.incrementStat(pwmApplication, Statistic.EMAIL_SEND_FAILURES);
                return WorkQueueProcessor.ProcessResult.RETRY;
            } else {
                LOGGER.error(
                        "error sending email (" + e.getMessage() + ") " + emailItemBean.toDebugString() + ", permanent failure, discarding message");
                StatisticsManager.incrementStat(pwmApplication, Statistic.EMAIL_SEND_DISCARDS);
                return WorkQueueProcessor.ProcessResult.FAILED;
            }
        }
    }

    private Transport getSmtpTransport()
            throws MessagingException, PwmUnrecoverableException
    {
        final String mailUser = this.pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_USERNAME);
        final PasswordData mailPassword = this.pwmApplication.getConfig().readSettingAsPassword(PwmSetting.EMAIL_PASSWORD);
        final String mailhost = this.pwmApplication.getConfig().readSettingAsString(PwmSetting.EMAIL_SERVER_ADDRESS);
        final int mailport = (int)this.pwmApplication.getConfig().readSettingAsLong(PwmSetting.EMAIL_SERVER_PORT);

        // Login to SMTP server first if both username and password is given
        final javax.mail.Session session = javax.mail.Session.getInstance(javaMailProps, null);
        final Transport tr = session.getTransport("smtp");

        final boolean authenticated = !(mailUser == null || mailUser.length() < 1 || mailPassword == null);

        if (authenticated) {
            // create a new Session object for the message
            tr.connect(mailhost, mailport, mailUser, mailPassword.getStringValue());
        } else {
            tr.connect();
        }

        LOGGER.debug("connected to " + mailhost + ":" + mailport + " " + (authenticated ? "(secure)" : "(plaintext)"));
        return tr;
    }

    public List<Message> convertEmailItemToMessages(final EmailItemBean emailItemBean, final Configuration config)
            throws MessagingException
    {
        final List<Message> messages = new ArrayList<>();
        final boolean hasPlainText = emailItemBean.getBodyPlain() != null && emailItemBean.getBodyPlain().length() > 0;
        final boolean hasHtml = emailItemBean.getBodyHtml() != null && emailItemBean.getBodyHtml().length() > 0;
        final String subjectEncodingCharset = config.readAppProperty(AppProperty.SMTP_SUBJECT_ENCODING_CHARSET);

        // create a new Session object for the messagejavamail
        final javax.mail.Session session = javax.mail.Session.getInstance(javaMailProps, null);

        final String emailTo = emailItemBean.getTo();
        if (emailTo != null) {
            final InternetAddress[] recipients = InternetAddress.parse(emailTo);
            for (final InternetAddress recipient : recipients) {
                final MimeMessage message = new MimeMessage(session);
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


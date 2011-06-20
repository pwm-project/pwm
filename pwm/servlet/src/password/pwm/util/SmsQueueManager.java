/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.ContextManager;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.bean.SmsItemBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBException;
import password.pwm.util.pwmdb.PwmDBStoredQueue;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class SmsQueueManager implements PwmService {
// ------------------------------ FIELDS ------------------------------

    private static final int ERROR_RETRY_WAIT_TIME_MS = 60 * 1000;

    private static final PwmLogger LOGGER = PwmLogger.getLogger(SmsQueueManager.class);

    private PwmDBStoredQueue smsSendQueue;
    private final ContextManager theManager;

    private STATUS status = PwmService.STATUS.NEW;
    private volatile boolean threadActive;
    private long maxErrorWaitTimeMS = 5 * 60 * 1000;

    private HealthRecord lastSendFailure;

    public enum SmsNumberFormat {
        PLAIN,
        PLUS,
        ZEROS
    }

    public enum SmsDataEncoding {
        NONE,
        URL,
        XML,
        HTML,
        CSV,
        JAVA,
        JAVASCRIPT,
        SQL
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public SmsQueueManager(final ContextManager theManager)
            throws PwmDBException {
        this.theManager = theManager;
        this.maxErrorWaitTimeMS = theManager.getConfig().readSettingAsLong(PwmSetting.SMS_MAX_QUEUE_AGE) * 1000;

        final PwmDB pwmDB = theManager.getPwmDB();

        if (pwmDB == null) {
            status = STATUS.CLOSED;
            return;
        }

        smsSendQueue = PwmDBStoredQueue.createPwmDBStoredQueue(pwmDB, PwmDB.DB.SMS_QUEUE);

        status = PwmService.STATUS.OPEN;

        {
            final SmsSendThread smsSendThread = new SmsSendThread();
            smsSendThread.setDaemon(true);
            smsSendThread.setName("pwm-SmsQueueManager");
            smsSendThread.start();
            threadActive = true;
        }
    }
// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface PwmService ---------------------

    public void init(final ContextManager contextManager) throws PwmUnrecoverableException {
    }

    public STATUS status() {
        return status;
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
            LOGGER.info("waiting up to 30 seconds for sms sender thread to close....");

            while (threadActive && (System.currentTimeMillis() - startTime) < 30 * 1000) {
                Helper.pause(100);
            }

            try {
                if (!smsSendQueue.isEmpty()) {
                    LOGGER.warn("closing sms queue with " + smsSendQueue.size() + " message(s) in queue");
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

// -------------------------- OTHER METHODS --------------------------

    public void addSmsToQueue(final SmsItemBean smsItem) throws PwmUnrecoverableException {
        if (status != PwmService.STATUS.OPEN) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CLOSING));
        }

        if (!determineIfSmsCanBeDelivered(smsItem)) {
            return;
        }

        final SmsEvent event = new SmsEvent(smsItem, System.currentTimeMillis());
        if (smsSendQueue.size() < PwmConstants.MAX_SMS_QUEUE_SIZE) {
            try {
                final String jsonEvent = (new Gson()).toJson(event);
                smsSendQueue.addLast(jsonEvent);
            } catch (Exception e) {
                LOGGER.error("error writing to pwmDB queue, discarding sms send request: " + e.getMessage());
            }
        } else {
            LOGGER.warn("sms queue full, discarding sms send request: " + smsItem);
        }
    }

    private boolean determineIfSmsCanBeDelivered(final SmsItemBean smsItem) {
        final Configuration config = theManager.getConfig();
        final String gatewayUrl = config.readSettingAsString(PwmSetting.SMS_GATEWAY_URL);
        final String gatewayUser = config.readSettingAsString(PwmSetting.SMS_GATEWAY_USER);
        final String gatewayPass = config.readSettingAsString(PwmSetting.SMS_GATEWAY_PASSWORD);

        if (gatewayUrl == null || gatewayUrl.length() < 1) {
            LOGGER.debug("discarding sms send event (no SMS gateway url configured) " + smsItem.toString());
            return false;
        }

        if (gatewayUser == null || gatewayUser.length() < 1) {
            LOGGER.debug("discarding sms send event (no SMS gateway user configured) " + smsItem.toString());
            return false;
        }

        if (gatewayPass == null || gatewayPass.length() < 1) {
            LOGGER.debug("discarding sms send event (no SMS gateway password configured) " + smsItem.toString());
            return false;
        }

        if (smsItem.getTo() == null || smsItem.getTo().length() < 1) {
            LOGGER.debug("discarding sms send event (no to address) " + smsItem.toString());
            return false;
        }

        if (smsItem.getMessage() == null || smsItem.getMessage().length() < 1) {
            LOGGER.debug("discarding sms send event (no message) " + smsItem.toString());
            return false;
        }

        return true;
    }

    public int queueSize() {
        if (smsSendQueue == null || status != STATUS.OPEN) {
            return 0;
        }

        return this.smsSendQueue.size();
    }

    private boolean processQueue() {
        while (smsSendQueue.peekFirst() != null) {
            final String jsonEvent = smsSendQueue.peekFirst();
            if (jsonEvent != null) {
                final SmsEvent event = (new Gson()).fromJson(jsonEvent, SmsEvent.class);

                if ((System.currentTimeMillis() - maxErrorWaitTimeMS) > event.getQueueInsertTimestamp()) {
                    LOGGER.debug("discarding sms event due to maximum retry age: " + event.getSmsItem().toString());
                    smsSendQueue.pollFirst();
                } else {
                    final SmsItemBean smsItem = event.getSmsItem();
                    if (!determineIfSmsCanBeDelivered(smsItem)) {
                        smsSendQueue.pollFirst();
                        return true;
                    }

                    final boolean success = sendSms(smsItem);
                    if (!success) {
                        return false;
                    }
                    smsSendQueue.pollFirst();
                }
            }
        }
        return true;
    }

    private boolean sendSms(final SmsItemBean smsItemBean) {
        boolean success = true;
        while (success && smsItemBean.hasNextPart()) {
            success = sendSmsPart(smsItemBean);
        }
        return success;
    }

    private boolean sendSmsPart(final SmsItemBean smsItemBean) {
        final Configuration config = theManager.getConfig();

        final String gatewayUser = config.readSettingAsString(PwmSetting.SMS_GATEWAY_USER);
        final String gatewayPass = config.readSettingAsString(PwmSetting.SMS_GATEWAY_PASSWORD);

        final String contentType = config.readSettingAsString(PwmSetting.SMS_REQUEST_CONTENT_TYPE);
        final SmsDataEncoding encoding = SmsDataEncoding.valueOf(config.readSettingAsString(PwmSetting.SMS_REQUEST_CONTENT_ENCODING));
        
        final List<String> extraHeaders = config.readSettingAsStringArray(PwmSetting.SMS_GATEWAY_REQUEST_HEADERS);

        String requestData = config.readSettingAsLocalizedString(PwmSetting.SMS_REQUEST_DATA, smsItemBean.getLocale());

        // Replace strings in requestData
        {
            final String senderId = smsItemBean.getFrom() == null ? "" : smsItemBean.getFrom();
            requestData = requestData.replaceAll("%USER%", smsDataEncode(gatewayUser, encoding));
            requestData = requestData.replaceAll("%PASS%", smsDataEncode(gatewayPass, encoding));
            requestData = requestData.replaceAll("%SENDERID%", smsDataEncode(senderId, encoding));
            requestData = requestData.replaceAll("%MESSAGE%", smsDataEncode(smsItemBean.getNextPart(), encoding));
            requestData = requestData.replaceAll("%TO%", smsDataEncode(formatSmsNumber(smsItemBean.getTo()), encoding));
        }

        if (requestData.indexOf("%REQUESTID%")>=0) {
            final String chars = config.readSettingAsString(PwmSetting.SMS_REQUESTID_CHARS);
            final int idLength = new Long(config.readSettingAsLong(PwmSetting.SMS_REQUESTID_LENGTH)).intValue();
            final String requestId = PwmRandom.getInstance().alphaNumericString(chars, idLength);
            requestData = requestData.replaceAll("%REQUESTID%", smsDataEncode(requestId, encoding));
        }

        final String gatewayUrl = config.readSettingAsString(PwmSetting.SMS_GATEWAY_URL);
        final String gatewayMethod = config.readSettingAsString(PwmSetting.SMS_GATEWAY_METHOD);
        final String gatewayAuthMethod = config.readSettingAsString(PwmSetting.SMS_GATEWAY_AUTHMETHOD);
        LOGGER.trace("SMS data: " + requestData);
        try {
            final HttpPost httpRequest;
            if (gatewayMethod.equalsIgnoreCase("POST")) {
                // POST request
                httpRequest = new HttpPost(gatewayUrl);
                if (contentType != null && contentType.length()>0) {
                    httpRequest.setHeader("Content-Type", contentType);
                }
                httpRequest.setEntity(new StringEntity(requestData));
            } else {
                // GET request
                final String fullUrl = gatewayUrl.endsWith("?") ? gatewayUrl + requestData : gatewayUrl + "?" + requestData;
                httpRequest = new HttpPost(fullUrl);
            }

            if (extraHeaders != null) {
               	final Pattern pattern = Pattern.compile("^([A-Za-z0-9_\\.-]+):[ \t]*([^ \t].*)");
                for (final String header : extraHeaders) {
                    final Matcher matcher = pattern.matcher(header);
                    if (matcher.matches()) {
                    	final String hname = matcher.group(1);
                    	final String hvalue = matcher.group(2);
                    	LOGGER.debug("Adding HTTP header \"" + hname + "\" with value \"" + hvalue + "\"");
                    	httpRequest.addHeader(hname, hvalue);
                    } else {
                    	LOGGER.warn("Cannot parse HTTP header: " + header);
                    }
                }
            }

            if ("BASIC".equalsIgnoreCase(gatewayAuthMethod) && gatewayUser != null && gatewayPass != null) {
                httpRequest.getParams().setParameter("Authorization", new BasicAuthInfo(gatewayUser, gatewayPass).toAuthHeader());
            }

            final HttpClient httpClient = Helper.getHttpClient(config);
            final HttpResponse httpResponse = httpClient.execute(httpRequest);
            final String responseBody = EntityUtils.toString(httpResponse.getEntity());

            final List<String> okMessages = config.readSettingAsStringArray(PwmSetting.SMS_RESPONSE_OK_REGEX);
            if (matchExpressions(responseBody, okMessages)) {
                return true;
            }
        } catch (IOException e) {
            LOGGER.error("unexpected exception while sending SMS: " + e.getMessage(), e);
        }

        return false;
    }

    private String smsDataEncode(final String data, final SmsDataEncoding encoding) {
        String returnData;
        switch (encoding) {
            case NONE:
                returnData = data;
                break;
            case CSV:
                returnData = StringEscapeUtils.escapeCsv(data);
                break;
            case HTML:
                returnData = StringEscapeUtils.escapeHtml(data);
                break;
            case JAVA:
                returnData = StringEscapeUtils.escapeJava(data);
                break;
            case JAVASCRIPT:
                returnData = StringEscapeUtils.escapeJavaScript(data);
                break;
            case XML:
                returnData = StringEscapeUtils.escapeXml(data);
                break;
            default:
                try {
                    returnData = URLEncoder.encode(data,"UTF8");
                } catch (UnsupportedEncodingException e) {
                    returnData = data;
                    LOGGER.warn("Unexpected missing encoder for charset 'UTF8': " + e.getMessage());
                }
                break;
        }
        return returnData;
    }

    private boolean matchExpressions(final String in, final List<String> regexes) {
        if (in != null) {
            if (regexes == null) {
                return true;
            }
            for (final String regex : regexes) {
                LOGGER.trace("Matching string \"" + in + "\" against pattern \"" + regex + "\"");
                if (in.matches(regex)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String formatSmsNumber(final String smsNumber) {
        final Configuration config = theManager.getConfig();
        final String cc = config.readSettingAsString(PwmSetting.SMS_DEFAULT_COUNTRY_CODE);
        final SmsNumberFormat format = SmsNumberFormat.valueOf(config.readSettingAsString(PwmSetting.SMS_PHONE_NUMBER_FORMAT).toUpperCase());
        String ret = smsNumber;
        // Remove (0)
        ret = ret.replaceAll("\\(0\\)","");
        // Remove leading double zero, replace by plus
        if (ret.substring(0,1).equals("00")) {
            ret = "+" + ret.substring(2);
        }
        // Replace leading zero by country code
        if (ret.charAt(0) == '0') {
            ret = cc + ret.substring(1);
        }
        // Add a leading plus if necessary
        if (ret.charAt(0) != '+') {
            ret = "+" + ret;
        }
        // Remove any non-numeric, non-plus characters
        final String tmp = ret;
        ret = "";
        for(int i=0;i<tmp.length();i++) {
            if ((i==0&&tmp.charAt(i)=='+')||(
                    (tmp.charAt(i)>='0'&&tmp.charAt(i)<='9'))
                    ) {
                ret += tmp.charAt(i);
            }
        }
        // Now the number should be in full international format
        // Let's see if we need to change anything:
        switch(format) {
            case PLAIN:
                // remove plus
                ret = ret.substring(1);
                break;
            case PLUS:
                // keep full international format
                break;
            case ZEROS:
                // replace + with 00
                ret = "00" + ret.substring(1);
                break;
            default:
                // keep full international format
                break;
        }
        return ret;
    }

    // -------------------------- INNER CLASSES --------------------------
    private class SmsSendThread extends Thread {
        public void run() {
            LOGGER.trace("starting up sms queue processing thread, queue size is " + smsSendQueue.size());

            while (status == PwmService.STATUS.OPEN) {

                boolean success = false;
                try {
                    success = processQueue();
                    if (success) {
                        lastSendFailure = null;
                    }
                } catch (Exception e) {
                    LOGGER.error("unexpected exception while processing sms queue: " + e.getMessage(), e);
                    LOGGER.error("unable to process sms queue successfully; sleeping for " + TimeDuration.asCompactString(ERROR_RETRY_WAIT_TIME_MS));
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
                LOGGER.error("unexpected exception while processing sms queue: " + e.getMessage(), e);
            }

            threadActive = false;
            LOGGER.trace("closing sms queue processing thread");
        }
    }

    private static class SmsEvent implements Serializable {
        private SmsItemBean smsItem;
        private long queueInsertTimestamp;

        private SmsEvent() {
        }

        private SmsEvent(final SmsItemBean smsItem, final long queueInsertTimestamp) {
            this.smsItem = smsItem;
            this.queueInsertTimestamp = queueInsertTimestamp;
        }

        public SmsItemBean getSmsItem() {
            return smsItem;
        }

        public long getQueueInsertTimestamp() {
            return queueInsertTimestamp;
        }
    }

}

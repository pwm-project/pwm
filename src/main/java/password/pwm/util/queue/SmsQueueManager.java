/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SmsItemBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.*;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class SmsQueueManager extends AbstractQueueManager {
    private static final PwmLogger LOGGER = PwmLogger.forClass(SmsQueueManager.class);

// ------------------------------ FIELDS ------------------------------

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

    public static final String TOKEN_USER       = "%USER%";
    public static final String TOKEN_SENDERID   = "%SENDERID%";
    public static final String TOKEN_MESSAGE    = "%MESSAGE%";
    public static final String TOKEN_TO         = "%TO%";
    public static final String TOKEN_PASS       = "%PASS%";
    public static final String TOKEN_REQUESTID  = "%REQUESTID%";

    private SmsSendEngine smsSendEngine;

// --------------------------- CONSTRUCTORS ---------------------------

    public SmsQueueManager() {
    }
// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface PwmService ---------------------

    public void init(
            final PwmApplication pwmApplication
    )
            throws PwmException
    {
        super.LOGGER = PwmLogger.forClass(SmsQueueManager.class);
        final Settings settings = new Settings(
                new TimeDuration(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SMS_MAX_AGE_MS))),
                new TimeDuration(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SMS_RETRY_TIMEOUT_MS))),
                Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SMS_MAX_COUNT)),
                EmailQueueManager.class.getSimpleName()
        );
        super.init(
                pwmApplication,
                LocalDB.DB.SMS_QUEUE,
                settings,
                PwmApplication.AppAttribute.SMS_ITEM_COUNTER,
                SmsQueueManager.class.getSimpleName()
        );
        smsSendEngine = new SmsSendEngine(pwmApplication.getConfig());
    }


// -------------------------- OTHER METHODS --------------------------

    public void addSmsToQueue(final SmsItemBean smsItem)
            throws PwmUnrecoverableException
    {
        shortenMessageIfNeeded(smsItem);
        if (!determineIfItemCanBeDelivered(smsItem)) {
            return;
        }

        try {
            add(smsItem);
        } catch (Exception e) {
            LOGGER.error("error writing to LocalDB queue, discarding sms send request: " + e.getMessage());
        }
    }

    protected void shortenMessageIfNeeded(final SmsItemBean smsItem) throws PwmUnrecoverableException {
        final Boolean shorten = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SMS_USE_URL_SHORTENER);
        if (shorten) {
            final String message = smsItem.getMessage();
            smsItem.setMessage(pwmApplication.getUrlShortener().shortenUrlInText(message));
        }
    }

    public static boolean smsIsConfigured(final Configuration config) {
        final String gatewayUrl = config.readSettingAsString(PwmSetting.SMS_GATEWAY_URL);
        final String gatewayUser = config.readSettingAsString(PwmSetting.SMS_GATEWAY_USER);
        final PasswordData gatewayPass = config.readSettingAsPassword(PwmSetting.SMS_GATEWAY_PASSWORD);
        if (gatewayUrl == null || gatewayUrl.length() < 1) {
            LOGGER.debug("SMS gateway url is not configured");
            return false;
        }

        if (gatewayUser != null && gatewayUser.length() > 0 && (gatewayPass == null)) {
            LOGGER.debug("SMS gateway user configured, but no password provided");
            return false;
        }

        return true;
    }

    protected boolean determineIfItemCanBeDelivered(final SmsItemBean smsItem) {
        final Configuration config = pwmApplication.getConfig();
        if (!smsIsConfigured(config)) {
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

    void sendItem(final String item) throws PwmOperationalException {
        final SmsItemBean smsItemBean = JsonUtil.deserialize(item, SmsItemBean.class);
        try {
            for (final String msgPart : splitMessage(smsItemBean.getMessage())) {
                smsSendEngine.sendSms(smsItemBean.getTo(), msgPart);
            }
            StatisticsManager.incrementStat(pwmApplication, Statistic.SMS_SEND_SUCCESSES);
        } catch (PwmUnrecoverableException e) {
            StatisticsManager.incrementStat(pwmApplication, Statistic.SMS_SEND_FAILURES);
            LOGGER.error("discarding sms message due to permanent failure: " + e.getErrorInformation().toDebugStr());
        }
    }

    @Override
    List<HealthRecord> failureToHealthRecord(FailureInfo failureInfo) {
        return Collections.singletonList(HealthRecord.forMessage(HealthMessage.SMS_SendFailure, failureInfo.getErrorInformation().toDebugStr()));
    }

    private List<String> splitMessage(final String input) {
        final int size = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH);

        final List<String> returnObj = new ArrayList<>((input.length() + size - 1) / size);

        for (int start = 0; start < input.length(); start += size) {
            returnObj.add(input.substring(start, Math.min(input.length(), start + size)));
        }
        return returnObj;
    }


    protected static String smsDataEncode(final String data, final SmsDataEncoding encoding) {
        String returnData;
        switch (encoding) {
            case NONE:
                returnData = data;
                break;
            case CSV:
                returnData = StringUtil.escapeCsv(data);
                break;
            case HTML:
                returnData = StringUtil.escapeHtml(data);
                break;
            case JAVA:
                returnData = StringUtil.escapeJava(data);
                break;
            case JAVASCRIPT:
                returnData = StringUtil.escapeJS(data);
                break;
            case XML:
                returnData = StringUtil.escapeXml(data);
                break;
            default:
                returnData = data == null ? "" : StringUtil.urlEncode(data);
                break;
        }
        return returnData;
    }

    private static void determineIfResultSuccessful(
            final Configuration config,
            final int resultCode,
            final String resultBody
    )
            throws PwmOperationalException
    {
        final List<String> resultCodeTests = config.readSettingAsStringArray(PwmSetting.SMS_SUCCESS_RESULT_CODE);
        if (resultCodeTests != null && !resultCodeTests.isEmpty()) {
            final String resultCodeStr = String.valueOf(resultCode);
            if (!resultCodeTests.contains(resultCodeStr)) {
                throw new PwmOperationalException(new ErrorInformation(
                        PwmError.ERROR_SMS_SEND_ERROR,
                        "response result code " + resultCode + " is not a configured successful result code"
                ));
            }
        }

        final List<String> regexBodyTests = config.readSettingAsStringArray(PwmSetting.SMS_RESPONSE_OK_REGEX);
        if (regexBodyTests == null || regexBodyTests.isEmpty()) {
            return;

        }

        if (resultBody == null || resultBody.isEmpty()) {
            throw new PwmOperationalException(new ErrorInformation(
                    PwmError.ERROR_SMS_SEND_ERROR,
                    "result has no body but there are configured regex response matches, so send not considered successful"
            ));
        }

        for (final String regex : regexBodyTests) {
            final Pattern p = Pattern.compile(regex, Pattern.DOTALL);
            final Matcher m = p.matcher(resultBody);
            if (m.matches()) {
                LOGGER.trace("result body matched configured regex match setting: " + regex);
                return;
            }
        }

        throw new PwmOperationalException(new ErrorInformation(
                PwmError.ERROR_SMS_SEND_ERROR,
                "result body did not matching any configured regex match settings"
        ));
    }

    protected static String formatSmsNumber(final Configuration config, final String smsNumber) {
        final long ccLong = config.readSettingAsLong(PwmSetting.SMS_DEFAULT_COUNTRY_CODE);
        String countryCodeNumber = "";
        if (ccLong > 0) {
            countryCodeNumber = String.valueOf(ccLong);
        }

        final SmsNumberFormat format = config.readSettingAsEnum(PwmSetting.SMS_PHONE_NUMBER_FORMAT,SmsNumberFormat.class);
        String returnValue = smsNumber;

        // Remove (0)
        returnValue = returnValue.replaceAll("\\(0\\)","");

        // Remove leading double zero, replace by plus
        if (returnValue.startsWith("00")) {
            returnValue = "+" + returnValue.substring(2, returnValue.length());
        }

        // Replace leading zero by country code
        if (returnValue.startsWith("0")) {
            returnValue = countryCodeNumber + returnValue.substring(1, returnValue.length());
        }

        // Add a leading plus if necessary
        if (!returnValue.startsWith("+")) {
            returnValue = "+" + returnValue;
        }

        // Remove any non-numeric, non-plus characters
        returnValue = returnValue.replaceAll("[^0-9\\+]","");

        // Now the number should be in full international format
        // Let's see if we need to change anything:
        switch(format) {
            case PLAIN:
                // remove plus
                returnValue = returnValue.replaceAll("^\\+","");

                // add country code
                returnValue = countryCodeNumber + returnValue;
                break;
            case PLUS:
                // keep full international format
                break;
            case ZEROS:
                // replace + with 00
                returnValue = "00" + returnValue.substring(1);
                break;
            default:
                // keep full international format
                break;
        }
        return returnValue;
    }

    @Override
    protected String queueItemToDebugString(QueueEvent queueEvent)
    {
        final Map<String,Object> debugOutputMap = new LinkedHashMap<>();
        debugOutputMap.put("itemID", queueEvent.getItemID());
        debugOutputMap.put("timestamp", queueEvent.getTimestamp());
        final SmsItemBean smsItemBean = JsonUtil.deserialize(queueEvent.getItem(), SmsItemBean.class);

        debugOutputMap.put("to", smsItemBean.getTo());

        return JsonUtil.serializeMap(debugOutputMap);
    }

    @Override
    protected void noteDiscardedItem(QueueEvent queueEvent)
    {
        StatisticsManager.incrementStat(pwmApplication, Statistic.SMS_SEND_DISCARDS);
    }

    private static class SmsSendEngine {
        private static final PwmLogger LOGGER = PwmLogger.forClass(SmsSendEngine.class);
        private final Configuration config;
        private String lastResponseBody;

        private SmsSendEngine(Configuration configuration)
        {
            this.config = configuration;
        }


        /**
         *
         * @param to
         * @param message
         * @throws PwmUnrecoverableException - If operation failed and a retry is unlikely to succeed
         * @throws PwmOperationalException - If operation failed and should be retried.
         */
        protected void sendSms(final String to, final String message)
                throws PwmUnrecoverableException, PwmOperationalException
        {
            lastResponseBody = null;
            final long startTime = System.currentTimeMillis();

            final String gatewayUser = config.readSettingAsString(PwmSetting.SMS_GATEWAY_USER);
            final PasswordData gatewayPass = config.readSettingAsPassword(PwmSetting.SMS_GATEWAY_PASSWORD);

            final String contentType = config.readSettingAsString(PwmSetting.SMS_REQUEST_CONTENT_TYPE);
            final SmsDataEncoding encoding = SmsDataEncoding.valueOf(config.readSettingAsString(PwmSetting.SMS_REQUEST_CONTENT_ENCODING));

            final List<String> extraHeaders = config.readSettingAsStringArray(PwmSetting.SMS_GATEWAY_REQUEST_HEADERS);

            String requestData = config.readSettingAsString(PwmSetting.SMS_REQUEST_DATA);

            // Replace strings in requestData
            {
                final String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
                requestData = requestData.replace(TOKEN_USER, smsDataEncode(gatewayUser, encoding));
                requestData = requestData.replace(TOKEN_SENDERID, smsDataEncode(senderId, encoding));
                requestData = requestData.replace(TOKEN_MESSAGE, smsDataEncode(message, encoding));
                requestData = requestData.replace(TOKEN_TO, smsDataEncode(formatSmsNumber(config, to), encoding));
            }

            try {
                final String gatewayStrPass = gatewayPass == null ? null : gatewayPass.getStringValue();
                requestData = requestData.replace(TOKEN_PASS, smsDataEncode(gatewayStrPass, encoding));
            } catch (PwmUnrecoverableException e) {
                LOGGER.error("unable to read sms password while reading configuration");
            }

            if (requestData.contains(TOKEN_REQUESTID)) {
                final String chars = config.readSettingAsString(PwmSetting.SMS_REQUESTID_CHARS);
                final int idLength = new Long(config.readSettingAsLong(PwmSetting.SMS_REQUESTID_LENGTH)).intValue();
                final String requestId = PwmRandom.getInstance().alphaNumericString(chars, idLength);
                requestData = requestData.replaceAll(TOKEN_REQUESTID, smsDataEncode(requestId, encoding));
            }

            final String gatewayUrl = config.readSettingAsString(PwmSetting.SMS_GATEWAY_URL);
            final String gatewayMethod = config.readSettingAsString(PwmSetting.SMS_GATEWAY_METHOD);
            final String gatewayAuthMethod = config.readSettingAsString(PwmSetting.SMS_GATEWAY_AUTHMETHOD);

            LOGGER.trace("preparing to send SMS data: " + requestData);
            try {
                final HttpRequestBase httpRequest;
                if (gatewayMethod.equalsIgnoreCase("POST")) {
                    // POST request
                    httpRequest = new HttpPost(gatewayUrl);
                    if (contentType != null && contentType.length()>0) {
                        httpRequest.setHeader("Content-Type", contentType);
                    }
                    ((HttpPost) httpRequest).setEntity(new StringEntity(requestData));
                } else {
                    // GET request
                    final String fullUrl = gatewayUrl.endsWith("?") ? gatewayUrl + requestData : gatewayUrl + "?" + requestData;
                    httpRequest = new HttpGet(fullUrl);
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

                if ("HTTP".equalsIgnoreCase(gatewayAuthMethod) && gatewayUser != null && gatewayPass != null) {
                    LOGGER.debug("Using Basic Authentication");
                    final BasicAuthInfo ba = new BasicAuthInfo(gatewayUser, gatewayPass);
                    httpRequest.addHeader(PwmConstants.HttpHeader.Authorization.getHttpName(), ba.toAuthHeader());
                }

                final HttpClient httpClient = PwmHttpClient.getHttpClient(config);
                final HttpResponse httpResponse = httpClient.execute(httpRequest);
                final String responseBody = EntityUtils.toString(httpResponse.getEntity());
                final int resultCode = httpResponse.getStatusLine().getStatusCode();
                lastResponseBody = httpResponse.getStatusLine() + "\n" + responseBody;
                LOGGER.trace("sms send result body: " + httpResponse.getStatusLine().toString() + "\n" + responseBody);

                determineIfResultSuccessful(config, resultCode, responseBody);
                LOGGER.debug("SMS send successful, HTTP status: " + httpResponse.getStatusLine().getStatusCode());
            } catch (IOException e) {
                final ErrorInformation errorInformation = new ErrorInformation(
                        PwmError.ERROR_SMS_SEND_ERROR,
                        "IO error while sending SMS: " + e.getMessage());
                throw new PwmOperationalException(errorInformation);
            } catch (PwmOperationalException e) {
                throw e;
            } catch (Exception e) {
                final ErrorInformation errorInformation = new ErrorInformation(
                        PwmError.ERROR_SMS_SEND_ERROR,
                        "unexpected error while sending SMS, discarding message: " + e.getMessage());
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        public String getLastResponseBody()
        {
            return lastResponseBody;
        }
    }

    public static String sendDirectMessage(
            final Configuration configuration,
            final SmsItemBean smsItemBean

    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final SmsSendEngine smsSendEngine = new SmsSendEngine(configuration);
        smsSendEngine.sendSms(smsItemBean.getTo(), smsItemBean.getMessage());
        return smsSendEngine.getLastResponseBody();
    }
}

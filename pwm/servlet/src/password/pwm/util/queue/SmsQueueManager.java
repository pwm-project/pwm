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

import com.google.gson.GsonBuilder;
import org.apache.commons.lang.StringEscapeUtils;
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
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.*;
import password.pwm.util.localdb.LocalDB;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class SmsQueueManager extends AbstractQueueManager {
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
        LOGGER = PwmLogger.getLogger(SmsQueueManager.class);
        final Settings settings = new Settings(
                new TimeDuration(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SMS_MAX_AGE_MS))),
                new TimeDuration(Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SMS_RETRY_TIMEOUT_MS))),
                Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.QUEUE_SMS_MAX_COUNT)),
                EmailQueueManager.class.getSimpleName()
        );
        super.init(pwmApplication, LocalDB.DB.SMS_QUEUE, settings, PwmApplication.AppAttribute.SMS_ITEM_COUNTER);
    }


// -------------------------- OTHER METHODS --------------------------

    public void addSmsToQueue(final SmsItemBean smsItem)
            throws PwmUnrecoverableException
    {
        shortenMessageIfNeeded(smsItem);
        if (!determineIfItemCanBeDelivered(smsItem)) {
            return;
        }

        final String smsItemGson = Helper.getGson().toJson(smsItem);
        final int itemID = getNextItemCount();
        final QueueEvent event = new QueueEvent(smsItemGson, new Date(), itemID);
        try {
            add(event);
        } catch (Exception e) {
            LOGGER.error("error writing to LocalDB queue, discarding sms send request: " + e.getMessage());
        }
    }

    protected void shortenMessageIfNeeded(final SmsItemBean smsItem) {
        final Boolean shorten = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SMS_USE_URL_SHORTENER);
        if (shorten) {
            final String message = smsItem.getMessage();
            smsItem.setMessage(pwmApplication.getUrlShortener().shortenUrlInText(message));
        }
    }

    protected boolean determineIfItemCanBeDelivered(final SmsItemBean smsItem) {
        final Configuration config = pwmApplication.getConfig();
        final String gatewayUrl = config.readSettingAsString(PwmSetting.SMS_GATEWAY_URL);
        final String gatewayUser = config.readSettingAsString(PwmSetting.SMS_GATEWAY_USER);
        final String gatewayPass = config.readSettingAsString(PwmSetting.SMS_GATEWAY_PASSWORD);

        if (gatewayUrl == null || gatewayUrl.length() < 1) {
            LOGGER.debug("discarding sms send event (no SMS gateway url configured) " + smsItem.toString());
            return false;
        }

        if (gatewayUser != null && gatewayUser.length() > 0 && (gatewayPass == null || gatewayPass.length() < 1)) {
            LOGGER.debug("discarding sms send event (SMS gateway user configured, but no password provided) " + smsItem.toString());
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

    protected boolean sendItem(final String item) {
        final SmsItemBean smsItemBean = (Helper.getGson()).fromJson(item, SmsItemBean.class);
        boolean success = true;
        while (success && smsItemBean.hasNextPart()) {
            success = sendSmsPart(smsItemBean);
        }
        return success;
    }

    protected boolean sendSmsPart(final SmsItemBean smsItemBean) {
        final long startTime = System.currentTimeMillis();
        final Configuration config = pwmApplication.getConfig();

        final String gatewayUser = config.readSettingAsString(PwmSetting.SMS_GATEWAY_USER);
        final String gatewayPass = config.readSettingAsString(PwmSetting.SMS_GATEWAY_PASSWORD);

        final String contentType = config.readSettingAsString(PwmSetting.SMS_REQUEST_CONTENT_TYPE);
        final SmsDataEncoding encoding = SmsDataEncoding.valueOf(config.readSettingAsString(PwmSetting.SMS_REQUEST_CONTENT_ENCODING));

        final List<String> extraHeaders = config.readSettingAsStringArray(PwmSetting.SMS_GATEWAY_REQUEST_HEADERS);

        String requestData = config.readSettingAsString(PwmSetting.SMS_REQUEST_DATA);

        // Replace strings in requestData
        {
            final String senderId = smsItemBean.getFrom() == null ? "" : smsItemBean.getFrom();
            requestData = requestData.replaceAll("%USER%", smsDataEncode(gatewayUser, encoding));
            requestData = requestData.replaceAll("%PASS%", smsDataEncode(gatewayPass, encoding));
            requestData = requestData.replaceAll("%SENDERID%", smsDataEncode(senderId, encoding));
            requestData = requestData.replaceAll("%MESSAGE%", smsDataEncode(smsItemBean.getNextPart(), encoding));
            requestData = requestData.replaceAll("%TO%", smsDataEncode(formatSmsNumber(smsItemBean.getTo()), encoding));
        }

        if (requestData.contains("%REQUESTID%")) {
            final String chars = config.readSettingAsString(PwmSetting.SMS_REQUESTID_CHARS);
            final int idLength = new Long(config.readSettingAsLong(PwmSetting.SMS_REQUESTID_LENGTH)).intValue();
            final String requestId = PwmRandom.getInstance().alphaNumericString(chars, idLength);
            requestData = requestData.replaceAll("%REQUESTID%", smsDataEncode(requestId, encoding));
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
                httpRequest.addHeader(PwmConstants.HTTP_HEADER_BASIC_AUTH, ba.toAuthHeader());
            }

            final HttpClient httpClient = Helper.getHttpClient(config);
            final HttpResponse httpResponse = httpClient.execute(httpRequest);
            final String responseBody = EntityUtils.toString(httpResponse.getEntity());

            final List<String> okMessages = config.readSettingAsStringArray(PwmSetting.SMS_RESPONSE_OK_REGEX);
            if (okMessages == null || okMessages.size() == 0 || matchExpressions(responseBody, okMessages)) {
                LOGGER.debug("SMS send successful, HTTP status: " + httpResponse.getStatusLine()  + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")\n body: " + responseBody);
                return true;
            }

            LOGGER.error("SMS send failure, HTTP status: " + httpResponse.getStatusLine() + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")\n body: " + responseBody);
            return false;
        } catch (IOException e) {
            LOGGER.error("unexpected exception while sending SMS: " + e.getMessage(), e);
            return false;
        }
    }

    protected String smsDataEncode(final String data, final SmsDataEncoding encoding) {
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
                    returnData = (data==null)?"":URLEncoder.encode(data,"UTF8");
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
                Pattern p = Pattern.compile(regex, Pattern.DOTALL);
                Matcher m = p.matcher(in);
                if (m.matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected String formatSmsNumber(final String smsNumber) {
        final Configuration config = pwmApplication.getConfig();

		long ccLong = config.readSettingAsLong(PwmSetting.SMS_DEFAULT_COUNTRY_CODE);
        String countryCodeNumber = "";
        if (ccLong > 0) {
        	countryCodeNumber = String.valueOf(ccLong);
        }

        final SmsNumberFormat format = SmsNumberFormat.valueOf(config.readSettingAsString(PwmSetting.SMS_PHONE_NUMBER_FORMAT).toUpperCase());
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
        {
            final String tmp = returnValue;
            returnValue = "";
            for(int i=0; i < tmp.length(); i++) {
                if ((i==0 && tmp.charAt(i) == '+') || ((tmp.charAt(i) >= '0' && tmp.charAt(i) <= '9'))) {
                    returnValue += tmp.charAt(i);
                }
            }
        }

        // Now the number should be in full international format
        // Let's see if we need to change anything:
        switch(format) {
            case PLAIN:
                // remove plus
                returnValue = returnValue.substring(1);
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
        final Map<String,Object> debugOutputMap = new LinkedHashMap<String, Object>();
        debugOutputMap.put("itemID", queueEvent.getItemID());
        debugOutputMap.put("timestamp", queueEvent.getTimestamp());
        final SmsItemBean smsItemBean = Helper.getGson().fromJson(queueEvent.getItem(), SmsItemBean.class);

        debugOutputMap.put("to", smsItemBean.getTo());
        debugOutputMap.put("from", smsItemBean.getFrom());

        return Helper.getGson(new GsonBuilder().disableHtmlEscaping()).toJson(debugOutputMap);
    }

}

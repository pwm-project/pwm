/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.util.queue;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.SmsItemBean;
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
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.localdb.WorkQueueProcessor;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class SmsQueueManager implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SmsQueueManager.class );

    public enum SmsNumberFormat
    {
        RAW,
        PLAIN,
        PLUS,
        ZEROS
    }

    public enum SmsDataEncoding
    {
        NONE,
        URL,
        XML,
        HTML,
        CSV,
        JAVA,
        JAVASCRIPT,
        SQL
    }

    public static final String TOKEN_USER = "%USER%";
    public static final String TOKEN_SENDERID = "%SENDERID%";
    public static final String TOKEN_MESSAGE = "%MESSAGE%";
    public static final String TOKEN_TO = "%TO%";
    public static final String TOKEN_PASS = "%PASS%";
    public static final String TOKEN_REQUESTID = "%REQUESTID%";

    private SmsSendEngine smsSendEngine;

    private WorkQueueProcessor<SmsItemBean> workQueueProcessor;
    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private ErrorInformation lastError;

    public SmsQueueManager( )
    {
    }

    public void init(
            final PwmApplication pwmApplication
    )
            throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;
        if ( pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            LOGGER.warn( () -> "localdb is not open,  will remain closed" );
            status = STATUS.CLOSED;
            return;
        }

        final WorkQueueProcessor.Settings settings = WorkQueueProcessor.Settings.builder()
                .maxEvents( Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.QUEUE_SMS_MAX_COUNT ) ) )
                .retryDiscardAge( TimeDuration.of( pwmApplication.getConfig().readSettingAsLong( PwmSetting.SMS_MAX_QUEUE_AGE ), TimeDuration.Unit.SECONDS ) )
                .retryInterval( TimeDuration.of(
                        Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.QUEUE_SMS_RETRY_TIMEOUT_MS ) ),
                        TimeDuration.Unit.MILLISECONDS )
                )
                .build();

        final LocalDBStoredQueue localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue( pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.SMS_QUEUE );

        workQueueProcessor = new WorkQueueProcessor<>( pwmApplication, localDBStoredQueue, settings, new SmsItemProcessor(), this.getClass() );

        smsSendEngine = new SmsSendEngine( pwmApplication, pwmApplication.getConfig() );

        status = STATUS.OPEN;
    }

    private class SmsItemProcessor implements WorkQueueProcessor.ItemProcessor<SmsItemBean>
    {
        @Override
        public WorkQueueProcessor.ProcessResult process( final SmsItemBean workItem )
        {
            try
            {
                for ( final String msgPart : splitMessage( workItem.getMessage() ) )
                {
                    smsSendEngine.sendSms( workItem.getTo(), msgPart, workItem.getSessionLabel() );
                }
                StatisticsManager.incrementStat( pwmApplication, Statistic.SMS_SEND_SUCCESSES );
                lastError = null;
            }
            catch ( final PwmUnrecoverableException e )
            {
                StatisticsManager.incrementStat( pwmApplication, Statistic.SMS_SEND_DISCARDS );
                StatisticsManager.incrementStat( pwmApplication, Statistic.SMS_SEND_FAILURES );
                LOGGER.error( () -> "discarding sms message due to permanent failure: " + e.getErrorInformation().toDebugStr() );
                lastError = e.getErrorInformation();
                return WorkQueueProcessor.ProcessResult.FAILED;
            }
            catch ( final PwmOperationalException e )
            {
                StatisticsManager.incrementStat( pwmApplication, Statistic.SMS_SEND_FAILURES );
                lastError = e.getErrorInformation();
                return WorkQueueProcessor.ProcessResult.RETRY;
            }

            return WorkQueueProcessor.ProcessResult.SUCCESS;
        }

        @Override
        public String convertToDebugString( final SmsItemBean workItem )
        {
            final Map<String, Object> debugOutputMap = new LinkedHashMap<>();

            debugOutputMap.put( "to", workItem.getTo() );

            return JsonUtil.serializeMap( debugOutputMap );
        }
    }

    public void addSmsToQueue( final SmsItemBean smsItem )
            throws PwmUnrecoverableException
    {
        final SmsItemBean shortenedBean = shortenMessageIfNeeded( smsItem );
        if ( !determineIfItemCanBeDelivered( shortenedBean ) )
        {
            return;
        }

        try
        {
            workQueueProcessor.submit( shortenedBean );
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error writing to LocalDB queue, discarding sms send request: " + e.getMessage() );
        }
    }

    SmsItemBean shortenMessageIfNeeded(
            final SmsItemBean smsItem
    )
            throws PwmUnrecoverableException
    {
        final Boolean shorten = pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.SMS_USE_URL_SHORTENER );
        if ( shorten )
        {
            final String message = smsItem.getMessage();
            final String shortenedMessage = pwmApplication.getUrlShortener().shortenUrlInText( message );
            return new SmsItemBean( smsItem.getTo(), shortenedMessage, smsItem.getSessionLabel() );
        }
        return smsItem;
    }

    public static boolean smsIsConfigured( final Configuration config )
    {
        final String gatewayUrl = config.readSettingAsString( PwmSetting.SMS_GATEWAY_URL );
        final String gatewayUser = config.readSettingAsString( PwmSetting.SMS_GATEWAY_USER );
        final PasswordData gatewayPass = config.readSettingAsPassword( PwmSetting.SMS_GATEWAY_PASSWORD );
        if ( gatewayUrl == null || gatewayUrl.length() < 1 )
        {
            LOGGER.debug( () -> "SMS gateway url is not configured" );
            return false;
        }

        if ( gatewayUser != null && gatewayUser.length() > 0 && ( gatewayPass == null ) )
        {
            LOGGER.debug( () -> "SMS gateway user configured, but no password provided" );
            return false;
        }

        return true;
    }

    boolean determineIfItemCanBeDelivered( final SmsItemBean smsItem )
    {
        final Configuration config = pwmApplication.getConfig();
        if ( !smsIsConfigured( config ) )
        {
            return false;
        }

        if ( smsItem.getTo() == null || smsItem.getTo().length() < 1 )
        {
            LOGGER.debug( () -> "discarding sms send event (no to address) " + smsItem.toString() );
            return false;
        }

        if ( smsItem.getMessage() == null || smsItem.getMessage().length() < 1 )
        {
            LOGGER.debug( () -> "discarding sms send event (no message) " + smsItem.toString() );
            return false;
        }

        return true;
    }

    @Override
    public STATUS status( )
    {
        return status;
    }

    @Override
    public void close( )
    {
        if ( workQueueProcessor != null )
        {
            workQueueProcessor.close();
        }
        workQueueProcessor = null;

        status = STATUS.CLOSED;
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        if ( lastError != null )
        {
            return Collections.singletonList( HealthRecord.forMessage( HealthMessage.SMS_SendFailure, lastError.toDebugStr() ) );
        }
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> debugItems = new LinkedHashMap<>();
        if ( workQueueProcessor != null )
        {
            debugItems.putAll( workQueueProcessor.debugInfo() );
        }
        if ( status() == STATUS.OPEN )
        {
            return new ServiceInfoBean( Collections.singletonList( DataStorageMethod.LOCALDB ), debugItems );
        }
        else
        {
            return new ServiceInfoBean( Collections.emptyList(), debugItems );
        }
    }

    private List<String> splitMessage( final String input )
    {
        final int size = ( int ) pwmApplication.getConfig().readSettingAsLong( PwmSetting.SMS_MAX_TEXT_LENGTH );

        final List<String> returnObj = new ArrayList<>( ( input.length() + size - 1 ) / size );

        for ( int start = 0; start < input.length(); start += size )
        {
            returnObj.add( input.substring( start, Math.min( input.length(), start + size ) ) );
        }
        return returnObj;
    }


    protected static String smsDataEncode( final String data, final SmsDataEncoding encoding )
    {
        final String normalizedString = data == null ? "" : data;

        switch ( encoding )
        {
            case NONE:
                return normalizedString;

            case URL:
                return StringUtil.urlEncode( normalizedString );

            case CSV:
                return StringUtil.escapeCsv( normalizedString );

            case HTML:
                return StringUtil.escapeHtml( normalizedString );

            case JAVA:
                return StringUtil.escapeJava( normalizedString );

            case JAVASCRIPT:
                return StringUtil.escapeJS( normalizedString );

            case XML:
                return StringUtil.escapeXml( normalizedString );

            default:
                return normalizedString;

        }
    }

    private static void determineIfResultSuccessful(
            final Configuration config,
            final int resultCode,
            final String resultBody
    )
            throws PwmOperationalException
    {
        final List<String> resultCodeTests = config.readSettingAsStringArray( PwmSetting.SMS_SUCCESS_RESULT_CODE );
        if ( resultCodeTests != null && !resultCodeTests.isEmpty() )
        {
            final String resultCodeStr = String.valueOf( resultCode );
            if ( !resultCodeTests.contains( resultCodeStr ) )
            {
                throw new PwmOperationalException( new ErrorInformation(
                        PwmError.ERROR_SMS_SEND_ERROR,
                        "response result code " + resultCode + " is not a configured successful result code"
                ) );
            }
        }

        final List<String> regexBodyTests = config.readSettingAsStringArray( PwmSetting.SMS_RESPONSE_OK_REGEX );
        if ( regexBodyTests == null || regexBodyTests.isEmpty() )
        {
            return;

        }

        if ( resultBody == null || resultBody.isEmpty() )
        {
            throw new PwmOperationalException( new ErrorInformation(
                    PwmError.ERROR_SMS_SEND_ERROR,
                    "result has no body but there are configured regex response matches, so send not considered successful"
            ) );
        }

        for ( final String regex : regexBodyTests )
        {
            final Pattern p = Pattern.compile( regex, Pattern.DOTALL );
            final Matcher m = p.matcher( resultBody );
            if ( m.matches() )
            {
                LOGGER.trace( () -> "result body matched configured regex match setting: " + regex );
                return;
            }
        }

        throw new PwmOperationalException( new ErrorInformation(
                PwmError.ERROR_SMS_SEND_ERROR,
                "result body did not matching any configured regex match settings"
        ) );
    }

    static String formatSmsNumber( final Configuration config, final String smsNumber )
    {
        final SmsNumberFormat format = config.readSettingAsEnum( PwmSetting.SMS_PHONE_NUMBER_FORMAT, SmsNumberFormat.class );

        if ( format == SmsNumberFormat.RAW )
        {
            return smsNumber;
        }

        final long ccLong = config.readSettingAsLong( PwmSetting.SMS_DEFAULT_COUNTRY_CODE );
        String countryCodeNumber = "";
        if ( ccLong > 0 )
        {
            countryCodeNumber = String.valueOf( ccLong );
        }

        String returnValue = smsNumber;

        // Remove (0)
        returnValue = returnValue.replaceAll( "\\(0\\)", "" );

        // Remove leading double zero, replace by plus
        if ( returnValue.startsWith( "00" ) )
        {
            returnValue = "+" + returnValue.substring( 2, returnValue.length() );
        }

        // Replace leading zero by country code
        if ( returnValue.startsWith( "0" ) )
        {
            returnValue = countryCodeNumber + returnValue.substring( 1, returnValue.length() );
        }

        // Add a leading plus if necessary
        if ( !returnValue.startsWith( "+" ) )
        {
            returnValue = "+" + returnValue;
        }

        // Remove any non-numeric, non-plus characters
        returnValue = returnValue.replaceAll( "[^0-9\\+]", "" );

        // Now the number should be in full international format
        // Let's see if we need to change anything:
        switch ( format )
        {
            case PLAIN:
                // remove plus
                returnValue = returnValue.replaceAll( "^\\+", "" );

                // add country code
                returnValue = countryCodeNumber + returnValue;
                break;
            case PLUS:
                // keep full international format
                break;
            case ZEROS:
                // replace + with 00
                returnValue = "00" + returnValue.substring( 1 );
                break;
            default:
                // keep full international format
                break;
        }
        return returnValue;
    }

    private static class SmsSendEngine
    {
        private static final PwmLogger LOGGER = PwmLogger.forClass( SmsSendEngine.class );
        private final PwmApplication pwmApplication;
        private final Configuration config;
        private String lastResponseBody;

        private SmsSendEngine( final PwmApplication pwmApplication, final Configuration configuration )
        {
            this.pwmApplication = pwmApplication;
            this.config = configuration;
        }

        protected void sendSms( final String to, final String message, final SessionLabel sessionLabel )
                throws PwmUnrecoverableException, PwmOperationalException
        {
            lastResponseBody = null;

            final String requestData = makeRequestData( to, message );

            LOGGER.trace( () -> "preparing to send SMS data: " + requestData );

            final PwmHttpClientRequest pwmHttpClientRequest = makeRequest( requestData );

            final PwmHttpClient pwmHttpClient;
            {
                if ( JavaHelper.isEmpty( config.readSettingAsCertificate( PwmSetting.SMS_GATEWAY_CERTIFICATES ) ) )
                {
                    pwmHttpClient = pwmApplication.getHttpClientService().getPwmHttpClient( );
                }
                else
                {
                    final PwmHttpClientConfiguration clientConfiguration = PwmHttpClientConfiguration.builder()
                            .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates )
                            .certificates( config.readSettingAsCertificate( PwmSetting.SMS_GATEWAY_CERTIFICATES ) )
                            .build();

                    pwmHttpClient = pwmApplication.getHttpClientService().getPwmHttpClient( clientConfiguration );
                }
            }

            try
            {
                final PwmHttpClientResponse pwmHttpClientResponse = pwmHttpClient.makeRequest( pwmHttpClientRequest, sessionLabel );
                final int resultCode = pwmHttpClientResponse.getStatusCode();

                final String responseBody = pwmHttpClientResponse.getBody();
                lastResponseBody = responseBody;

                determineIfResultSuccessful( config, resultCode, responseBody );
                LOGGER.debug( () -> "SMS send successful, HTTP status: " + resultCode );
            }
            catch ( final PwmUnrecoverableException e )
            {
                final ErrorInformation errorInformation = new ErrorInformation(
                        PwmError.ERROR_SMS_SEND_ERROR,
                        "error while sending SMS, discarding message: " + e.getMessage() );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }

        private String makeRequestData(
                final String to,
                final String message
        )
        {

            final SmsDataEncoding encoding = config.readSettingAsEnum( PwmSetting.SMS_REQUEST_CONTENT_ENCODING, SmsDataEncoding.class );

            String requestData = config.readSettingAsString( PwmSetting.SMS_REQUEST_DATA );

            requestData = applyUserPassTokens( requestData );

            // Replace strings in requestData
            {
                final String senderId = config.readSettingAsString( PwmSetting.SMS_SENDER_ID );
                requestData = requestData.replace( TOKEN_SENDERID, smsDataEncode( senderId, encoding ) );
                requestData = requestData.replace( TOKEN_MESSAGE, smsDataEncode( message, encoding ) );
                requestData = requestData.replace( TOKEN_TO, smsDataEncode( formatSmsNumber( config, to ), encoding ) );
            }

            if ( requestData.contains( TOKEN_REQUESTID ) )
            {
                final PwmRandom pwmRandom = pwmApplication.getSecureService().pwmRandom();
                final String chars = config.readSettingAsString( PwmSetting.SMS_REQUESTID_CHARS );
                final int idLength = Long.valueOf( config.readSettingAsLong( PwmSetting.SMS_REQUESTID_LENGTH ) ).intValue();
                final String requestId = pwmRandom.alphaNumericString( chars, idLength );
                requestData = requestData.replaceAll( TOKEN_REQUESTID, smsDataEncode( requestId, encoding ) );
            }

            return requestData;
        }

        private String applyUserPassTokens( final String input )
        {
            final SmsDataEncoding encoding = config.readSettingAsEnum( PwmSetting.SMS_REQUEST_CONTENT_ENCODING, SmsDataEncoding.class );

            final String gatewayUser = config.readSettingAsString( PwmSetting.SMS_GATEWAY_USER );
            final PasswordData gatewayPass = config.readSettingAsPassword( PwmSetting.SMS_GATEWAY_PASSWORD );

            String modifiableText = input;
            modifiableText = modifiableText.replace( TOKEN_USER, smsDataEncode( gatewayUser, encoding ) );
            modifiableText = modifiableText.replace( TOKEN_USER, smsDataEncode( gatewayUser, encoding ) );

            try
            {
                final String gatewayStrPass = gatewayPass == null ? null : gatewayPass.getStringValue();
                modifiableText = modifiableText.replace( TOKEN_PASS, smsDataEncode( gatewayStrPass, encoding ) );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "unable to read sms password while reading configuration: " + e.getMessage() );
            }

            return modifiableText;
        }

        private PwmHttpClientRequest makeRequest(
                final String requestData
        )
                throws PwmUnrecoverableException
        {
            final String gatewayUser = config.readSettingAsString( PwmSetting.SMS_GATEWAY_USER );
            final PasswordData gatewayPass = config.readSettingAsPassword( PwmSetting.SMS_GATEWAY_PASSWORD );
            final String contentType = config.readSettingAsString( PwmSetting.SMS_REQUEST_CONTENT_TYPE );
            final List<String> extraHeaders = config.readSettingAsStringArray( PwmSetting.SMS_GATEWAY_REQUEST_HEADERS );
            final String gatewayUrl = config.readSettingAsString( PwmSetting.SMS_GATEWAY_URL );
            final String gatewayMethod = config.readSettingAsString( PwmSetting.SMS_GATEWAY_METHOD );
            final String gatewayAuthMethod = config.readSettingAsString( PwmSetting.SMS_GATEWAY_AUTHMETHOD );

            final HttpMethod httpMethod = "POST".equalsIgnoreCase( gatewayMethod )
                    ? HttpMethod.POST
                    : HttpMethod.GET;

            final Map<String, String> headers = new LinkedHashMap<>();
            {
                if ( "HTTP".equalsIgnoreCase( gatewayAuthMethod ) && gatewayUser != null && gatewayPass != null )
                {
                    final BasicAuthInfo basicAuthInfo = new BasicAuthInfo( gatewayUser, gatewayPass );
                    headers.put( HttpHeader.Authorization.getHttpName(), basicAuthInfo.toAuthHeader() );
                }


                if ( !StringUtil.isEmpty( contentType ) && httpMethod == HttpMethod.POST )
                {
                    headers.put( HttpHeader.ContentType.getHttpName(), contentType );
                }

                if ( extraHeaders != null )
                {
                    final Pattern pattern = Pattern.compile( "^([A-Za-z0-9_\\.-]+):[ \t]*([^ \t].*)" );
                    for ( final String header : extraHeaders )
                    {
                        final Matcher matcher = pattern.matcher( header );
                        if ( matcher.matches() )
                        {
                            final String headerName = matcher.group( 1 );
                            final String headerValue = matcher.group( 2 );
                            final String tokenizedValue = applyUserPassTokens( headerValue );
                            headers.put( headerName, tokenizedValue );
                        }
                        else
                        {
                            LOGGER.warn( () -> "Cannot parse HTTP header: " + header );
                        }
                    }
                }

            }

            final String fullUrl = httpMethod == HttpMethod.POST
                    ? gatewayUrl
                    : gatewayUrl.endsWith( "?" ) ? gatewayUrl + requestData : gatewayUrl + "?" + requestData;

            final String body = httpMethod == HttpMethod.POST
                    ? requestData
                    : null;

            return PwmHttpClientRequest.builder()
                    .method( httpMethod )
                    .url( fullUrl )
                    .body( body )
                    .headers( headers )
                    .build();
        }

        public String getLastResponseBody( )
        {
            return lastResponseBody;
        }
    }

    public static String sendDirectMessage(
            final PwmApplication pwmApplication,
            final Configuration configuration,
            final SessionLabel sessionLabel,
            final SmsItemBean smsItemBean

    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final SmsSendEngine smsSendEngine = new SmsSendEngine( pwmApplication, configuration );
        smsSendEngine.sendSms( smsItemBean.getTo(), smsItemBean.getMessage(), sessionLabel );
        return smsSendEngine.getLastResponseBody();
    }

    public int queueSize( )
    {
        return workQueueProcessor == null
                ? 0
                : workQueueProcessor.queueSize();
    }

    public Instant eldestItem( )
    {
        return workQueueProcessor == null
                ? null
                : workQueueProcessor.eldestItem();
    }

}

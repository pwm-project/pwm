/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.svc.sms;

import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.PasswordData;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SmsSendEngine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SmsSendEngine.class );

    private final PwmApplication pwmApplication;
    private final AppConfig config;

    private PwmHttpClientResponse lastResponse;

    SmsSendEngine(
            final PwmApplication pwmApplication,
            final AppConfig domainConfig
    )
    {
        this.pwmApplication = pwmApplication;
        this.config = domainConfig;
    }

    protected void sendSms( final String to, final String message, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        lastResponse = null;

        final String requestData = makeRequestData( to, message, sessionLabel );

        LOGGER.trace( sessionLabel, () -> "preparing to send SMS data: " + requestData );

        final PwmHttpClientRequest pwmHttpClientRequest = makeRequest( requestData, sessionLabel );

        try
        {
            final PwmHttpClient pwmHttpClient = makePwmHttpClient( sessionLabel );
            final PwmHttpClientResponse pwmHttpClientResponse = pwmHttpClient.makeRequest( pwmHttpClientRequest );
            final int resultCode = pwmHttpClientResponse.getStatusCode();
            lastResponse = pwmHttpClientResponse;

            final String responseBody = pwmHttpClientResponse.getBody();

            SmsUtil.determineIfResultSuccessful( config, resultCode, responseBody );
            LOGGER.debug( sessionLabel, () -> "SMS send successful, HTTP status: " + resultCode );
        }
        catch ( final PwmUnrecoverableException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_SMS_SEND_ERROR,
                    "error while sending SMS, discarding message: " + e.getMessage() );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    private PwmHttpClient makePwmHttpClient( final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        if ( CollectionUtil.isEmpty( config.readSettingAsCertificate( PwmSetting.SMS_GATEWAY_CERTIFICATES ) ) )
        {
            return pwmApplication.getHttpClientService().getPwmHttpClient( sessionLabel );
        }
        else
        {
            final PwmHttpClientConfiguration clientConfiguration = PwmHttpClientConfiguration.builder()
                    .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates )
                    .certificates( config.readSettingAsCertificate( PwmSetting.SMS_GATEWAY_CERTIFICATES ) )
                    .build();

            return pwmApplication.getHttpClientService().getPwmHttpClient( clientConfiguration, sessionLabel );
        }
    }

    private String makeRequestData(
            final String to,
            final String message,
            final SessionLabel sessionLabel
    )
    {

        final SmsDataEncoding encoding = config.readSettingAsEnum( PwmSetting.SMS_REQUEST_CONTENT_ENCODING, SmsDataEncoding.class );

        String requestData = config.readSettingAsString( PwmSetting.SMS_REQUEST_DATA );

        requestData = applyUserPassTokens( requestData, sessionLabel );

        // Replace strings in requestData
        {
            final String senderId = config.readSettingAsString( PwmSetting.SMS_SENDER_ID );
            requestData = requestData.replace( SmsQueueService.TOKEN_SENDERID, SmsUtil.smsDataEncode( senderId, encoding ) );
            requestData = requestData.replace( SmsQueueService.TOKEN_MESSAGE, SmsUtil.smsDataEncode( message, encoding ) );
            requestData = requestData.replace( SmsQueueService.TOKEN_TO, SmsUtil.smsDataEncode( SmsUtil.formatSmsNumber( config, to ), encoding ) );
        }

        if ( requestData.contains( SmsQueueService.TOKEN_REQUESTID ) )
        {
            final PwmRandom pwmRandom = pwmApplication.getSecureService().pwmRandom();
            final String chars = config.readSettingAsString( PwmSetting.SMS_REQUESTID_CHARS );
            final int idLength = Long.valueOf( config.readSettingAsLong( PwmSetting.SMS_REQUESTID_LENGTH ) ).intValue();
            final String requestId = pwmRandom.alphaNumericString( chars, idLength );
            requestData = requestData.replaceAll( SmsQueueService.TOKEN_REQUESTID, SmsUtil.smsDataEncode( requestId, encoding ) );
        }

        return requestData;
    }

    private String applyUserPassTokens( final String input, final SessionLabel sessionLabel )
    {
        final SmsDataEncoding encoding = config.readSettingAsEnum( PwmSetting.SMS_REQUEST_CONTENT_ENCODING, SmsDataEncoding.class );

        final String gatewayUser = config.readSettingAsString( PwmSetting.SMS_GATEWAY_USER );
        final PasswordData gatewayPass = config.readSettingAsPassword( PwmSetting.SMS_GATEWAY_PASSWORD );

        String modifiableText = input;
        modifiableText = modifiableText.replace( SmsQueueService.TOKEN_USER, SmsUtil.smsDataEncode( gatewayUser, encoding ) );
        modifiableText = modifiableText.replace( SmsQueueService.TOKEN_USER, SmsUtil.smsDataEncode( gatewayUser, encoding ) );

        try
        {
            final String gatewayStrPass = gatewayPass == null ? null : gatewayPass.getStringValue();
            modifiableText = modifiableText.replace( SmsQueueService.TOKEN_PASS, SmsUtil.smsDataEncode( gatewayStrPass, encoding ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( sessionLabel, () -> "unable to read sms password while reading configuration: " + e.getMessage() );
        }

        return modifiableText;
    }

    private PwmHttpClientRequest makeRequest(
            final String requestData,
            final SessionLabel sessionLabel
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


            if ( StringUtil.notEmpty( contentType ) && httpMethod == HttpMethod.POST )
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
                        final String tokenizedValue = applyUserPassTokens( headerValue, sessionLabel );
                        headers.put( headerName, tokenizedValue );
                    }
                    else
                    {
                        LOGGER.warn( sessionLabel, () -> "Cannot parse HTTP header: " + header );
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

    PwmHttpClientResponse getLastResponse()
    {
        return lastResponse;
    }
}

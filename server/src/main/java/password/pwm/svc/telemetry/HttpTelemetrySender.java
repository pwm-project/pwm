/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.svc.telemetry;

import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TelemetryPublishBean;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class HttpTelemetrySender implements TelemetrySender
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( HttpTelemetrySender.class );

    private PwmApplication pwmApplication;
    private Settings settings;

    @Override
    public void init( final PwmApplication pwmApplication, final String initString )
    {
        this.pwmApplication = pwmApplication;
        settings = JsonUtil.deserialize( initString, HttpTelemetrySender.Settings.class );
    }

    @Override
    public void publish( final TelemetryPublishBean statsPublishBean )
            throws PwmUnrecoverableException
    {
        final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.promiscuous )
                .build();
        final PwmHttpClient pwmHttpClient = pwmApplication.getHttpClientService().getPwmHttpClient( pwmHttpClientConfiguration );
        final String body = JsonUtil.serialize( statsPublishBean );
        final Map<String, String> headers = new HashMap<>();
        headers.put( HttpHeader.ContentType.getHttpName(), HttpContentType.json.getHeaderValueWithEncoding() );
        headers.put( HttpHeader.Accept.getHttpName(), PwmConstants.AcceptValue.json.getHeaderValue() );
        final PwmHttpClientRequest pwmHttpClientRequest = PwmHttpClientRequest.builder()
                .method( HttpMethod.POST )
                .url( settings.getUrl() )
                .body( body )
                .headers( headers )
                .build();

        LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "preparing to send telemetry data to '" + settings.getUrl() + ")" );
        pwmHttpClient.makeRequest( pwmHttpClientRequest, SessionLabel.TELEMETRY_SESSION_LABEL );
        LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "sent telemetry data to '" + settings.getUrl() + ")" );
    }

    @Getter
    @AllArgsConstructor
    private static class Settings implements Serializable
    {
        private String url;
    }
}

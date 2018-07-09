/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.http.client.PwmHttpClientRequest;
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
                .promiscuous( true )
                .build();
        final PwmHttpClient pwmHttpClient = new PwmHttpClient( pwmApplication, SessionLabel.TELEMETRY_SESSION_LABEL, pwmHttpClientConfiguration );
        final String body = JsonUtil.serialize( statsPublishBean );
        final Map<String, String> headers = new HashMap<>();
        headers.put( HttpHeader.ContentType.getHttpName(), HttpContentType.json.getHeaderValue() );
        headers.put( HttpHeader.Accept.getHttpName(), PwmConstants.AcceptValue.json.getHeaderValue() );
        final PwmHttpClientRequest pwmHttpClientRequest = new PwmHttpClientRequest(
                HttpMethod.POST,
                settings.getUrl(),
                body,
                headers
        );
        LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, "preparing to send telemetry data to '" + settings.getUrl() + ")" );
        pwmHttpClient.makeRequest( pwmHttpClientRequest );
        LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, "sent telemetry data to '" + settings.getUrl() + ")" );
    }

    @Getter
    @AllArgsConstructor
    private static class Settings implements Serializable
    {
        private String url;
    }
}

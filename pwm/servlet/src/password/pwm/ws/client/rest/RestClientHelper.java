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

package password.pwm.ws.client.rest;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.util.Locale;

public class RestClientHelper {
    private static final PwmLogger LOGGER = PwmLogger.forClass(RestClientHelper.class);

    public static String makeOutboundRestWSCall(
            final PwmApplication pwmApplication,
            final Locale locale,
            final String url,
            final String jsonRequestBody
    )
            throws PwmOperationalException, PwmUnrecoverableException {
        final HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Accept", PwmConstants.AcceptValue.json.getHeaderValue());
        if (locale != null) {
            httpPost.setHeader("Accept-Locale", locale.toString());
        }
        httpPost.setHeader("Content-Type", PwmConstants.ContentTypeValue.json.getHeaderValue());
        final HttpResponse httpResponse;
        try {
            final StringEntity stringEntity = new StringEntity(jsonRequestBody);
            stringEntity.setContentType(PwmConstants.AcceptValue.json.getHeaderValue());
            httpPost.setEntity(stringEntity);
            LOGGER.debug("beginning external rest call to: " + httpPost.toString() + ", body: " + jsonRequestBody);
            httpResponse = PwmHttpClient.getHttpClient(pwmApplication.getConfig()).execute(httpPost);
            final String responseBody = EntityUtils.toString(httpResponse.getEntity());
            LOGGER.trace("external rest call returned: " + httpResponse.getStatusLine().toString() + ", body: " + responseBody);
            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                final String errorMsg = "received non-200 response code (" + httpResponse.getStatusLine().getStatusCode() + ") when executing web-service";
                LOGGER.error(errorMsg);
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
            }
            return responseBody;
        } catch (IOException e) {
            final String errorMsg = "http response error while executing external rest call, error: " + e.getMessage();
            LOGGER.error(errorMsg);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg),e);
        }
    }
}

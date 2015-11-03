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

package password.pwm.ws.client.rest.naaf;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.ServletHelper;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.http.client.PwmHttpClientRequest;
import password.pwm.http.client.PwmHttpClientResponse;
import password.pwm.util.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.*;

public class NAAFEndPoint {
    private static final PwmLogger LOGGER = PwmLogger.forClass(NAAFEndPoint.class);

    private final String id;
    private final String salt;
    private final String secret;
    private final String endpointURL;

    private String endpoint_session_id;
    private PwmHttpClient pwmHttpClient;

    private Locale locale;

    public NAAFEndPoint(
            final PwmApplication pwmApplication,
            final String url,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        this.locale = locale;

        final Configuration config = pwmApplication.getConfig();
        this.endpointURL = url;
        this.id = config.readAppProperty(AppProperty.NAAF_ID);
        this.secret = config.readAppProperty(AppProperty.NAAF_SECRET);
        final int saltLength = Integer.parseInt(config.readAppProperty(AppProperty.NAAF_SALT_LENGTH));
        this.salt = PwmRandom.getInstance().alphaNumericString(saltLength);

        final X509Certificate[] naafWsCerts = config.readSettingAsCertificate(PwmSetting.NAAF_WS_CERTIFICATE);
        final PwmHttpClientConfiguration pwmHttpClientConfiguration = new PwmHttpClientConfiguration(naafWsCerts);
        this.pwmHttpClient = new PwmHttpClient(pwmApplication, null, pwmHttpClientConfiguration);
        establishEndpointSession();
    }

    public void establishEndpointSession()
            throws PwmUnrecoverableException
    {
        LOGGER.debug("establishing endpoint connection to " + endpointURL);
        final String m1 = id + salt;
        final String m1Hash = SecureEngine.hash(m1, PwmHashAlgorithm.SHA256).toLowerCase();
        final String m2 = secret + m1Hash;
        final String m2Hash = SecureEngine.hash(m2, PwmHashAlgorithm.SHA256).toLowerCase();

        final HashMap<String, Object> initConnectMap = new HashMap<>();
        initConnectMap.put("salt", salt);
        initConnectMap.put("endpoint_secret_hash", m2Hash);
        initConnectMap.put("session_data", new HashMap<String, String>());

        final PwmHttpClientResponse response = makeApiRequest(
                HttpMethod.POST,
                "/endpoints/" + id + "/sessions",
                initConnectMap
        );

        final String body = response.getBody();
        final Map<String, String> responseValues = JsonUtil.deserializeStringMap(body);

        endpoint_session_id = responseValues.get("endpoint_session_id");
        LOGGER.debug("endpoint connection established to " + endpointURL + ", endpoint_session_id=" + endpoint_session_id);
    }

    String getEndpoint_session_id() {
        return endpoint_session_id;
    }

    String getEndpointURL() {
        return endpointURL;
    }

    PwmHttpClient getPwmHttpClient() {
        return pwmHttpClient;
    }

    public List<NAAFChainBean> readChains(final String username) throws PwmUnrecoverableException {
        final Map<String, String> urlParams = new LinkedHashMap<>();
        urlParams.put("username", username);
        urlParams.put("application", "NAM");
        urlParams.put("is_trusted", "true");
        urlParams.put("endpoint_session_id", this.getEndpoint_session_id());

        final String url = ServletHelper.appendAndEncodeUrlParameters( "/logon/chains", urlParams );

        final PwmHttpClientResponse response = makeApiRequest(
                HttpMethod.POST,
                url,
                null
        );

        final NAAFChainInformationResponseBean naafChainInformationResponseBean = JsonUtil.deserialize(
                response.getBody(),
                NAAFChainInformationResponseBean.class
        );

        return naafChainInformationResponseBean.getChains();
    }


    PwmHttpClientResponse makeApiRequest(
            final HttpMethod method,
            final String urlPart,
            final Serializable body
    )
            throws PwmUnrecoverableException
    {
        final Map<String,String> headers = new HashMap<>();
        headers.put(PwmConstants.HttpHeader.Content_Type.getHttpName(), "application/json");
        if (locale != null) {
            headers.put(PwmConstants.HttpHeader.Accept_Language.getHttpName(), locale.toLanguageTag());
        }

        final PwmHttpClientRequest pwmHttpClientRequest = new PwmHttpClientRequest(
                method,
                getEndpointURL() + urlPart,
                JsonUtil.serialize(body),
                headers
        );
        return pwmHttpClient.makeRequest(pwmHttpClientRequest);

    }
}

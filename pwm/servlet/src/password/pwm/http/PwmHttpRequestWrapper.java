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

package password.pwm.http;

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.config.Configuration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

public abstract class PwmHttpRequestWrapper {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmHttpRequestWrapper.class);

    private final HttpServletRequest httpServletRequest;
    private final Configuration configuration;

    protected PwmHttpRequestWrapper(HttpServletRequest request, final Configuration configuration)
    {
        this.httpServletRequest = request;
        this.configuration = configuration;
    }

    public HttpServletRequest getHttpServletRequest()
    {
        return this.httpServletRequest;
    }

    public boolean isJsonRequest() {
        final String acceptHeader = this.readHeaderValueAsString(PwmConstants.HttpHeader.Accept);
        return acceptHeader.contains(PwmConstants.AcceptValue.json.getHeaderValue());
    }

    public String getContextPath() {
        return httpServletRequest.getContextPath();
    }

    public String readRequestBodyAsString()
            throws IOException, PwmUnrecoverableException
    {
        final int maxChars = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_BODY_MAXREAD_LENGTH));
        return readRequestBodyAsString(maxChars);
    }

    public String readRequestBodyAsString(final int maxChars) throws IOException {
        final StringBuilder inputData = new StringBuilder();
        String line;
        try {
            final BufferedReader reader = this.getHttpServletRequest().getReader();
            while (((line = reader.readLine()) != null) && inputData.length() < maxChars) {
                inputData.append(line);
            }
        } catch (Exception e) {
            LOGGER.error("error reading request body stream: " + e.getMessage());
        }
        return inputData.toString();
    }

    public Map<String,String> readBodyAsJsonStringMap()
            throws IOException, PwmUnrecoverableException
    {
        final String bodyString = readRequestBodyAsString();
        final Map<String, String> inputMap = JsonUtil.deserializeStringMap(bodyString);

        final Map<String, String> outputMap = new LinkedHashMap<>();
        if (inputMap != null) {
            for (final String key : inputMap.keySet()) {
                if (key != null) {
                    final String sanitizedName = Validator.sanitizeInputValue(configuration, key, 1024);
                    final String sanitizedValue = Validator.sanitizeInputValue(configuration, inputMap.get(key),
                            1024 * 1024 * 10);
                    outputMap.put(sanitizedName, sanitizedValue);
                }
            }
        }

        return Collections.unmodifiableMap(outputMap);
    }

    public String readParameterAsString(final String name, final int maxLength)
            throws PwmUnrecoverableException
    {
        final List<String> results = readParameterAsStrings(name, maxLength);
        if (results == null || results.isEmpty()) {
            return "";
        }

        return results.iterator().next();
    }

    public PasswordData readParameterAsPassword(final String name)
            throws PwmUnrecoverableException
    {
        final String returnValue = readParameterAsString(name,null);
        return returnValue == null ? null : new PasswordData(returnValue);
    }

    public String readParameterAsString(final String name, final String valueIfNotPresent)
            throws PwmUnrecoverableException
    {
        final int maxLength = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));
        final String returnValue = readParameterAsString(name, maxLength);
        return returnValue == null || returnValue.isEmpty() ? valueIfNotPresent : returnValue;
    }

    public String readParameterAsString(final String name)
            throws PwmUnrecoverableException
    {
        final int maxLength = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));
        return readParameterAsString(name, maxLength);
    }

    public List<String> readParameterAsStrings(
            final String name,
            final int maxLength
    ) throws PwmUnrecoverableException {
        final HttpServletRequest req = this.getHttpServletRequest();

        final String[] rawValues = req.getParameterValues(name);
        if (rawValues == null || rawValues.length == 0) {
            return Collections.emptyList();
        }

        final List<String> resultSet = new ArrayList<>();
        for (final String rawValue : rawValues) {
            final String sanitizedValue = Validator.sanitizeInputValue(configuration, rawValue, maxLength);

            if (sanitizedValue.length() > 0) {
                resultSet.add(sanitizedValue);
            }
        }

        return resultSet;
    }

    public String readHeaderValueAsString(final PwmConstants.HttpHeader headerName) {
        final HttpServletRequest req = this.getHttpServletRequest();
        final String rawValue = req.getHeader(headerName.getHttpName());
        final String sanitizedInputValue = Validator.sanitizeInputValue(configuration, rawValue);
        return Validator.sanitizeHeaderValue(configuration, sanitizedInputValue);

    }

    public Map<String,List<String>> readHeaderValuesMap() {
        final HttpServletRequest req = this.getHttpServletRequest();
        final Map<String,List<String>> returnObj = new LinkedHashMap<>();

        for (final Enumeration<String> headerNameEnum = req.getHeaderNames();headerNameEnum.hasMoreElements();) {
            final String headerName = headerNameEnum.nextElement();
            final List<String> valueList = new ArrayList<>();
            for (final Enumeration<String> headerValueEnum = req.getHeaders(headerName); headerValueEnum.hasMoreElements();) {
                final String headerValue = headerValueEnum.nextElement();
                final String sanitizedInputValue = Validator.sanitizeInputValue(configuration, headerValue);
                final String sanitizedHeaderValue = Validator.sanitizeHeaderValue(configuration, sanitizedInputValue);
                if (sanitizedHeaderValue != null && !sanitizedHeaderValue.isEmpty()) {
                    valueList.add(sanitizedHeaderValue);
                }
            }
            if (!valueList.isEmpty()) {
                returnObj.put(headerName, Collections.unmodifiableList(valueList));
            }
        }
        return Collections.unmodifiableMap(returnObj);
    }

    public List<String> parameterNames() {
        final List<String> returnObj = new ArrayList();
        for (Enumeration nameEnum = getHttpServletRequest().getParameterNames(); nameEnum.hasMoreElements();) {
            final String paramName = nameEnum.nextElement().toString();
            final String returnName = Validator.sanitizeInputValue(configuration, paramName);
            returnObj.add(returnName);
        }
        return Collections.unmodifiableList(returnObj);
    }

    public Map<String,String> readParametersAsMap()
            throws PwmUnrecoverableException
    {
        final Map<String, String> returnObj = new HashMap<>();
        for (String paramName : parameterNames()) {
            final String paramValue = readParameterAsString(paramName);
            returnObj.put(paramName, paramValue);
        }
        return Collections.unmodifiableMap(returnObj);
    }

    public Map<String,List<String>> readMultiParametersAsMap()
            throws PwmUnrecoverableException
    {
        final int maxLength = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));
        final Map<String, List<String>> returnObj = new HashMap<>();
        for (String paramName : parameterNames()) {
            final List<String> values = readParameterAsStrings(paramName, maxLength);
            returnObj.put(paramName, values);
        }
        return Collections.unmodifiableMap(returnObj);
    }
}

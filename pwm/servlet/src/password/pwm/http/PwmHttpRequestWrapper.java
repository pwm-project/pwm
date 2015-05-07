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

package password.pwm.http;

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.config.Configuration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.*;

public abstract class PwmHttpRequestWrapper {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmHttpRequestWrapper.class);

    private final HttpServletRequest httpServletRequest;
    private final Configuration configuration;

    protected PwmHttpRequestWrapper(HttpServletRequest request, final Configuration configuration) {
        this.httpServletRequest = request;
        this.configuration = configuration;
    }

    public HttpServletRequest getHttpServletRequest() {
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
            throws IOException, PwmUnrecoverableException {
        final int maxChars = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_BODY_MAXREAD_LENGTH));
        return readRequestBodyAsString(maxChars);
    }

    public String readRequestBodyAsString(final int maxChars) 
            throws IOException 
    {
        final int BUFFER_SIZE = 1024;
        final StringBuilder inputData = new StringBuilder();
        try {
            final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            this.getHttpServletRequest().getInputStream(), 
                            Charset.forName("UTF8")
                    )
            );
            final char[] charBuffer = new char[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = reader.read(charBuffer)) > 0 && inputData.length() < maxChars) {
                inputData.append(charBuffer, 0, bytesRead);
            }
        } catch (Exception e) {
            LOGGER.error("error reading request body stream: " + e.getMessage());
        }
        return inputData.toString();
    }

    public Map<String, String> readBodyAsJsonStringMap()
            throws IOException, PwmUnrecoverableException {
        return readBodyAsJsonStringMap(false);
    }
    
    public Map<String, String> readBodyAsJsonStringMap(boolean bypassInputValidation)
            throws IOException, PwmUnrecoverableException 
    {
        final String bodyString = readRequestBodyAsString();
        final Map<String, String> inputMap = JsonUtil.deserializeStringMap(bodyString);

        final boolean trim = Boolean.parseBoolean(configuration.readAppProperty(AppProperty.SECURITY_INPUT_TRIM));
        final boolean passwordTrim = Boolean.parseBoolean(configuration.readAppProperty(AppProperty.SECURITY_INPUT_PASSWORD_TRIM));
        final int maxLength = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));

        final Map<String, String> outputMap = new LinkedHashMap<>();
        if (inputMap != null) {
            for (final String key : inputMap.keySet()) {
                if (key != null) {
                    final boolean passwordType = key.toLowerCase().contains("password");
                    String value;
                    value = bypassInputValidation 
                            ? inputMap.get(key) : 
                            Validator.sanitizeInputValue(configuration, inputMap.get(key), maxLength);
                    value = passwordType && passwordTrim ? value.trim() : value;
                    value = !passwordType && trim ? value.trim() : value;
                    
                    final String sanitizedName = Validator.sanitizeInputValue(configuration, key, maxLength);
                    outputMap.put(sanitizedName, value);
                }
            }
        }

        return Collections.unmodifiableMap(outputMap);
    }


    public PasswordData readParameterAsPassword(final String name)
            throws PwmUnrecoverableException 
    {
        final int maxLength = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));
        final boolean trim = Boolean.parseBoolean(configuration.readAppProperty(AppProperty.SECURITY_INPUT_TRIM));
        
        final String rawValue = httpServletRequest.getParameter(name);
        if (rawValue != null && !rawValue.isEmpty()) {
            final String decodedValue = decodeStringToDefaultCharSet(rawValue);
            final String sanitizedValue = Validator.sanitizeInputValue(configuration, decodedValue, maxLength);
            if (sanitizedValue != null) {
                final String trimmedVale = trim ? sanitizedValue.trim() : sanitizedValue;
                return new PasswordData(trimmedVale);
            }
        }
        return null;
    }

    public String readParameterAsString(final String name, final int maxLength)
            throws PwmUnrecoverableException {
        final List<String> results = readParameterAsStrings(name, maxLength);
        if (results == null || results.isEmpty()) {
            return "";
        }

        return results.iterator().next();
    }

    public String readParameterAsString(final String name, final String valueIfNotPresent)
            throws PwmUnrecoverableException {
        final int maxLength = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));
        final String returnValue = readParameterAsString(name, maxLength);
        return returnValue == null || returnValue.isEmpty() ? valueIfNotPresent : returnValue;
    }

    public boolean hasParameter(final String name)
            throws PwmUnrecoverableException {
        return this.getHttpServletRequest().getParameterMap().containsKey(name);
    }

    public String readParameterAsString(final String name)
            throws PwmUnrecoverableException {
        final int maxLength = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));
        return readParameterAsString(name, maxLength);
    }

    public boolean readParameterAsBoolean(final String name)
            throws PwmUnrecoverableException {
        final String strValue = readParameterAsString(name);
        return strValue != null && Boolean.parseBoolean(strValue);
    }

    public int readParameterAsInt(final String name, final int defaultValue)
            throws PwmUnrecoverableException {
        final String strValue = readParameterAsString(name);
        try {
            return Integer.parseInt(strValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public List<String> readParameterAsStrings(
            final String name,
            final int maxLength
    ) 
            throws PwmUnrecoverableException 
    {
        final HttpServletRequest req = this.getHttpServletRequest();
        final boolean trim = Boolean.parseBoolean(configuration.readAppProperty(AppProperty.SECURITY_INPUT_TRIM));
        final String[] rawValues = req.getParameterValues(name);
        if (rawValues == null || rawValues.length == 0) {
            return Collections.emptyList();
        }

        final List<String> resultSet = new ArrayList<>();
        for (final String rawValue : rawValues) {
            final String decodedValue = decodeStringToDefaultCharSet(rawValue);
            final String sanitizedValue = Validator.sanitizeInputValue(configuration, decodedValue, maxLength);

            if (sanitizedValue.length() > 0) {
                resultSet.add(trim ? sanitizedValue.trim() : sanitizedValue);
            }
        }

        return resultSet;
    }

    public String readHeaderValueAsString(final PwmConstants.HttpHeader headerName) {
        return readHeaderValueAsString(headerName.getHttpName());
    }

    public String readHeaderValueAsString(final String headerName) {
        final int maxChars = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));
        final HttpServletRequest req = this.getHttpServletRequest();
        final String rawValue = req.getHeader(headerName);
        final String sanitizedInputValue = Validator.sanitizeInputValue(configuration, rawValue, maxChars);
        return Validator.sanitizeHeaderValue(configuration, sanitizedInputValue);
    }

    public Map<String, List<String>> readHeaderValuesMap() {
        final int maxChars = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));
        final HttpServletRequest req = this.getHttpServletRequest();
        final Map<String, List<String>> returnObj = new LinkedHashMap<>();

        for (final Enumeration<String> headerNameEnum = req.getHeaderNames(); headerNameEnum.hasMoreElements(); ) {
            final String headerName = headerNameEnum.nextElement();
            final List<String> valueList = new ArrayList<>();
            for (final Enumeration<String> headerValueEnum = req.getHeaders(headerName); headerValueEnum.hasMoreElements(); ) {
                final String headerValue = headerValueEnum.nextElement();
                final String sanitizedInputValue = Validator.sanitizeInputValue(configuration, headerValue, maxChars);
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
        final int maxChars = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));
        final List<String> returnObj = new ArrayList();
        for (Enumeration nameEnum = getHttpServletRequest().getParameterNames(); nameEnum.hasMoreElements(); ) {
            final String paramName = nameEnum.nextElement().toString();
            final String returnName = Validator.sanitizeInputValue(configuration, paramName, maxChars);
            returnObj.add(returnName);
        }
        return Collections.unmodifiableList(returnObj);
    }

    public Map<String, String> readParametersAsMap()
            throws PwmUnrecoverableException {
        final Map<String, String> returnObj = new HashMap<>();
        for (String paramName : parameterNames()) {
            final String paramValue = readParameterAsString(paramName);
            returnObj.put(paramName, paramValue);
        }
        return Collections.unmodifiableMap(returnObj);
    }

    public Map<String, List<String>> readMultiParametersAsMap()
            throws PwmUnrecoverableException {
        final int maxLength = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_PARAM_MAX_READ_LENGTH));
        final Map<String, List<String>> returnObj = new HashMap<>();
        for (String paramName : parameterNames()) {
            final List<String> values = readParameterAsStrings(paramName, maxLength);
            returnObj.put(paramName, values);
        }
        return Collections.unmodifiableMap(returnObj);
    }

    public String readCookie(final String cookieName) {
        final int maxChars = Integer.parseInt(configuration.readAppProperty(AppProperty.HTTP_COOKIE_MAX_READ_LENGTH));
        final Cookie[] cookies = this.getHttpServletRequest().getCookies();
        for (final Cookie cookie : cookies) {
            if (cookie.getName() != null && cookie.getName().equals(cookieName)) {
                final String rawCookieValue = cookie.getValue();
                final String decodedCookieValue = StringUtil.urlDecode(rawCookieValue);
                return Validator.sanitizeInputValue(configuration, decodedCookieValue, maxChars);
            }
        }
        return null;
    }

    private static String decodeStringToDefaultCharSet(final String input) {
        String decodedValue = input;
        try {
            decodedValue = new String(input.getBytes("ISO-8859-1"), PwmConstants.DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("error decoding request parameter: " + e.getMessage());
        }
        return decodedValue;
    }

    public HttpMethod getMethod() {
        return HttpMethod.fromString(this.getHttpServletRequest().getMethod());
    }

}


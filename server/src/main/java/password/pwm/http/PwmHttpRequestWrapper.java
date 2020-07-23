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

package password.pwm.http;

import com.google.gson.JsonParseException;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.ServletUtility;
import password.pwm.util.Validator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PwmHttpRequestWrapper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmHttpRequestWrapper.class );

    private final HttpServletRequest httpServletRequest;
    private final Configuration configuration;

    private static final Set<String> HTTP_PARAM_DEBUG_STRIP_VALUES =
            Collections.unmodifiableSet( new HashSet<>( Arrays.asList(
                    "password",
                    PwmConstants.PARAM_TOKEN,
                    PwmConstants.PARAM_RESPONSE_PREFIX ) )
            );

    private static final Set<String> HTTP_HEADER_DEBUG_STRIP_VALUES =
            Collections.unmodifiableSet( new HashSet<>( Arrays.asList(
                    HttpHeader.Authorization.getHttpName() ) )
            );

    public enum Flag
    {
        BypassValidation
    }

    public PwmHttpRequestWrapper( final HttpServletRequest request, final Configuration configuration )
    {
        this.httpServletRequest = request;
        this.configuration = configuration;
    }

    public HttpServletRequest getHttpServletRequest( )
    {
        return this.httpServletRequest;
    }

    public boolean isJsonRequest( )
    {
        final String acceptHeader = this.readHeaderValueAsString( HttpHeader.Accept );
        return acceptHeader.contains( PwmConstants.AcceptValue.json.getHeaderValue() );
    }

    public boolean isHtmlRequest( )
    {
        final String acceptHeader = this.readHeaderValueAsString( HttpHeader.Accept );
        return acceptHeader.contains( PwmConstants.AcceptValue.html.getHeaderValue() ) || acceptHeader.contains( "*/*" );
    }

    public String getContextPath( )
    {
        return httpServletRequest.getContextPath();
    }

    public String readRequestBodyAsString( )
            throws IOException, PwmUnrecoverableException
    {
        final int maxChars = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_BODY_MAXREAD_LENGTH ) );
        return readRequestBodyAsString( maxChars );
    }

    public String readRequestBodyAsString( final int maxChars )
            throws IOException, PwmUnrecoverableException
    {
        return ServletUtility.readRequestBodyAsString( this.getHttpServletRequest(), maxChars );
    }

    public Map<String, String> readBodyAsJsonStringMap( final Flag... flags )
            throws IOException, PwmUnrecoverableException
    {
        final boolean bypassInputValidation = flags != null && Arrays.asList( flags ).contains( Flag.BypassValidation );
        final String bodyString = readRequestBodyAsString();
        final Map<String, String> inputMap = JsonUtil.deserializeStringMap( bodyString );

        final boolean trim = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.SECURITY_INPUT_TRIM ) );
        final boolean passwordTrim = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.SECURITY_INPUT_PASSWORD_TRIM ) );
        final int maxLength = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_PARAM_MAX_READ_LENGTH ) );

        final Map<String, String> outputMap = new LinkedHashMap<>();
        if ( inputMap != null )
        {
            for ( final Map.Entry<String, String> entry : inputMap.entrySet() )
            {
                final String key = entry.getKey();
                if ( key != null )
                {
                    final boolean passwordType = key.toLowerCase().contains( "password" );
                    String value;
                    value = bypassInputValidation
                            ? entry.getValue()
                            : Validator.sanitizeInputValue( configuration, entry.getValue(), maxLength );
                    value = passwordType && passwordTrim ? value.trim() : value;
                    value = !passwordType && trim ? value.trim() : value;

                    final String sanitizedName = Validator.sanitizeInputValue( configuration, key, maxLength );
                    outputMap.put( sanitizedName, value );
                }
            }
        }

        return Collections.unmodifiableMap( outputMap );
    }

    public Map<String, Object> readBodyAsJsonMap( final Flag... flags )
            throws IOException, PwmUnrecoverableException
    {
        final boolean bypassInputValidation = flags != null && Arrays.asList( flags ).contains( Flag.BypassValidation );
        final String bodyString = readRequestBodyAsString();
        final Map<String, Object> inputMap = JsonUtil.deserializeMap( bodyString );

        final boolean trim = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.SECURITY_INPUT_TRIM ) );
        final boolean passwordTrim = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.SECURITY_INPUT_PASSWORD_TRIM ) );
        final int maxLength = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_PARAM_MAX_READ_LENGTH ) );

        final Map<String, Object> outputMap = new LinkedHashMap<>();
        if ( inputMap != null )
        {
            for ( final Map.Entry<String, Object> entry : inputMap.entrySet() )
            {
                final String key = entry.getKey();
                if ( key != null )
                {
                    final boolean passwordType = key.toLowerCase().contains( "password" );
                    final Object value;
                    if ( inputMap.get( key ) instanceof String )
                    {
                        String stringValue = bypassInputValidation
                                ? ( String ) entry.getValue()
                                : Validator.sanitizeInputValue( configuration, ( String ) entry.getValue(), maxLength );
                        stringValue = passwordType && passwordTrim ? stringValue.trim() : stringValue;
                        stringValue = !passwordType && trim ? stringValue.trim() : stringValue;
                        value = stringValue;
                    }
                    else
                    {
                        value = entry.getValue();
                    }

                    final String sanitizedName = Validator.sanitizeInputValue( configuration, key, maxLength );
                    outputMap.put( sanitizedName, value );
                }
            }
        }

        return Collections.unmodifiableMap( outputMap );
    }

    public PasswordData readParameterAsPassword( final String name )
            throws PwmUnrecoverableException
    {
        final int maxLength = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_PARAM_MAX_READ_LENGTH ) );
        final boolean trim = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.SECURITY_INPUT_PASSWORD_TRIM ) );

        final String rawValue = httpServletRequest.getParameter( name );
        if ( rawValue != null && !rawValue.isEmpty() )
        {
            final String decodedValue = decodeStringToDefaultCharSet( rawValue );
            final String sanitizedValue = Validator.sanitizeInputValue( configuration, decodedValue, maxLength );
            if ( sanitizedValue != null )
            {
                final String trimmedVale = trim ? sanitizedValue.trim() : sanitizedValue;
                return new PasswordData( trimmedVale );
            }
        }
        return null;
    }

    public String readParameterAsString( final String name, final int maxLength, final Flag... flags )
            throws PwmUnrecoverableException
    {
        final List<String> results = readParameterAsStrings( name, maxLength, flags );
        if ( results == null || results.isEmpty() )
        {
            return "";
        }

        return results.iterator().next();
    }

    public String readParameterAsString( final String name, final String valueIfNotPresent )
            throws PwmUnrecoverableException
    {
        final int maxLength = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_PARAM_MAX_READ_LENGTH ) );
        final String returnValue = readParameterAsString( name, maxLength );
        return returnValue == null || returnValue.isEmpty() ? valueIfNotPresent : returnValue;
    }

    public boolean hasParameter( final String name )
            throws PwmUnrecoverableException
    {
        return this.getHttpServletRequest().getParameterMap().containsKey( name );
    }

    public String readParameterAsString( final String name, final Flag... flags )
            throws PwmUnrecoverableException
    {
        final int maxLength = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_PARAM_MAX_READ_LENGTH ) );
        return readParameterAsString( name, maxLength, flags );
    }

    public boolean readParameterAsBoolean( final String name )
            throws PwmUnrecoverableException
    {
        final String strValue = readParameterAsString( name );
        return strValue != null && Boolean.parseBoolean( strValue );
    }

    public <E extends Enum<E>> E readParameterAsEnum( final String name, final Class<E> enumClass, final E defaultValue )
            throws PwmUnrecoverableException
    {
        final String value = readParameterAsString( name, Flag.BypassValidation );
        return JavaHelper.readEnumFromString( enumClass, defaultValue, value );
    }

    public int readParameterAsInt( final String name, final int defaultValue )
            throws PwmUnrecoverableException
    {
        final String strValue = readParameterAsString( name );
        try
        {
            return Integer.parseInt( strValue );
        }
        catch ( final NumberFormatException e )
        {
            return defaultValue;
        }
    }

    public List<String> readParameterAsStrings(
            final String name,
            final int maxLength,
            final Flag... flags
    )
            throws PwmUnrecoverableException
    {
        final boolean bypassInputValidation = flags != null && Arrays.asList( flags ).contains( Flag.BypassValidation );
        final HttpServletRequest req = this.getHttpServletRequest();
        final boolean trim = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.SECURITY_INPUT_TRIM ) );
        final String[] rawValues = req.getParameterValues( name );
        if ( rawValues == null || rawValues.length == 0 )
        {
            return Collections.emptyList();
        }

        final List<String> resultSet = new ArrayList<>();
        for ( final String rawValue : rawValues )
        {
            final String decodedValue = decodeStringToDefaultCharSet( rawValue );
            final String sanitizedValue = bypassInputValidation
                    ? decodedValue
                    : Validator.sanitizeInputValue( configuration, decodedValue, maxLength );

            if ( sanitizedValue.length() > 0 )
            {
                resultSet.add( trim ? sanitizedValue.trim() : sanitizedValue );
            }
        }

        return resultSet;
    }

    public String readHeaderValueAsString( final HttpHeader headerName )
    {
        return readHeaderValueAsString( headerName.getHttpName() );
    }

    public String readHeaderValueAsString( final String headerName )
    {
        final int maxChars = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_PARAM_MAX_READ_LENGTH ) );
        final HttpServletRequest req = this.getHttpServletRequest();
        final String rawValue = req.getHeader( headerName );
        final String sanitizedInputValue = Validator.sanitizeInputValue( configuration, rawValue, maxChars );
        return Validator.sanitizeHeaderValue( configuration, sanitizedInputValue );
    }

    public Map<String, List<String>> readHeaderValuesMap( )
    {
        final int maxChars = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_PARAM_MAX_READ_LENGTH ) );
        final HttpServletRequest req = this.getHttpServletRequest();
        final Map<String, List<String>> returnObj = new LinkedHashMap<>();

        for ( final Enumeration<String> headerNameEnum = req.getHeaderNames(); headerNameEnum.hasMoreElements(); )
        {
            final String headerName = headerNameEnum.nextElement();
            final List<String> valueList = new ArrayList<>();
            for ( final Enumeration<String> headerValueEnum = req.getHeaders( headerName ); headerValueEnum.hasMoreElements(); )
            {
                final String headerValue = headerValueEnum.nextElement();
                final String sanitizedInputValue = Validator.sanitizeInputValue( configuration, headerValue, maxChars );
                final String sanitizedHeaderValue = Validator.sanitizeHeaderValue( configuration, sanitizedInputValue );
                if ( sanitizedHeaderValue != null && !sanitizedHeaderValue.isEmpty() )
                {
                    valueList.add( sanitizedHeaderValue );
                }
            }
            if ( !valueList.isEmpty() )
            {
                returnObj.put( headerName, Collections.unmodifiableList( valueList ) );
            }
        }
        return Collections.unmodifiableMap( returnObj );
    }

    public List<String> parameterNames( )
    {
        final int maxChars = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_PARAM_MAX_READ_LENGTH ) );
        final List<String> returnObj = new ArrayList();
        for ( final Enumeration nameEnum = getHttpServletRequest().getParameterNames(); nameEnum.hasMoreElements(); )
        {
            final String paramName = nameEnum.nextElement().toString();
            final String returnName = Validator.sanitizeInputValue( configuration, paramName, maxChars );
            returnObj.add( returnName );
        }
        return Collections.unmodifiableList( returnObj );
    }

    public Map<String, String> readParametersAsMap( )
            throws PwmUnrecoverableException
    {
        final Map<String, String> returnObj = new HashMap<>();
        for ( final String paramName : parameterNames() )
        {
            final String paramValue = readParameterAsString( paramName );
            returnObj.put( paramName, paramValue );
        }
        return Collections.unmodifiableMap( returnObj );
    }

    public Map<String, List<String>> readMultiParametersAsMap( )
            throws PwmUnrecoverableException
    {
        final int maxLength = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_PARAM_MAX_READ_LENGTH ) );
        final Map<String, List<String>> returnObj = new HashMap<>();
        for ( final String paramName : parameterNames() )
        {
            final List<String> values = readParameterAsStrings( paramName, maxLength );
            returnObj.put( paramName, values );
        }
        return Collections.unmodifiableMap( returnObj );
    }

    public String readCookie( final String cookieName )
    {
        final int maxChars = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_COOKIE_MAX_READ_LENGTH ) );
        final Cookie[] cookies = this.getHttpServletRequest().getCookies();
        if ( cookies != null )
        {
            for ( final Cookie cookie : cookies )
            {
                if ( cookie.getName() != null && cookie.getName().equals( cookieName ) )
                {
                    final String rawCookieValue = cookie.getValue();
                    final String decodedCookieValue = StringUtil.urlDecode( rawCookieValue );
                    return Validator.sanitizeInputValue( configuration, decodedCookieValue, maxChars );
                }
            }
        }

        return null;
    }

    private static String decodeStringToDefaultCharSet( final String input )
    {
        String decodedValue = input;
        try
        {
            decodedValue = new String( input.getBytes( "ISO-8859-1" ), PwmConstants.DEFAULT_CHARSET );
        }
        catch ( final UnsupportedEncodingException e )
        {
            LOGGER.error( () -> "error decoding request parameter: " + e.getMessage() );
        }
        return decodedValue;
    }

    public HttpMethod getMethod( )
    {
        return HttpMethod.fromString( this.getHttpServletRequest().getMethod() );
    }

    public Configuration getConfig( )
    {
        return configuration;
    }

    public String getURLwithoutQueryString( )
    {
        final HttpServletRequest req = this.getHttpServletRequest();
        final String requestUri = ( String ) req.getAttribute( "javax.servlet.forward.request_uri" );
        return ( requestUri == null ) ? req.getRequestURI() : requestUri;
    }

    public String debugHttpHeaders( )
    {
        final String lineSeparator = "\n";
        final StringBuilder sb = new StringBuilder();


        sb.append( "http" ).append( getHttpServletRequest().isSecure() ? "s " : " non-" ).append( "secure request headers: " );
        sb.append( lineSeparator );

        sb.append( debugOutputMapToString( readHeaderValuesMap(), HTTP_HEADER_DEBUG_STRIP_VALUES ) );

        return sb.toString();
    }

    private static String debugOutputMapToString(
            final Map<String, List<String>> input,
            final Collection<String> stripValues
    )
    {
        final String lineSeparator = "\n";

        final StringBuilder sb = new StringBuilder();
        for ( final Map.Entry<String, List<String>> entry : input.entrySet() )
        {
            final String paramName = entry.getKey();
            for ( final String paramValue : entry.getValue() )
            {
                sb.append( "  " ).append( paramName ).append( "=" );
                boolean strip = false;
                for ( final String stripValue : stripValues )
                {
                    if ( paramName.toLowerCase().contains( stripValue.toLowerCase() ) )
                    {
                        strip = true;
                    }
                }
                if ( strip )
                {
                    sb.append( PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT );
                }
                else
                {
                    sb.append( "'" );
                    sb.append( paramValue );
                    sb.append( "'" );
                }

                sb.append( lineSeparator );
            }
        }

        if ( sb.length() > 0 )
        {
            if ( lineSeparator.equals( sb.substring( sb.length() - lineSeparator.length(), sb.length() ) ) )
            {
                sb.delete( sb.length() - lineSeparator.length(), sb.length() );
            }
        }

        return sb.toString();
    }

    public String debugHttpRequestToString( final String extraText, final boolean includeHeaders )
            throws PwmUnrecoverableException
    {

        final StringBuilder sb = new StringBuilder();
        final HttpServletRequest req = this.getHttpServletRequest();

        sb.append( req.getMethod() );
        sb.append( " request for: " );
        sb.append( getURLwithoutQueryString() );

        if ( includeHeaders )
        {
            sb.append( "\n " );
            sb.append( debugHttpHeaders() );
            sb.append( "\n" );
            sb.append( " parameters:" );
        }

        if ( req.getParameterMap().isEmpty() )
        {
            sb.append( " (no params)" );
            if ( extraText != null )
            {
                sb.append( " " );
                sb.append( extraText );
            }
        }
        else
        {
            if ( extraText != null )
            {
                sb.append( " " );
                sb.append( extraText );
            }
            sb.append( "\n" );

            sb.append( debugOutputMapToString( this.readMultiParametersAsMap(), HTTP_PARAM_DEBUG_STRIP_VALUES ) );
        }

        return sb.toString();
    }


    public <T> T readBodyAsJsonObject( final Class<T> classOfT )
            throws IOException, PwmUnrecoverableException
    {
        final String json = readRequestBodyAsString();
        try
        {
            return JsonUtil.deserialize( json, classOfT );
        }
        catch ( final Exception e )
        {
            if ( e instanceof JsonParseException )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_REST_INVOCATION_ERROR, "unable to parse json body: " + e.getCause().getMessage() );
            }
            throw e;
        }
    }
}


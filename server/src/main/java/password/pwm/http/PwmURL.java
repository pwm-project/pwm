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

package password.pwm.http;

import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class PwmURL
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmURL.class );

    private final URI uri;
    private final String contextPath;
    private final AppConfig appConfig;

    private PwmURL(
            final URI uri,
            final String contextPath,
            final AppConfig appConfig
    )
    {

        this.uri = Objects.requireNonNull( uri ).normalize();
        this.contextPath = Objects.requireNonNull( contextPath );
        this.appConfig = Objects.requireNonNull( appConfig );
    }

    /**
     * Compare two uri strings for equality of 'base'.  Specifically, the schema, host and port
     * are compared for equality.
     *
     * @param uri1 uri to compare
     * @param uri2 uri to compare
     * @return true if schema, host and port of uri1 and uri2 are equal.
     */
    public static boolean compareUriBase( final String uri1, final String uri2 )
    {
        if ( uri1 == null && uri2 == null )
        {
            return true;
        }

        if ( uri1 == null || uri2 == null )
        {
            return false;
        }

        final URI parsedUri1 = URI.create( uri1 );
        final URI parsedUri2 = URI.create( uri2 );

        if ( !StringUtil.equals( parsedUri1.getScheme(), parsedUri2.getScheme() ) )
        {
            return false;
        }

        if ( !StringUtil.equals( parsedUri1.getHost(), parsedUri2.getHost() ) )
        {
            return false;
        }

        if ( parsedUri1.getPort() != parsedUri2.getPort() )
        {
            return false;
        }

        return true;
    }

    public static PwmURL create(
            final HttpServletRequest req
    )
            throws PwmUnrecoverableException
    {
        return new PwmURL(
                URI.create( req.getRequestURL().toString() ),
                req.getContextPath(),
                ContextManager.getPwmApplication( req ).getConfig() );
    }

    public static PwmURL create(
            final HttpServletRequest req,
            final AppConfig appConfig
    )
    {
        return new PwmURL(
                URI.create( req.getRequestURL().toString() ),
                req.getContextPath(),
                appConfig );
    }

    public static PwmURL create(
            final URI uri,
            final String contextPath,
            final AppConfig appConfig
    )
            throws PwmUnrecoverableException
    {
        return new PwmURL( uri, contextPath, appConfig );
    }

    public boolean isResourceURL( )
    {
        return checkIfStartsWithURL( List.of( PwmConstants.URL_PREFIX_PUBLIC + "/resources/" ) ) || isReferenceURL();
    }

    public boolean isReferenceURL( )
    {
        return checkIfMatchesURL(
                List.of( PwmConstants.URL_PREFIX_PUBLIC + "/reference" ) )
                || checkIfStartsWithURL( List.of( PwmConstants.URL_PREFIX_PUBLIC + "/reference/" ) );
    }


    public boolean isPrivateUrl( )
    {
        return checkIfStartsWithURL( List.of( PwmConstants.URL_PREFIX_PRIVATE + "/" ) );
    }

    public boolean isAdminUrl( )
    {
        return matches( PwmServletDefinition.Admin );
    }

    public boolean isIndexPage( )
    {
        return checkIfMatchesURL( List.of(
                "",
                "/",
                PwmConstants.URL_PREFIX_PRIVATE,
                PwmConstants.URL_PREFIX_PUBLIC,
                PwmConstants.URL_PREFIX_PRIVATE + "/",
                PwmConstants.URL_PREFIX_PUBLIC + "/"
        ) );
    }

    public boolean isPublicUrl( )
    {
        return checkIfStartsWithURL( List.of( PwmConstants.URL_PREFIX_PUBLIC + "/" ) );
    }

    public boolean isCommandServletURL( )
    {
        return matches( PwmServletDefinition.PublicCommand )
                || matches( PwmServletDefinition.PrivateCommand );

    }

    public boolean isRestService( )
    {
        return checkIfStartsWithURL( List.of( PwmConstants.URL_PREFIX_PUBLIC + "/rest/" ) );

    }

    public boolean isConfigManagerURL( )
    {
        return checkIfStartsWithURL( List.of( PwmConstants.URL_PREFIX_PRIVATE + "/config/" ) );
    }

    public boolean isConfigGuideURL( )
    {
        return matches( PwmServletDefinition.ConfigGuide );
    }


    public boolean isChangePasswordURL( )
    {
        return matches( PwmServletDefinition.PrivateChangePassword )
                || matches( PwmServletDefinition.PublicChangePassword );
    }

    public Optional<PwmServletDefinition> forServletDefinition()
    {
        for ( final PwmServletDefinition pwmServletDefinition : PwmServletDefinition.values() )
        {
            if ( checkIfStartsWithURL( pwmServletDefinition.urlPatterns() ) )
            {
                return Optional.of( pwmServletDefinition );
            }
        }
        return Optional.empty();
    }

    public boolean matches( final PwmServletDefinition servletDefinition )
    {
        return matches( Collections.singleton( servletDefinition ) );
    }

    public boolean matches( final Collection<PwmServletDefinition> servletDefinitions )
    {
        final Optional<PwmServletDefinition> foundDefinition = forServletDefinition();
        return foundDefinition.isPresent() && servletDefinitions.contains( foundDefinition.get() );
    }

    public boolean isLocalizable( )
    {
        return !isConfigGuideURL()
                && !isAdminUrl()
                && !isReferenceURL()
                && !isConfigManagerURL();
    }

    public String toString( )
    {
        return uri.toString();
    }

    private boolean checkIfStartsWithURL( final List<String> url )
    {
        final String servletRequestPath = pathMinusContextAndDomain();

        for ( final String loopURL : url )
        {
            if ( servletRequestPath.startsWith( loopURL ) )
            {
                return true;
            }
        }

        return false;
    }

    private boolean checkIfMatchesURL( final List<String> url )
    {
        final String servletRequestPath = pathMinusContextAndDomain();

        for ( final String loopURL : url )
        {
            if ( servletRequestPath.equals( loopURL ) )
            {
                return true;
            }
        }

        return false;
    }

    public List<String> splitPaths()
    {
        return splitPathString( this.uri.getPath() );
    }

    public static List<String> splitPathString( final String input )
    {
        if ( input == null )
        {
            return Collections.emptyList();
        }
        final List<String> urlSegments = new ArrayList<>( Arrays.asList( input.split( "/" ) ) );
        urlSegments.removeIf( StringUtil::isEmpty );
        return Collections.unmodifiableList( urlSegments );
    }

    public static String appendAndEncodeUrlParameters(
            final String inputUrl,
            final Map<String, String> parameters
    )
    {
        String output = inputUrl == null ? "" : inputUrl;

        if ( parameters != null )
        {
            for ( final Map.Entry<String, String> entry : parameters.entrySet() )
            {
                output = appendAndEncodeUrlParameters( output, entry.getKey(), entry.getValue() );
            }
        }

        return output;
    }

    public static String appendAndEncodeUrlParameters(
            final String inputUrl,
            final String paramName,
            final String value
    )
    {
        final StringBuilder output = new StringBuilder( inputUrl );
        final String encodedValue = value == null
                ? ""
                : StringUtil.urlEncode( value );

        output.append( output.toString().contains( "?" ) ? "&" : "?" );
        output.append( paramName );
        output.append( "=" );
        output.append( encodedValue );

        if ( output.charAt( 0 ) == '?' || output.charAt( 0 ) == '&' )
        {
            output.deleteCharAt( 0 );
        }

        return output.toString();
    }

    public static String encodeParametersToFormBody( final Map<String, String> parameters )
    {
        final StringBuilder output = new StringBuilder( );

        for ( final Map.Entry<String, String> entry : parameters.entrySet() )
        {
            final String paramName = entry.getKey();
            final String value = entry.getValue();
            final String encodedValue = value == null
                    ? ""
                    : StringUtil.urlEncode( value );

            output.append( output.length() > 0 ? "&" : "" );
            output.append( paramName );
            output.append( "=" );
            output.append( encodedValue );
        }

        return output.toString();
    }


    public static int portForUriSchema( final URI uri )
    {
        final int port = uri.getPort();
        if ( port < 1 )
        {
            return portForUriScheme( uri.getScheme() );
        }
        return port;
    }

    private static int portForUriScheme( final String scheme )
    {
        if ( scheme == null )
        {
            throw new NullPointerException( "scheme cannot be null" );
        }
        switch ( scheme )
        {
            case "http":
                return 80;

            case "https":
                return 443;

            case "ldap":
                return 389;

            case "ldaps":
                return 636;

            default:
                throw new IllegalArgumentException( "unknown scheme: " + scheme );
        }
    }

    public String getPostServletPath( final PwmServletDefinition pwmServletDefinition )
    {
        final String path = this.uri.getPath();
        for ( final String pattern : pwmServletDefinition.urlPatterns() )
        {
            final String patternWithContext = this.contextPath + pattern;
            if ( path.startsWith( patternWithContext ) )
            {
                return path.substring( patternWithContext.length() );
            }
        }
        return "";
    }

    public String determinePwmServletPath( )
    {
        final String requestPath = this.pathMinusContextAndDomain();
        for ( final PwmServletDefinition servletDefinition : PwmServletDefinition.values() )
        {
            for ( final String pattern : servletDefinition.urlPatterns() )
            {
                if ( requestPath.startsWith( pattern ) )
                {
                    return pattern;
                }
            }
        }
        return requestPath;
    }

    private String pathMinusContextAndDomain()
    {
        String path = this.uri.getPath();
        if ( path.startsWith( this.contextPath ) )
        {
            path = path.substring( this.contextPath.length() );
        }

        if ( appConfig.isMultiDomain() )
        {
            for ( final String domain : appConfig.getDomainIDs() )
            {
                final String testPath = '/' + domain;
                if ( path.startsWith( testPath ) )
                {
                    return path.substring( testPath.length() );
                }
            }
        }

        return path;
    }

    public static boolean testIfUrlMatchesAllowedPattern(
            final String testURI,
            final List<String> whiteList,
            final SessionLabel sessionLabel
    )
    {
        final String regexPrefix = "regex:";
        for ( final String loopFragment : whiteList )
        {
            if ( loopFragment.startsWith( regexPrefix ) )
            {
                try
                {
                    final String strPattern = loopFragment.substring( regexPrefix.length() );
                    final Pattern pattern = Pattern.compile( strPattern );
                    if ( pattern.matcher( testURI ).matches() )
                    {
                        LOGGER.debug( sessionLabel, () -> "positive URL match for regex pattern: " + strPattern );
                        return true;
                    }
                    else
                    {
                        LOGGER.trace( sessionLabel, () -> "negative URL match for regex pattern: " + strPattern );
                    }
                }
                catch ( final Exception e )
                {
                    LOGGER.error( sessionLabel, () -> "error while testing URL match for regex pattern: '" + loopFragment + "', error: " + e.getMessage() );
                }

            }
            else
            {
                if ( testURI.startsWith( loopFragment ) )
                {
                    LOGGER.debug( sessionLabel, () -> "positive URL match for pattern: " + loopFragment );
                    return true;
                }
                else
                {
                    LOGGER.trace( sessionLabel, () -> "negative URL match for pattern: " + loopFragment );
                }
            }
        }

        return false;
    }
}

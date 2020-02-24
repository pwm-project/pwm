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

import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class PwmURL
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmURL.class );

    private URI uri;
    private String contextPath;

    public PwmURL(
            final URI uri,
            final String contextPath
    )
    {
        Objects.requireNonNull( uri );
        this.uri = uri.normalize();
        this.contextPath = contextPath;
    }

    public PwmURL(
            final HttpServletRequest req
    )
    {
        this( URI.create( req.getRequestURL().toString() ), req.getContextPath() );
    }

    /**
     * Compare two uri strings for equality of 'base'.  Specifically, the schema, host and port
     * are compared for equality.
     *
     * @param uri1 uri to compare
     * @param uri2 uri to compare
     * @return true if scheama, host and port of uri1 and uri2 are equal.
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

    public boolean isLoginServlet( )
    {
        return isPwmServletURL( PwmServletDefinition.Login );
    }

    public boolean isResourceURL( )
    {
        return checkIfStartsWithURL( PwmConstants.URL_PREFIX_PUBLIC + "/resources/" ) || isReferenceURL();
    }

    public boolean isReferenceURL( )
    {
        return checkIfMatchesURL( PwmConstants.URL_PREFIX_PUBLIC + "/reference" ) || checkIfStartsWithURL( PwmConstants.URL_PREFIX_PUBLIC + "/reference/" );
    }

    public boolean isLogoutURL( )
    {
        return isPwmServletURL( PwmServletDefinition.Logout );
    }

    public boolean isForgottenPasswordServlet( )
    {
        return isPwmServletURL( PwmServletDefinition.ForgottenPassword );
    }

    public boolean isForgottenUsernameServlet( )
    {
        return isPwmServletURL( PwmServletDefinition.ForgottenUsername );
    }

    public boolean isUserActivationServlet( )
    {
        return isPwmServletURL( PwmServletDefinition.ActivateUser );
    }

    public boolean isNewUserRegistrationServlet( )
    {
        return isPwmServletURL( PwmServletDefinition.NewUser );
    }

    public boolean isOauthConsumer( )
    {
        return isPwmServletURL( PwmServletDefinition.OAuthConsumer );
    }

    public boolean isPrivateUrl( )
    {
        return checkIfStartsWithURL( PwmConstants.URL_PREFIX_PRIVATE + "/" );
    }

    public boolean isAdminUrl( )
    {
        return isPwmServletURL( PwmServletDefinition.Admin );
    }

    public boolean isIndexPage( )
    {
        return checkIfMatchesURL(
                "",
                "/",
                PwmConstants.URL_PREFIX_PRIVATE,
                PwmConstants.URL_PREFIX_PUBLIC,
                PwmConstants.URL_PREFIX_PRIVATE + "/",
                PwmConstants.URL_PREFIX_PUBLIC + "/"
        );
    }

    public boolean isPublicUrl( )
    {
        return checkIfStartsWithURL( PwmConstants.URL_PREFIX_PUBLIC + "/" );
    }

    public boolean isCommandServletURL( )
    {
        return isPwmServletURL( PwmServletDefinition.PublicCommand )
                || isPwmServletURL( PwmServletDefinition.PrivateCommand );

    }

    public boolean isRestService( )
    {
        return checkIfStartsWithURL( PwmConstants.URL_PREFIX_PUBLIC + "/rest/" );

    }

    public boolean isConfigManagerURL( )
    {
        return checkIfStartsWithURL( PwmConstants.URL_PREFIX_PRIVATE + "/config/" );
    }

    public boolean isClientApiServlet( )
    {
        return isPwmServletURL( PwmServletDefinition.ClientApi );
    }

    public boolean isConfigGuideURL( )
    {
        return isPwmServletURL( PwmServletDefinition.ConfigGuide );
    }

    public boolean isPwmServletURL( final PwmServletDefinition pwmServletDefinition )
    {
        return checkIfStartsWithURL( pwmServletDefinition.urlPatterns() );
    }

    public boolean isChangePasswordURL( )
    {
        return isPwmServletURL( PwmServletDefinition.PrivateChangePassword )
                || isPwmServletURL( PwmServletDefinition.PublicChangePassword );
    }

    public boolean isSetupResponsesURL( )
    {
        return isPwmServletURL( PwmServletDefinition.SetupResponses );
    }

    public boolean isSetupOtpSecretURL( )
    {
        return isPwmServletURL( PwmServletDefinition.SetupOtp );
    }

    public boolean isProfileUpdateURL( )
    {
        return isPwmServletURL( PwmServletDefinition.UpdateProfile );
    }

    public PwmServletDefinition forServletDefinition()
    {
        for ( final PwmServletDefinition pwmServletDefinition : PwmServletDefinition.values() )
        {
            if ( isPwmServletURL( pwmServletDefinition ) )
            {
                return pwmServletDefinition;
            }
        }
        return null;
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

    private boolean checkIfStartsWithURL( final String... url )
    {
        final String servletRequestPath = uri.getPath();
        if ( servletRequestPath == null )
        {
            return false;
        }

        for ( final String loopURL : url )
        {
            if ( servletRequestPath.startsWith( contextPath + loopURL ) )
            {
                return true;
            }
        }

        return false;
    }

    private boolean checkIfMatchesURL( final String... url )
    {
        final String servletRequestPath = uri.getPath();
        if ( servletRequestPath == null )
        {
            return false;
        }

        for ( final String loopURL : url )
        {
            final String testURL = contextPath + loopURL;
            if ( servletRequestPath.equals( testURL ) )
            {
                return true;
            }
        }

        return false;
    }

    public static List<String> splitPathString( final String input )
    {
        if ( input == null )
        {
            return Collections.emptyList();
        }
        final List<String> urlSegments = new ArrayList<>( Arrays.asList( input.split( "/" ) ) );
        for ( final Iterator<String> iterator = urlSegments.iterator(); iterator.hasNext(); )
        {
            final String segment = iterator.next();
            if ( segment == null || segment.isEmpty() )
            {
                iterator.remove();
            }
        }
        return urlSegments;
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
        final String requestPath = this.uri.getPath();
        for ( final PwmServletDefinition servletDefinition : PwmServletDefinition.values() )
        {
            for ( final String pattern : servletDefinition.urlPatterns() )
            {
                final String testPath = contextPath + pattern;
                if ( requestPath.startsWith( testPath ) )
                {
                    return testPath;
                }
            }
        }
        return requestPath;
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

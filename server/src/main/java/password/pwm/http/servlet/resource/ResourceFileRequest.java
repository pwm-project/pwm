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

package password.pwm.http.servlet.resource;

import lombok.Value;
import org.webjars.WebJarAssetLocator;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ResourceFileRequest
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ResourceFileRequest.class );

    private static final Map<String, String> WEB_JAR_VERSION_MAP = Map.copyOf( new WebJarAssetLocator().getWebJars() );

    /** Contains a list of all resources (files) found inside the resources folder of all JARs in the WAR's classpath. **/
    private static final Collection<String> WEB_JAR_ASSET_LIST = Collections.unmodifiableCollection( new ArrayList<>( new WebJarAssetLocator().listAssets() ) );

    private final HttpServletRequest httpServletRequest;
    private final DomainConfig domainConfig;
    private final ResourceServletConfiguration resourceServletConfiguration;

    private final Optional<FileResource> fileResource;

    ResourceFileRequest(
            final DomainConfig domainConfig,
            final ResourceServletConfiguration resourceServletConfiguration,
            final HttpServletRequest httpServletRequest
    )
            throws PwmUnrecoverableException
    {
        this.domainConfig = domainConfig;
        this.resourceServletConfiguration = resourceServletConfiguration;
        this.httpServletRequest = httpServletRequest;

        final String resourcePathUri = this.getRequestURI();
        final ServletContext servletContext = this.getHttpServletRequest().getServletContext();
        fileResource = resolveRequestedResource( domainConfig, servletContext, resourcePathUri, resourceServletConfiguration );
    }

    HttpServletRequest getHttpServletRequest()
    {
        return httpServletRequest;
    }

    ResourceServletConfiguration getResourceServletConfiguration()
    {
        return resourceServletConfiguration;
    }

    String getRequestURI()
    {
        return stripNonceFromURI( figureRequestPathMinusContext() );
    }

    String getReturnContentType()
            throws PwmUnrecoverableException
    {
        final boolean acceptsCompression = allowsCompression();
        final String rawContentType = getRawMimeType();
        return acceptsCompression ? rawContentType : rawContentType + ";charset=UTF-8";
    }

    Optional<FileResource> getRequestedFileResource()
            throws PwmUnrecoverableException
    {
        return fileResource;
    }

    private String getRawMimeType()
            throws PwmUnrecoverableException
    {
        if ( fileResource.isPresent() )
        {
            final String filename = fileResource.get().getName();
            final String contentType = this.httpServletRequest.getServletContext().getMimeType( filename );
            if ( contentType == null )
            {
                if ( filename.endsWith( ".woff2" ) )
                {
                    return "font/woff2";
                }
            }

            // If content type is unknown, then set the default value.
            // For all content types, see: http://www.w3schools.com/media/media_mimeref.asp
            // To add new content types, add new mime-mapping entry in web.xml.
            return contentType == null
                    ? HttpContentType.octetstream.getMimeType()
                    : contentType;
        }

        return HttpContentType.octetstream.getMimeType();
    }

    boolean allowsCompression()
            throws PwmUnrecoverableException
    {
        // If content type is text, then determine whether GZIP content encoding is supported by
        // the browser and expand content type with the one and right character encoding.
        if ( resourceServletConfiguration.isEnableGzip() )
        {
            final String contentType = getRawMimeType();
            if ( contentType.startsWith( "text" ) || contentType.contains( "javascript" ) )
            {
                final PwmHttpRequestWrapper pwmHttpRequestWrapper = new PwmHttpRequestWrapper( httpServletRequest, domainConfig.getAppConfig() );
                final String acceptEncoding = pwmHttpRequestWrapper.readHeaderValueAsString( HttpHeader.AcceptEncoding );
                return acceptEncoding != null && accepts( acceptEncoding, "gzip" );
            }
        }
        return false;
    }

    private String stripNonceFromURI(
            final String uriString
    )
    {
        if ( !resourceServletConfiguration.isEnablePathNonce() )
        {
            return uriString;
        }

        final Matcher theMatcher = resourceServletConfiguration.getNoncePattern().matcher( uriString );

        if ( theMatcher.find() )
        {
            return theMatcher.replaceFirst( "" );
        }

        return uriString;
    }

    private String figureRequestPathMinusContext()
    {
        final String requestURI = httpServletRequest.getRequestURI();
        return requestURI.substring( httpServletRequest.getContextPath().length() );
    }

    static String deriveEffectiveURI(
            final DomainConfig domainConfig,
            final String inputResourcePathUri
    )
    {
        // URL-decode the file name (might contain spaces and on) and prepare file object.
        String effectiveUri = StringUtil.urlDecode( inputResourcePathUri );

        // parse out the session key...
        if ( effectiveUri.contains( ";" ) )
        {
            effectiveUri = effectiveUri.substring( 0, effectiveUri.indexOf( ";" ) );
        }

        if ( domainConfig.getAppConfig().isMultiDomain() )
        {
            final String domainPrefix = "/" + domainConfig.getDomainID().stringValue();
            if ( effectiveUri.startsWith( domainPrefix ) )
            {
                effectiveUri = effectiveUri.substring( domainPrefix.length() );
            }
        }

        return effectiveUri;
    }

    static Optional<FileResource> resolveRequestedResource(
            final DomainConfig domainConfig,
            final ServletContext servletContext,
            final String inputResourcePathUri,
            final ResourceServletConfiguration resourceServletConfiguration
    )
            throws PwmUnrecoverableException
    {
        final String effectiveUri = deriveEffectiveURI( domainConfig, inputResourcePathUri );

        if ( !effectiveUri.startsWith( ResourceFileServlet.RESOURCE_PATH ) )
        {
            LOGGER.warn( () -> "illegal url request to " + effectiveUri );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "illegal url request" ) );
        }

        final ResourceRequestContext context = new ResourceRequestContext( effectiveUri, domainConfig, resourceServletConfiguration, servletContext );
        for ( final ResourceUriResolver resourceUriResolver : RESOLVERS )
        {
            final Optional<FileResource> resource = resourceUriResolver.resolveUri( context );
            if ( resource.isPresent() )
            {
                return resource;
            }
        }

        return Optional.empty();
    }

    @Value
    private static class ResourceRequestContext
    {
        private final String uri;
        private final DomainConfig domainConfig;
        private final ResourceServletConfiguration resourceServletConfiguration;
        private final ServletContext servletContext;
    }

    private interface ResourceUriResolver
    {
        Optional<FileResource> resolveUri( ResourceRequestContext resourceRequestContext )
                throws PwmUnrecoverableException;
    }

    private static final List<ResourceUriResolver> RESOLVERS = List.of(
            new ThemeUriResolver(),
            new BuiltInZipFileUriResolver(),
            new WebJarUriResolver(),
            new RealFileUriResolver(),
            new CustomZipFileUriResolver()
    );

    private static class ThemeUriResolver implements ResourceUriResolver
    {
        @Override
        public Optional<FileResource> resolveUri( final ResourceRequestContext resourceRequestContext )
        {
            final String effectiveUri = resourceRequestContext.getUri();
            final DomainConfig domainConfig = resourceRequestContext.getDomainConfig();

            final String embedThemeUrl = ResourceFileServlet.RESOURCE_PATH
                    + ResourceFileServlet.THEME_CSS_PATH.replace( ResourceFileServlet.TOKEN_THEME, ResourceFileServlet.EMBED_THEME );
            final String embedThemeMobileUrl = ResourceFileServlet.RESOURCE_PATH
                    + ResourceFileServlet.THEME_CSS_MOBILE_PATH.replace( ResourceFileServlet.TOKEN_THEME, ResourceFileServlet.EMBED_THEME );

            if ( effectiveUri.equalsIgnoreCase( embedThemeUrl ) )
            {
                return Optional.of( new ConfigSettingFileResource( PwmSetting.DISPLAY_CSS_EMBED, domainConfig, effectiveUri ) );
            }
            else if ( effectiveUri.equalsIgnoreCase( embedThemeMobileUrl ) )
            {
                return Optional.of( new ConfigSettingFileResource( PwmSetting.DISPLAY_CSS_MOBILE_EMBED, domainConfig, effectiveUri ) );
            }

            return Optional.empty();
        }
    }

    private static class BuiltInZipFileUriResolver implements ResourceUriResolver
    {
        @Override
        public Optional<FileResource> resolveUri( final ResourceRequestContext resourceRequestContext )
                throws PwmUnrecoverableException
        {
            final String effectiveUri = resourceRequestContext.getUri();
            final ResourceServletConfiguration resourceServletConfiguration = resourceRequestContext.getResourceServletConfiguration();

            // check files system zip files.
            final Map<String, ZipFile> zipResources = resourceServletConfiguration.getZipResources();
            for ( final Map.Entry<String, ZipFile> entry : zipResources.entrySet() )
            {
                final String path = entry.getKey();
                if ( effectiveUri.startsWith( path ) )
                {
                    final String zipSubPath = effectiveUri.substring( path.length() + 1 );
                    final ZipFile zipFile = entry.getValue();
                    final ZipEntry zipEntry = zipFile.getEntry( zipSubPath );
                    if ( zipEntry != null )
                    {
                        return Optional.of( new ZipFileResource( zipFile, zipEntry ) );
                    }
                }
                if ( effectiveUri.startsWith( zipResources.get( path ).getName() ) )
                {
                    LOGGER.warn( () -> "illegal url request to " + effectiveUri + " zip resource" );
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "illegal url request" ) );
                }
            }

            return Optional.empty();
        }
    }

    private static class WebJarUriResolver implements ResourceUriResolver
    {
        @Override
        public Optional<FileResource> resolveUri( final ResourceRequestContext resourceRequestContext )
        {
            final String effectiveUri = resourceRequestContext.getUri();
            final ServletContext servletContext = resourceRequestContext.getServletContext();

            if ( effectiveUri.startsWith( ResourceFileServlet.WEBJAR_BASE_URL_PATH ) )
            {
                // This allows us to override a webjar file, if needed.  Mostly helpful during development.
                final File file = new File( servletContext.getRealPath( effectiveUri ) );
                if ( file.exists() )
                {
                    return Optional.of( new RealFileResource( file ) );
                }

                final String remainingPath = effectiveUri.substring( ResourceFileServlet.WEBJAR_BASE_URL_PATH.length() );

                final String webJarName;
                final String webJarPath;
                {
                    final int slashIndex = remainingPath.indexOf( "/" );
                    if ( slashIndex < 0 )
                    {
                        return Optional.empty();
                    }
                    webJarName = remainingPath.substring( 0, slashIndex );
                    webJarPath = remainingPath.substring( slashIndex + 1 );
                }

                final String versionString = WEB_JAR_VERSION_MAP.get( webJarName );
                if ( versionString == null )
                {
                    return Optional.empty();
                }

                final String fullPath = ResourceFileServlet.WEBJAR_BASE_FILE_PATH + "/" + webJarName + "/" + versionString + "/" + webJarPath;
                if ( WEB_JAR_ASSET_LIST.contains( fullPath ) )
                {
                    final ClassLoader classLoader = servletContext.getClassLoader();
                    final InputStream inputStream = classLoader.getResourceAsStream( fullPath );

                    if ( inputStream != null )
                    {
                        return Optional.of( new InputStreamFileResource( inputStream, fullPath ) );
                    }
                }
            }

            return Optional.empty();
        }
    }

    @Value
    private static class InputStreamFileResource implements FileResource
    {
        private final InputStream inputStream;
        private final String name;

        @Override
        public long length( )
        {
            return 0;
        }

        @Override
        public Instant lastModified( )
        {
            return Instant.EPOCH;
        }
    }


    private static class RealFileUriResolver implements ResourceUriResolver
    {
        @Override
        public Optional<FileResource> resolveUri( final ResourceRequestContext resourceRequestContext )
                throws PwmUnrecoverableException
        {
            final String effectiveUri = resourceRequestContext.getUri();
            final ServletContext servletContext = resourceRequestContext.getServletContext();

            // convert to file.
            final String filePath = servletContext.getRealPath( effectiveUri );
            final File file = new File( filePath );

            if ( file.exists() )
            {
                verifyPath( file, servletContext );

                return Optional.of( new RealFileResource( file ) );
            }

            return Optional.empty();
        }

        private void verifyPath(
                final File file,
                final ServletContext servletContext
        )
                throws PwmUnrecoverableException
        {
            // figure top-most path allowed by request
            final String parentDirectoryPath = servletContext.getRealPath( ResourceFileServlet.RESOURCE_PATH );
            final File parentDirectory = new File( parentDirectoryPath );

            {
                //verify the requested page is a child of the servlet resource path.
                int recursions = 0;
                File recurseFile = file.getParentFile();
                while ( recurseFile != null && recursions < 100 )
                {
                    if ( parentDirectory.equals( recurseFile ) )
                    {
                        return;
                    }
                    recurseFile = recurseFile.getParentFile();
                    recursions++;
                }
            }

            LOGGER.warn( () -> "attempt to access file outside of servlet path " + file.getAbsolutePath() );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "illegal file path request" ) );
        }
    }

    private static class CustomZipFileUriResolver implements ResourceUriResolver
    {
        @Override
        public Optional<FileResource> resolveUri( final ResourceRequestContext resourceRequestContext )
                throws PwmUnrecoverableException
        {
            final String effectiveUri = resourceRequestContext.getUri();
            final ResourceServletConfiguration resourceServletConfiguration = resourceRequestContext.getResourceServletConfiguration();

            // check custom (configuration defined) zip file bundles
            final Map<String, FileResource> customResources = resourceServletConfiguration.getCustomFileBundle();
            for ( final Map.Entry<String, FileResource> entry : customResources.entrySet() )
            {
                final String customFileName = entry.getKey();
                final String testName = ResourceFileServlet.RESOURCE_PATH + "/" + customFileName;
                if ( testName.equals( effectiveUri ) )
                {
                    return Optional.of( entry.getValue() );
                }
            }

            return Optional.empty();
        }
    }



    /**
     * Returns true if the given accept header accepts the given value.
     *
     * @param acceptHeader The accept header.
     * @param toAccept     The value to be accepted.
     * @return True if the given accept header accepts the given value.
     */
    private static boolean accepts( final String acceptHeader, final String toAccept )
    {
        final String[] acceptValues = acceptHeader.split( "\\s*(,|;)\\s*" );
        Arrays.sort( acceptValues );
        return Arrays.binarySearch( acceptValues, toAccept ) > -1
                || Arrays.binarySearch( acceptValues, toAccept.replaceAll( "/.*$", "/*" ) ) > -1
                || Arrays.binarySearch( acceptValues, "*/*" ) > -1;
    }
}

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

package password.pwm.http.servlet.resource;

import org.webjars.WebJarAssetLocator;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ResourceFileRequest
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ResourceFileRequest.class );

    private static final Map<String, String> WEB_JAR_VERSION_MAP = Collections.unmodifiableMap( new HashMap<>( new WebJarAssetLocator().getWebJars() ) );

    /** Contains a list of all resources (files) found inside the resources folder of all JARs in the WAR's classpath. **/
    private static final Collection<String> WEB_JAR_ASSET_LIST = Collections.unmodifiableCollection( new ArrayList<>( new WebJarAssetLocator().listAssets() ) );

    private final HttpServletRequest httpServletRequest;
    private final Configuration configuration;
    private final ResourceServletConfiguration resourceServletConfiguration;

    private FileResource fileResource;

    ResourceFileRequest(
            final Configuration configuration,
            final ResourceServletConfiguration resourceServletConfiguration,
            final HttpServletRequest httpServletRequest
    )
    {
        this.configuration = configuration;
        this.resourceServletConfiguration = resourceServletConfiguration;
        this.httpServletRequest = httpServletRequest;
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

    FileResource getRequestedFileResource()
            throws PwmUnrecoverableException
    {
        if ( fileResource == null )
        {
            final String resourcePathUri = this.getRequestURI();
            final ServletContext servletContext = this.getHttpServletRequest().getServletContext();
            fileResource = resolveRequestedResource( configuration, servletContext, resourcePathUri, resourceServletConfiguration );
        }
        return fileResource;
    }

    private String getRawMimeType()
            throws PwmUnrecoverableException
    {
        final String filename = getRequestedFileResource().getName();
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
        return contentType == null ? "application/octet-stream" : contentType;
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
                final PwmHttpRequestWrapper pwmHttpRequestWrapper = new PwmHttpRequestWrapper( httpServletRequest, configuration );
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

    static FileResource resolveRequestedResource(
            final Configuration configuration,
            final ServletContext servletContext,
            final String resourcePathUri,
            final ResourceServletConfiguration resourceServletConfiguration
    )
            throws PwmUnrecoverableException
    {

        // URL-decode the file name (might contain spaces and on) and prepare file object.
        String filename = StringUtil.urlDecode( resourcePathUri );

        // parse out the session key...
        if ( filename.contains( ";" ) )
        {
            filename = filename.substring( 0, filename.indexOf( ";" ) );
        }


        if ( !filename.startsWith( ResourceFileServlet.RESOURCE_PATH ) )
        {
            final String filenameFinal = filename;
            LOGGER.warn( () -> "illegal url request to " + filenameFinal );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "illegal url request" ) );
        }

        {
            final String embedThemeUrl = ResourceFileServlet.RESOURCE_PATH
                    + ResourceFileServlet.THEME_CSS_PATH.replace( ResourceFileServlet.TOKEN_THEME, ResourceFileServlet.EMBED_THEME );
            final String embedThemeMobileUrl = ResourceFileServlet.RESOURCE_PATH
                    + ResourceFileServlet.THEME_CSS_MOBILE_PATH.replace( ResourceFileServlet.TOKEN_THEME, ResourceFileServlet.EMBED_THEME );

            if ( filename.equalsIgnoreCase( embedThemeUrl ) )
            {
                return new ConfigSettingFileResource( PwmSetting.DISPLAY_CSS_EMBED, configuration, filename );
            }
            else if ( filename.equalsIgnoreCase( embedThemeMobileUrl ) )
            {
                return new ConfigSettingFileResource( PwmSetting.DISPLAY_CSS_MOBILE_EMBED, configuration, filename );
            }
        }


        {
            final FileResource resource = handleWebjarURIs( servletContext, resourcePathUri );
            if ( resource != null )
            {
                return resource;
            }
        }

        {
            // check files system zip files.
            final Map<String, ZipFile> zipResources = resourceServletConfiguration.getZipResources();
            for ( final Map.Entry<String, ZipFile> entry : zipResources.entrySet() )
            {
                final String path = entry.getKey();
                if ( filename.startsWith( path ) )
                {
                    final String zipSubPath = filename.substring( path.length() + 1, filename.length() );
                    final ZipFile zipFile = entry.getValue();
                    final ZipEntry zipEntry = zipFile.getEntry( zipSubPath );
                    if ( zipEntry != null )
                    {
                        return new ZipFileResource( zipFile, zipEntry );
                    }
                }
                if ( filename.startsWith( zipResources.get( path ).getName() ) )
                {
                    final String filenameFinal = filename;
                    LOGGER.warn( () -> "illegal url request to " + filenameFinal + " zip resource" );
                    throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "illegal url request" ) );
                }
            }
        }

        // convert to file.
        final String filePath = servletContext.getRealPath( filename );
        final File file = new File( filePath );

        // figure top-most path allowed by request
        final String parentDirectoryPath = servletContext.getRealPath( ResourceFileServlet.RESOURCE_PATH );
        final File parentDirectory = new File( parentDirectoryPath );

        FileResource fileSystemResource = null;
        {
            //verify the requested page is a child of the servlet resource path.
            int recursions = 0;
            File recurseFile = file.getParentFile();
            while ( recurseFile != null && recursions < 100 )
            {
                if ( parentDirectory.equals( recurseFile ) )
                {
                    fileSystemResource = new RealFileResource( file );
                    break;
                }
                recurseFile = recurseFile.getParentFile();
                recursions++;
            }
        }

        if ( fileSystemResource == null )
        {
            LOGGER.warn( () -> "attempt to access file outside of servlet path " + file.getAbsolutePath() );
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "illegal file path request" ) );
        }

        if ( !fileSystemResource.exists() )
        {
            // check custom (configuration defined) zip file bundles
            final Map<String, FileResource> customResources = resourceServletConfiguration.getCustomFileBundle();
            for ( final Map.Entry<String, FileResource> entry : customResources.entrySet() )
            {
                final String customFileName = entry.getKey();
                final String testName = ResourceFileServlet.RESOURCE_PATH + "/" + customFileName;
                if ( testName.equals( resourcePathUri ) )
                {
                    return entry.getValue();
                }
            }
        }

        return fileSystemResource;
    }

    private static FileResource handleWebjarURIs(
            final ServletContext servletContext,
            final String resourcePathUri
    )
            throws PwmUnrecoverableException
    {
        if ( resourcePathUri.startsWith( ResourceFileServlet.WEBJAR_BASE_URL_PATH ) )
        {
            // This allows us to override a webjar file, if needed.  Mostly helpful during development.
            final File file = new File( servletContext.getRealPath( resourcePathUri ) );
            if ( file.exists() )
            {
                return new RealFileResource( file );
            }

            final String remainingPath = resourcePathUri.substring( ResourceFileServlet.WEBJAR_BASE_URL_PATH.length(), resourcePathUri.length() );

            final String webJarName;
            final String webJarPath;
            {
                final int slashIndex = remainingPath.indexOf( "/" );
                if ( slashIndex < 0 )
                {
                    return null;
                }
                webJarName = remainingPath.substring( 0, slashIndex );
                webJarPath = remainingPath.substring( slashIndex + 1, remainingPath.length() );
            }

            final String versionString = WEB_JAR_VERSION_MAP.get( webJarName );
            if ( versionString == null )
            {
                return null;
            }

            final String fullPath = ResourceFileServlet.WEBJAR_BASE_FILE_PATH + "/" + webJarName + "/" + versionString + "/" + webJarPath;
            if ( WEB_JAR_ASSET_LIST.contains( fullPath ) )
            {
                final ClassLoader classLoader = servletContext.getClassLoader();
                final InputStream inputStream = classLoader.getResourceAsStream( fullPath );

                if ( inputStream != null )
                {
                    return new InputStreamFileResource( inputStream, fullPath );
                }
            }
        }

        return null;
    }

    private static class InputStreamFileResource implements FileResource
    {
        private final InputStream inputStream;
        private final String fullPath;

        InputStreamFileResource( final InputStream inputStream, final String fullPath )
        {
            this.inputStream = inputStream;
            this.fullPath = fullPath;
        }

        @Override
        public InputStream getInputStream( ) throws IOException
        {
            return inputStream;
        }

        @Override
        public long length( )
        {
            return 0;
        }

        @Override
        public long lastModified( )
        {
            return 0;
        }

        @Override
        public boolean exists( )
        {
            return true;
        }

        @Override
        public String getName( )
        {
            return fullPath;
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

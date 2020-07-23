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

import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.FileValue;
import password.pwm.http.bean.ImmutableByteArray;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

class ResourceServletConfiguration
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ResourceServletConfiguration.class );

    // settings with default values, values are set by app properties.
    private int maxCacheItems = 100;
    private long cacheExpireSeconds = 60;
    private boolean enableGzip = false;
    private boolean enablePathNonce = false;
    private long maxCacheBytes = 1024;

    private final Map<String, ZipFile> zipResources = new HashMap<>();
    private final Map<String, FileResource> customFileBundle = new HashMap<>();
    private Pattern noncePattern;
    private String nonceValue;

    private ResourceServletConfiguration( )
    {
    }

    private ResourceServletConfiguration( final PwmApplication pwmApplication )
    {
        LOGGER.trace( () -> "initializing" );
        final Configuration configuration = pwmApplication.getConfig();
        maxCacheItems = Integer.parseInt( configuration.readAppProperty( AppProperty.HTTP_RESOURCES_MAX_CACHE_ITEMS ) );
        cacheExpireSeconds = Long.parseLong( configuration.readAppProperty( AppProperty.HTTP_RESOURCES_EXPIRATION_SECONDS ) );
        enableGzip = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.HTTP_RESOURCES_ENABLE_GZIP ) );
        enablePathNonce = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.HTTP_RESOURCES_ENABLE_PATH_NONCE ) );
        maxCacheBytes = Long.parseLong( configuration.readAppProperty( AppProperty.HTTP_RESOURCES_MAX_CACHE_BYTES ) );

        final String noncePrefix = configuration.readAppProperty( AppProperty.HTTP_RESOURCES_NONCE_PATH_PREFIX );
        noncePattern = Pattern.compile( noncePrefix + "[^/]*?/" );
        nonceValue = pwmApplication.getRuntimeNonce();

        final String zipFileResourceParam = configuration.readAppProperty( AppProperty.HTTP_RESOURCES_ZIP_FILES );
        if ( zipFileResourceParam != null && !zipFileResourceParam.isEmpty() )
        {
            final List<ConfiguredZipFileResource> configuredZipFileResources = JsonUtil.deserialize( zipFileResourceParam, new TypeToken<ArrayList<ConfiguredZipFileResource>>()
            {
            } );
            for ( final ConfiguredZipFileResource configuredZipFileResource : configuredZipFileResources )
            {
                final File webInfPath = pwmApplication.getPwmEnvironment().getContextManager().locateWebInfFilePath();
                if ( webInfPath != null )
                {
                    try
                    {
                        final File zipFileFile = new File(
                                webInfPath.getParentFile() + "/"
                                        + ResourceFileServlet.RESOURCE_PATH
                                        + configuredZipFileResource.getZipFile()
                        );
                        final ZipFile zipFile = new ZipFile( zipFileFile );
                        zipResources.put( ResourceFileServlet.RESOURCE_PATH + configuredZipFileResource.getUrl(), zipFile );
                        LOGGER.debug( () -> "registered resource-zip file " + configuredZipFileResource.getZipFile() + " at path " + zipFileFile.getAbsolutePath() );
                    }
                    catch ( final IOException e )
                    {
                        LOGGER.warn( () -> "unable to resource-zip file " + configuredZipFileResource + ", error: " + e.getMessage() );
                    }
                }
                else
                {
                    LOGGER.error( () -> "can't register resource-zip file " + configuredZipFileResource.getZipFile() + " because WEB-INF path is unknown" );
                }
            }
        }

        final Map<FileValue.FileInformation, FileValue.FileContent> files = configuration.readSettingAsFile( PwmSetting.DISPLAY_CUSTOM_RESOURCE_BUNDLE );
        if ( files != null && !files.isEmpty() )
        {
            final Map.Entry<FileValue.FileInformation, FileValue.FileContent> entry = files.entrySet().iterator().next();
            final FileValue.FileInformation fileInformation = entry.getKey();
            final FileValue.FileContent fileContent = entry.getValue();
            LOGGER.debug( () -> "examining configured zip file resource for items name=" + fileInformation.getFilename() + ", size=" + fileContent.size() );

            try
            {
                final Map<String, FileResource> customFiles = makeMemoryFileMapFromZipInput( fileContent.getContents() );
                customFileBundle.clear();
                customFileBundle.putAll( customFiles );
            }
            catch ( final IOException e )
            {
                LOGGER.error( () -> "error assembling memory file map zip bundle: " + e.getMessage() );
            }
        }


    }

    static ResourceServletConfiguration createResourceServletConfiguration( final PwmApplication pwmApplication )
    {
        return new ResourceServletConfiguration( pwmApplication );
    }

    static ResourceServletConfiguration defaultConfiguration( )
    {
        return new ResourceServletConfiguration();
    }

    long getCacheExpireSeconds( )
    {
        return cacheExpireSeconds;
    }

    boolean isEnableGzip( )
    {
        return enableGzip;
    }

    boolean isEnablePathNonce( )
    {
        return enablePathNonce;
    }

    long getMaxCacheBytes( )
    {
        return maxCacheBytes;
    }

    Map<String, ZipFile> getZipResources( )
    {
        return zipResources;
    }

    Map<String, FileResource> getCustomFileBundle( )
    {
        return customFileBundle;
    }

    Pattern getNoncePattern( )
    {
        return noncePattern;
    }

    String getNonceValue( )
    {
        return nonceValue;
    }

    int getMaxCacheItems( )
    {
        return maxCacheItems;
    }

    private static Map<String, FileResource> makeMemoryFileMapFromZipInput( final ImmutableByteArray content )
            throws IOException
    {
        final ZipInputStream stream = new ZipInputStream( new ByteArrayInputStream( content.copyOf() ) );
        final Map<String, FileResource> memoryMap = new HashMap<>();

        ZipEntry entry;
        while ( ( entry = stream.getNextEntry() ) != null )
        {
            if ( !entry.isDirectory() )
            {
                final String name = entry.getName();
                final long lastModified = entry.getTime();
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                IOUtils.copy( stream, byteArrayOutputStream );
                final ImmutableByteArray contents = ImmutableByteArray.of( byteArrayOutputStream.toByteArray() );
                memoryMap.put( name, new MemoryFileResource( name, contents, lastModified ) );
                {
                    final String finalEntry = entry.getName();
                    LOGGER.trace( () -> "discovered file in configured resource bundle: " + finalEntry );
                }
            }
        }
        return memoryMap;
    }

    private static class ConfiguredZipFileResource implements Serializable
    {
        private String url;
        private String zipFile;

        public String getUrl( )
        {
            return url;
        }

        public void setUrl( final String url )
        {
            this.url = url;
        }

        String getZipFile( )
        {
            return zipFile;
        }

        void setZipFile( final String zipFile )
        {
            this.zipFile = zipFile;
        }
    }
}

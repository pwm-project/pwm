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

package password.pwm.http.servlet.resource;

import lombok.Value;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import password.pwm.AppProperty;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.FileValue;
import password.pwm.http.bean.ImmutableByteArray;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@Value
class ResourceServletConfiguration
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ResourceServletConfiguration.class );

    // settings with default values, values are set by app properties.
    private int maxCacheItems;
    private long cacheExpireSeconds;
    private boolean enableGzip;
    private boolean enablePathNonce;
    private long maxCacheBytes;

    private final Map<String, ZipFile> zipResources;
    private final Map<String, FileResource> customFileBundle;
    private Pattern noncePattern;
    private String nonceValue;

    private ResourceServletConfiguration()
    {
        maxCacheItems = 100;
        cacheExpireSeconds = 60;
        enableGzip = false;
        enablePathNonce = false;
        maxCacheBytes = 1024;

        zipResources = Collections.emptyMap();
        customFileBundle = Collections.emptyMap();
        noncePattern = null;
        nonceValue = null;
    }

    private ResourceServletConfiguration( final SessionLabel sessionLabel, final PwmDomain pwmDomain )
    {
        LOGGER.trace( sessionLabel, () -> "initializing" );
        final DomainConfig domainConfig = pwmDomain.getConfig();
        maxCacheItems = Integer.parseInt( domainConfig.readAppProperty( AppProperty.HTTP_RESOURCES_MAX_CACHE_ITEMS ) );
        cacheExpireSeconds = Long.parseLong( domainConfig.readAppProperty( AppProperty.HTTP_RESOURCES_EXPIRATION_SECONDS ) );
        enableGzip = Boolean.parseBoolean( domainConfig.readAppProperty( AppProperty.HTTP_RESOURCES_ENABLE_GZIP ) );
        enablePathNonce = Boolean.parseBoolean( domainConfig.readAppProperty( AppProperty.HTTP_RESOURCES_ENABLE_PATH_NONCE ) );
        maxCacheBytes = Long.parseLong( domainConfig.readAppProperty( AppProperty.HTTP_RESOURCES_MAX_CACHE_BYTES ) );

        final String noncePrefix = domainConfig.readAppProperty( AppProperty.HTTP_RESOURCES_NONCE_PATH_PREFIX );
        noncePattern = Pattern.compile( noncePrefix + "[^/]*?/" );
        nonceValue = pwmDomain.getPwmApplication().getRuntimeNonce();

        zipResources = makeZipResourcesFromConfig( sessionLabel, pwmDomain, domainConfig );

        customFileBundle = makeCustomFileBundle( sessionLabel, domainConfig );
    }

    private Map<String, FileResource> makeCustomFileBundle(
            final SessionLabel sessionLabel,
            final DomainConfig domainConfig )
    {
        final Map<String, FileResource> customFileBundle = new HashMap<>();
        final Map<FileValue.FileInformation, FileValue.FileContent> files = domainConfig.readSettingAsFile( PwmSetting.DISPLAY_CUSTOM_RESOURCE_BUNDLE );
        if ( !CollectionUtil.isEmpty( files ) )
        {
            final Map.Entry<FileValue.FileInformation, FileValue.FileContent> entry = files.entrySet().iterator().next();
            final FileValue.FileInformation fileInformation = entry.getKey();
            final FileValue.FileContent fileContent = entry.getValue();
            LOGGER.debug( sessionLabel, () -> "examining configured zip file resource for items name=" + fileInformation.getFilename() + ", size=" + fileContent.size() );

            try
            {
                final byte[] bytes = entry.getValue().getContents().copyOf();
                final String path = "/tmp/" + domainConfig.getDomainID().stringValue();
                FileUtils.writeByteArrayToFile( new File( path ), bytes );

                final Map<String, FileResource> customFiles = makeMemoryFileMapFromZipInput( sessionLabel, fileContent.getContents() );
                customFileBundle.putAll( customFiles );
            }
            catch ( final IOException e )
            {
                LOGGER.error( sessionLabel, () -> "error assembling memory file map zip bundle: " + e.getMessage() );
            }
        }
        return Collections.unmodifiableMap( customFileBundle );
    }

    private Map<String, ZipFile> makeZipResourcesFromConfig(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final DomainConfig domainConfig )
    {
        final Map<String, ZipFile> zipResources = new HashMap<>();
        final String zipFileResourceParam = domainConfig.getAppConfig().readAppProperty( AppProperty.HTTP_RESOURCES_ZIP_FILES );
        if ( StringUtil.notEmpty( zipFileResourceParam ) )
        {
            final List<ConfiguredZipFileResource> configuredZipFileResources = JsonFactory.get().deserializeList( zipFileResourceParam, ConfiguredZipFileResource.class );
            for ( final ConfiguredZipFileResource configuredZipFileResource : configuredZipFileResources )
            {
                final Optional<File> webInfPath = pwmDomain.getPwmApplication().getPwmEnvironment().getContextManager().locateWebInfFilePath();
                if ( webInfPath.isPresent() )
                {
                    try
                    {
                        final File zipFileFile = new File(
                                webInfPath.get().getParentFile() + "/"
                                        + ResourceFileServlet.RESOURCE_PATH
                                        + configuredZipFileResource.getZipFile()
                        );
                        final ZipFile zipFile = new ZipFile( zipFileFile );
                        zipResources.put( ResourceFileServlet.RESOURCE_PATH + configuredZipFileResource.getUrl(), zipFile );
                        LOGGER.debug( sessionLabel, () -> "registered resource-zip file " + configuredZipFileResource.getZipFile() + " at path " + zipFileFile.getAbsolutePath() );
                    }
                    catch ( final IOException e )
                    {
                        LOGGER.warn( sessionLabel, () -> "unable to resource-zip file " + configuredZipFileResource + ", error: " + e.getMessage() );
                    }
                }
                else
                {
                    LOGGER.error( sessionLabel, () -> "can't register resource-zip file " + configuredZipFileResource.getZipFile() + " because WEB-INF path is unknown" );
                }
            }
        }
        return Collections.unmodifiableMap( zipResources );
    }

    static ResourceServletConfiguration fromConfig( final SessionLabel sessionLabel, final PwmDomain pwmDomain )
    {
        return new ResourceServletConfiguration( sessionLabel, pwmDomain );
    }

    static ResourceServletConfiguration defaultConfiguration( )
    {
        return new ResourceServletConfiguration();
    }

    private static Map<String, FileResource> makeMemoryFileMapFromZipInput( final SessionLabel sessionLabel, final ImmutableByteArray content )
            throws IOException
    {
        final ZipInputStream stream = new ZipInputStream( content.newByteArrayInputStream() );
        final Map<String, FileResource> memoryMap = new HashMap<>();

        ZipEntry entry;
        while ( ( entry = stream.getNextEntry() ) != null )
        {
            if ( !entry.isDirectory() )
            {
                final String name = entry.getName();
                final Instant lastModified = Instant.ofEpochMilli( entry.getTime() );
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                IOUtils.copy( stream, byteArrayOutputStream );
                final ImmutableByteArray contents = ImmutableByteArray.of( byteArrayOutputStream.toByteArray() );
                memoryMap.put( name, new MemoryFileResource( name, contents, lastModified ) );
                {
                    final String finalEntry = entry.getName();
                    LOGGER.trace( sessionLabel, () -> "discovered file in configured resource bundle: " + finalEntry );
                }
            }
        }
        return memoryMap;
    }

    @Value
    private static class ConfiguredZipFileResource implements Serializable
    {
        private String url;
        private String zipFile;
    }
}

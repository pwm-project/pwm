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

package password.pwm.http.servlet.resource;

import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.FileValue;
import password.pwm.util.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

class ResourceServletConfiguration {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ResourceServletConfiguration.class);

    // settings with default values, values are set by app properties.
    private int maxCacheItems;
    private long cacheExpireSeconds;
    private boolean enableGzip = false;
    private boolean enablePathNonce = false;
    private long maxCacheBytes;

    private final Map<String, ZipFile> zipResources = new HashMap<>();
    private final Map<String, FileResource> customFileBundle = new HashMap<>();
    private Pattern noncePattern;
    private String nonceValue;

    public long getCacheExpireSeconds() {
        return cacheExpireSeconds;
    }

    public boolean isEnableGzip() {
        return enableGzip;
    }

    public boolean isEnablePathNonce() {
        return enablePathNonce;
    }

    public long getMaxCacheBytes() {
        return maxCacheBytes;
    }

    public Map<String, ZipFile> getZipResources() {
        return zipResources;
    }

    public Map<String, FileResource> getCustomFileBundle() {
        return customFileBundle;
    }

    public Pattern getNoncePattern() {
        return noncePattern;
    }

    public String getNonceValue() {
        return nonceValue;
    }

    public int getMaxCacheItems() {
        return maxCacheItems;
    }

    ResourceServletConfiguration(final PwmApplication pwmApplication) {
        LOGGER.trace("initializing");
        maxCacheItems = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_MAX_CACHE_ITEMS));
        cacheExpireSeconds = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_EXPIRATION_SECONDS));
        enableGzip = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_ENABLE_GZIP));
        enablePathNonce = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_ENABLE_PATH_NONCE));
        maxCacheBytes = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_MAX_CACHE_BYTES));

        final String noncePrefix = pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_NONCE_PATH_PREFIX);
        noncePattern = Pattern.compile(noncePrefix + "[^/]*?/");
        nonceValue = pwmApplication.getInstanceNonce();

        final String zipFileResourceParam = pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_ZIP_FILES);
        if (zipFileResourceParam != null && !zipFileResourceParam.isEmpty()) {
            final List<ConfiguredZipFileResource> configuredZipFileResources = JsonUtil.deserialize(zipFileResourceParam, new TypeToken<ArrayList<ConfiguredZipFileResource>>() {
            });
            for (final ConfiguredZipFileResource loopInitParam : configuredZipFileResources) {
                final File webInfPath = pwmApplication.getPwmEnvironment().getContextManager().locateWebInfFilePath();
                if (webInfPath != null) {
                    try {
                        final File zipFileFile = new File(webInfPath.getParentFile() + loopInitParam.getZipFile());
                        final ZipFile zipFile = new ZipFile(zipFileFile);
                        zipResources.put(loopInitParam.getUrl(), zipFile);
                        LOGGER.debug("registered resource-zip file " + loopInitParam.getZipFile() + " at path " + zipFileFile.getAbsolutePath());
                    } catch (IOException e) {
                        LOGGER.warn("unable to resource-zip file " + loopInitParam + ", error: " + e.getMessage());
                    }
                } else {
                    LOGGER.error("can't register resource-zip file " + loopInitParam.getZipFile() + " because WEB-INF path is unknown");
                }
            }
        }

        final Map<FileValue.FileInformation, FileValue.FileContent> files = pwmApplication.getConfig().readSettingAsFile(PwmSetting.DISPLAY_CUSTOM_RESOURCE_BUNDLE);
        if (files != null && !files.isEmpty()) {
            final FileValue.FileInformation fileInformation = files.keySet().iterator().next();
            final FileValue.FileContent fileContent = files.get(fileInformation);
            LOGGER.debug("examining configured zip file resource for items name=" + fileInformation.getFilename() + ", size=" + fileContent.size());

            try {
                final Map<String, FileResource> customFiles = makeMemoryFileMapFromZipInput(fileContent.getContents());
                customFileBundle.clear();
                customFileBundle.putAll(customFiles);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static Map<String, FileResource> makeMemoryFileMapFromZipInput(byte[] content)
            throws IOException
    {
        final ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(content));
        final Map<String, FileResource> memoryMap = new HashMap<>();

        ZipEntry entry;
        while ((entry = stream.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                final String name = entry.getName();
                final long lastModified = entry.getTime();
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                IOUtils.copy(stream,byteArrayOutputStream);
                final byte[] contents = byteArrayOutputStream.toByteArray();
                memoryMap.put(name,new MemoryFileResource(name,contents,lastModified));
                LOGGER.trace("discovered file in configured resource bundle: " + entry.getName());
            }
        }
        return memoryMap;
    }

    private static class ConfiguredZipFileResource implements Serializable {
        private String url;
        private String zipFile;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getZipFile() {
            return zipFile;
        }

        public void setZipFile(String zipFile) {
            this.zipFile = zipFile;
        }
    }
}

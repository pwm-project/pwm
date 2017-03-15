/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import org.apache.commons.io.output.NullOutputStream;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.EventRateMeter;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.Percent;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.ChecksumOutputStream;
import password.pwm.util.secure.PwmHashAlgorithm;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ResourceServletService implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ResourceServletService.class);


    private ResourceServletConfiguration resourceServletConfiguration;
    private Map<CacheKey, CacheEntry> cacheMap;
    private EventRateMeter.MovingAverage cacheHitRatio = new EventRateMeter.MovingAverage(60 * 60 * 1000);
    private String resourceNonce;
    private STATUS status = STATUS.NEW;

    private PwmApplication pwmApplication;

    public String getResourceNonce() {
        return resourceNonce;
    }

    public Map<CacheKey, CacheEntry> getCacheMap() {
        return cacheMap;
    }

    public EventRateMeter.MovingAverage getCacheHitRatio() {
        return cacheHitRatio;
    }


    public long bytesInCache() {
        final Map<CacheKey, CacheEntry> responseCache = getCacheMap();
        final Map<CacheKey, CacheEntry> cacheCopy = new HashMap<>();
        cacheCopy.putAll(responseCache);
        long cacheByteCount = 0;
        for (final CacheKey cacheKey : cacheCopy.keySet()) {
            final CacheEntry cacheEntry = responseCache.get(cacheKey);
            if (cacheEntry != null && cacheEntry.getEntity() != null) {
                cacheByteCount += cacheEntry.getEntity().length;
            }
        }
        return cacheByteCount;
    }

    public int itemsInCache() {
        final Map<CacheKey, CacheEntry> responseCache = getCacheMap();
        return responseCache.size();
    }

    public Percent cacheHitRatio() {
        final BigDecimal numerator = BigDecimal.valueOf(getCacheHitRatio().getAverage());
        final BigDecimal denominator = BigDecimal.ONE;
        return new Percent(numerator, denominator);
    }

    @Override
    public STATUS status() {
        return status;
    }

    @Override
    public void init(final PwmApplication pwmApplication) throws PwmException {
        this.pwmApplication = pwmApplication;
        status = STATUS.OPENING;
        try {
            this.resourceServletConfiguration = ResourceServletConfiguration.createResourceServletConfiguration(pwmApplication);

            cacheMap = new ConcurrentLinkedHashMap.Builder<CacheKey, CacheEntry>()
                    .maximumWeightedCapacity(resourceServletConfiguration.getMaxCacheItems())
                    .build();

            status = STATUS.OPEN;
        } catch (Exception e) {
            LOGGER.error("error during cache initialization, will remain closed; error: " + e.getMessage());
            status = STATUS.CLOSED;
            return;
        }

        try {
            resourceNonce = makeResourcePathNonce();
        } catch (Exception e) {
            LOGGER.error("error during nonce generation, will remain closed; error: " + e.getMessage());
            status = STATUS.CLOSED;
        }
    }

    @Override
    public void close() {

    }

    @Override
    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfo serviceInfo() {
        return null;
    }

    ResourceServletConfiguration getResourceServletConfiguration() {
        return resourceServletConfiguration;
    }

    private String makeResourcePathNonce()
            throws PwmUnrecoverableException, IOException
    {
        final boolean enablePathNonce = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_ENABLE_PATH_NONCE));
        if (!enablePathNonce) {
            return "";
        }

        final Instant startTime = Instant.now();
        final ChecksumOutputStream checksumOs = new ChecksumOutputStream(PwmHashAlgorithm.MD5, new NullOutputStream());

        if (pwmApplication.getPwmEnvironment().getContextManager() != null) {
            try {
                final File webInfPath = pwmApplication.getPwmEnvironment().getContextManager().locateWebInfFilePath();
                if (webInfPath != null && webInfPath.exists()) {
                    final File basePath = webInfPath.getParentFile();
                    if (basePath != null && basePath.exists()) {
                        final File resourcePath = new File(basePath.getAbsolutePath() + File.separator + "public" + File.separator + "resources");
                        if (resourcePath.exists()) {
                            final List<FileSystemUtility.FileSummaryInformation> fileSummaryInformations = new ArrayList<>();
                            fileSummaryInformations.addAll(FileSystemUtility.readFileInformation(resourcePath));
                            for (final FileSystemUtility.FileSummaryInformation fileSummaryInformation : fileSummaryInformations) {
                                checksumOs.write((fileSummaryInformation.getSha1sum()).getBytes(PwmConstants.DEFAULT_CHARSET));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("unable to generate resource path nonce: " + e.getMessage());
            }
        }

        for (final FileResource fileResource : getResourceServletConfiguration().getCustomFileBundle().values()) {
            JavaHelper.copyWhilePredicate(fileResource.getInputStream(), checksumOs, o -> true);
        }

        if (getResourceServletConfiguration().getZipResources() != null) {
            for (final String key : getResourceServletConfiguration().getZipResources().keySet()) {
                final ZipFile value = getResourceServletConfiguration().getZipResources().get(key);
                checksumOs.write(key.getBytes(PwmConstants.DEFAULT_CHARSET));
                for (Enumeration<? extends ZipEntry> zipEnum = value.entries(); zipEnum.hasMoreElements(); ) {
                    final ZipEntry entry = zipEnum.nextElement();
                    checksumOs.write(Long.toHexString(entry.getSize()).getBytes(PwmConstants.DEFAULT_CHARSET));
                }
            }
        }

        final String nonce = JavaHelper.byteArrayToHexString(checksumOs.getInProgressChecksum()).toLowerCase();
        LOGGER.debug("completed generation of nonce '" + nonce + "' in " + TimeDuration.fromCurrent(startTime).asCompactString());

        final String noncePrefix = pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_RESOURCES_NONCE_PATH_PREFIX);
        return "/" + noncePrefix + nonce;
    }

    public boolean checkIfThemeExists(final PwmRequest pwmRequest, final String themeName)
            throws PwmUnrecoverableException
    {
        if (themeName == null) {
            return false;
        }

        if (themeName.equals(ResourceFileServlet.EMBED_THEME)) {
            return true;
        }

        if (!themeName.matches(pwmRequest.getConfig().readAppProperty(AppProperty.SECURITY_INPUT_THEME_MATCH_REGEX))) {
            LOGGER.warn(pwmRequest, "discarding suspicious theme name in request: " + themeName);
            return false;
        }

        final ServletContext servletContext = pwmRequest.getHttpServletRequest().getServletContext();

        final String[] testUrls = new String[]{ ResourceFileServlet.THEME_CSS_PATH,  ResourceFileServlet.THEME_CSS_MOBILE_PATH };

        for (final String testUrl : testUrls) {
            final String themePathUrl = ResourceFileServlet.RESOURCE_PATH + testUrl.replace(ResourceFileServlet.TOKEN_THEME, themeName);
            final FileResource resolvedFile = ResourceFileServlet.resolveRequestedFile(servletContext, themePathUrl, getResourceServletConfiguration());
            if (resolvedFile != null && resolvedFile.exists()) {
                LOGGER.debug(pwmRequest, "check for theme validity of '" + themeName + "' returned true");
                return true;
            }
        }

        LOGGER.debug(pwmRequest, "check for theme validity of '" + themeName + "' returned false");
        return false;
    }
}

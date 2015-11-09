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

import org.apache.commons.io.IOUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.ServletHelper;
import password.pwm.http.servlet.PwmServlet;
import password.pwm.svc.stats.EventRateMeter;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@WebServlet(
        name="ResourceFileServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/resources/*"
        }
)
public class ResourceFileServlet extends HttpServlet implements PwmServlet {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ResourceFileServlet.class);


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
    {
        PwmRequest pwmRequest = null;
        try {
            pwmRequest = PwmRequest.forRequest(req, resp);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to satisfy request using standard mechanism, reverting to raw resource server");
        }

        if (pwmRequest != null) {
            try {
                processAction(pwmRequest);
            } catch (PwmUnrecoverableException e) {
                LOGGER.error(pwmRequest,"error during resource servlet request processing: " + e.getMessage());
            }
        } else {
            try {
                rawRequestProcessor(req,resp);
            } catch (PwmUnrecoverableException e) {
                LOGGER.error("error serving raw resource request: " + e.getMessage());
            }
        }
    }

    protected void rawRequestProcessor(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, PwmUnrecoverableException
    {

        final FileResource file = resolveRequestedFile(
                req.getServletContext(),
                req.getRequestURI(),
                req,
                Collections.<String, ZipFile>emptyMap(),
                Collections.<String, FileResource>emptyMap()
        );

        if (file == null || !file.exists()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        handleUncachedResponse(resp, file, false);
    }



    protected void processAction(PwmRequest pwmRequest)
            throws ServletException, IOException, PwmUnrecoverableException
    {
        if (pwmRequest.getMethod() != HttpMethod.GET) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"unable to process resource request for request method " + pwmRequest.getMethod()));
        }

        PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final ResourceServletService resourceService = pwmApplication.getResourceServletService();
        final ResourceServletConfiguration resourceConfiguration = resourceService.getResourceServletConfiguration();


        final String requestURI = stripNonceFromURI(resourceConfiguration, pwmRequest.getHttpServletRequest().getRequestURI());

        try {
            if ( handleSpecialURIs(pwmApplication, requestURI, pwmRequest.getHttpServletRequest(), pwmRequest.getPwmResponse().getHttpServletResponse(), resourceConfiguration)) {
                return;
            }
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "unexpected error detecting/handling special request uri: " + e.getMessage());
        }

        final FileResource file;
        try {
            file = resolveRequestedFile(this.getServletContext(), requestURI, pwmRequest.getHttpServletRequest(), resourceConfiguration.getZipResources(), resourceConfiguration.getCustomFileBundle());
        } catch (PwmUnrecoverableException e) {
            pwmRequest.getPwmResponse().getHttpServletResponse().sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            try {
                pwmRequest.debugHttpRequestToLog("returning HTTP 500 status");
            } catch (PwmUnrecoverableException e2) { /* noop */ }
            return;
        }

        if (file == null || !file.exists()) {
            pwmRequest.getPwmResponse().getHttpServletResponse().sendError(HttpServletResponse.SC_NOT_FOUND);
            try {
                pwmRequest.debugHttpRequestToLog("returning HTTP 404 status");
            } catch (PwmUnrecoverableException e) { /* noop */ }
            return;
        }

        // Get content type by file name and set default GZIP support and content disposition.
        String contentType = getServletContext().getMimeType(file.getName());
        boolean acceptsGzip = false;

        // If content type is unknown, then set the default value.
        // For all content types, see: http://www.w3schools.com/media/media_mimeref.asp
        // To add new content types, add new mime-mapping entry in web.xml.
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        // If content type is text, then determine whether GZIP content encoding is supported by
        // the browser and expand content type with the one and right character encoding.
        if (resourceConfiguration.isEnableGzip()) {
            if (contentType.startsWith("text") || contentType.contains("javascript")) {
                final String acceptEncoding = pwmRequest.readHeaderValueAsString(PwmConstants.HttpHeader.Accept_Encoding);
                acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");
                contentType += ";charset=UTF-8";
            }
        }

        final HttpServletResponse response = pwmRequest.getPwmResponse().getHttpServletResponse();
        final String eTagValue = resourceConfiguration.getNonceValue();

        {   // reply back with etag.
            final String ifNoneMatchValue = pwmRequest.readHeaderValueAsString(PwmConstants.HttpHeader.If_None_Match);
            if (ifNoneMatchValue != null && ifNoneMatchValue.equals(eTagValue)) {
                response.reset();
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                ServletHelper.addPwmResponseHeaders(pwmRequest, false);
                try {
                    pwmRequest.debugHttpRequestToLog("returning HTTP 304 status");
                } catch (PwmUnrecoverableException e2) { /* noop */ }
                return;
            }
        }

        // Initialize response.
        response.reset();
        response.setDateHeader("Expires", System.currentTimeMillis() + (resourceConfiguration.getCacheExpireSeconds() * 1000));
        response.setHeader("ETag", resourceConfiguration.getNonceValue());
        response.setContentType(contentType);

        // set pwm headers
        ServletHelper.addPwmResponseHeaders(pwmRequest, false);

        try {
            boolean fromCache = false;
            StringBuilder debugText = new StringBuilder();
            try {
                fromCache = handleCacheableResponse(resourceConfiguration, response, file, acceptsGzip, resourceService.getCacheMap());
                if (fromCache || acceptsGzip) {
                    debugText.append("(");
                    if (fromCache) debugText.append("cached");
                    if (fromCache && acceptsGzip) debugText.append(", ");
                    if (acceptsGzip) debugText.append("gzip");
                    debugText.append(")");
                } else {
                    debugText = new StringBuilder("(not cached)");
                }
                StatisticsManager.incrementStat(pwmApplication, Statistic.HTTP_RESOURCE_REQUESTS);
            } catch (UncacheableResourceException e) {
                handleUncachedResponse(response, file, acceptsGzip);
                debugText = new StringBuilder();
                debugText.append("(uncacheable");
                if (acceptsGzip) debugText.append(", gzip");
                debugText.append(")");
            }
            try {
                pwmRequest.debugHttpRequestToLog(debugText.toString());
            } catch (PwmUnrecoverableException e) {
                        /* noop */
            }

            final EventRateMeter.MovingAverage cacheHitRatio = resourceService.getCacheHitRatio();
            cacheHitRatio.update(fromCache ? 1 : 0);
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "error fulfilling response for url '" + requestURI + "', error: " + e.getMessage());
        }
    }

    private boolean handleCacheableResponse(
            final ResourceServletConfiguration resourceServletConfiguration,
            final HttpServletResponse response,
            final FileResource file,
            final boolean acceptsGzip,
            final Map<CacheKey, CacheEntry> responseCache
    )
            throws UncacheableResourceException, IOException
    {

        if (file.length() > resourceServletConfiguration.getMaxCacheBytes()) {
            throw new UncacheableResourceException("file to large to cache");
        }

        boolean fromCache = false;
        final CacheKey cacheKey = new CacheKey(file, acceptsGzip);
        CacheEntry cacheEntry = responseCache.get(cacheKey);
        if (cacheEntry == null) {
            final Map<String, String> headers = new HashMap<>();
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final InputStream input = file.getInputStream();

            try {
                if (acceptsGzip) {
                    final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(output);
                    headers.put("Content-Encoding", "gzip");
                    copy(input, gzipOutputStream);
                    close(gzipOutputStream);
                } else {
                    copy(input, output);
                }
            } finally {
                close(input);
                close(output);
            }

            final byte[] entity = output.toByteArray();
            headers.put("Content-Length", String.valueOf(entity.length));
            cacheEntry = new CacheEntry(entity, headers);
        } else {
            fromCache = true;
        }

        responseCache.put(cacheKey, cacheEntry);
        for (final String key : cacheEntry.getHeaderStrings().keySet()) {
            response.setHeader(key, cacheEntry.getHeaderStrings().get(key));
        }

        final OutputStream responseOutputStream = response.getOutputStream();
        try {
            copy(new ByteArrayInputStream(cacheEntry.getEntity()), responseOutputStream);
        } finally {
            close(responseOutputStream);
        }

        return fromCache;
    }

    private static void handleUncachedResponse(
            final HttpServletResponse response,
            final FileResource file,
            final boolean acceptsGzip
    ) throws IOException {
        // Prepare streams.
        OutputStream output = null;
        InputStream input = null;

        try {
            // Open streams.
            input = new BufferedInputStream(file.getInputStream());
            output = new BufferedOutputStream(response.getOutputStream());

            if (acceptsGzip) {
                // The browser accepts GZIP, so GZIP the content.
                response.setHeader("Content-Encoding", "gzip");
                output = new GZIPOutputStream(output);
            } else {
                // Content length is not directly predictable in case of GZIP.
                // So only add it if there is no means of GZIP, else browser will hang.
                response.setHeader("Content-Length", String.valueOf(file.length()));
            }

            // Copy full range.
            copy(input, output);
        } finally {
            // Gently close streams.
            close(output);
            close(input);
        }

    }

    /**
     * Returns true if the given accept header accepts the given value.
     *
     * @param acceptHeader The accept header.
     * @param toAccept     The value to be accepted.
     * @return True if the given accept header accepts the given value.
     */
    private static boolean accepts(final String acceptHeader, final String toAccept) {
        final String[] acceptValues = acceptHeader.split("\\s*(,|;)\\s*");
        Arrays.sort(acceptValues);
        return Arrays.binarySearch(acceptValues, toAccept) > -1
                || Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1
                || Arrays.binarySearch(acceptValues, "*/*") > -1;
    }

    /**
     * Copy the given byte range of the given input to the given output.
     *
     * @param input  The input to copy the given range to the given output for.
     * @param output The output to copy the given range from the given input for.
     * @throws IOException If something fails at I/O level.
     */
    private static void copy(final InputStream input, final OutputStream output)
            throws IOException
    {
        IOUtils.copy(input,output);
    }

    /**
     * Close the given resource.
     *
     * @param resource The resource to be closed.
     */
    private static void close(final Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException ignore) {
            }
        }
    }

    private static FileResource resolveRequestedFile(
            final ServletContext servletContext,
            final String requestURI,
            final HttpServletRequest request,
            final Map<String, ZipFile> zipResources,
            final Map<String, FileResource> customResources
    )
            throws UnsupportedEncodingException, PwmUnrecoverableException
    {
        // Get requested file by path info.
        final String requestFileURI = requestURI.substring(request.getContextPath().length(), requestURI.length());

        // URL-decode the file name (might contain spaces and on) and prepare file object.
        String filename = StringUtil.urlDecode(requestFileURI);

        // parse out the session key...
        if (filename.contains(";")) {
            filename = filename.substring(0, filename.indexOf(";"));
        }

        for (final String customFileName : customResources.keySet()) {
            final String testName = request.getContextPath() + "/public/resources/" + customFileName;
            if (testName.equals(requestURI)) {
                return customResources.get(customFileName);
            }
        }

        // check zip files.
        for (final String path : zipResources.keySet()) {
            if (filename.startsWith(path)) {
                final String zipSubPath = filename.substring(path.length() + 1, filename.length());
                final ZipFile zipFile = zipResources.get(path);
                final ZipEntry zipEntry = zipFile.getEntry(zipSubPath);
                if (zipEntry != null) {
                    return new ZipFileResource(zipFile, zipEntry);
                }
            }
        }

        // convert to file.
        final String filePath = servletContext.getRealPath(filename);
        final File file = new File(filePath);

        // figure top-most path allowed by request
        final String parentDirectoryPath = servletContext.getRealPath(request.getServletPath());
        final File parentDirectory = new File(parentDirectoryPath);

        { //verify the requested page is a child of the servlet resource path.
            int recursions = 0;
            File recurseFile = file.getParentFile();
            while (recurseFile != null && recursions < 100) {
                if (parentDirectory.equals(recurseFile)) {
                    return new RealFileResource(file);
                }
                recurseFile = recurseFile.getParentFile();
                recursions++;
            }
        }

        LOGGER.warn("attempt to access file outside of servlet path " + file.getAbsolutePath());
        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "illegal file path request"));
    }

    private boolean handleSpecialURIs(
            final PwmApplication pwmApplication,
            final String requestURI,
            final HttpServletRequest request,
            final HttpServletResponse response,
            final ResourceServletConfiguration resourceServletConfiguration
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        if (requestURI != null) {
            if (requestURI.startsWith(request.getContextPath() + "/public/resources/themes/embed/style.css")) {
                writeConfigSettingToBody(pwmApplication, PwmSetting.DISPLAY_CSS_EMBED, response, resourceServletConfiguration);
                return true;
            } else if (requestURI.startsWith(request.getContextPath() + "/public/resources/themes/embed/mobileStyle.css")) {
                writeConfigSettingToBody(pwmApplication, PwmSetting.DISPLAY_CSS_MOBILE_EMBED, response, resourceServletConfiguration);
                return true;
            }
        }
        return false;
    }

    private void writeConfigSettingToBody(
            final PwmApplication pwmApplication,
            final PwmSetting pwmSetting,
            final HttpServletResponse response,
            final ResourceServletConfiguration resourceServletConfiguration
    )
            throws PwmUnrecoverableException, IOException
    {
        final String bodyText = pwmApplication.getConfig().readSettingAsString(pwmSetting);
        try {
            response.setContentType("text/css");
            response.setDateHeader("Expires", System.currentTimeMillis() + (resourceServletConfiguration.getCacheExpireSeconds() * 1000));
            response.setHeader("Cache-Control", "public, max-age=" + resourceServletConfiguration.getCacheExpireSeconds());
            if (bodyText != null && bodyText.length() > 0) {
                response.setIntHeader("Content-Length", bodyText.length());
                copy(new ByteArrayInputStream(bodyText.getBytes()), response.getOutputStream());
            } else {
                response.setIntHeader("Content-Length", 0);
            }
        } finally {
            close(response.getOutputStream());
        }
    }

    private String stripNonceFromURI(
            final ResourceServletConfiguration resourceServletConfiguration,
            final String uriString
    )
    {
        if (!resourceServletConfiguration.isEnablePathNonce()) {
            return uriString;
        }

        final Matcher theMatcher = resourceServletConfiguration.getNoncePattern().matcher(uriString);

        if (theMatcher.find()) {
            return theMatcher.replaceFirst("");
        }

        return uriString;
    }
}
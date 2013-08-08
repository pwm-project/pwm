/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

/*
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package password.pwm.servlet;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ResourceFileServlet extends HttpServlet {

    private static final int BUFFER_SIZE = 10 * 1024; // 10k

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ResourceFileServlet.class);
    private static final Pattern NONCE_PATTERN = Pattern.compile(PwmConstants.RESOURCE_SERVLET_NONCE_PATH_PREFIX + "[^/]*?/");

    private final Map<String,ZipFile> zipResources = new HashMap<String,ZipFile>();

    public void init()
            throws ServletException
    {
        final ConcurrentMap<CacheKey, CacheEntry> newCacheMap = new ConcurrentLinkedHashMap.Builder<CacheKey, CacheEntry>()
                .maximumWeightedCapacity(PwmConstants.RESOURCE_SERVLET_MAX_CACHE_ITEMS)
                .build();
        this.getServletContext().setAttribute(PwmConstants.CONTEXT_ATTR_RESOURCE_CACHE,newCacheMap);

        final String zipFileResourceParam = this.getInitParameter("zipFileResources");
        if (zipFileResourceParam != null) {
            for (final String loopInitParam : zipFileResourceParam.split(";")) {
                if (!loopInitParam.endsWith(".zip")) {
                    LOGGER.warn("invalid zipFileResources parameter, must end in '.zip': " + loopInitParam);
                } else {
                    final String pathName = loopInitParam.substring(0, loopInitParam.length() - 4);
                    try {
                        final ZipFile zipFile = new ZipFile(this.getServletContext().getRealPath(loopInitParam));
                        zipResources.put(pathName, zipFile);
                    } catch (IOException e) {
                        LOGGER.warn("unable to load zip file resource for " + loopInitParam + ", error: " + e.getMessage());
                    }
                }
            }
        }
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    private static Map<CacheKey, CacheEntry> getCache(final ServletContext servletContext)  {
        return (Map<CacheKey,CacheEntry>)servletContext.getAttribute(PwmConstants.CONTEXT_ATTR_RESOURCE_CACHE);
    }

    public static void clearCache(final ServletContext servletContext) {
        getCache(servletContext).clear();
    }

    public void processRequest (
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws IOException {

        final PwmApplication pwmApplication;
        try {
            pwmApplication = ContextManager.getPwmApplication(request);
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn("error reading pwm session/application: " + e.getMessage());
            response.sendError(500,"error reading pwm session/application: " + e.getMessage());
            return;
        }

        final String requestURI = stripNonceFromURI(request.getRequestURI());

        try {
            if (handleSpecialURIs(requestURI, request, response)) {
                return;
            }
        } catch (Exception e) {
            LOGGER.error("unexpected error detecting/handling special request uri: " + e.getMessage());
        }

        final FileResource file = resolveRequestedFile(requestURI, request, zipResources);

        if (file == null || !file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            LOGGER.trace(ServletHelper.debugHttpRequest(request));
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
        if (PwmConstants.RESOURCE_SERVLET_ENABLE_GZIP) {
            if (contentType.startsWith("text") || contentType.contains("javascript")) {
                final String acceptEncoding = request.getHeader("Accept-Encoding");
                acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");
                contentType += ";charset=UTF-8";
            }
        }

        // Initialize response.
        response.reset();
        response.setBufferSize(BUFFER_SIZE);
        //response.setDateHeader("Expires", System.currentTimeMillis() + PwmConstants.RESOURCE_SERVLET_EXPIRATION_SECONDS * 1000);
        response.setHeader("Cache-Control","public, max-age=" + PwmConstants.RESOURCE_SERVLET_EXPIRATION_SECONDS);
        response.setHeader("ETag", makeNonce(pwmApplication));
        response.setHeader("Vary","Accept-Encoding");
        response.setContentType(contentType);

        // set pwm headers
        ServletHelper.addPwmResponseHeaders(pwmApplication, response, false);

        try {
            try {
                final boolean fromCache = handleCacheableResponse(response, file, acceptsGzip);
                if (fromCache || acceptsGzip) {
                    final StringBuilder debugText = new StringBuilder();
                    debugText.append("(");
                    if (fromCache) debugText.append("cached");
                    if (fromCache && acceptsGzip) debugText.append(", ");
                    if (acceptsGzip) debugText.append("gzip");
                    debugText.append(")");
                    LOGGER.trace(ServletHelper.debugHttpRequest(request,debugText.toString()));
                } else {
                    LOGGER.trace(ServletHelper.debugHttpRequest(request,"(not cached)"));
                }
            } catch (UncacheableResourceException e) {
                handleUncachedResponse(response, file, acceptsGzip);
                final StringBuilder debugText = new StringBuilder();
                debugText.append("(uncacheable");
                if (acceptsGzip) debugText.append(", gzip");
                debugText.append(")");
                LOGGER.trace(ServletHelper.debugHttpRequest(request,debugText.toString()));
            }
        } catch (Exception e) {
            LOGGER.error("error fulfilling response for url '" + requestURI + "', error: " + e.getMessage());
        }
    }

    public static long bytesInCache(final ServletContext servletContext) {
        final Map<CacheKey,CacheEntry> responseCache = getCache(servletContext);
        final Map<CacheKey,CacheEntry> cacheCopy = new HashMap<CacheKey, CacheEntry>();
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

    public static int itemsInCache(final ServletContext servletContext) {
        final Map<CacheKey,CacheEntry> responseCache = getCache(servletContext);
        return responseCache.size();
    }

    private boolean handleCacheableResponse(
            final HttpServletResponse response,
            final FileResource file,
            final boolean acceptsGzip
    ) throws
            UncacheableResourceException, IOException
    {
        final Map<CacheKey,CacheEntry> responseCache = getCache(getServletContext());

        if (file.length() > PwmConstants.RESOURCE_SERVLET_MAX_CACHE_BYTES) {
            throw new UncacheableResourceException("file to large to cache");
        }

        boolean fromCache = false;
        final CacheKey cacheKey = new CacheKey(file, acceptsGzip);
        CacheEntry cacheEntry = responseCache.get(cacheKey);
        if (cacheEntry == null) {
            final Map<String,String> headers = new HashMap<String,String>();
            final ByteArrayOutputStream output = new ByteArrayOutputStream(BUFFER_SIZE);
            final InputStream input = file.getInputStream();

            try {
                if (acceptsGzip) {
                    final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(output, BUFFER_SIZE);
                    headers.put("Content-Encoding", "gzip");
                    copy (input,gzipOutputStream);
                    close(gzipOutputStream);
                } else {
                    copy(input,output);
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

        responseCache.put(cacheKey,cacheEntry);
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
    ) throws IOException
    {
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
                output = new GZIPOutputStream(output, BUFFER_SIZE);
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

    // Helpers (can be refactored to public utility class) ----------------------------------------

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
            throws IOException {
        final byte[] buffer = new byte[BUFFER_SIZE];
        int read;

        while ((read = input.read(buffer)) > 0) {
            output.write(buffer, 0, read);
        }
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
                // Ignore IOException. If you want to handle this anyway, it might be useful to know
                // that this will generally only be thrown when the client aborted the request.
            }
        }
    }

    private static FileResource resolveRequestedFile(
            final String requestURI,
            final HttpServletRequest request,
            final Map<String,ZipFile> zipResources
    )
            throws UnsupportedEncodingException
    {
        final ServletContext servletContext = request.getSession().getServletContext();

        // Get requested file by path info.
        final String requestFileURI = requestURI.substring(request.getContextPath().length(), requestURI.length());

        // URL-decode the file name (might contain spaces and on) and prepare file object.
        String filename = URLDecoder.decode(requestFileURI, "UTF-8");

        // parse out the session key...
        if (filename.contains(";")) {
            filename = filename.substring(0, filename.indexOf(";"));
        }

        for (final String path : zipResources.keySet()) {
            if (filename.startsWith(path)) {
                final String zipSubPath = filename.substring(path.length() + 1,filename.length());
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
        return null;
    }

    private boolean handleSpecialURIs(
            final String requestURI,
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws PwmUnrecoverableException, IOException, ServletException {
        if (requestURI != null) {
            if (requestURI.startsWith(request.getContextPath() + "/public/resources/themes/embed/style.css")) {
                writeConfigSettingToBody(PwmSetting.DISPLAY_CSS_EMBED, request, response);
                return true;
            } else if (requestURI.startsWith(request.getContextPath() + "/public/resources/themes/embed/mobileStyle.css")) {
                writeConfigSettingToBody(PwmSetting.DISPLAY_CSS_MOBILE_EMBED, request, response);
                return true;
            }
        }
        return false;
    }

    private static void writeConfigSettingToBody(
            final PwmSetting pwmSetting,
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws PwmUnrecoverableException, IOException {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
        final String bodyText = pwmApplication.getConfig().readSettingAsString(pwmSetting);
        try {
            response.setContentType("text/css");
            response.setDateHeader("Expires", System.currentTimeMillis() + PwmConstants.RESOURCE_SERVLET_EXPIRATION_SECONDS * 1000);
            if (bodyText != null && bodyText.length() > 0) {
                response.setIntHeader("Content-Length", bodyText.length());
                copy(new ByteArrayInputStream(bodyText.getBytes()),response.getOutputStream());
            } else {
                response.setIntHeader("Content-Length", 0);
            }
        } finally {
            close(response.getOutputStream());
        }
    }

    private static final class UncacheableResourceException extends Exception {
        private UncacheableResourceException(String message) {
            super(message);
        }
    }

    private static final class CacheEntry implements Serializable {
        final private byte[] entity;
        final private Map<String,String> headerStrings;

        private CacheEntry(final byte[] entity, final Map<String,String> headerStrings) {
            this.entity = entity;
            this.headerStrings = headerStrings;
        }

        public byte[] getEntity() {
            return entity;
        }

        public Map<String,String> getHeaderStrings() {
            return headerStrings;
        }
    }

    private static final class CacheKey implements Serializable {
        final private String fileName;
        final private boolean acceptsGzip;
        final private long fileModificationTimestamp;

        private CacheKey(final FileResource file, final boolean acceptsGzip) {
            this.fileName = file.getName();
            this.acceptsGzip = acceptsGzip;
            this.fileModificationTimestamp = file.lastModified();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheKey cacheKey = (CacheKey) o;

            if (acceptsGzip != cacheKey.acceptsGzip) return false;
            if (fileModificationTimestamp != cacheKey.fileModificationTimestamp) return false;
            if (fileName != null ? !fileName.equals(cacheKey.fileName) : cacheKey.fileName != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = fileName != null ? fileName.hashCode() : 0;
            result = 31 * result + (acceptsGzip ? 1 : 0);
            result = 31 * result + (int) (fileModificationTimestamp ^ (fileModificationTimestamp >>> 32));
            return result;
        }
    }

    static interface FileResource {
        InputStream getInputStream() throws IOException;
        long length();
        long lastModified();
        boolean exists();
        String getName();
    }

    private static class ZipFileResource implements FileResource {
        private final ZipFile zipFile;
        private final ZipEntry zipEntry;

        private ZipFileResource(ZipFile zipFile, ZipEntry zipEntry) {
            this.zipFile = zipFile;
            this.zipEntry = zipEntry;
        }

        public InputStream getInputStream()
                throws IOException
        {
            return zipFile.getInputStream(zipEntry);
        }

        public long length() {
            return zipEntry.getSize();
        }

        public long lastModified() {
            return zipEntry.getTime();
        }

        public boolean exists() {
            return zipEntry != null && zipFile != null;
        }

        public String getName() {
            return zipFile.getName() + ":" + zipEntry.getName();
        }
    }

    private static class RealFileResource implements FileResource {
        private final File realFile;

        private RealFileResource(File realFile) {
            this.realFile = realFile;
        }

        public InputStream getInputStream() throws IOException {
            return new FileInputStream(realFile);
        }

        public long length() {
            return realFile.length();
        }

        public long lastModified() {
            return realFile.lastModified();
        }

        public boolean exists() {
            return realFile.exists();
        }

        public String getName() {
            return realFile.getAbsolutePath();
        }
    }

    private static String stripNonceFromURI(final String uriString) {
        if (!PwmConstants.RESOURCE_SERVLET_ENABLE_PATH_NONCE) {
            return uriString;
        }

        final Matcher theMatcher = NONCE_PATTERN.matcher(uriString);

        if (theMatcher.find()) {
            return theMatcher.replaceFirst("");
        }

        return uriString;
    }

    public static String makeResourcePathNonce(
            final PwmApplication pwmApplication
    )
    {
        if (PwmConstants.RESOURCE_SERVLET_ENABLE_PATH_NONCE) {
            return '/' + PwmConstants.RESOURCE_SERVLET_NONCE_PATH_PREFIX + makeNonce(pwmApplication);
        } else {
            return "";
        }
    }

    public static String makeNonce(final PwmApplication pwmApplication) {
        return Long.toString(pwmApplication.getStartupTime().getTime(),36);
    }
}
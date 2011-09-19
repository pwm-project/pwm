/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.TimeDuration;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class ResourceFileServlet extends HttpServlet {

    private static final int BUFFER_SIZE = 10 * 1024; // 10k
    private static final long DEFAULT_EXPIRE_TIME_MS = 10 * 60 * 1000; // 10 minutes
    private static final int DEFAULT_MAX_CACHE_FILE_SIZE = 50 * 1024; // 50k
    private static final int DEFAULT_MAX_CACHE_ITEM_LIMIT = 100; // 100 items

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ResourceFileServlet.class);

    private Map<CacheKey,CacheEntry> responseCache;

    private long expireTimeMs = DEFAULT_EXPIRE_TIME_MS;
    private int internalCacheItemLimit = DEFAULT_MAX_CACHE_ITEM_LIMIT;
    private int internalMaxCacheFileSize = DEFAULT_MAX_CACHE_FILE_SIZE;

    public void init() throws ServletException {
        try {
            expireTimeMs = Long.parseLong(this.getInitParameter("expireTimeMs"));
        } catch (Exception e) {
            LOGGER.warn("unable to parse 'expireTimeMs' servlet parameter: " + e.getMessage());
        }

        try {
            internalCacheItemLimit = Integer.parseInt(this.getInitParameter("internalCacheItemLimit"));
        } catch (Exception e) {
            LOGGER.warn("unable to parse 'internalCacheItemLimit' servlet parameter: " + e.getMessage());
        }

        try {
            internalMaxCacheFileSize = Integer.parseInt(this.getInitParameter("internalMaxCacheFileSize"));
        } catch (Exception e) {
            LOGGER.warn("unable to parse 'internalMaxCacheFileSize' servlet parameter: " + e.getMessage());
        }

        responseCache = new LinkedHashMap<CacheKey,CacheEntry>(internalCacheItemLimit, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<CacheKey, CacheEntry> entry) {
                return this.size() > internalCacheItemLimit;
            }
        };

        LOGGER.trace("using resource expire time of " + TimeDuration.asCompactString(expireTimeMs));
    }

    protected void doHead(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response, false);
    }

    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response, true);
    }

    private void processRequest
            (final HttpServletRequest request, final HttpServletResponse response, final boolean includeBody)
            throws IOException {

        final File file = resolveRequestedFile(request);

        PwmSession pwmSession = null;
        try {
            pwmSession = PwmSession.getPwmSession(request);
        } catch (PwmUnrecoverableException e) {
            // ignore
        }


        if (file == null || !file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
            return;
        }

        final String fileName = file.getName();
        final String eTag = makeETag(request, file);

        // If-None-Match header should contain "*" or ETag. If so, then return 304.
        final String ifNoneMatch = request.getHeader("If-None-Match");
        if (ifNoneMatch != null && matches(ifNoneMatch, eTag)) {
            response.setHeader("ETag", eTag); // Required in 304.
            response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        // If-Match header should contain "*" or ETag. If not, then return 412.
        final String ifMatch = request.getHeader("If-Match");
        if (ifMatch != null && !matches(ifMatch, eTag)) {
            response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }

        // Get content type by file name and set default GZIP support and content disposition.
        String contentType = getServletContext().getMimeType(fileName);
        boolean acceptsGzip = false;

        // If content type is unknown, then set the default value.
        // For all content types, see: http://www.w3schools.com/media/media_mimeref.asp
        // To add new content types, add new mime-mapping entry in web.xml.
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        // If content type is text, then determine whether GZIP content encoding is supported by
        // the browser and expand content type with the one and right character encoding.
        if (contentType.startsWith("text")) {
            final String acceptEncoding = request.getHeader("Accept-Encoding");
            acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");
            contentType += ";charset=UTF-8";
        }

        // Initialize response.
        response.reset();
        response.setBufferSize(BUFFER_SIZE);
        response.setDateHeader("Expires", System.currentTimeMillis() + expireTimeMs);
        response.setContentType(contentType);

        try {
            handleCachedResponse(response, file, includeBody, acceptsGzip);
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request,"(from cache)"));
        } catch (UncacheableResourceException e) {
            handleUncachedResponse(response, file, includeBody, acceptsGzip);
            LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));
        }
    }

    private void handleCachedResponse(
            final HttpServletResponse response,
            final File file,
            final boolean includeBody,
            final boolean acceptsGzip
    ) throws
            UncacheableResourceException, IOException {
        if (!includeBody) {
            throw new UncacheableResourceException();
        }

        if (file.length() > internalMaxCacheFileSize) {
            throw new UncacheableResourceException();
        }

        final CacheKey cacheKey = new CacheKey(file, acceptsGzip);
        CacheEntry cacheEntry = responseCache.get(cacheKey);
        if (cacheEntry == null) {
            final Map<String,String> headers = new HashMap<String,String>();
            final ByteArrayOutputStream output = new ByteArrayOutputStream(BUFFER_SIZE);
            final FileInputStream input = new FileInputStream(file);

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
    }

    private static void handleUncachedResponse(
            final HttpServletResponse response,
            final File file,
            final boolean includeBody,
            final boolean acceptsGzip
    ) throws IOException
    {
        // Prepare streams.
        OutputStream output = null;
        InputStream input = null;

        try {
            // Open streams.
            input = new FileInputStream(file);
            output = response.getOutputStream();

            if (includeBody) {
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
            }
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
     * Returns true if the given match header matches the given value.
     *
     * @param matchHeader The match header.
     * @param toMatch     The value to be matched.
     * @return True if the given match header matches the given value.
     */
    private static boolean matches(final String matchHeader, final String toMatch) {
        final String[] matchValues = matchHeader.split("\\s*,\\s*");
        Arrays.sort(matchValues);
        return Arrays.binarySearch(matchValues, toMatch) > -1
                || Arrays.binarySearch(matchValues, "*") > -1;
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

    private static String makeETag(final HttpServletRequest req, final File file) {
        final StringBuilder sb = new StringBuilder();

        String startupTime = null;
        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
            startupTime = String.valueOf(pwmApplication.getStartupTime().getTime());
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to load context startup time: " + e.getMessage());
        }

        sb.append(PwmConstants.BUILD_NUMBER);
        sb.append("-");
        sb.append(startupTime);
        sb.append("-");
        sb.append(file.length());
        sb.append("-");
        sb.append(file.lastModified());
        return sb.toString();
    }

    private static File resolveRequestedFile(final HttpServletRequest request)
            throws UnsupportedEncodingException {
        final ServletContext servletContext = request.getSession().getServletContext();

        // Get requested file by path info.
        final String requestURI = request.getRequestURI();
        final String requestFileURI = requestURI.substring(request.getContextPath().length(), requestURI.length());

        // URL-decode the file name (might contain spaces and on) and prepare file object.
        String filename = URLDecoder.decode(requestFileURI, "UTF-8");

        // parse out the session key...
        if (filename.contains(";")) {
            filename = filename.substring(0, filename.indexOf(";"));
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
                    return file;
                }
                recurseFile = recurseFile.getParentFile();
                recursions++;
            }
        }

        LOGGER.warn("attempt to access file outside of servlet path " + file.getAbsolutePath());
        return null;
    }

    private static final class UncacheableResourceException extends Exception {

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
        final private File file;
        final private boolean acceptsGzip;
        final private long fileModificationTimestamp;

        private CacheKey(final File file, final boolean acceptsGzip) {
            this.file = file;
            this.acceptsGzip = acceptsGzip;
            this.fileModificationTimestamp = file.lastModified();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final CacheKey cacheKey = (CacheKey) o;

            return acceptsGzip == cacheKey.acceptsGzip && fileModificationTimestamp == cacheKey.fileModificationTimestamp && !(file != null ? !file.equals(cacheKey.file) : cacheKey.file != null);
        }

        @Override
        public int hashCode() {
            int result = file != null ? file.hashCode() : 0;
            result = 31 * result + (acceptsGzip ? 1 : 0);
            result = 31 * result + (int) (fileModificationTimestamp ^ (fileModificationTimestamp >>> 32));
            return result;
        }
    }
}
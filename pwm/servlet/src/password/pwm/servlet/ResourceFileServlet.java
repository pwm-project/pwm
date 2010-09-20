/*
 * net/balusc/webapp/FileServlet.java
 *
 * Copyright (C) 2009 BalusC
 *
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

import password.pwm.Helper;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

/**
 * String Etag/Expire Time based file servlet request; used to get around tomcat's lame default
 * cache header handling.
 *
 * Based on http://balusc.blogspot.com/2009/02/fileservlet-supporting-resume-and.html
 */
public class ResourceFileServlet extends HttpServlet {

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 10; // 10k
    private static final long DEFAULT_EXPIRE_TIME = 1000 * 60 * 10; // 10 minutes

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ResourceFileServlet.class);

    private long expireTime;

    public void init() throws ServletException {
        expireTime = DEFAULT_EXPIRE_TIME;

        final String expireTimeStr = this.getInitParameter("expireTimeMs");
        if (expireTimeStr != null && expireTimeStr.length() > 0) {
            try {
                expireTime = Long.parseLong(expireTimeStr);
            } catch (Exception e) {
                LOGGER.warn("unable to parse 'expireTimeMs' servlet parameter: " + e.getMessage());
            }
        }

        LOGGER.trace("using resource expire time of " + TimeDuration.asCompactString(expireTime));
    }

    protected void doHead(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException
    {
        processRequest(request, response, false);
    }

    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException
    {
        processRequest(request, response, true);
    }

    private void processRequest
            (final HttpServletRequest request, final HttpServletResponse response, final boolean content)
            throws IOException
    {
        LOGGER.trace(PwmSession.getPwmSession(request), Helper.debugHttpRequest(request));

        final File file = resolveRequestedFile(request);

        if (file == null || !file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        final String fileName = file.getName();
        final String eTag = makeETag(request,file);

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
        response.setBufferSize(DEFAULT_BUFFER_SIZE);
        response.setHeader("ETag", eTag);
        response.setDateHeader("Expires", System.currentTimeMillis() + expireTime);

        // Prepare streams.
        RandomAccessFile input = null;
        OutputStream output = null;

        try {
            // Open streams.
            input = new RandomAccessFile(file, "r");
            output = response.getOutputStream();
            response.setContentType(contentType);

            if (content) {
                if (acceptsGzip) {
                    // The browser accepts GZIP, so GZIP the content.
                    response.setHeader("Content-Encoding", "gzip");
                    output = new GZIPOutputStream(output, DEFAULT_BUFFER_SIZE);
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
     * @param acceptHeader The accept header.
     * @param toAccept The value to be accepted.
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
     * @param matchHeader The match header.
     * @param toMatch The value to be matched.
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
     * @param input The input to copy the given range to the given output for.
     * @param output The output to copy the given range from the given input for.
     * @throws IOException If something fails at I/O level.
     */
    private static void copy(final RandomAccessFile input, final OutputStream output)
            throws IOException
    {
        final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;

        while ((read = input.read(buffer)) > 0) {
            output.write(buffer, 0, read);
        }
    }

    /**
     * Close the given resource.
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
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        sb.append(PwmConstants.BUILD_NUMBER);
        sb.append("-");
        sb.append(pwmSession.getContextManager().getStartupTime().getTime());
        sb.append("-");
        sb.append(file.length());
        sb.append("-");
        sb.append(file.lastModified());
        return sb.toString();
    }

    private static File resolveRequestedFile(final HttpServletRequest request)
            throws UnsupportedEncodingException
    {
        final ServletContext servletContext = request.getSession().getServletContext();

        // Get requested file by path info.
        final String requestURI = request.getRequestURI();
        final String requestFileURI = requestURI.substring(request.getContextPath().length(),requestURI.length());

        // URL-decode the file name (might contain spaces and on) and prepare file object.
        final String filename = URLDecoder.decode(requestFileURI, "UTF-8");
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
}
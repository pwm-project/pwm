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

package password.pwm.http.filter;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmURL;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

/**
 * GZip Filter Wrapper.  This filter must be invoked _before_ a PwmRequest object is instantiated, else
 * it will cache a reference to the original response and break the application.
 */
public class GZIPFilter implements Filter {
    private static final PwmLogger LOGGER = PwmLogger.forClass(GZIPFilter.class);

    public void init(FilterConfig filterConfig)
            throws ServletException
    {
    }

    public void destroy()
    {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException
    {
        final String acceptEncoding = ((HttpServletRequest)servletRequest).getHeader(PwmConstants.HttpHeader.Accept_Encoding.getHttpName());
        if (acceptEncoding != null && acceptEncoding.contains("gzip") && isEnabled(servletRequest)) {
            GZIPHttpServletResponseWrapper gzipResponse = new GZIPHttpServletResponseWrapper((HttpServletResponse)servletResponse);
            gzipResponse.addHeader("Content-Encoding", "gzip");
            filterChain.doFilter(servletRequest, gzipResponse);
            gzipResponse.finish();

        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    private boolean isEnabled(final ServletRequest servletRequest) {

        try {
            final PwmURL pwmURL = new PwmURL((HttpServletRequest) servletRequest);
            if (pwmURL.isResourceURL() || pwmURL.isWebServiceURL()) {
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("unable to parse request url, defaulting to non-gzip: " + e.getMessage());
        }

        final PwmApplication pwmApplication;
        try {
            pwmApplication = ContextManager.getPwmApplication((HttpServletRequest) servletRequest);
            return Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_ENABLE_GZIP));
        } catch (PwmUnrecoverableException e) {
            LOGGER.trace("unable to read http-gzip app-property, defaulting to non-gzip: " + e.getMessage());
        }
        return false;
    }


    public static class GZIPHttpServletResponseWrapper extends HttpServletResponseWrapper {
        private ServletResponseGZIPOutputStream gzipStream;
        private ServletOutputStream outputStream;
        private PrintWriter printWriter;

        public GZIPHttpServletResponseWrapper(HttpServletResponse response) throws IOException {
            super(response);
        }

        public void finish() throws IOException {
            if (printWriter != null) {
                printWriter.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (gzipStream != null) {
                gzipStream.close();
            }
        }

        @Override
        public void flushBuffer() throws IOException {
            if (printWriter != null) {
                printWriter.flush();
            }
            if (outputStream != null) {
                outputStream.flush();
            }
            super.flushBuffer();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (printWriter != null) {
                throw new IllegalStateException("getWriter() has previously been invoked, can not call getOutputStream()");
            }
            if (outputStream == null) {
                initGzip();
                outputStream = gzipStream;
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (outputStream != null) {
                throw new IllegalStateException("getOutputStream() has previously been invoked, can not call getWriter()");
            }
            if (printWriter == null) {
                initGzip();
                printWriter = new PrintWriter(new OutputStreamWriter(gzipStream, getResponse().getCharacterEncoding()));
            }
            return printWriter;
        }

        @Override
        public void setContentLength(int len) {
        }

        private void initGzip() throws IOException {
            gzipStream = new ServletResponseGZIPOutputStream(getResponse().getOutputStream());
        }
    }

    public static class ServletResponseGZIPOutputStream extends ServletOutputStream {
        private final AtomicBoolean open = new AtomicBoolean(true);
        private ServletOutputStream servletOutputStream;
        private GZIPOutputStream gzipStream;

        public ServletResponseGZIPOutputStream(ServletOutputStream output) throws IOException {
            servletOutputStream = output;
            gzipStream = new GZIPOutputStream(output);
        }

        @Override
        public void close() throws IOException {
            if (open.compareAndSet(true, false)) {
                gzipStream.close();
            }
        }

        @Override
        public void flush() throws IOException {
            gzipStream.flush();
        }

        @Override
        public void write(final byte b[]) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(final byte b[], final int off, final int len) throws IOException {
            if (!open.get()) {
                throw new IOException("Stream closed!");
            }
            gzipStream.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            if (!open.get()) {
                throw new IOException("Stream closed!");
            }
            gzipStream.write(b);
        }

        /*
        // servlet 3.1 method
        public void setWriteListener(WriteListener writeListener)
        {
            servletOutputStream.setWriteListener(writeListener);
        }

        // servlet 3.1 method
        public boolean isReady()
        {
            return servletOutputStream.isReady();
        }
        */
    }
}

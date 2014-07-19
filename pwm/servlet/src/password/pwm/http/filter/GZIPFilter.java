/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

public class GZIPFilter implements Filter {
    private ServletContext servletContext;

    public void init(FilterConfig filterConfig)
            throws ServletException
    {
        this.servletContext = filterConfig.getServletContext();
    }

    public void destroy()
    {
    }

    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    )
            throws IOException, ServletException
    {
        final HttpServletRequest httpRequest = (HttpServletRequest) request;
        final HttpServletResponse httpResponse = (HttpServletResponse) response;
        final String acceptEncoding = httpRequest.getHeader("Accept-Encoding");
        if (acceptEncoding != null && acceptEncoding.contains("gzip") && isEnabled()) {
            GZIPHttpServletResponseWrapper gzipResponse = new GZIPHttpServletResponseWrapper(httpResponse);
            chain.doFilter(request, gzipResponse);
            gzipResponse.finish();
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean isEnabled() {
        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(servletContext);
            return Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_ENABLE_GZIP));
        } catch (PwmUnrecoverableException e) {
            /* noop */
        }
        return false;
    }


    public static class GZIPHttpServletResponseWrapper extends HttpServletResponseWrapper {
        private ServletResponseGZIPOutputStream gzipStream;
        private ServletOutputStream outputStream;
        private PrintWriter printWriter;

        public GZIPHttpServletResponseWrapper(HttpServletResponse response) throws IOException {
            super(response);
            response.addHeader("Content-Encoding", "gzip");
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
        private GZIPOutputStream gzipStream;

        public ServletResponseGZIPOutputStream(OutputStream output) throws IOException {
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
        public void write(byte b[]) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
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
    }
}

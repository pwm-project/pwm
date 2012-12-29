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

package password.pwm;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.zip.GZIPOutputStream;

public class GZIPFilter implements Filter {

    public void doFilter(ServletRequest req, ServletResponse res,
                         FilterChain chain) throws IOException, ServletException {
        if (req instanceof HttpServletRequest) {
            final HttpServletRequest request = (HttpServletRequest) req;
            final HttpServletResponse response = (HttpServletResponse) res;
            final String acceptEncodingHeader = request.getHeader("accept-encoding");
            if (PwmConstants.SERVLET_FILTER_ENABLE_GZIP && acceptEncodingHeader != null && acceptEncodingHeader.contains("gzip")) {
                final GZIPResponseWrapper wrappedResponse = new GZIPResponseWrapper(response);
                chain.doFilter(req, wrappedResponse);
                wrappedResponse.finishResponse();
                return;
            }
            chain.doFilter(req, res);
        }
    }

    public void init(FilterConfig filterConfig) {
        // noop
    }

    public void destroy() {
        // noop
    }

    public static class GZIPResponseWrapper extends HttpServletResponseWrapper {
        protected HttpServletResponse origResponse = null;
        protected ServletOutputStream stream = null;
        protected PrintWriter writer = null;

        public GZIPResponseWrapper(HttpServletResponse response) {
            super(response);
            origResponse = response;
        }

        public ServletOutputStream createOutputStream() throws IOException {
            return (new GZIPResponseStream(origResponse));
        }

        public void finishResponse() {
            try {
                if (writer != null) {
                    writer.close();
                } else {
                    if (stream != null) {
                        stream.close();
                    }
                }
            } catch (IOException e) {
                //noop
            }
        }

        public void flushBuffer() throws IOException {
            stream.flush();
        }

        public ServletOutputStream getOutputStream() throws IOException {
            if (writer != null) {
                throw new IllegalStateException("getWriter() has already been called!");
            }

            if (stream == null)
                stream = createOutputStream();
            return (stream);
        }

        public PrintWriter getWriter() throws IOException {
            if (writer != null) {
                return (writer);
            }

            if (stream != null) {
                throw new IllegalStateException("getOutputStream() has already been called!");
            }

            stream = createOutputStream();
            writer = new PrintWriter(new OutputStreamWriter(stream, "UTF-8"));
            return (writer);
        }

        public void setContentLength(int length) {}
    }

    public static class GZIPResponseStream extends ServletOutputStream {
        protected ByteArrayOutputStream baos = null;
        protected GZIPOutputStream gzipstream = null;
        protected boolean closed = false;
        protected HttpServletResponse response = null;
        protected ServletOutputStream output = null;

        public GZIPResponseStream(HttpServletResponse response) throws IOException {
            super();
            closed = false;
            this.response = response;
            this.output = response.getOutputStream();
            baos = new ByteArrayOutputStream();
            gzipstream = new GZIPOutputStream(baos);
        }

        public void close() throws IOException {
            if (closed) {
                throw new IOException("This output stream has already been closed");
            }
            gzipstream.finish();

            byte[] bytes = baos.toByteArray();


            response.addHeader("Content-Length",
                    Integer.toString(bytes.length));
            response.addHeader("Content-Encoding", "gzip");
            output.write(bytes);
            output.flush();
            output.close();
            closed = true;
        }

        public void flush() throws IOException {
            if (closed) {
                throw new IOException("Cannot flush a closed output stream");
            }
            gzipstream.flush();
        }

        public void write(int b) throws IOException {
            if (closed) {
                throw new IOException("Cannot write to a closed output stream");
            }
            gzipstream.write((byte)b);
        }

        public void write(byte b[]) throws IOException {
            write(b, 0, b.length);
        }

        public void write(byte b[], int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Cannot write to a closed output stream");
            }
            gzipstream.write(b, off, len);
        }

        public boolean closed() {
            return (this.closed);
        }

        public void reset() {
            //noop
        }
    }

}
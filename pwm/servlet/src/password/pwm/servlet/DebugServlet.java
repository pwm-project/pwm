/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.servlet;

import password.pwm.util.PwmLogger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;

/**
 * @author Jason D. Rivard
 */
public class DebugServlet extends HttpServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(DebugServlet.class);

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Servlet ---------------------

    public void init(final ServletConfig config)
            throws ServletException
    {
        super.init(config);
    }

// -------------------------- OTHER METHODS --------------------------

    public void doGet(
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws ServletException, IOException
    {
        doService(request, response);
    }

    public static void doService(
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws ServletException, IOException
    {                                              
        try {
            response.setContentType("text/html");

            final StringBuilder htmlOutput = new StringBuilder();
            final StringBuilder debugOutput = new StringBuilder();

            debugOutput.append("DebugServlet invoked; output: ");

            htmlOutput.append("<html>");
            htmlOutput.append("<p><center><b><FONT SIZE=+2>DebugServlet</FONT></b></center>");

            //Cookies
            htmlOutput.append("<p><b>Cookies</b><br>");
            htmlOutput.append("<table border=1 >");
            htmlOutput.append("<tr><th>Name</th><th>Value</th></tr>");

            debugOutput.append("**Cookies**");
            final Cookie[] cookies = request.getCookies();
            Cookie cookie;
            if (cookies != null) {
                for (final Cookie cooky : cookies) {
                    cookie = cooky;
                    htmlOutput.append("<tr><td>");
                    htmlOutput.append(cookie.getName());
                    htmlOutput.append("</td><td>");
                    htmlOutput.append(cookie.getValue());
                    htmlOutput.append("</td><tr>");
                    debugOutput.append(cookie.getName() + ": " + cookie.getValue());
                }
            }
            htmlOutput.append("</table>");

            //Headers
            htmlOutput.append("<p><b>Request Headers</b><br>");
            htmlOutput.append("<table border=1 >");
            htmlOutput.append("<tr><th>Name</th><th>Value</th></tr>");
            debugOutput.append("**Request Headers**");

            for (final Enumeration headers = request.getHeaderNames(); headers.hasMoreElements();) {
                final String headerName = (String) headers.nextElement();
                final String headerValue = request.getHeader(headerName);

                htmlOutput.append("<tr><td>");
                htmlOutput.append(headerName);
                htmlOutput.append("</td><td>");
                htmlOutput.append(headerValue);
                htmlOutput.append("</td><tr>");
                debugOutput.append(headerName + ": " + headerValue);
            }

            htmlOutput.append("</table>");

            //Method (GET/POST)
            htmlOutput.append("<p><b>Method</b><br>");
            debugOutput.append("**Method**");
            htmlOutput.append("Method: " + request.getMethod());
            debugOutput.append("Method: " + request.getMethod());
            htmlOutput.append("<br><input type=submit name=Submit value=Submit><br>");

            //Parameters
            htmlOutput.append("<p><b>Request Parameters</b><br>");
            htmlOutput.append("<table border=1 >");
            htmlOutput.append("<tr><th>Name</th><th>Value</th></tr>");
            debugOutput.append("**Request Parameters**");
            for (Enumeration parameters = request.getParameterNames(); parameters.hasMoreElements();) {
                final String parameterName = (String) parameters.nextElement();
                final String parameterValue = request.getParameter(parameterName);
                htmlOutput.append("<tr><td>");
                htmlOutput.append(parameterName);
                htmlOutput.append("</td><td>");
                htmlOutput.append(parameterValue);
                htmlOutput.append("</td><tr>");
                debugOutput.append(parameterName + ": " + parameterValue);
            }
            htmlOutput.append("</table>");

            //PathInfo
            htmlOutput.append("<p><b>Path Info</b><br>");
            debugOutput.append("**Path Info**");
            htmlOutput.append("Path Info: " + request.getPathInfo());
            debugOutput.append("Path Info: " + request.getPathInfo());

            //Path Translated
            htmlOutput.append("<p><b>Path Translated</b><br>");
            debugOutput.append("**Path Translated**");
            htmlOutput.append("Path Translated: " + request.getPathTranslated());
            debugOutput.append("Path Translated: " + request.getPathTranslated());

            //Query String
            htmlOutput.append("<p><b>Query String</b><br>");
            debugOutput.append("**Query String**");
            htmlOutput.append("Query String: " + request.getQueryString());
            debugOutput.append("Query String: " + request.getQueryString());

            //Request URI
            htmlOutput.append("<p><b>Request URI</b><br>");
            debugOutput.append("**Request URI**");
            htmlOutput.append("Request URI: " + request.getRequestURI());
            debugOutput.append("Request URI: " + request.getRequestURI());

            //Servlet Path
            htmlOutput.append("<p><b>Servlet Path</b><br>");
            debugOutput.append("**Servlet Path**");
            htmlOutput.append("Servlet Path: " + request.getServletPath());
            debugOutput.append("Servlet Path: " + request.getServletPath());

            // -- UIBean --
            htmlOutput.append("<hr/>");

            htmlOutput.append("</form></html>");

            //Output the data to response
            response.getOutputStream().print(htmlOutput.toString());

            //Output the data to debugger.
            LOGGER.debug(debugOutput.toString());
        } catch (Throwable e) {
            LOGGER.error("unexpecte error building output",e);
        }
    }

    public void doPost(
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws ServletException, IOException
    {
        doService(request, response);
    }
}
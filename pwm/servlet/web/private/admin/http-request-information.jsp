<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.util.stats.StatisticsManager" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.Enumeration" %>
<%@ page import="password.pwm.Validator" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2011 The PWM Project
  ~
  ~ This program is free software; you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation; either version 2 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="HTTP Request Information"/>
</jsp:include>
<div id="wrapper">
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <table>
            <tr>
                <td colspan="10" class="title">
                    Request Information
                </td>
            </tr>
            <tr>
                <td class="key">
                    Request Method
                </td>
                <td>
                    <%= request.getMethod() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Path Info
                </td>
                <td>
                    <%= request.getPathInfo() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Path Translated
                </td>
                <td>
                    <%= request.getPathTranslated() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Query String
                </td>
                <td>
                    <%= request.getQueryString() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Request URI
                </td>
                <td>
                    <%= request.getRequestURI() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Servlet Path
                </td>
                <td>
                    <%= request.getServletPath() %>
                </td>
            </tr>
        </table>
        <br class="clear"/>
        <% if (request.getCookies() != null && request.getCookies().length > 0) { %>
        <table>
            <tr>
                <td colspan="10" class="title">
                    Cookies
                </td>
            </tr>
            <% for (final Cookie cookie : request.getCookies()) { %>
            <tr>
                <td class="key">
                    <%= Validator.sanatizeInputValue(ContextManager.getPwmApplication(session).getConfig(), cookie.getName(), 1024) %>
                </td>
                <td>
                    <%= Validator.sanatizeInputValue(ContextManager.getPwmApplication(session).getConfig(), cookie.getValue(), 1024) %>
                </td>
            </tr>
            <% } %>
        </table>
        <br class="clear"/>
        <% } %>
        <% if (request.getHeaderNames() != null && request.getHeaderNames().hasMoreElements()) { %>
        <table>
            <tr>
                <td colspan="10" class="title">
                    Headers
                </td>
            </tr>
            <% for (final Enumeration headerEnum = request.getHeaderNames(); headerEnum.hasMoreElements();) { %>
            <% final String loopHeader = (String) headerEnum.nextElement(); %>
            <% for (final Enumeration valueEnum = request.getHeaders(loopHeader); valueEnum.hasMoreElements();) { %>
            <% final String loopValue = (String) valueEnum.nextElement(); %>
            <tr>
                <td class="key">
                    <%= Validator.sanatizeInputValue(ContextManager.getPwmApplication(session).getConfig(), loopHeader, 1024) %>
                </td>
                <td>
                    <%= Validator.sanatizeInputValue(ContextManager.getPwmApplication(session).getConfig(), loopValue, 1024) %>
                </td>
            </tr>
            <% } %>
            <% } %>
        </table>
        <br class="clear"/>
        <% } %>
        <% if (request.getParameterNames() != null && request.getParameterNames().hasMoreElements()) { %>
        <table>
            <tr>
                <td colspan="10" class="title">
                    Parameters
                </td>
            </tr>
            <% for (final Enumeration parameterEnum = request.getParameterNames(); parameterEnum.hasMoreElements();) { %>
            <% final String loopParameter = (String) parameterEnum.nextElement(); %>
            <% for (final String loopValue : Validator.readStringsFromRequest(request, loopParameter, 1024)) { %>
            <tr>
                <td class="key">
                    <%= Validator.sanatizeInputValue(ContextManager.getPwmApplication(session).getConfig(), loopParameter, 1024) %>
                </td>
                <td>
                    <%= Validator.sanatizeInputValue(ContextManager.getPwmApplication(session).getConfig(), loopValue, 1024) %>
                </td>
            </tr>
            <% } %>
            <% } %>
        </table>
        <% } %>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>



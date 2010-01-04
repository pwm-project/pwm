<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.util.stats.StatisticsManager" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.Enumeration" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body onunload="unloadHandler();">
<jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="HTTP Request Information"/></jsp:include>
<div id="wrapper">
    <div id="centerbody">
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
                    <%= cookie.getName() %>
                </td>
                <td>
                    <%= cookie.getValue() %>
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
            <% for (final Enumeration headerEnum = request.getHeaderNames(); headerEnum.hasMoreElements(); ) { %>
            <% final String loopHeader = (String)headerEnum.nextElement(); %>
            <% for (final Enumeration valueEnum = request.getHeaders(loopHeader); valueEnum.hasMoreElements(); ) { %>
            <% final String loopValue = (String)valueEnum.nextElement(); %>
            <tr>
                <td class="key">
                    <%= loopHeader %>
                </td>
                <td>
                    <%= loopValue %>
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
            <% for (final Enumeration parameterEnum = request.getParameterNames(); parameterEnum.hasMoreElements(); ) { %>
            <% final String loopParameter = (String)parameterEnum.nextElement(); %>
            <% for (final String loopValue : request.getParameterValues(loopParameter)) { %>
            <tr>
                <td class="key">
                    <%= loopParameter %>
                </td>
                <td>
                    <%= loopValue %>
                </td>
            </tr>
            <% } %>
            <% } %>
        </table>
        <% } %>
    </div>
</div>
<%@ include file="../jsp/footer.jsp" %>
</body>
</html>



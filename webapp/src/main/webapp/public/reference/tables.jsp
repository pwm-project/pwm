<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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

<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.svc.event.AuditEvent" %>
<%@ page import="password.pwm.svc.stats.Statistic" %>
<%@ page import="java.util.Locale" %>

<!DOCTYPE html>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final Locale userLocale = JspUtility.locale(request); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Errors, Audit Events and Statistics"/>
    </jsp:include>
    <div id="centerbody" style="width: 800px">
        <%@ include file="reference-nav.jsp"%>

        <ol>
            <li><a href="#eventStatistics">Event Statistics</a></li>
            <li><a href="#auditEvents">Audit Events</a></li>
            <li><a href="#errors">Errors</a></li>
        </ol>
        <br/>
        <h1><a id="eventStatistics">Event Statistics</a></h1>
        <table>
            <tr>
                <td>
                    <h3>Label</h3>
                </td>
                <td>
                    <h3>Key</h3>
                </td>
                <td>
                    <h3>Description</h3>
                </td>
            </tr>
            <% for (final Statistic statistic : Statistic.sortedValues(userLocale)) { %>
            <tr>
                <td>
                    <%=statistic.getLabel(userLocale)%>
                </td>
                <td>
                    <%=statistic.name()%>
                </td>
                <td>
                    <%=statistic.getDescription(userLocale)%>
                </td>
            </tr>
            <% } %>
        </table>
        <h1><a id="auditEvents">Audit Events</a></h1>
        <table>
            <tr>
                <td>
                    <h3>Key</h3>
                </td>
                <td>
                    <h3>Type</h3>
                </td>
                <td>
                    <h3>Resource Key</h3>
                </td>
                <td>
                    <h3>Label</h3>
                </td>
            </tr>
            <% for (final AuditEvent auditEvent : AuditEvent.values()) { %>
            <tr>
                <td>
                    <%= auditEvent.toString() %>
                </td>
                <td>
                    <%= auditEvent.getType() %>
                </td>
                <td>
                    <%= auditEvent.getMessage().getKey() %>
                </td>
                <td>
                    <%= auditEvent.getMessage().getLocalizedMessage(userLocale, null) %>
                </td>
            </tr>
            <% } %>
        </table>
        <h1><a id="errors">Errors</a></h1>
        <table class="tablemain">
            <tr>
                <td>
                    <h3>Error Number</h3>
                </td>
                <td>
                    <h3>Key</h3>
                </td>
                <td>
                    <h3>Resource Key</h3>
                </td>
                <td>
                    <h3>Message</h3>
                </td>
            </tr>
            <% for (final PwmError error : PwmError.values()) { %>
            <tr>
                <td>
                    <%=error.getErrorCode()%>
                </td>
                <td >
                    <%=error.toString()%>
                </td>
                <td>
                    <%=error.getResourceKey()%>
                </td>
                <td>
                    <%=error.getLocalizedMessage(userLocale, null)%>
                </td>
            </tr>
            <% } %>
        </table>
        <div class="push"></div>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>

<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
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

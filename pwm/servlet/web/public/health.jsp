<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2010 The PWM Project
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

<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.health.HealthMonitor" %>
<%@ page import="password.pwm.health.HealthRecord" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<% final ContextManager contextManager = ContextManager.getContextManager(this.getServletConfig().getServletContext()); %>
<% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, request.getLocale()); %>
<% password.pwm.PwmSession.getPwmSession(session).unauthenticateUser(); %>
<body class="tundra">
<meta http-equiv="refresh" content="60"/>
<div id="wrapper">
    <div id="centerbody">
        <table class="tablemain">
            <tr>
                <td class="title" colspan="10">
                    <pwm:Display key="APPLICATION-TITLE"/> Health
                </td>
            </tr>
            <%
                final HealthMonitor healthMonitor = contextManager.getHealthMonitor();
                final List<HealthRecord> healthRecords = healthMonitor.getHealthRecords();
            %>
            <tr>
                <td colspan="15" style="text-align:center">
                    <%= healthMonitor.getLastHealthCheckDate() == null ? "" : "Last health check performed at " + dateFormat.format(healthMonitor.getLastHealthCheckDate()) %>
                </td>
            </tr>
            <% for (final HealthRecord healthRecord : healthRecords) { %>
            <%
                final String color;
                switch (healthRecord.getHealthStatus()) {
                    case GOOD:
                        color = "#8ced3f";
                        break;
                    case CAUTION:
                        color = "#FFCD59";
                        break;
                    case WARN:
                        color = "#d20734";
                        break;
                    default:
                        color = "white";
                }
            %>
            <tr>
                <td class="key">
                    <%= healthRecord.getTopic() %>
                </td>
                <td width="5%" style="background-color: <%=color%>">
                    <%= healthRecord.getHealthStatus() %>
                </td>
                <td>
                    <%= healthRecord.getDetail() %>
                </td>
            </tr>
            <% } %>
        </table>
        <p style="text-align:center;">
            <a href="<%=request.getContextPath()%>">PWM Main Menu</a> | <a
                href="<%=request.getContextPath()%>/admin/status.jsp">Admin Menu</a>
        </p>
    </div>
</div>
</body>
</html>

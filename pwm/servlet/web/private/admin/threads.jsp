<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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

<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmApplication pwmApplication = ContextManager.getPwmApplication(session); %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="PWM Status"/>
</jsp:include>
<div id="centerbody">
<%@ include file="admin-nav.jsp" %>
<table class="tablemain">
    <tr>
        <td class="title" colspan="10">
            <a name="threads"></a>Java Threads
        </td>
    </tr>
    <tr>
        <td style="font-weight:bold;">
            Id
        </td>
        <td style="font-weight:bold;">
            Name
        </td>
        <td style="font-weight:bold;">
            Priority
        </td>
        <td style="font-weight:bold;">
            State
        </td>
        <td style="font-weight:bold;">
            Daemon
        </td>
    </tr>
    <%
        final Thread[] tArray = new Thread[Thread.activeCount()];
        Thread.enumerate(tArray);
        try {
            for (final Thread t : tArray) {
    %>
    <tr>
        <td>
            <%= t.getId() %>
        </td>
        <td>
            <%= t.getName() != null ? t.getName() : "n/a" %>
        </td>
        <td>
            <%= t.getPriority() %>
        </td>
        <td>
            <%= t.getState().toString().toLowerCase() %>
        </td>
        <td>
            <%= String.valueOf(t.isDaemon()) %>
        </td>
    </tr>
    <% } %>
    <% } catch (Exception e) { /* */ } %>
</table>
</div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>



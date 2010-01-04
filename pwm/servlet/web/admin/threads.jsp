<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
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
<%@ include file="../jsp/header.jsp" %>
<body onunload="unloadHandler();">
<div id="wrapper">
    <jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="Java Threads"/></jsp:include>
    <div id="centerbody" style="width:90%">
        <p style="text-align:center;">
            <a href="status.jsp">Status</a> | <a href="eventlog.jsp">Event Log</a> | <a href="intruderstatus.jsp">Intruder Status</a> | <a href="activesessions.jsp">Active Sessions</a> | <a href="config.jsp">Configuration</a> | <a href="threads.jsp">Threads</a> | <a href="UserInformation">User Information</a>
        </p>
        <br/>
        <table class="tablemain">
            <tr>
                <td class="title">
                    Iteration
                </td>
                <td class="title">
                    Name
                </td>
                <td class="title">
                    Prioirty
                </td>
                <td class="title">
                    State
                </td>
                <td class="title">
                    Daemon
                </td>
            </tr>
            <%
                final Thread[] tArray = new Thread[Thread.activeCount()];
                Thread.enumerate(tArray);
                try {
                    for (int i = 0; i < tArray.length; i++) {
                        final Thread t = tArray[i];
            %>
            <tr>
                <td>
                    <%= i %>
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
            <% } catch (Exception e) {  e.printStackTrace(); } %>
        </table>
    </div>
</div>
<%@ include file="../jsp/footer.jsp" %>
</body>
</html>
